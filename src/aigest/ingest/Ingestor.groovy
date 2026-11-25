package aigest.ingest

import aigest.cli.CliOptions

import aigest.config.Config
import aigest.config.Config.ConfigFile
import aigest.config.Config.ResolvedConfig
import aigest.config.ConfigLoader

import aigest.git.GitLogReader

import aigest.output.Formatter
import aigest.output.OutputWriter

import aigest.rules.RuleEngine

import aigest.util.PathUtils
import aigest.util.Result

import aigest.walk.BinaryDetector
import aigest.walk.FileWalker
import aigest.walk.GitIgnoreFilter

import groovy.transform.CompileStatic

import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class Ingestor {

    private Ingestor() { }

    static abstract class FileEntry {

        final String relativePath

        protected FileEntry(String relativePath) {
            this.relativePath = relativePath
        }

        abstract <R> R fold(Closure<R> onBinary, Closure<R> onText)

    }

    static final class BinaryFile extends FileEntry {

        BinaryFile(String relativePath) {
            super(relativePath)
        }

        @Override
        <R> R fold(Closure<R> onBinary, Closure<R> onText) {
            onBinary.call(this)
        }

    }

    static final class TextFile extends FileEntry {

        final String content

        TextFile(String relativePath, String content) {
            super(relativePath)
            this.content = content
        }

        @Override
        <R> R fold(Closure<R> onBinary, Closure<R> onText) {
            onText.call(this)
        }

    }

    static abstract class GitLogs {

        private GitLogs() { }

        abstract <R> R fold(
            Closure<R> onDisabled,
            Closure<R> onUnavailable,
            Closure<R> onAvailable
        )

        boolean isAvailable() {
            this.<Boolean>fold(
                { false },
                { String ignored -> false },
                { String ignored -> true }
            ).booleanValue()
        }

        static final class Disabled extends GitLogs {

            public static final Disabled INSTANCE = new Disabled()

            private Disabled() { }

            @Override
            <R> R fold(
                Closure<R> onDisabled,
                Closure<R> onUnavailable,
                Closure<R> onAvailable
            ) {
                onDisabled.call()
            }

        }

        static final class Unavailable extends GitLogs {

            final String reason

            Unavailable(String reason) {
                this.reason = reason
            }

            @Override
            <R> R fold(
                Closure<R> onDisabled,
                Closure<R> onUnavailable,
                Closure<R> onAvailable
            ) {
                onUnavailable.call(reason)
            }

        }

        static final class Available extends GitLogs {

            final String content

            Available(String content) {
                this.content = content
            }

            @Override
            <R> R fold(
                Closure<R> onDisabled,
                Closure<R> onUnavailable,
                Closure<R> onAvailable
            ) {
                onAvailable.call(content)
            }

        }

    }

    static final class IngestStats {

        final int totalFiles
        final int textFiles
        final int binaryFiles
        final long totalChars

        IngestStats(int totalFiles, int textFiles, int binaryFiles, long totalChars) {
            this.totalFiles = totalFiles
            this.textFiles = textFiles
            this.binaryFiles = binaryFiles
            this.totalChars = totalChars
        }

        static IngestStats fromEntries(List<FileEntry> entries) {
            int textCount = 0
            int binaryCount = 0
            long charCount = 0L

            for (FileEntry entry : entries) {
                entry.fold(
                    { BinaryFile b ->
                        binaryCount++
                    },
                    { TextFile t ->
                        textCount++
                        charCount += t.content.length()
                    }
                )
            }

            new IngestStats(entries.size(), textCount, binaryCount, charCount)
        }

    }

    static final class IngestBundle {

        final Path repoRoot
        final String activeProfile
        final List<FileEntry> entries
        final GitLogs gitLogs
        final IngestStats stats

        IngestBundle(
            Path repoRoot,
            String activeProfile,
            List<FileEntry> entries,
            GitLogs gitLogs,
            IngestStats stats
        ) {
            this.repoRoot = repoRoot
            this.activeProfile = activeProfile
            this.entries = Collections.unmodifiableList(new ArrayList<FileEntry>(entries))
            this.gitLogs = gitLogs
            this.stats = stats
        }

    }

    static final class IngestReport {

        final Path outputPath
        final String activeProfile
        final IngestStats stats
        final boolean gitLogsIncluded

        IngestReport(
            Path outputPath,
            String activeProfile,
            IngestStats stats,
            boolean gitLogsIncluded
        ) {
            this.outputPath = outputPath
            this.activeProfile = activeProfile
            this.stats = stats
            this.gitLogsIncluded = gitLogsIncluded
        }

    }

    static Result<IngestBundle, String> collectBundle(CliOptions options) {
        validateRepoRoot(options.repoRoot).flatMap { Path root ->
            loadAndResolveConfig(options, root).flatMap { ResolvedConfig resolved ->
                walkAndRead(root, options.outputFile, resolved).map { List<FileEntry> entries ->
                    GitLogs logs = resolveGitLogs(root, options.includeGitLogs)
                    new IngestBundle(
                        root,
                        resolved.activeProfile,
                        entries,
                        logs,
                        IngestStats.fromEntries(entries)
                    )
                }
            }
        }
    }

    static Result<IngestReport, String> run(CliOptions options) {
        collectBundle(options).flatMap { IngestBundle bundle ->
            String rendered = Formatter.format(bundle)
            Result.<Path>trying("writing ${options.outputFile}") {
                OutputWriter.write(options.outputFile, rendered)
            }.map { Path written ->
                new IngestReport(
                    written,
                    bundle.activeProfile,
                    bundle.stats,
                    bundle.gitLogs.isAvailable()
                )
            }
        }
    }

    private static Result<Path, String> validateRepoRoot(Path repoRoot) {
        repoRoot == null || !Files.exists(repoRoot)
            ? Result.<Path, String>err("Repository directory does not exist: ${repoRoot}")
            : (!Files.isDirectory(repoRoot)
                ? Result.<Path, String>err("Target path is not a directory: ${repoRoot}")
                : Result.<Path, String>ok(repoRoot))
    }

    private static Result<ResolvedConfig, String> loadAndResolveConfig(
        CliOptions options,
        Path repoRoot
    ) {
        Result.zip(
            ConfigLoader.load(options.globalConfigPath),
            ConfigLoader.loadRepo(repoRoot)
        ) { ConfigFile globalCfg, ConfigFile repoCfg ->
            Config.resolve(globalCfg, repoCfg, options.profileName)
        }.flatMap { Result<ResolvedConfig, String> resolved ->
            resolved
        }
    }

    private static Result<List<FileEntry>, String> walkAndRead(
        Path repoRoot,
        Path outputFile,
        ResolvedConfig resolved
    ) {
        RuleEngine rules = RuleEngine.fromConfig(resolved)

        GitIgnoreFilter gitignore = rules.hasIncludeOnly()
            ? GitIgnoreFilter.EMPTY
            : GitIgnoreFilter.load(repoRoot)
        Path normalizedOutput = outputFile.toAbsolutePath().normalize()

        FileWalker.collect(repoRoot, rules, gitignore).flatMap { List<Path> paths ->
            List<Result<FileEntry, String>> readResults = paths
                .findAll { Path p -> p.toAbsolutePath().normalize() != normalizedOutput }
                .collect { Path p -> readEntry(repoRoot, p) }

            Result.sequence(readResults)
        }
    }

    private static Result<FileEntry, String> readEntry(Path repoRoot, Path file) {
        String rel = PathUtils.toNormalizedRelative(repoRoot, file)

        Result.<FileEntry>trying("reading ${rel}") {
            if (BinaryDetector.isBinaryFile(file)) {
                return (FileEntry) new BinaryFile(rel)
            }

            try {
                String content = Files.readString(file, StandardCharsets.UTF_8)
                return (FileEntry) new TextFile(rel, content)
            } catch (CharacterCodingException ignored) {
                return (FileEntry) new BinaryFile(rel)
            }
        }
    }

    private static GitLogs resolveGitLogs(Path repoRoot, boolean includeGitLogs) {
        !includeGitLogs
            ? GitLogs.Disabled.INSTANCE
            : GitLogReader.read(repoRoot).<GitLogs>fold(
                { String err -> (GitLogs) new GitLogs.Unavailable(err) },
                { String logText ->
                    logText.isEmpty()
                        ? (GitLogs) new GitLogs.Unavailable('no git commits found')
                        : (GitLogs) new GitLogs.Available(logText)
                }
            )
    }

}

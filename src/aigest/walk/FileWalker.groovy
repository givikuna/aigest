package aigest.walk

import aigest.rules.RuleEngine

import aigest.util.PathUtils
import aigest.util.Result

import groovy.transform.CompileStatic

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

@CompileStatic
final class FileWalker {

    private FileWalker() { }

    static Result<List<Path>, String> collect(
        Path repoRoot,
        RuleEngine rules,
        GitIgnoreFilter gitignore
    ) {
        return Result.<List<Path>>trying("walking ${repoRoot}") {
            List<Path> collected = new ArrayList<>()

            boolean useGitIgnore = !rules.hasIncludeOnly()

            Files.walkFileTree(repoRoot, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String rel = PathUtils.toNormalizedRelative(repoRoot, dir)

                    if (rel.isEmpty()) {
                        return FileVisitResult.CONTINUE
                    }

                    if (dir.fileName != null && dir.fileName.toString() == '.git') {
                        return FileVisitResult.SKIP_SUBTREE
                    }

                    if (useGitIgnore && gitignore.isIgnored(rel + '/dummy')) {
                        return FileVisitResult.SKIP_SUBTREE
                    }

                    if (rules.shouldSkipDirectory(rel)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }

                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE

                    String rel = PathUtils.toNormalizedRelative(repoRoot, file)

                    if (useGitIgnore && gitignore.isIgnored(rel)) return FileVisitResult.CONTINUE
                    if (rules.shouldIgnoreFile(rel)) return FileVisitResult.CONTINUE

                    collected.add(file)
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE
                }

            })

            collected.sort { Path a, Path b ->
                PathUtils.toNormalizedRelative(repoRoot, a) <=>
                PathUtils.toNormalizedRelative(repoRoot, b)
            }

            Collections.unmodifiableList(collected)
        }
    }

}

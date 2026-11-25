package aigest.git

import aigest.util.Result

import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.nio.file.Path

@CompileStatic
final class GitLogReader {

    public static final int DEFAULT_LIMIT = 50

    private GitLogReader() { }

    static Result<String, String> read(Path repoRoot) {
        read(repoRoot, DEFAULT_LIMIT)
    }

    static Result<String, String> read(Path repoRoot, int limit) {
        return Result.<String>trying("reading git log for ${repoRoot}") {
            ProcessBuilder pb = new ProcessBuilder(
                'git', '-C', repoRoot.toString(), 'log',
                '--pretty=format:%ai|%s',
                '--name-only',
                '-n', String.valueOf(limit)
            )
            Process proc = pb.start()

            String stdout = proc.inputStream.getText(StandardCharsets.UTF_8.name())
            String stderr = proc.errorStream.getText(StandardCharsets.UTF_8.name())

            int code = proc.waitFor()

            if (code != 0) {
                throw new IOException("git log exited ${code}: ${stderr.trim()}")
            }

            return stdout.trim()
        }
    }

}

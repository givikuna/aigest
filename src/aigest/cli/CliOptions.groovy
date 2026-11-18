package aigest.cli

import groovy.transform.CompileStatic
import java.nio.file.Path
import java.nio.file.Paths

@CompileStatic
final class CliOptions {

    final String profileName
    final Path repoRoot
    final Path outputFile
    final Path globalConfigPath
    final boolean includeGitLogs
    final boolean showHelp
    final boolean showVersion

    CliOptions(
        String profileName,
        Path repoRoot,
        Path outputFile,
        Path globalConfigPath,
        boolean includeGitLogs,
        boolean showHelp,
        boolean showVersion
    ) {
        this.profileName = profileName
        this.repoRoot = repoRoot.toAbsolutePath().normalize()
        this.outputFile = outputFile.toAbsolutePath().normalize()
        this.globalConfigPath = globalConfigPath.toAbsolutePath().normalize()
        this.includeGitLogs = includeGitLogs
        this.showHelp = showHelp
        this.showVersion = showVersion
    }

    static Path defaultGlobalConfigPath() {
        String xdgConfig = System.getenv('XDG_CONFIG_HOME')
        if (xdgConfig != null && !xdgConfig.trim().isEmpty()) {
            return Paths.get(xdgConfig, 'aigest', 'aigest.json')
        }
        String userHome = System.getProperty('user.home', '.')
        return Paths.get(userHome, '.config', 'aigest', 'aigest.json')
    }

}

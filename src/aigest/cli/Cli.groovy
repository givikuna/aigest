package aigest.cli

import groovy.transform.CompileStatic
import java.nio.file.Path
import java.nio.file.Paths

@CompileStatic
final class Cli {

    private Cli() { }

    static CliOptions parse(String[] args) {
        String profile = null
        Path repoRoot = Paths.get('.')
        Path outputFile = null
        Path globalConfig = CliOptions.defaultGlobalConfigPath()
        boolean includeGitLogs = false
        boolean showHelp = false
        boolean showVersion = false

        List<String> tokens = args != null ? Arrays.asList(args) : Collections.<String>emptyList()

        int i = 0

        while (i < tokens.size()) {
            String arg = tokens.get(i)
            switch (arg) {
                case '-h':
                case '--help':
                    showHelp = true
                    break
                case '-v':
                case '--version':
                    showVersion = true
                    break
                case '-o':
                case '--output':
                    if (i + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("Missing value for ${arg}")
                    }
                    i++
                    outputFile = Paths.get(tokens.get(i))
                    break
                case '-d':
                case '--dir':
                    if (i + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("Missing value for ${arg}")
                    }
                    i++
                    repoRoot = Paths.get(tokens.get(i))
                    break
                case '-c':
                case '--global-config':
                    if (i + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("Missing value for ${arg}")
                    }
                    i++
                    globalConfig = Paths.get(tokens.get(i))
                    break
                case '--no-logs':
                    includeGitLogs = false
                    break
                case '--include-logs':
                    includeGitLogs = true
                    break
                default:
                    if (arg.startsWith('-')) {
                        throw new IllegalArgumentException("Unknown option: ${arg}")
                    }
                    if (profile != null) {
                        throw new IllegalArgumentException(
                            "Multiple profiles specified ('${profile}' and '${arg}'). Only one profile can run at a time."
                        )
                    }
                    profile = arg
                    break
            }
            i++
        }

        if (outputFile == null) {
            String defaultName = (profile != null && !profile.isEmpty())
                ? "aigest-${profile}.xml"
                : 'aigest-output.xml'
            outputFile = repoRoot.resolve(defaultName)
        }

        return new CliOptions(
            profile,
            repoRoot,
            outputFile,
            globalConfig,
            includeGitLogs,
            showHelp,
            showVersion
        )
    }

    static String usage() {
        return '''
            Usage: aigest [profile] [options]

            Arguments:
            [profile]                 Optional profile name from aigest.json (runs main config if omitted)

            Options:
            -o, --output <path>       Output .xml file path (default: aigest-output.txt or aigest-<profile>.xml)
            -d, --dir <path>          Target repository root directory (default: current directory)
            -c, --global-config <p>   Global config path (default: ~/.config/aigest/aigest.json)
                --include-logs        Include git commit logs in output
                --no-logs             Exclude git commit logs from output (default)
            -v, --version             Display version information
            -h, --help                Show this help message
        '''.stripIndent().trim()
    }

}

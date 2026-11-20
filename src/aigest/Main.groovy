package aigest

import aigest.cli.Cli
import aigest.cli.CliOptions

import aigest.ingest.Ingestor
import aigest.ingest.Ingestor.IngestReport

import aigest.util.Result

import groovy.transform.CompileStatic

@CompileStatic
final class Main {

    private Main() { }

    static abstract class Action {

        private Action() { }

        abstract <R> R fold(
            Closure<R> onHelp,
            Closure<R> onVersion,
            Closure<R> onIngest
        )

        static Action classify(CliOptions options) {
            if (options.showHelp) {
                return ShowHelp.INSTANCE
            }

            if (options.showVersion) {
                return ShowVersion.INSTANCE
            }

            new RunIngest(options)
        }

        static final class ShowHelp extends Action {

            public static final ShowHelp INSTANCE = new ShowHelp()

            private ShowHelp() { }

            @Override
            <R> R fold(Closure<R> onHelp, Closure<R> onVersion, Closure<R> onIngest) {
                onHelp.call()
            }

        }

        static final class ShowVersion extends Action {

            public static final ShowVersion INSTANCE = new ShowVersion()

            private ShowVersion() { }

            @Override
            <R> R fold(Closure<R> onHelp, Closure<R> onVersion, Closure<R> onIngest) {
                onVersion.call()
            }

        }

        static final class RunIngest extends Action {

            final CliOptions options

            RunIngest(CliOptions options) {
                this.options = options
            }

            @Override
            <R> R fold(Closure<R> onHelp, Closure<R> onVersion, Closure<R> onIngest) {
                onIngest.call(options)
            }

        }

    }

    static void main(String[] args) {
        System.exit(execute(args, System.out, System.err))
    }

    static int execute(String[] args, PrintStream out, PrintStream err) {
        Result<CliOptions, String> parsed = Result.<CliOptions>trying('parsing arguments') {
            Cli.parse(args)
        }

        parsed
            .map { CliOptions opts -> Action.classify(opts) }
            .flatMap { Action action -> runAction(action, out) }
            .<Integer>fold(
                { String error ->
                         err.println("${Version.NAME}: error: ${error}")
                         Integer.valueOf(1)
                },
                { Integer code -> code }
            ).intValue()
    }

    private static Result<Integer, String> runAction(Action action, PrintStream out) {
        action.<Result<Integer, String>>fold(
            {
                out.println(Cli.usage())
                Result.<Integer, String>ok(Integer.valueOf(0))
            },
            {
                out.println(Version.banner())
                Result.<Integer, String>ok(Integer.valueOf(0))
            },
            { CliOptions opts ->
                Ingestor.run(opts).map { IngestReport report ->
                    out.println(formatReport(report))
                    Integer.valueOf(0)
                }
            }
        )
    }

    private static String formatReport(IngestReport report) {
        String logsStatus = report.gitLogsIncluded ? 'included' : 'omitted'
        "Wrote ${report.outputPath} " +
            "(profile: ${report.activeProfile}, " +
            "files: ${report.stats.textFiles} text / ${report.stats.binaryFiles} binary, " +
            "chars: ${report.stats.totalChars}, " +
            "git logs: ${logsStatus})"
    }

}

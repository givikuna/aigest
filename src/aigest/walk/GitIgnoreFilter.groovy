package aigest.walk

import aigest.util.PathUtils

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

@CompileStatic
final class GitIgnoreFilter {

    static final GitIgnoreFilter EMPTY = new GitIgnoreFilter(
        Collections.<IgnorePattern>emptyList()
    )

    private final List<IgnorePattern> patterns

    private static final class IgnorePattern {

        final Pattern regex
        final boolean negated

        IgnorePattern(Pattern regex, boolean negated) {
            this.regex = regex
            this.negated = negated
        }

    }

    private GitIgnoreFilter(List<IgnorePattern> patterns) {
        this.patterns = Collections.unmodifiableList(new ArrayList<IgnorePattern>(patterns))
    }

    static GitIgnoreFilter load(Path root) {
        Path file = root.resolve('.gitignore')
        if (!Files.isRegularFile(file)) {
            return EMPTY
        }

        List<IgnorePattern> collected = new ArrayList<>()

        try {
            for (String line : Files.readAllLines(file)) {
                IgnorePattern p = parseLine(line)

                if (p != null) {
                    collected.add(p)
                }
            }
        } catch (IOException ignored) { }

        collected.isEmpty() ? EMPTY : new GitIgnoreFilter(collected)
    }

    boolean isIgnored(String relativePath) {
        if (patterns.isEmpty()) return false

        String norm = PathUtils.normalizeRelPath(relativePath)

        if (norm.isEmpty()) return false

        boolean ignored = false

        for (IgnorePattern p : patterns) {
            if (p.regex.matcher(norm).matches()) {
                ignored = !p.negated
            }
        }

        ignored
    }

    private static IgnorePattern parseLine(String line) {
        String trimmed = line.trim()

        if (trimmed.isEmpty() || trimmed.startsWith('#')) {
            return null
        }

        boolean negated = trimmed.startsWith('!')
        String glob = negated ? trimmed.substring(1).trim() : trimmed

        if (glob.isEmpty()) return null

        Pattern compiled = globToRegex(glob)

        compiled == null ? null : new IgnorePattern(compiled, negated)
    }

    private static Pattern globToRegex(String glob) {
        String pat = glob.startsWith('/') ? glob.substring(1) : glob

        if (pat.isEmpty()) {
            return null
        }

        boolean anchored = glob.startsWith('/')
        boolean hasSlash = pat.contains('/')
        boolean trailingSlash = pat.endsWith('/')

        String body = trailingSlash ? pat.substring(0, pat.length() - 1) : pat

        StringBuilder sb = new StringBuilder()
        sb.append(anchored || hasSlash ? '^' : '(?:^|.*/)')

        int i = 0
        while (i < body.length()) {
            char c = body.charAt(i)

            if (c == ('*' as char)) {
                boolean dbl = i + 1 < body.length() && body.charAt(i + 1) == ('*' as char)

                if (dbl) {
                    sb.append('.*')
                    i++
                    if (i + 1 < body.length() && body.charAt(i + 1) == ('/' as char)) {
                        i++
                    }
                } else {
                    sb.append('[^/]*')
                }
            } else if (c == ('?' as char)) {
                sb.append('[^/]')
            } else if (c == ('[' as char)) {
                int end = body.indexOf(']', i + 1)

                if (end > i) {
                    sb.append(body.substring(i, end + 1))
                    i = end
                } else {
                    sb.append('\\[')
                }
            } else if ('.*+^$(){}|\\'.indexOf((int) c) >= 0) {
                sb.append('\\').append(c)
            } else {
                sb.append(c)
            }

            i++
        }

        sb.append(trailingSlash ? '/.*' : '(?:/.*)?').append('$')

        Pattern.compile(sb.toString())
    }

}

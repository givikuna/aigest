package aigest.rules

import groovy.transform.CompileStatic

import java.util.regex.Pattern

@CompileStatic
final class RegexSet {

    public static final RegexSet EMPTY = new RegexSet(Collections.<Pattern>emptyList())

    final List<Pattern> patterns

    private RegexSet(List<Pattern> patterns) {
        this.patterns = Collections.unmodifiableList(new ArrayList<Pattern>(patterns))
    }

    static RegexSet fromStrings (Collection<String> rawPatterns) {
        if (rawPatterns == null || rawPatterns.isEmpty()) {
            return EMPTY
        }

        List<Pattern> compiled = new ArrayList<Pattern>(rawPatterns.size())

        for (String raw : rawPatterns) {
            if (raw != null && !raw.isEmpty()) {
                compiled.add(Pattern.compile(raw))
            }
        }

        return new RegexSet(compiled)
    }

    boolean isEmpty() {
        patterns.isEmpty()
    }

    boolean matchesAny(String candidate) {
        if (candidate == null || patterns.isEmpty()) {
            return false
        }

        for (Pattern pattern : patterns) {
            if (pattern.matcher(candidate).find()) {
                return true
            }
        }

        return false
    }
}

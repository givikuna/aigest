package aigest.rules

import aigest.util.PathUtils
import groovy.transform.CompileStatic

@CompileStatic
final class ExtMatcher {

    private ExtMatcher() { }

    static interface ExtRule {

        boolean isIgnoreAll()

        boolean matches(String normalizedRelPath, String fileName)

    }

    static final class ExtIgnore implements ExtRule {

        public static final ExtIgnore INSTANCE = new ExtIgnore()

        private ExtIgnore() { }

        @Override
        boolean isIgnoreAll() {
            true
        }

        @Override
        boolean matches(String normalizedRelPath, String fileName) {
            true
        }

    }

    static final class ExtAllow implements ExtRule {

        final List<String> ignoreAllIn
        final Map<String, RegexSet> ignoreAllRegexIn
        final RegexSet ignoreAllRegex

        ExtAllow(
            List<String> ignoreAllIn,
            Map<String, RegexSet> ignoreAllRegexIn,
            RegexSet ignoreAllRegex
        ) {
            List<String> normDirs = ignoreAllIn != null
                ? ignoreAllIn.collect { String d -> PathUtils.normalizeRelPath(d) }
                : Collections.<String>emptyList()
            this.ignoreAllIn = Collections.unmodifiableList(normDirs)

            Map<String, RegexSet> normRegexMap = new LinkedHashMap<String, RegexSet>()
            if (ignoreAllRegexIn != null) {
                ignoreAllRegexIn.each { String k, RegexSet v ->
                    normRegexMap.put(PathUtils.normalizeRelPath(k), v ?: RegexSet.EMPTY)
                }
            }
            this.ignoreAllRegexIn = Collections.unmodifiableMap(normRegexMap)
            this.ignoreAllRegex = ignoreAllRegex ?: RegexSet.EMPTY
        }

        @Override
        boolean isIgnoreAll() {
            false
        }

        @Override
        boolean matches(String normalizedRelPath, String fileName) {
            boolean inIgnoredDir = ignoreAllIn.any { String dirPrefix ->
                PathUtils.isInsideDir(normalizedRelPath, dirPrefix)
            }
            if (inIgnoredDir) return true

            if (ignoreAllRegex.matchesAny(fileName) ||
                ignoreAllRegex.matchesAny(normalizedRelPath)) {
                return true
            }

            ignoreAllRegexIn.any { String dirKey, RegexSet rs ->
                PathUtils.isInsideDir(normalizedRelPath, dirKey) &&
                    (rs.matchesAny(fileName) || rs.matchesAny(normalizedRelPath))
            }
        }

    }

    static boolean shouldIgnore(
        Map<String, ExtRule> rulesByExt,
        String normalizedRelPath,
        String fileName,
        String normalizedExt
    ) {
        if (rulesByExt == null || normalizedExt.isEmpty()) return false
        ExtRule rule = rulesByExt.get(normalizedExt)
        rule != null && rule.matches(normalizedRelPath, fileName)
    }

}

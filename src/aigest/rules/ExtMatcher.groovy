package aigest.rules

import aigest.util.PathUtils
import groovy.transform.CompileStatic

@CompileStatic
final class ExtMatcher {

    private ExtMatcher() { }

    static interface ExtRule {

        boolean isIgnoreAll()

    }

    static final class ExtIgnore implements ExtRule {

        public static final ExtIgnore INSTANCE = new ExtIgnore()

        private ExtIgnore() { }

        @Override
        boolean isIgnoreAll() { return true }

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
            List<String> normDirs = new ArrayList<String>()
            if (ignoreAllIn != null) {
                for (String d : ignoreAllIn) {
                    normDirs.add(PathUtils.normalizeRelPath(d))
                }
            }
            this.ignoreAllIn = Collections.unmodifiableList(normDirs)

            Map<String, RegexSet> normRegexMap = new LinkedHashMap<String, RegexSet>()
            if (ignoreAllRegexIn != null) {
                for (Map.Entry<String, RegexSet> entry : ignoreAllRegexIn.entrySet()) {
                    normRegexMap.put(PathUtils.normalizeRelPath(entry.key), entry.value)
                }
            }
            this.ignoreAllRegexIn = Collections.unmodifiableMap(normRegexMap)
            this.ignoreAllRegex = ignoreAllRegex != null ? ignoreAllRegex : RegexSet.EMPTY
        }

        @Override
        boolean isIgnoreAll() { return false }

    }

    static boolean shouldIgnore(
        Map<String, ExtRule> rulesByExt,
        String normalizedRelPath,
        String fileName,
        String normalizedExt
    ) {
        if (rulesByExt == null || normalizedExt.isEmpty()) {
            return false
        }

        ExtRule rule = rulesByExt.get(normalizedExt)

        if (rule == null) {
            return false
        }

        if (rule instanceof ExtIgnore) {
            return true
        }

        if (rule instanceof ExtAllow) {
            ExtAllow allow = (ExtAllow) rule

            for (String dirPrefix : allow.ignoreAllIn) {
                if (PathUtils.isInsideDir(normalizedRelPath, dirPrefix)) {
                    return true
                }
            }

            if (allow.ignoreAllRegex.matchesAny(fileName) ||
                allow.ignoreAllRegex.matchesAny(normalizedRelPath)) {
                return true
            }

            for (Map.Entry<String, RegexSet> entry : allow.ignoreAllRegexIn.entrySet()) {
                if (PathUtils.isInsideDir(normalizedRelPath, entry.key)) {
                    RegexSet rs = entry.value

                    if (rs.matchesAny(fileName) || rs.matchesAny(normalizedRelPath)) {
                        return true
                    }
                }
            }
        }

        false
    }

}

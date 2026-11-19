package aigest.rules

import aigest.util.PathUtils

import groovy.transform.CompileStatic

@CompileStatic
final class DirMatcher {

    private DirMatcher() { }

    static interface DirRule {

        boolean isIgnoreAll()

    }

    static final class DirIgnore implements DirRule {

        public static final DirIgnore INSTANCE = new DirIgnore()

        private DirIgnore() { }

        @Override
        boolean isIgnoreAll() { return true }

    }

    static final class DirAllow implements DirRule {

        final Set<String> ignoreAllOfExt
        final RegexSet unboundedRegex
        final Map<Integer, RegexSet> depthRegex

        DirAllow(
            Collection<String> ignoreAllOfExt,
            RegexSet unboundedRegex,
            Map<Integer, RegexSet> depthRegex
        ) {
            Set<String> normExts = new LinkedHashSet<String>()

            if (ignoreAllOfExt != null) {
                for (String ext : ignoreAllOfExt) {
                    normExts.add(PathUtils.normalizeExt(ext))
                }
            }

            this.ignoreAllOfExt = Collections.unmodifiableSet(normExts)
            this.unboundedRegex = unboundedRegex != null ? unboundedRegex : RegexSet.EMPTY
            this.depthRegex = depthRegex != null
                ? Collections.unmodifiableMap(new LinkedHashMap<Integer, RegexSet>(depthRegex))
                : Collections.<Integer, RegexSet>emptyMap()
        }

        @Override
        boolean isIgnoreAll() {
            false
        }

    }

    static boolean isDirectoryIgnored(Map<String, DirRule> rulesByDir, String normalizedDirRelPath) {
        if (rulesByDir == null) return false

        for (Map.Entry<String, DirRule> entry : rulesByDir.entrySet()) {
            String ruleDir = entry.key
            DirRule rule = entry.value
            if (rule instanceof DirIgnore && PathUtils.isInsideDir(normalizedDirRelPath, ruleDir)) {
                return true
            }
        }

        false
    }

    static boolean shouldIgnore(
        Map<String, DirRule> rulesByDir,
        String normalizedRelPath,
        String fileName,
        String normalizedExt
    ) {
        if (rulesByDir == null || rulesByDir.isEmpty()) {
            return false
        }

        for (Map.Entry<String, DirRule> entry : rulesByDir.entrySet()) {
            String dirKey = entry.key
            DirRule rule = entry.value

            int depth = PathUtils.relativeDepthInDir(normalizedRelPath, dirKey)
            if (depth < 0) {
                continue
            }

            if (rule instanceof DirIgnore) {
                return true
            }

            if (rule instanceof DirAllow) {
                DirAllow allow = (DirAllow) rule

                if (!normalizedExt.isEmpty() && allow.ignoreAllOfExt.contains(normalizedExt)) {
                    return true
                }

                if (allow.unboundedRegex.matchesAny(fileName) ||
                    allow.unboundedRegex.matchesAny(normalizedRelPath)) {
                    return true
                }

                for (Map.Entry<Integer, RegexSet> depthEntry : allow.depthRegex.entrySet()) {
                    int allowedDepth = depthEntry.key
                    if (depth <= allowedDepth) {
                        RegexSet rs = depthEntry.value
                        if (rs.matchesAny(fileName) || rs.matchesAny(normalizedRelPath)) {
                            return true
                        }
                    }
                }
            }
        }

        false
    }

}

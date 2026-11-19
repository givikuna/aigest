package aigest.rules

import aigest.util.PathUtils
import groovy.transform.CompileStatic

@CompileStatic
final class DirMatcher {

    private DirMatcher() { }

    static interface DirRule {

        boolean isIgnoreAll()

        boolean matchesFile(
            String normalizedRelPath,
            String fileName,
            String normalizedExt,
            int depth
        )

    }

    static final class DirIgnore implements DirRule {

        public static final DirIgnore INSTANCE = new DirIgnore()

        private DirIgnore() { }

        @Override
        boolean isIgnoreAll() {
            true
        }

        @Override
        boolean matchesFile(
            String normalizedRelPath,
            String fileName,
            String normalizedExt,
            int depth
        ) {
            true
        }

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
                ignoreAllOfExt.each { String ext ->
                    String norm = PathUtils.normalizeExt(ext)
                    if (!norm.isEmpty()) normExts.add(norm)
                }
            }

            this.ignoreAllOfExt = Collections.unmodifiableSet(normExts)
            this.unboundedRegex = unboundedRegex ?: RegexSet.EMPTY
            this.depthRegex = depthRegex != null
                ? Collections.unmodifiableMap(new LinkedHashMap<Integer, RegexSet>(depthRegex))
                : Collections.<Integer, RegexSet>emptyMap()
        }

        @Override
        boolean isIgnoreAll() {
            false
        }

        @Override
        boolean matchesFile(
            String normalizedRelPath,
            String fileName,
            String normalizedExt,
            int depth
        ) {
            if (!normalizedExt.isEmpty() && ignoreAllOfExt.contains(normalizedExt)) {
                return true
            }

            if (unboundedRegex.matchesAny(fileName) ||
                unboundedRegex.matchesAny(normalizedRelPath)) {
                return true
            }

            depthRegex.any { Integer maxDepth, RegexSet rs ->
                depth <= maxDepth.intValue() &&
                    (rs.matchesAny(fileName) || rs.matchesAny(normalizedRelPath))
            }
        }

    }

    static boolean isDirectoryIgnored(
        Map<String, DirRule> rulesByDir,
        String normalizedDirRelPath
    ) {
        if (rulesByDir == null || rulesByDir.isEmpty()) return false

        Map.Entry<String, DirRule> deepest = mostSpecificRule(rulesByDir, normalizedDirRelPath)
        if (deepest == null || !deepest.value.isIgnoreAll()) return false

        boolean hasNestedAllow = rulesByDir.any { String ruleDir, DirRule rule ->
            !rule.isIgnoreAll() &&
                ruleDir != normalizedDirRelPath &&
                PathUtils.isInsideDir(ruleDir, normalizedDirRelPath)
        }

        !hasNestedAllow
    }

    static boolean shouldIgnore(
        Map<String, DirRule> rulesByDir,
        String normalizedRelPath,
        String fileName,
        String normalizedExt
    ) {
        if (rulesByDir == null || rulesByDir.isEmpty()) return false

        Map.Entry<String, DirRule> deepest = mostSpecificRule(rulesByDir, normalizedRelPath)
        if (deepest != null && deepest.value.isIgnoreAll()) {
            return true
        }

        int minActiveLength = deepest != null ? deepest.key.length() : -1

        int deepestIgnoreLen = rulesByDir.entrySet()
            .findAll { Map.Entry<String, DirRule> e ->
                e.value.isIgnoreAll() && PathUtils.isInsideDir(normalizedRelPath, e.key)
            }
            .collect { Map.Entry<String, DirRule> e -> e.key.length() }
            .max() ?: -1

        rulesByDir.entrySet().any { Map.Entry<String, DirRule> entry ->
            if (entry.value.isIgnoreAll()) return false
            if (entry.key.length() < deepestIgnoreLen && minActiveLength >= 0) return false
            int depth = PathUtils.relativeDepthInDir(normalizedRelPath, entry.key)
            depth >= 0 && entry.value.matchesFile(normalizedRelPath, fileName, normalizedExt, depth)
        }
    }

    private static Map.Entry<String, DirRule> mostSpecificRule(
        Map<String, DirRule> rulesByDir,
        String normalizedRelPath
    ) {
        List> matching = rulesByDir.entrySet().findAll {
            Map.Entry<String, DirRule> e -> PathUtils.isInsideDir(normalizedRelPath, e.key)
        }
        if (matching.isEmpty()) return null
        matching.max { Map.Entry<String, DirRule> e -> e.key.length() }
    }

}
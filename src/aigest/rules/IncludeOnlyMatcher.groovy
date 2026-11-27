package aigest.rules

import aigest.util.PathUtils

import groovy.transform.CompileStatic

@CompileStatic
final class IncludeOnlyMatcher {

    private IncludeOnlyMatcher() { }

    static abstract class IncludeOnlyRule {

        private IncludeOnlyRule() { }

        static IncludeOnlyRule of(Collection<String> dirs, Collection<String> files) {
            Set<String> normFiles = new LinkedHashSet<String>()
            if (files != null) {
                for (String f : files) {
                    if (f != null && !f.trim().isEmpty()) {
                        String norm = PathUtils.normalizeRelPath(f)
                        if (!norm.isEmpty()) {
                            normFiles.add(norm)
                        }
                    }
                }
            }

            Set<String> normDirs = new LinkedHashSet<String>()
            if (dirs != null) {
                for (String d : dirs) {
                    if (d != null && !d.trim().isEmpty()) {
                        normDirs.add(PathUtils.normalizeRelPath(d))
                    }
                }
            }

            if (normDirs.isEmpty() && normFiles.isEmpty()) {
                return None.INSTANCE
            }

            new Active(normDirs, normFiles)
        }

        abstract boolean isActive()

        abstract boolean shouldSkipDirectory(String normalizedDirRelPath)

        abstract boolean shouldIncludeFile(String normalizedFileRelPath)

        static final class None extends IncludeOnlyRule {

            public static final None INSTANCE = new None()

            private None() { }

            @Override
            boolean isActive() {
                false
            }

            @Override
            boolean shouldSkipDirectory(String normalizedDirRelPath) {
                false
            }

            @Override
            boolean shouldIncludeFile(String normalizedFileRelPath) {
                true
            }

        }

        static final class Active extends IncludeOnlyRule {

            final Set<String> dirs
            final Set<String> files

            Active(Set<String> dirs, Set<String> files) {
                this.dirs = Collections.unmodifiableSet(new LinkedHashSet<String>(dirs))
                this.files = Collections.unmodifiableSet(new LinkedHashSet<String>(files))
            }

            @Override
            boolean isActive() {
                true
            }

            @Override
            boolean shouldSkipDirectory(String normalizedDirRelPath) {
                if (normalizedDirRelPath.isEmpty()) {
                    return false
                }

                boolean matchesDir = dirs.any { String incDir ->
                    PathUtils.isInsideDir(normalizedDirRelPath, incDir) ||
                        PathUtils.isInsideDir(incDir, normalizedDirRelPath)
                }

                if (matchesDir) {
                    return false
                }

                !files.any { String incFile ->
                    incFile.startsWith(normalizedDirRelPath + '/')
                }
            }

            @Override
            boolean shouldIncludeFile(String normalizedFileRelPath) {
                files.contains(normalizedFileRelPath) ||
                    dirs.any { String incDir ->
                        incDir.isEmpty() || normalizedFileRelPath.startsWith(incDir + '/')
                    }
            }

        }

    }

}

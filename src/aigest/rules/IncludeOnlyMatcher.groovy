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

            // files > dirs
            if (!normFiles.isEmpty()) {
                return new FilesOnly(normFiles)
            }

            Set<String> normDirs = new LinkedHashSet<String>()
            if (dirs != null) {
                for (String d : dirs) {
                    if (d != null && !d.trim().isEmpty()) {
                        normDirs.add(PathUtils.normalizeRelPath(d))
                    }
                }
            }

            if (!normDirs.isEmpty()) {
                return new DirsOnly(normDirs)
            }

            None.INSTANCE
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

        static final class DirsOnly extends IncludeOnlyRule {

            final Set<String> dirs

            DirsOnly(Set<String> dirs) {
                this.dirs = Collections.unmodifiableSet(new LinkedHashSet<String>(dirs))
            }

            @Override
            boolean isActive() {
                true
            }

            @Override
            boolean shouldSkipDirectory(String normalizedDirRelPath) {
                !dirs.any { String incDir ->
                    PathUtils.isInsideDir(normalizedDirRelPath, incDir) ||
                        PathUtils.isInsideDir(incDir, normalizedDirRelPath)
                }
            }

            @Override
            boolean shouldIncludeFile(String normalizedFileRelPath) {
                dirs.any { String incDir ->
                    incDir.isEmpty() || normalizedFileRelPath.startsWith(incDir + '/')
                }
            }

        }

        static final class FilesOnly extends IncludeOnlyRule {

            final Set<String> files

            FilesOnly(Set<String> files) {
                this.files = Collections.unmodifiableSet(new LinkedHashSet<String>(files))
            }

            @Override
            boolean isActive() {
                true
            }

            @Override
            boolean shouldSkipDirectory(String normalizedDirRelPath) {
                normalizedDirRelPath.isEmpty()
                    ? false
                    : !files.any { String incFile ->
                        incFile.startsWith(normalizedDirRelPath + '/')
                    }
            }

            @Override
            boolean shouldIncludeFile(String normalizedFileRelPath) {
                files.contains(normalizedFileRelPath)
            }

        }

    }

}

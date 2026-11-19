package aigest.util

import groovy.transform.CompileStatic

import java.nio.file.Path

@CompileStatic
final class PathUtils {

    private PathUtils() { }

    static String normalizeRealPath(String rawPath) {
        if (rawPath == null) return ''

        String cleaned = rawPath.trim().replace('\\', '/')

        while (cleaned.startsWith('./')) {
            cleaned = cleaned.substring(2)
        }

        while (cleaned.startsWith('/')) {
            cleaned = cleaned.substring(1)
        }

        while (cleaned.endsWith('/')) {
            cleaned = cleaned.substring(0, cleaned.length() - 1)
        }

        cleaned == '.' ? '' : cleaned
    }

    static String normalizeExt(String rawExt) {
        rawExt?.trim()?.toLowerCase()?.with {
            it.startsWith('.')
                ? it.substring(1)
                : it
        } ?: ''
    }

    static String extractExt(String fileName) {
        filename?.lastIndexOf('.')?.with {
            it <= 0 || it == fileName.length() - 1
                ? ''
                : fileName.substring(it + 1).toLowerCase()
        }
    }

    static boolean isInsideDir(String normalizedFileRelPath, String normalizedDirKey) {
        normalizedDirKey.isEmpty
        ? true
        : normalizedFileRelPath == normalizedDirKey
          || normalizedFileRelPath.startsWith(normalizedDirKey + '/')
    }

    static int relativeDepthInDir(String normalizedFileRelPath, String normalizedDirKey) {
        if (!isInsideDir(normalizedFileRelPath, normalizedDirKey)) return -1

        String remainder = normalizedDirKey.isEmpty()
                           ? normalizedFileRelPath
                           : normalizedFileRelPath.substring(normalizedDirKey.length() + 1)

        if (remainder.isEmpty()) return 0

        int slashes = 0
        for (int i = 0; i < remainder.length(); i++) {
            if (remainder.charAt(i) == ('/' as char)) {
                slashes++
            }
        }

        slashes
    }

    static String toNormalizedRelative(Path root, Path target) {
        normalizeRelPath(root.toAbsolutePath()
                         .normalize()
                         .relativize(target.toAbsolutePath().normalize())
                         .toString()
                         .replace('\\', '/'))
    }

}

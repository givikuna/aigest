package aigest.util

import groovy.transform.CompileStatic

import java.nio.file.Path

@CompileStatic
final class PathUtils {

    private PathUtils() { }

    static String normalizeRelPath(String rawPath) {
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
        if (rawExt == null) return ''
        String trimmed = rawExt.trim().toLowerCase(Locale.ROOT)
        trimmed.startsWith('.') ? trimmed.substring(1) : trimmed
    }

    static String fileNameOf(String normalizedRelPath) {
        if (normalizedRelPath == null || normalizedRelPath.isEmpty()) return ''
        int lastSlash = normalizedRelPath.lastIndexOf('/')
        lastSlash >= 0 ? normalizedRelPath.substring(lastSlash + 1) : normalizedRelPath
    }

    static String extractExt(String fileName) {
        if (fileName == null || fileName.isEmpty()) return ''
        int dot = fileName.lastIndexOf('.')
        (dot <= 0 || dot == fileName.length() - 1)
            ? ''
            : fileName.substring(dot + 1).toLowerCase(Locale.ROOT)
    }

    static boolean isInsideDir(String normalizedRelPath, String normalizedDirKey) {
        if (normalizedDirKey.isEmpty()) return true
        normalizedRelPath == normalizedDirKey ||
            normalizedRelPath.startsWith(normalizedDirKey + '/')
    }

    static int relativeDepthInDir(String normalizedFileRelPath, String normalizedDirKey) {
        if (!isInsideDir(normalizedFileRelPath, normalizedDirKey)) return -1
        if (normalizedFileRelPath == normalizedDirKey) return 0

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
        normalizeRelPath(
            root.toAbsolutePath()
                .normalize()
                .relativize(target.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/')
        )
    }

}
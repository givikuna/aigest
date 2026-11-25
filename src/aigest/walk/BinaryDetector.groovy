package aigest.walk

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class BinaryDetector {

    private static final int SAMPLE_SIZE = 8192

    private BinaryDetector() { }

    static boolean isBinaryBytes(byte[] buffer, int length) {
        if (buffer == null || length <= 0) {
            return false
        }

        int limit = Math.min(length, buffer.length)
        int suspiciousBytes = 0

        for (int i = 0; i < limit; i++) {
            int b = buffer[i] & 0xFF

            if (b == 0x00) {
                return true
            }

            if (b < 0x09 || (b > 0x0D && b < 0x20 && b != 0x1B)) {
                suspiciousBytes++
            }
        }

        ((double) suspiciousBytes / (double) limit) > 0.30d
    }

    static boolean isBinaryFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return false
        }

        byte[] sample = new byte[SAMPLE_SIZE]
        int bytesRead = 0

        Files.newInputStream(file).withCloseable { InputStream is ->
            bytesRead = is.read(sample, 0, SAMPLE_SIZE)
        }

        if (bytesRead <= 0) {
            return false
        }

        return isBinaryBytes(sample, bytesRead)
    }

}

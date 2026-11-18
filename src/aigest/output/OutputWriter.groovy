package aigest.output

import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class OutputWriter {

    private OutputWriter() { }

    static Path write(Path outputPath, String content) {
        Path normalized = outputPath.toAbsolutePath().normalize()
        Path parent = normalized.getParent()

        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }

        Files.writeString(normalized, content, StandardCharsets.UTF_8)

        return normalized
    }

}

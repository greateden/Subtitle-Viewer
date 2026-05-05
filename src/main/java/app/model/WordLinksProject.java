package app.model;

import java.util.List;

public record WordLinksProject(
        String format,
        String mode,
        List<Sentence> sentences
) {
}

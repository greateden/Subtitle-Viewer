package app.model;

import java.util.List;

public record Sentence(
        int index,
        double start,
        double end,
        String mode,
        List<SubtitleLine> lines
) {
    public boolean contains(double timeSeconds) {
        return timeSeconds >= start && timeSeconds <= end;
    }
}

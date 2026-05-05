package app.model;

public record SubtitleToken(
        String text,
        Integer source_id,
        Double start,
        Double end,
        Boolean estimated
) {
    public boolean isActive(double timeSeconds) {
        if (start == null || end == null) {
            return false;
        }

        return timeSeconds >= start && timeSeconds <= end;
    }

    public boolean hasTiming() {
        return start != null && end != null;
    }
}

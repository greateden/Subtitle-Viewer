package app.model;

import java.util.List;

public record SubtitleLine(
        String role,
        String language,
        String text,
        List<SubtitleToken> tokens
) {
    public String displayName() {
        if (role == null || role.isBlank()) {
            return language == null ? "Line" : language.toUpperCase();
        }

        if (language == null || language.isBlank()) {
            return role;
        }

        return role + " (" + language.toUpperCase() + ")";
    }
}

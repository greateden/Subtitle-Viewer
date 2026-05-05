package app.io;

import app.model.WordLinksProject;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

public final class WordLinksLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private WordLinksLoader() {
    }

    public static WordLinksProject load(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), WordLinksProject.class);
    }
}

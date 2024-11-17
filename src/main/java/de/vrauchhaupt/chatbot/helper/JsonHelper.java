package de.vrauchhaupt.chatbot.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.github.ollama4j.utils.Utils;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonHelper {

    @NotNull
    public static ObjectMapper objectMapper() {
        return Utils.getObjectMapper();
    }

    @NotNull
    public static ObjectWriter objectWriter() {
        return objectMapper()
                .writerWithDefaultPrettyPrinter();
    }

    @NotNull
    public static String serialize(@NotNull Object object) {
        try {
            return objectWriter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not deserialize '" + object + "'", e);
        }
    }

    public static <T> T loadFromFile(Path path, Class<T> clazz) {
        if (!Files.exists(path))
            return null;
        try {
            return objectMapper().readValue(path.toFile(), clazz);
        } catch (IOException e) {
            throw new RuntimeException("Could not read json from file '" + path.toAbsolutePath() + "'", e);
        }
    }
}

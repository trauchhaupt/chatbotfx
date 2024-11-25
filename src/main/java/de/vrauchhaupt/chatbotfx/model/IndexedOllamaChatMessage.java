package de.vrauchhaupt.chatbotfx.model;

import io.github.ollama4j.models.chat.OllamaChatMessage;

import java.util.Objects;

public class IndexedOllamaChatMessage {
    private static int ID_COUNTER = 0;
    private final int id;
    private final OllamaChatMessage chatMessage;

    public static int newId()
    {
        return ++ID_COUNTER;
    }

    public IndexedOllamaChatMessage( int id, OllamaChatMessage chatMessage) {
        this.id = id;
        this.chatMessage = chatMessage;
    }

    public IndexedOllamaChatMessage( OllamaChatMessage chatMessage) {
        this.id = newId();
        this.chatMessage = chatMessage;
    }

    public int getId() {
        return id;
    }

    public OllamaChatMessage getChatMessage() {
        return chatMessage;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        IndexedOllamaChatMessage that = (IndexedOllamaChatMessage) object;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

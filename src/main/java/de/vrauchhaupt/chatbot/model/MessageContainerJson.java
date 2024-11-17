package de.vrauchhaupt.chatbot.model;

import io.github.ollama4j.models.chat.OllamaChatMessage;

import java.util.ArrayList;
import java.util.List;

public class MessageContainerJson {
    private List<OllamaChatMessage> messages = new ArrayList<>();

    public List<OllamaChatMessage> getMessages() {
        if (messages == null)
            return new ArrayList<>();
        return messages;
    }

    public void setMessages(List<OllamaChatMessage> messages) {
        this.messages = messages == null ? new ArrayList<>() : messages;
    }
}

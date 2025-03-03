package de.vrauchhaupt.chatbotfx.model;

import io.github.ollama4j.models.chat.OllamaChatMessageRole;

public class PersonJson extends AbstractJson{
    private OllamaChatMessageRole role;
    private String name;
    private String description;

    public OllamaChatMessageRole getRole() {
        return role;
    }

    public PersonJson setRole(OllamaChatMessageRole role) {
        this.role = role;
        return this;
    }

    public String getName() {
        return name;
    }

    public PersonJson setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public PersonJson setDescription(String description) {
        this.description = description;
        return this;
    }
}

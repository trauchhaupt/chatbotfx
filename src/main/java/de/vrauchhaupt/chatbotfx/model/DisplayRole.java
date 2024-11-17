package de.vrauchhaupt.chatbotfx.model;

import io.github.ollama4j.models.chat.OllamaChatMessageRole;

public enum DisplayRole {
    TOOL("#ff5c33"),
    SYSTEM("#D3D3D3"),
    USER("#b3d9ff"),
    ASSISTANT("#ffb366");

    private final String backgroundColor;

    DisplayRole(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public static DisplayRole of(OllamaChatMessageRole role) {
        if ( role == null)
            return TOOL;
        if (role.getRoleName().equals(OllamaChatMessageRole.SYSTEM.getRoleName()))
            return SYSTEM;
        if (role.getRoleName().equals(OllamaChatMessageRole.USER.getRoleName()))
            return USER;
        if (role.getRoleName().equals(OllamaChatMessageRole.ASSISTANT.getRoleName()))
            return ASSISTANT;
        return TOOL;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }
}

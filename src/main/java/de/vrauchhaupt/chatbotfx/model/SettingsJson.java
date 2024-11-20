package de.vrauchhaupt.chatbotfx.model;

public class SettingsJson extends AbstractJson {
    private String pathToPiper;
    private String pathToLlmModelCards;
    private String pathToLlmModelFiles;
    private String pathToTtsModelFiles;
    private String selectedLlmModelCard;
    private String ollamaHost;

    public String getPathToPiper() {
        return pathToPiper;
    }

    public SettingsJson setPathToPiper(String pathToPiper) {
        this.pathToPiper = pathToPiper;
        return this;
    }

    public String getPathToLlmModelCards() {
        return pathToLlmModelCards;
    }

    public SettingsJson setPathToLlmModelCards(String pathToLlmModelCards) {
        this.pathToLlmModelCards = pathToLlmModelCards;
        return this;
    }

    public String getPathToLlmModelFiles() {
        return pathToLlmModelFiles;
    }

    public SettingsJson setPathToLlmModelFiles(String pathToLlmModelFiles) {
        this.pathToLlmModelFiles = pathToLlmModelFiles;
        return this;
    }

    public String getPathToTtsModelFiles() {
        return pathToTtsModelFiles;
    }

    public SettingsJson setPathToTtsModelFiles(String pathToTtsModelFiles) {
        this.pathToTtsModelFiles = pathToTtsModelFiles;
        return this;
    }

    public String getSelectedLlmModelCard() {
        return selectedLlmModelCard;
    }

    public SettingsJson setSelectedLlmModelCard(String selectedLlmModelCard) {
        this.selectedLlmModelCard = selectedLlmModelCard;
        return this;
    }

    public String getOllamaHost() {
        return ollamaHost;
    }

    public SettingsJson setOllamaHost(String ollamaHost) {
        this.ollamaHost = ollamaHost;
        return this;
    }



}

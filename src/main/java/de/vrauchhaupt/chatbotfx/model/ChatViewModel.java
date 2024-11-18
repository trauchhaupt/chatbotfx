package de.vrauchhaupt.chatbotfx.model;

import de.vrauchhaupt.chatbotfx.IMessaging;
import de.vrauchhaupt.chatbotfx.helper.JsonHelper;
import de.vrauchhaupt.chatbotfx.manager.*;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatViewModel implements IMessaging {

    private static final int MAX_RECENT_MESSAGES_TO_SEND = 70;
    private static ChatViewModel INSTANCE = null;

    private final ChatbotLlmStreamHandler streamHandler = new ChatbotLlmStreamHandler();
    private final ObservableList<OllamaChatMessage> messages = FXCollections.observableArrayList();
    private int curImageIndex = 1;

    private ChatViewModel() {
    }

    public static ChatViewModel instance() {
        if (INSTANCE == null)
            INSTANCE = new ChatViewModel();
        return INSTANCE;
    }


    public void clearHistory() {
        messages.clear();
        resetCurImageIndex();
    }

    public List<OllamaChatMessage> getFullHistory() {
        return messages;
    }

    public List<OllamaChatMessage> trimmedHistory() {
        List<OllamaChatMessage> returnValue = new ArrayList<>();
        if (messages.size() <= MAX_RECENT_MESSAGES_TO_SEND + 1)
            returnValue.addAll(messages);
        else {
            OllamaChatMessage firstMessage = messages.getFirst(); // if it is a system (what should be, this is important)
            if (firstMessage.getRole().equals(OllamaChatMessageRole.SYSTEM)) {
                returnValue.add(firstMessage);
            }
            returnValue.addAll(messages.subList(Math.max(0, messages.size() - MAX_RECENT_MESSAGES_TO_SEND), messages.size()));
        }
        return returnValue;
    }

    private void append(List<OllamaChatMessage> allNewMessages) {
        List<OllamaChatMessage> newMessages = allNewMessages.stream()
                .filter(x -> messages.stream().noneMatch(y -> y.getContent().equals(x.getContent())))
                .toList();
        messages.addAll(newMessages);
    }

    public void ask(String systemPrompt, String userPrompt) {

        LlmModelCardJson selectedLlModelCard = LlmModelCardManager.instance().getSelectedLlModelCard();
        if (trimmedHistory().isEmpty())
            append(OllamaManager.instance().systemNoticeAndAsk(
                    selectedLlModelCard.getSystem(),
                    userPrompt,
                    trimmedHistory(),
                    selectedLlModelCard.getLlmModel(),
                    streamHandler));
        else
            append(OllamaManager.instance().systemNoticeAndAsk(
                    systemPrompt,
                    userPrompt,
                    trimmedHistory(),
                    selectedLlModelCard.getLlmModel(),
                    streamHandler));

    }

    public void saveMessagesToFile() {
        LlmModelCardJson selectedLlModelCard = LlmModelCardManager.instance().getSelectedLlModelCard();
        try {
            Path modelCardsDirectory = SettingsManager.instance().getPathToLlmModelCards();
            Path dirToSave = modelCardsDirectory.resolve(selectedLlModelCard.getModelCardName() + "_save");
            if (!Files.exists(dirToSave))
                Files.createDirectory(dirToSave);
            Path messageJsonFile = dirToSave.resolve(selectedLlModelCard.getModelCardName() + ".messages");
            Files.deleteIfExists(messageJsonFile);
            MessageContainerJson valueToWrite = new MessageContainerJson();
            valueToWrite.setMessages(new ArrayList<>(messages));
            JsonHelper.objectWriter().writeValue(messageJsonFile.toFile(), valueToWrite);
            // delete old saved files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirToSave, selectedLlModelCard.getModelCardName() + "_*.jpg")) {
                for (Path entry : stream) {
                    try {
                        Files.deleteIfExists(entry);
                    } catch (IOException e) {
                        throw new RuntimeException("Could not save " + entry + " to ", e);
                    }
                }
            }
            // copy the new files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modelCardsDirectory, selectedLlModelCard.getModelCardName() + "_*.jpg")) {
                for (Path entry : stream) {
                    Path destination = dirToSave.resolve(entry.getFileName());
                    try {
                        Files.copy(entry, destination);
                    } catch (IOException e) {
                        throw new RuntimeException("Could not save " + entry + " to ", e);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not save messages for " + selectedLlModelCard.getModelCardName(), e);
        }
    }

    public void deleteExistingRuntimeImages() {
        LlmModelCardJson selectedLlModelCard = LlmModelCardManager.instance().getSelectedLlModelCard();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SettingsManager.instance().getPathToLlmModelCards(), selectedLlModelCard.getModelCardName() + "_*.jpg")) {
            for (Path entry : stream) {
                try {
                    Files.delete(entry); // Delete each .jpg file
                } catch (IOException e) {
                    System.err.println("Failed to delete: " + entry.toString() + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to list directory: " + e.getMessage());
        }
    }

    public void loadMessagesFromFile(IPrintFunction printer) {
        LlmModelCardJson selectedLlModelCard = LlmModelCardManager.instance().getSelectedLlModelCard();
        Path modelCardsDirectory = SettingsManager.instance().getPathToLlmModelCards();
        Path dirToLoadFrom = modelCardsDirectory.resolve(selectedLlModelCard.getModelCardName() + "_save");
        Path messageJsonFile = dirToLoadFrom.resolve(selectedLlModelCard.getModelCardName() + ".messages");
        MessageContainerJson ollamaChatMessages = JsonHelper.loadFromFile(messageJsonFile, MessageContainerJson.class);
        if (ollamaChatMessages == null)
            messages.clear();
        else
            messages.setAll(ollamaChatMessages.getMessages());
        deleteExistingRuntimeImages();

        for (OllamaChatMessage ollamaChatMessage : getFullHistory()) {
            printer.render(DisplayRole.of(ollamaChatMessage.getRole()), ollamaChatMessage.getContent());
        }

        List<Path> filesToCopy = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirToLoadFrom, selectedLlModelCard.getModelCardName() + "_*.jpg")) {
            for (Path entry : stream) {
                filesToCopy.add(entry);
                Files.copy(entry, modelCardsDirectory.resolve(entry.getFileName()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not list existing image files from " + dirToLoadFrom);
        }
        Collections.sort(filesToCopy, Comparator.comparing(Path::getFileName));
        for (Path path : filesToCopy) {
            try {
                printer.addImage(getCurImageIndex(), Files.readAllBytes(path), null);
                increaseCurImageIndex();
            } catch (IOException e) {
                throw new RuntimeException("Could not read image bytes from " + path.toAbsolutePath(), e);
            }
        }
    }

    public int getCurImageIndex() {
        return curImageIndex;
    }

    public void resetCurImageIndex() {
        curImageIndex = 1;
    }

    public void increaseCurImageIndex() {
        curImageIndex++;
    }
}

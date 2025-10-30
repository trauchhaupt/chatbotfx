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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ChatViewModel implements IMessaging {

    private static ChatViewModel INSTANCE = null;

    private final ChatbotLlmStreamHandler streamHandler = new ChatbotLlmStreamHandler();
    private final ObservableList<IndexedOllamaChatMessage> messages = FXCollections.observableArrayList();
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

    public List<IndexedOllamaChatMessage> getFullHistory() {
        return new ArrayList<>(messages);
    }

    public int getFullHistorySize() {
        return messages.size();
    }

    public List<IndexedOllamaChatMessage> trimmedHistory() {
        List<IndexedOllamaChatMessage> returnValue = new ArrayList<>();
        if (messages.isEmpty())
            return returnValue;

        returnValue.addAll(messages);
        /*if (SettingsManager.instance().getMessagesToStripForLLM() <= 0 ||
                messages.size() <= SettingsManager.instance().getMessagesToStripForLLM() + 14)
            returnValue.addAll(messages);
        else {
            IndexedOllamaChatMessage firstMessage = messages.getFirst(); // if it is a system (what should be, this is important)
            if (firstMessage.getChatMessage().getRole().getRoleName().equals(OllamaChatMessageRole.SYSTEM.getRoleName())) {
                returnValue.add(firstMessage);
            }
            returnValue.addAll(messages.subList(Math.max(0, messages.size() - SettingsManager.instance().getMessagesToStripForLLM()), messages.size()));
        }*/
        return returnValue;
    }

    public void ask(String systemPrompt, String userPrompt) {

        LlmModelCardJson selectedLlModelCard = LlmModelCardManager.instance().getSelectedLlModelCard();
        if (trimmedHistory().isEmpty())
            OllamaManager.instance().systemNoticeAndAsk(
                    selectedLlModelCard.getSystem().replace("${NAME}", selectedLlModelCard.getModelCardName()),
                    userPrompt,
                    trimmedHistory(),
                    selectedLlModelCard,
                    streamHandler,
                    () -> logLn("Could not ask LLM '" + userPrompt + "'"));
        else
            OllamaManager.instance().systemNoticeAndAsk(
                    systemPrompt,
                    userPrompt,
                    trimmedHistory(),
                    selectedLlModelCard,
                    streamHandler,
                    () -> logLn("Could not ask LLM '" + userPrompt + "'"));

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
            valueToWrite.setMessages(messages.stream()
                    .map(IndexedOllamaChatMessage::getChatMessage)
                    .toList());
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
                    System.err.println("Failed to delete: " + entry + " - " + e.getMessage());
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
            messages.setAll(ollamaChatMessages.getMessages().stream()
                    .map(IndexedOllamaChatMessage::new)
                    .toList());
        deleteExistingRuntimeImages();

        for (IndexedOllamaChatMessage ollamaChatMessage : messages) {
            printer.renderOnFxThread(DisplayRole.of(ollamaChatMessage.getChatMessage().getRole()),
                    ollamaChatMessage.getChatMessage().getResponse(),
                    ollamaChatMessage.getId());
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
        filesToCopy.sort(Comparator.comparing(Path::getFileName));
        for (Path path : filesToCopy) {
            try {
                printer.addImage(getCurImageIndex(), Files.readAllBytes(path), path, null);
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

    public void setMessageOfId(int chatMessageIndex, String newMessage) {
        messages.stream().filter(x -> x.getId() == chatMessageIndex)
                .forEach(x -> x.getChatMessage().setResponse(newMessage));
    }

    public void appendSystemOrPrompt(OllamaChatMessageRole role, String prompt) {
        OllamaChatMessage ollamaChatMessage = new OllamaChatMessage();
        ollamaChatMessage.setRole(role);
        ollamaChatMessage.setResponse(prompt);
        messages.add(new IndexedOllamaChatMessage(ollamaChatMessage));
    }

    public synchronized void appendAssistant(TtsSentence ttsSentence) {
        IndexedOllamaChatMessage indexedOllamaChatMessage = messages.stream().filter(x -> x.getId() == ttsSentence.getChatMessageIndex())
                .findFirst().orElse(null);
        if (indexedOllamaChatMessage == null) {
            OllamaChatMessage ollamaChatMessage = new OllamaChatMessage();
            ollamaChatMessage.setRole(OllamaChatMessageRole.ASSISTANT);
            ollamaChatMessage.setResponse(ttsSentence.getText());
            messages.add(new IndexedOllamaChatMessage(ttsSentence.getChatMessageIndex(), ollamaChatMessage));
        } else {
            indexedOllamaChatMessage.getChatMessage().setResponse(
                    indexedOllamaChatMessage.getChatMessage().getResponse() + " " + ttsSentence.getText()
            );
        }
    }

    public void removeAllMessages(Set<Integer> messageIndexesToDelete) {
        List<IndexedOllamaChatMessage> messagesToRemove = messages.stream()
                .filter(x -> messageIndexesToDelete.contains(x.getId()))
                .toList();
        messages.removeAll(messagesToRemove);
    }

    public void replaceChatHistory(ArrayList<IndexedOllamaChatMessage> messagesToSummarize, OllamaChatMessage summarizing) {
        IndexedOllamaChatMessage firstElement = messagesToSummarize.getFirst();
        int firstElementIndex = messages.indexOf(firstElement);
        messages.removeAll(messagesToSummarize);
        messages.add(firstElementIndex, new IndexedOllamaChatMessage(firstElement.getId(), summarizing));
    }
}

package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.ChatViewModel;
import de.vrauchhaupt.chatbotfx.model.IndexedOllamaChatMessage;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.response.Model;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;
import jakarta.validation.constraints.NotNull;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class OllamaManager extends AbstractManager {

    private static final long REQUEST_TIMEOUT_SECONDS = 360L;
    private static final boolean VERBOSE = false;
    private static OllamaManager INSTANCE;
    private final BooleanProperty working = new SimpleBooleanProperty(false);
    private Thread currentLoadingThread = null;
    private Thread currentAskingThread = null;

    private OllamaManager() {

    }

    public static OllamaManager instance() {
        if (INSTANCE == null)
            INSTANCE = new OllamaManager();
        return INSTANCE;
    }

    private OllamaAPI newOllamaApi() {
        OllamaAPI ollamaAPI = new OllamaAPI(SettingsManager.instance().getOllamaHost());
        ollamaAPI.setVerbose(VERBOSE);
        ollamaAPI.setRequestTimeoutSeconds(REQUEST_TIMEOUT_SECONDS);
        return ollamaAPI;
    }

    public final boolean checkOllamaServerRunning() {
        try {
            newOllamaApi().listModels();
            return true;
        } catch (Exception e) {
            logLn("Ollama is not obviously not running, because of " + e.getMessage());
            return false;
        }
    }

    @NotNull
    public List<Model> listModels() {
        List<Model> models;
        try {
            working.set(true);
            models = newOllamaApi().listModels();
        } catch (Exception e) {
            logLn("Could not list models from ollama server", e);
            models = new ArrayList<>();
        } finally {
            working.set(false);
        }
        if (models == null)
            models = new ArrayList<>();
        else
            models = new ArrayList<>(models);
        return models;
    }

    public void systemNoticeAndAsk(String systemNotice,
                                   String message,
                                   List<IndexedOllamaChatMessage> messages,
                                   LlmModelCardJson model,
                                   ChatbotLlmStreamHandler streamHandler,
                                   Runnable onAskingFailure) {

        if (currentAskingThread != null) {
            logLn("Asking currently in action");
            onAskingFailure.run();
        }

        Options options = new OptionsBuilder()
                .setTemperature(model.getTemperature())
                .setTopP(model.getTop_p())
                .setTopK(model.getTop_k())
                .setRepeatPenalty(1.3f)
                .setRepeatLastN(128)
                .build();

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model.getLlmModel());
        OllamaChatRequestBuilder ollamaChatRequestBuilder = builder.withMessages(messages.stream()
                        .map(IndexedOllamaChatMessage::getChatMessage)
                        .collect(Collectors.toList()))
                .withOptions(options);
        if (systemNotice != null && !systemNotice.trim().isEmpty()) {
            ChatViewModel.instance().appendSystemOrPrompt(OllamaChatMessageRole.SYSTEM, systemNotice);
            Path modelCardsDirectory = SettingsManager.instance().getPathToLlmModelCards();
            Path path = modelCardsDirectory.resolve(LlmModelCardManager.instance().getSelectedLlModelCard().getModelCardName() + ".png");
            if (Files.exists(path) && messages.isEmpty()) {
                logLn("Attaching image '" + path + "' to LLM message.");
                ollamaChatRequestBuilder = ollamaChatRequestBuilder
                        .withMessage(OllamaChatMessageRole.SYSTEM, systemNotice, List.of(path.toFile()));
            } else {
                ollamaChatRequestBuilder = ollamaChatRequestBuilder
                        .withMessage(OllamaChatMessageRole.SYSTEM, systemNotice);
            }
        }

        if (message != null && !message.trim().isEmpty()) {
            ChatViewModel.instance().appendSystemOrPrompt(OllamaChatMessageRole.USER, message);
            ollamaChatRequestBuilder = ollamaChatRequestBuilder
                    .withMessage(OllamaChatMessageRole.USER, message);
        }
        OllamaChatRequest ollamaChatRequestModel = ollamaChatRequestBuilder.build();
        working.set(true);
        int newChatMessageId = IndexedOllamaChatMessage.newId();
        streamHandler.setChatMessageIndex(newChatMessageId);
        currentAskingThread = ThreadManager.instance().startThread("Asking Ollama Thread", () -> {
                    try {
                        OllamaChatResult chat = newOllamaApi().chat(ollamaChatRequestModel, streamHandler);
                        if ( chat.getHttpStatusCode() != 200)
                        {
                            logLn("Error code " + chat.getHttpStatusCode() + " was given!");
                        }
                        chat.getResponse();
                        streamHandler.inputHasStopped();
                    } catch (InterruptedException e) {
                        logLn("Stopping asking a message");
                    } catch (Exception e) {
                        onAskingFailure.run();
                        logLn("Could not ask '" + message + "' on model '" + model + "'", e);
                    } finally {
                        working.set(false);
                    }
                },
                x -> currentAskingThread = null);
    }

    public void loadModel(LlmModelCardJson llmModelCard,
                          Map.Entry<String, Path> llmModelFileForModelCard,
                          Consumer<LlmModelCardJson> llmModelCardWasLoaded,
                          Runnable llmModelCouldNotBeLoaded) {
        if (llmModelFileForModelCard == null || llmModelFileForModelCard.getKey() == null || llmModelFileForModelCard.getValue() == null) {
            logLn("Model card is null somewhere");
            llmModelCouldNotBeLoaded.run();
            return;
        }

        if (!Files.isRegularFile(llmModelFileForModelCard.getValue())) {
            logLn("Could not write modelfile for llm model at '" + llmModelFileForModelCard.getValue() + "'");
            llmModelCouldNotBeLoaded.run();
            return;
        }
        if (currentLoadingThread != null) {
            logLn("Loading already in progress");
            llmModelCouldNotBeLoaded.run();
            return;
        }

        System.out.println("Loading LLM model " + llmModelCard.getLlmModel());
        Path modelFile = llmModelFileForModelCard.getValue().getParent().resolve(llmModelFileForModelCard.getKey() + "_" + llmModelCard.getModelCardName() + "_ModelFile.txt");
        try (FileWriter fw = new FileWriter(modelFile.toFile())) {
            fw.write("FROM \"" + llmModelFileForModelCard.getValue().toAbsolutePath() + "\"\n");
        } catch (IOException e) {

            logLn("Could not write modelfile for llm model at '" + modelFile.toAbsolutePath() + "'", e);
            llmModelCouldNotBeLoaded.run();
            return;
        }

        try {
            working.set(true);
            currentLoadingThread = ThreadManager.instance().startThread("Loading OllamaApi", () -> {
                        try {
                            newOllamaApi().createModelWithFilePath(llmModelFileForModelCard.getKey(),
                                    modelFile.toAbsolutePath().toString());
                            llmModelCardWasLoaded.accept(llmModelCard);
                        } catch (Exception e) {
                            logLn("Could not load model", e);
                            llmModelCouldNotBeLoaded.run();
                        } finally {
                            working.set(false);
                        }
                    },
                    x -> currentLoadingThread = null);
        } catch (Exception e) {
            logLn("Could not create model by model file '" + modelFile + "'", e);
        }
    }

    @Override
    public boolean isWorking() {
        return working.get() || currentAskingThread != null || currentLoadingThread != null;
    }

    public void cancelWork() {
        if (currentLoadingThread != null) {
            try {
                currentLoadingThread.interrupt();
            } catch (Exception e) {
                logLn("Could not interrupt loading thread", e);
            }
        }
        if (currentAskingThread != null) {
            try {
                currentAskingThread.interrupt();
            } catch (Exception e) {
                logLn("Could not interrupt loading asking", e);
            }
        }
    }
}
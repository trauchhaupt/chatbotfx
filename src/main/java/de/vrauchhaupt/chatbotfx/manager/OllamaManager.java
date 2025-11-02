package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.ChatBot;
import de.vrauchhaupt.chatbotfx.helper.StringHelper;
import de.vrauchhaupt.chatbotfx.model.ChatViewModel;
import de.vrauchhaupt.chatbotfx.model.IndexedOllamaChatMessage;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import io.github.ollama4j.Ollama;
import io.github.ollama4j.exceptions.OllamaException;
import io.github.ollama4j.models.chat.*;
import io.github.ollama4j.models.response.Model;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;
import jakarta.validation.constraints.NotNull;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
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

    private static void inheritIO(final InputStream src, final PrintStream dest) {
        new Thread(new Runnable() {
            public void run() {
                Scanner sc = new Scanner(src);
                while (sc.hasNextLine()) {
                    dest.println(sc.nextLine());
                }
            }
        }).start();
    }

    public Ollama newOllamaApi() {
        Ollama ollamaAPI = new Ollama(SettingsManager.instance().getOllamaHost());
        ollamaAPI.setMetricsEnabled(false);
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

    public void unload(String curModelToUnload) {
        try {
            working.set(true);
            newOllamaApi().deleteModel(curModelToUnload, false);
        } catch (Exception e) {
            logLn("Could not delete model '" + curModelToUnload + "' from ollama server", e);
        } finally {
            working.set(false);
        }
    }

    public void paintPicture(List<IndexedOllamaChatMessage> messages,
                             LlmModelCardJson model) {
        ArrayList<IndexedOllamaChatMessage> messagesToSummarize = new ArrayList<>(messages);
        Options options = new OptionsBuilder()
                .setTemperature(0)
                .setTopP(model.getTop_p())
                .setTopK(model.getTop_k())
                .setRepeatPenalty(1.5f)
                .setRepeatLastN(64)
                .build();

        List<OllamaChatMessage> collect = messages.stream()
                .map(IndexedOllamaChatMessage::getChatMessage)
                .collect(Collectors.toList());
        collect.add(new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, "Create a LLM prompt about the story so far. It should be short and simple and describe one person out of this story."));
        OllamaChatRequest chatRequest = new OllamaChatRequest(model.getLlmModel(), true, collect)
                .withOptions(options);
        try {
            OllamaChatResult chatResult = newOllamaApi().chat(chatRequest, null);
            List<OllamaChatMessage> chatHistory = chatResult.getChatHistory();
            OllamaChatMessage pictureGenerationPrompt = chatHistory.getLast();
            logLn("Painting the picture with the following command:\n" + pictureGenerationPrompt.getResponse());
            ChatBot.chatMainWindow.fileNewImageRendering(pictureGenerationPrompt.getResponse());
        } catch (Exception e) {
            logLn("Could not summarize ", e);
        }
    }

    public void compressMessage(List<IndexedOllamaChatMessage> messages,
                                LlmModelCardJson model) {
        ArrayList<IndexedOllamaChatMessage> messagesToSummarize = new ArrayList<>(messages);
        Options options = new OptionsBuilder()
                .setTemperature(1.1f)
                .setTopP(model.getTop_p())
                .setTopK(model.getTop_k())
                .setRepeatPenalty(1.5f)
                .setRepeatLastN(64)
                .build();

        List<OllamaChatMessage> collect = messages.stream()
                .map(IndexedOllamaChatMessage::getChatMessage)
                .collect(Collectors.toList());
        collect.add(new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, "Summarize the history up to now in about 200 words."));
        OllamaChatRequest chatRequest = new OllamaChatRequest(model.getLlmModel(), true, collect)
                .withOptions(options);
        try {
            OllamaChatResult chatResult = newOllamaApi().chat(chatRequest, null);
            List<OllamaChatMessage> chatHistory = chatResult.getChatHistory();
            OllamaChatMessage sumarization = chatHistory.getLast();
            sumarization.setRole(OllamaChatMessageRole.SYSTEM);
            logLn("Summarized to the following:\n" + sumarization);
            messagesToSummarize.removeFirst(); // first message is always the intro
            ChatViewModel.instance().replaceChatHistory(messagesToSummarize, sumarization);
        } catch (Exception e) {
            logLn("Could not summarize ", e);
        }
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


        List<OllamaChatMessage> messagesToSend = messages.stream()
                .map(IndexedOllamaChatMessage::getChatMessage)
                .collect(Collectors.toList());
        OllamaChatRequest ollamaChatRequestModel = new OllamaChatRequest(model.getLlmModel(), false, messagesToSend)
                .withOptions(options);

        if (systemNotice != null && !systemNotice.trim().isEmpty()) {
            ChatViewModel.instance().appendSystemOrPrompt(OllamaChatMessageRole.SYSTEM, systemNotice);
            Path modelCardsDirectory = SettingsManager.instance().getPathToLlmModelCards();
            Path path = modelCardsDirectory.resolve(LlmModelCardManager.instance().getSelectedLlModelCard().getModelCardName() + ".png");
            if (Files.exists(path) && messages.isEmpty()) {
                logLn("Attaching image '" + path + "' to LLM message.");
                // @TODO .. add the correct way to add the image
                ollamaChatRequestModel.withMessage(OllamaChatMessageRole.SYSTEM, systemNotice);
            } else {
                ollamaChatRequestModel.withMessage(OllamaChatMessageRole.SYSTEM, systemNotice);
            }
        }


        if (message != null && !message.trim().isEmpty()) {
            ChatViewModel.instance().appendSystemOrPrompt(OllamaChatMessageRole.USER, message);
            ollamaChatRequestModel.withMessage(OllamaChatMessageRole.USER, message);
        }
        working.set(true);
        int newChatMessageId = IndexedOllamaChatMessage.newId();
        streamHandler.setChatMessageIndex(newChatMessageId);
        currentAskingThread = ThreadManager.instance().startThread("Asking Ollama Thread", () -> {
                    try {
                        OllamaChatResult chatResult = newOllamaApi().chat(ollamaChatRequestModel, streamHandler);
                        if (chatResult.getResponseModel().getError() != null) {
                            throw new RuntimeException("Error code " + chatResult.getResponseModel().getError() + " was given!");
                        }
                        streamHandler.inputHasStopped();
                        List<IndexedOllamaChatMessage> fullHistory = ChatViewModel.instance().getFullHistory();
                        int messagesToStripForLLM = SettingsManager.instance().getMessagesToStripForLLM();
                        if (fullHistory.size() > messagesToStripForLLM + 7) {
                            ThreadManager.instance().startThread("Summarize LLM Messages",
                                    () -> compressMessage(fullHistory.subList(0, messagesToStripForLLM), model),
                                    null);
                        }
                        if (fullHistory.size() > 4 && (fullHistory.size() % 5 == 0 || (fullHistory.size() & 6) == 0)) {
                            ThreadManager.instance().startThread("Paint a Picture",
                                    () -> paintPicture(fullHistory.subList(fullHistory.size() - 4, fullHistory.size()), model),
                                    null);
                        }
                    } catch (Exception e) {
                        onAskingFailure.run();
                        logLn("Could not ask '" + message + "' on model '" + model + "'", e);
                    } finally {
                        working.set(false);
                    }
                },
                x -> currentAskingThread = null);
    }

    public void loadModelSynchronous(LlmModelCardJson llmModelCard) {
        Map.Entry<String, Path> llmModelFileForModelCard = LlmModelCardManager.instance().findLlmModelFileForModelCard(llmModelCard);

        if (llmModelFileForModelCard == null || llmModelFileForModelCard.getKey() == null || llmModelFileForModelCard.getValue() == null) {
            throw new RuntimeException("Model card is null somewhere");
        }

        if (!Files.isRegularFile(llmModelFileForModelCard.getValue())) {
            throw new RuntimeException("Could not write modelfile for llm model at '" + llmModelFileForModelCard.getValue() + "'");
        }
        if (currentLoadingThread != null) {
            throw new RuntimeException("Loading already in progress");
        }

        try {
            doModelLoad(llmModelCard, llmModelFileForModelCard);
        } catch (Exception e) {
            throw new RuntimeException("Could not create model '" + llmModelCard.getLlmModel() + "' from llm model " + llmModelFileForModelCard.getValue().toAbsolutePath(), e);
        }
    }

    private void doModelLoad(LlmModelCardJson llmModelCard,
                             Map.Entry<String, Path> llmModelFileForModelCard) {

        try {
            Path ggufFile = llmModelFileForModelCard.getValue();
            Path modelFile = ggufFile.getParent().resolve(llmModelFileForModelCard.getKey() + "_ModelFile.txt");
            try (FileWriter fw = new FileWriter(modelFile.toFile())) {
                fw.write("FROM \"" + ggufFile.toAbsolutePath() + "\"\n");
            } catch (IOException e) {

                throw new RuntimeException("Could not write modelfile for llm model at '" + modelFile.toAbsolutePath() + "'", e);
            }
            System.out.println("Loading LLM model " + llmModelCard.getLlmModel());

            ProcessBuilder processBuilder = new ProcessBuilder("cmd",
                    "/c",
                    "ollama",
                    "create",
                    llmModelCard.getLlmModel(),
                    "-f",
                    modelFile.toAbsolutePath().toString());
            Process process = processBuilder.start();
            inheritIO(process.getInputStream(), System.out);
            inheritIO(process.getErrorStream(), System.err);
            process.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("Could not load model " + llmModelCard.getLlmModel() + " from " + llmModelFileForModelCard.getKey(), e);
        }
    }

    public void loadModel(LlmModelCardJson llmModelCard,
                          Consumer<LlmModelCardJson> llmModelCardWasLoaded,
                          Runnable llmModelCouldNotBeLoaded) {
        Map.Entry<String, Path> llmModelFileForModelCard = LlmModelCardManager.instance().findLlmModelFileForModelCard(llmModelCard);
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

        try {
            working.set(true);
            currentLoadingThread = ThreadManager.instance().startThread("Loading OllamaApi", () -> {
                        try {
                            doModelLoad(llmModelCard, llmModelFileForModelCard);
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
            logLn("Could not create model by model file '" + llmModelCard.getLlmModel() + "'", e);
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

    public OllamaChatResult chat(OllamaChatRequest request, OllamaChatTokenHandler tokenHandler) throws OllamaException {
        Path debugDirectory = Paths.get("debug");
        long startDate = new Date().getTime();
        long size = 0;
        Path debugFile = null;
        try {
            if (!Files.exists(debugDirectory)) {
                Files.createDirectory(debugDirectory);
            }
            debugFile = debugDirectory.resolve(request.getModel() + ".debug");
            try (OutputStream outputStream = Files.newOutputStream(debugFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                 OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
                writer.write("\n\n----------------------------------------------------------------------------------");
                for (OllamaChatMessage message : request.getMessages()) {
                    writer.write("\n<" + message.getRole().getRoleName().toUpperCase() + "> : ");
                    writer.write(StringHelper.toBlock(message.getResponse()));
                    size += message.getResponse().length();
                }
            }
        } catch (IOException e) {
            System.err.println("Could not write debug information");
            e.printStackTrace();
        }

        Ollama ollamaAPI = newOllamaApi();
        OllamaChatResult returnValue = ollamaAPI.chat(request, tokenHandler);

        if (debugFile != null) {
            try (OutputStream outputStream = Files.newOutputStream(debugFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                 OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
                long endDate = new Date().getTime();
                writer.write("\n###<" + returnValue.getResponseModel().getMessage().getRole().getRoleName().toUpperCase() + "> : ");
                writer.write(StringHelper.toBlock(returnValue.getResponseModel().getMessage().getResponse()));
                writer.write("\n\nSTATISTIC : " + ((endDate - startDate) / 1000) + " sec for " + size + " characters");
            } catch (IOException e) {
                System.err.println("Could not write debug information");
                e.printStackTrace();
            }
        }
        return returnValue;
    }


}
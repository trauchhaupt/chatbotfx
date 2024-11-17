package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.*;
import io.github.ollama4j.models.response.LibraryModel;
import io.github.ollama4j.models.response.Model;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;
import jakarta.validation.constraints.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OllamaManager {

    private static final long REQUEST_TIMEOUT_SECONDS = 360L;
    private static final boolean VERBOSE = false;
    private static final String OLLAMA_HOST = "http://localhost:11434/";
    private static OllamaManager INSTANCE;
    private final OllamaAPI ollamaAPI;
    private final Options options;


    private OllamaManager() {
        this.ollamaAPI = new OllamaAPI(OLLAMA_HOST);
        ollamaAPI.setVerbose(VERBOSE);
        ollamaAPI.setRequestTimeoutSeconds(REQUEST_TIMEOUT_SECONDS);
        checkOllamaServerRunning();
        options = new OptionsBuilder()
                .setTemperature(1.1f)
                .setTopP(0.7f)
                .setTopK(50)
                .setRepeatPenalty(1.3f)
                .build();
    }

    public static OllamaManager instance() {
        if (INSTANCE == null)
            INSTANCE = new OllamaManager();
        return INSTANCE;
    }

    public final void checkOllamaServerRunning() {
        try {
            List<ProcessHandle> processes = ProcessHandle.allProcesses().toList();
            for (ProcessHandle processHandle : processes) {
                if (!processHandle.isAlive())
                    continue;
                String commandLine = processHandle.info().command().orElse("");
                if (commandLine.contains("ollama.exe")) {
                    System.out.println("Found already running " + commandLine + " with process-id " + processHandle.pid());
                    return;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Cannot check if ollama is already running", e);
        }
        throw new RuntimeException("The ollama server is not running. Start it by calling 'ollama.exe serve'");
    }

    public List<LibraryModel> listLibraryModels() {
        List<LibraryModel> libraryModels = null;
        try {
            libraryModels = ollamaAPI.listModelsFromLibrary();
        } catch (Exception e) {
            throw new RuntimeException("Could not list library models from ollama server", e);
        }
        if (libraryModels == null)
            libraryModels = new ArrayList<>();
        else
            libraryModels = new ArrayList<>(libraryModels);
        return libraryModels;
    }

    @NotNull
    public List<Model> listModels() {
        List<Model> models;
        try {
            models = ollamaAPI.listModels();
        } catch (Exception e) {
            throw new RuntimeException("Could not list models from ollama server", e);
        }
        if (models == null)
            models = new ArrayList<>();
        else
            models = new ArrayList<>(models);
        return models;
    }

    public List<OllamaChatMessage> systemNoticeAndAsk(String systemNotice, String message,
                                                      List<OllamaChatMessage> messages, String model, ChatbotLlmStreamHandler streamHandler) {
        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model);
        OllamaChatRequestBuilder ollamaChatRequestBuilder = builder.withMessages(messages)
                .withOptions(options);
        if (systemNotice != null && !systemNotice.trim().isEmpty()) {
            Path modelCardsDirectory = SettingsManager.instance().getPathToLlmModelCards();
            Path path = modelCardsDirectory.resolve(LlmModelCardManager.instance().getSelectedLlModelCard().getModelCardName() + ".png");
            if (Files.exists(path)) {
                ollamaChatRequestBuilder = ollamaChatRequestBuilder
                        .withMessage(OllamaChatMessageRole.SYSTEM, systemNotice, List.of(path.toFile()));
            } else {
                ollamaChatRequestBuilder = ollamaChatRequestBuilder
                        .withMessage(OllamaChatMessageRole.SYSTEM, systemNotice);
            }
        }
        if (message != null && !message.trim().isEmpty())
            ollamaChatRequestBuilder = ollamaChatRequestBuilder
                    .withMessage(OllamaChatMessageRole.USER, message);
        OllamaChatRequest ollamaChatRequestModel = ollamaChatRequestBuilder.build();
        try {
            OllamaChatResult chat = ollamaAPI.chat(ollamaChatRequestModel, streamHandler);
            chat.getResponse();
            streamHandler.inputHasStopped();
            return chat.getChatHistory();
        } catch (Exception e) {
            throw new RuntimeException("Could not ask '" + message + "' on model '" + model + "'", e);
        }
    }

    public List<OllamaChatMessage> askWithImages(
            String message, List<OllamaChatMessage> messages, List<File> imageFiles, String model, ChatbotLlmStreamHandler streamHandler) {
        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model);
        OllamaChatRequest ollamaChatRequestModel = builder
                .withMessages(messages)
                .withMessage(OllamaChatMessageRole.USER, message, imageFiles)
                .withOptions(options)
                .build();
        try {
            OllamaChatResult chat = ollamaAPI.chat(ollamaChatRequestModel, streamHandler);
            chat.getResponse();
            streamHandler.inputHasStopped();
            return chat.getChatHistory();
        } catch (Exception e) {
            throw new RuntimeException("Could not ask '" + message + "' with images on model '" + model + "'", e);
        }
    }

    public void loadModel(
            LlmModelCardJson llmModelCard,
            Map.Entry<String, Path> llmModelFileForModelCard) {
        if (llmModelFileForModelCard == null || llmModelFileForModelCard.getKey() == null || llmModelFileForModelCard.getValue() == null)
            return;
        if (!Files.isRegularFile(llmModelFileForModelCard.getValue()))
            return;

        Path modelFile = llmModelFileForModelCard.getValue().getParent().resolve(llmModelFileForModelCard.getKey() + "_" + llmModelCard.getModelCardName() + "_ModelFile.txt");
        try (FileWriter fw = new FileWriter(modelFile.toFile())) {
            fw.write("FROM \"" + llmModelFileForModelCard.getValue().toAbsolutePath() + "\"\n");
            fw.write("PARAMETER temperature " + llmModelCard.getTemperature() + "\n");
            fw.write("PARAMETER top_p " + llmModelCard.getTop_p() + "\n");
            fw.write("PARAMETER top_k " + llmModelCard.getTop_k() + "\n");
            fw.write("PARAMETER stop \"<|start_header_id|>\"\n");
            fw.write("PARAMETER stop \"<|end_header_id|>\"\n");
            fw.write("PARAMETER stop \"<|eot_id|>\"\n");
            fw.write("PARAMETER stop \"<|reserved_special_token\"\n");
            fw.write("TEMPLATE \"\"\"{{ if .System }}<|start_header_id|>system<|end_header_id|>\n");
            fw.write("\n");
            fw.write("{{ .System }}<|eot_id|>{{ end }}{{ if .Prompt }}<|start_header_id|>user<|end_header_id|>\n");
            fw.write("\n");
            fw.write("{{ .Prompt }}<|eot_id|>{{ end }}<|start_header_id|>assistant<|end_header_id|>\n");
            fw.write("\n");
            fw.write("{{ .Response }}<|eot_id|>\"\"\"\n");
            //fw.write("SYSTEM " + llmModelCard.getSystem() + "\n");
        } catch (IOException e) {
            throw new RuntimeException("Could not write modelfile for llm model at '" + modelFile.toAbsolutePath() + "'", e);
        }

        try {
            ollamaAPI.createModelWithFilePath(llmModelFileForModelCard.getKey(),
                    modelFile.toAbsolutePath().toString());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not create model by model file '" + modelFile + "'", e);
        }
    }
}
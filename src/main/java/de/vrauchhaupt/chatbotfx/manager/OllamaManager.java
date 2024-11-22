package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.*;
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

public class OllamaManager extends AbstractManager {

    private static final long REQUEST_TIMEOUT_SECONDS = 360L;
    private static final boolean VERBOSE = false;
    private static OllamaManager INSTANCE;
    private final BooleanProperty working = new SimpleBooleanProperty(false);

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
            listModels();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
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
            throw new RuntimeException("Could not list models from ollama server", e);
        } finally {
            working.set(false);
        }
        if (models == null)
            models = new ArrayList<>();
        else
            models = new ArrayList<>(models);
        return models;
    }

    public List<OllamaChatMessage> systemNoticeAndAsk(String systemNotice, String message,
                                                      List<OllamaChatMessage> messages, LlmModelCardJson model, ChatbotLlmStreamHandler streamHandler) {

        Options options = new OptionsBuilder()
                .setTemperature(model.getTemperature())
                .setTopP(model.getTop_p())
                .setTopK(model.getTop_k())
                .setRepeatPenalty(1.7f)
                .build();

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model.getLlmModel());
        OllamaChatRequestBuilder ollamaChatRequestBuilder = builder.withMessages(messages)
                .withOptions(options);
        if (systemNotice != null && !systemNotice.trim().isEmpty()) {
            Path modelCardsDirectory = SettingsManager.instance().getPathToLlmModelCards();
            Path path = modelCardsDirectory.resolve(LlmModelCardManager.instance().getSelectedLlModelCard().getModelCardName() + ".png");
            if (Files.exists(path) && messages.size() == 0) {
                System.out.println("Attaching image '" + path + "' to LLM message.");
                ollamaChatRequestBuilder = ollamaChatRequestBuilder
                        .withMessage(OllamaChatMessageRole.SYSTEM, systemNotice, List.of(path.toFile()));
            } else {
                ollamaChatRequestBuilder = ollamaChatRequestBuilder
                        .withMessage(OllamaChatMessageRole.SYSTEM, systemNotice);
            }
        }

        if (message != null && !message.trim().isEmpty()) {
            ollamaChatRequestBuilder = ollamaChatRequestBuilder
                    .withMessage(OllamaChatMessageRole.USER, message);
        }
        OllamaChatRequest ollamaChatRequestModel = ollamaChatRequestBuilder.build();
        try {
            working.set(true);
            OllamaChatResult chat = newOllamaApi().chat(ollamaChatRequestModel, streamHandler);
            chat.getResponse();
            streamHandler.inputHasStopped();
            return chat.getChatHistory();
        } catch (Exception e) {
            throw new RuntimeException("Could not ask '" + message + "' on model '" + model + "'", e);
        } finally {
            working.set(false);
        }
    }

    public void loadModel(
            LlmModelCardJson llmModelCard,
            Map.Entry<String, Path> llmModelFileForModelCard) {
        if (llmModelFileForModelCard == null || llmModelFileForModelCard.getKey() == null || llmModelFileForModelCard.getValue() == null)
            return;
        if (!Files.isRegularFile(llmModelFileForModelCard.getValue()))
            return;
        System.out.println("Loading LLM model " +llmModelCard.getLlmModel());

        Path modelFile = llmModelFileForModelCard.getValue().getParent().resolve(llmModelFileForModelCard.getKey() + "_" + llmModelCard.getModelCardName() + "_ModelFile.txt");
        try (FileWriter fw = new FileWriter(modelFile.toFile())) {
            fw.write("FROM \"" + llmModelFileForModelCard.getValue().toAbsolutePath() + "\"\n");
            //fw.write("PARAMETER temperature " + llmModelCard.getTemperature() + "\n");
            //fw.write("PARAMETER top_p " + llmModelCard.getTop_p() + "\n");
            //fw.write("PARAMETER top_k " + llmModelCard.getTop_k() + "\n");
            //fw.write("PARAMETER stop \"<|start_header_id|>\"\n");
            //fw.write("PARAMETER stop \"<|end_header_id|>\"\n");
            //fw.write("PARAMETER stop \"<|eot_id|>\"\n");
            //fw.write("PARAMETER stop \"<|reserved_special_token\"\n");
            /*fw.write("TEMPLATE \"\"\"{{ if .System }}<|start_header_id|>system<|end_header_id|>\n");
            fw.write("\n");
            fw.write("{{ .System }}<|eot_id|>{{ end }}{{ if .Prompt }}<|start_header_id|>user<|end_header_id|>\n");
            fw.write("\n");
            fw.write("{{ .Prompt }}<|eot_id|>{{ end }}<|start_header_id|>assistant<|end_header_id|>\n");
            fw.write("\n");
            fw.write("{{ .Response }}<|eot_id|>\"\"\"\n");*/
            //fw.write("SYSTEM " + llmModelCard.getSystem() + "\n");
        } catch (IOException e) {
            throw new RuntimeException("Could not write modelfile for llm model at '" + modelFile.toAbsolutePath() + "'", e);
        }

        try {
            working.set(true);
            newOllamaApi().createModelWithFilePath(llmModelFileForModelCard.getKey(),
                    modelFile.toAbsolutePath().toString());
        } catch (Exception e) {
            throw new RuntimeException("Could not create model by model file '" + modelFile + "'", e);
        } finally {
            working.set(false);
        }
    }

    @Override
    public boolean isWorking() {
        return working.get();
    }
}
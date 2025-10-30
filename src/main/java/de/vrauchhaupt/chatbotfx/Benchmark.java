package de.vrauchhaupt.chatbotfx;

import de.vrauchhaupt.chatbotfx.helper.JsonHelper;
import de.vrauchhaupt.chatbotfx.manager.LlmModelCardManager;
import de.vrauchhaupt.chatbotfx.manager.OllamaManager;
import de.vrauchhaupt.chatbotfx.manager.SettingsManager;
import de.vrauchhaupt.chatbotfx.model.ChatMessageHelper;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import de.vrauchhaupt.chatbotfx.model.SceneJson;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.response.Model;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Benchmark extends AbstractProgram {

    Map<String, Long> llmPromptDurationTotal = new HashMap<>();
    Map<String, Long> llmPromptPromptsTotal = new HashMap<>();

    public static void main(String... args) {
        AbstractProgram.initAndRun(Benchmark.class);
    }

    @Override
    protected void run() throws Exception {
        SettingsManager.instance().setSelectedLlmModelCard(null);

        List<Path> sceneFiles = Files.list(SettingsManager.instance().getPathToLlmModelCards())
                .filter(x -> x.toString().toLowerCase().endsWith(".scene"))
                .toList();

        for (Path sceneFile : sceneFiles) {
            SceneJson scene = JsonHelper.loadFromFile(sceneFile, SceneJson.class);
            runWithScene(scene);
        }

        log("");
        log("###############################");
        log("##### SUMMARY Prompt LLM ######");
        log("###############################");
        llmPromptDurationTotal.forEach((key, value) -> log(key + " -> " + value + " sec. total"));
    }

    private void runWithScene(SceneJson scene) throws IOException {
        log("--------------------------------------------------------------");
        log("- SCENE : " + scene.getDescription());
        log("--------------------------------------------------------------");
        List<String> modelsLoadable = Files.list(SettingsManager.instance().getPathToLlmModelFiles())
                .filter(x -> x.getFileName().toString().endsWith(".gguf"))
                .map(x -> FilenameUtils.getBaseName(x.getFileName().toString()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        System.out.println(modelsLoadable.stream()
                .collect(Collectors.joining("\n")));
        for (String curModel : modelsLoadable) {
            LlmModelCardJson virtualModelCard = new LlmModelCardJson();
            virtualModelCard.setModelCardName("Sabine");
            virtualModelCard.setLlmModel(curModel);
            virtualModelCard.setSystem("");
            virtualModelCard.setTemperature(1.5f);
            virtualModelCard.setTop_k(20);
            virtualModelCard.setTop_p(0.5f);

            List<String> alreadyListedModels = OllamaManager.instance().listModels()
                    .stream()
                    .map(Model::getModelName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            ArrayList<String> modelsToUnload = new ArrayList<>(alreadyListedModels);
            modelsToUnload.remove(curModel);
            for (String curModelToUnload : modelsToUnload) {
                System.out.println("Unloading LLM model " + curModelToUnload);
                OllamaManager.instance().unload(curModelToUnload);
            }
            if (!alreadyListedModels.contains(curModel)) {
                OllamaManager.instance().loadModelSynchronous(virtualModelCard);
            }
            this.runWithSceneAndModel(scene, virtualModelCard);
        }
    }

    private void runWithSceneAndModel(SceneJson scene, LlmModelCardJson virtualModelCard) {

        List<OllamaChatMessage> scenery = scene.getSettingsMessages(virtualModelCard);
        List<OllamaChatMessage> history = new ArrayList<>();
        history.addAll(scenery);

        log("--------------------------------------------------------------");
        log("- MODEL : " + virtualModelCard.getLlmModel());
        log("--------------------------------------------------------------");

        try (FileWriter fwDebug = new FileWriter(new File(virtualModelCard.getLlmModel() + ".debug"))) {

            for (int iPrompt = 0; iPrompt < scene.getMessages().size(); iPrompt++) {
                OllamaChatMessage promptToWork = ChatMessageHelper.createReplacedChatMessage(scene.getMessages().get(iPrompt), virtualModelCard);
                log("#### Prompt " + iPrompt);
                Date startDate = logStart();
                log("<" + promptToWork.getRole().getRoleName().toUpperCase() + ">: " + promptToWork.getResponse());
                history.add(promptToWork);

                fwDebug.write("\n\n" + "#### Prompt " + iPrompt + "\n");
                fwDebug.write(history.stream()
                        .map(x -> "<" + x.getRole().getRoleName().toUpperCase() + ">: " + x.getResponse())
                        .collect(Collectors.joining("\n")));

                OllamaChatRequest chatRequest = new OllamaChatRequest(virtualModelCard.getLlmModel(), false, history)
                        .withOptions(options)
                        .withUseTools(false)
                        .build();

                OllamaChatResult chat = null;
                try {
                    chat = ollamaAPI.chat(chatRequest, null);
                } catch (Exception e) {
                    log("Failed to chat with AI");
                    log(e);
                    continue;
                }
                log("#### Answer");
                log(chat.getResponseModel().getMessage().getResponse());
                long duration = logEnd(startDate);
                if (llmPromptPromptsTotal.get(virtualModelCard.getLlmModel()) == null) // first one not, as model must be loaded
                {
                    llmPromptPromptsTotal.put(virtualModelCard.getLlmModel(), Long.valueOf(0));
                } else {
                    llmPromptPromptsTotal.put(virtualModelCard.getLlmModel(), llmPromptPromptsTotal.computeIfAbsent(virtualModelCard.getLlmModel(), x -> 0L) + 1);
                    llmPromptDurationTotal.put(virtualModelCard.getLlmModel(), llmPromptDurationTotal.computeIfAbsent(virtualModelCard.getLlmModel(), x -> 0L) + duration);
                }
                history = new ArrayList<>(chat.getChatHistory());
                log("------------------------------------------------------------------------------------------------------------------------");
                log("");
            }
            Long timeTotal = llmPromptDurationTotal.get(virtualModelCard.getLlmModel());
            Long amount = llmPromptPromptsTotal.get(virtualModelCard.getLlmModel());

            if (timeTotal == null || amount == null)
                fwDebug.write("\n\n-----------------------------------------------------------------\n" +
                        "NO EXECUTION");
            else
                fwDebug.write("\n\n-----------------------------------------------------------------\n" +
                        "Total : " + timeTotal + " seconds for " + amount + " prompts = " + (timeTotal / amount) + " seconds/prompt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
package de.vrauchhaupt.chatbotfx;

import de.vrauchhaupt.chatbotfx.helper.JsonHelper;
import de.vrauchhaupt.chatbotfx.manager.LlmModelCardManager;
import de.vrauchhaupt.chatbotfx.manager.OllamaManager;
import de.vrauchhaupt.chatbotfx.manager.SettingsManager;
import de.vrauchhaupt.chatbotfx.manager.StableDiffusionManager;
import de.vrauchhaupt.chatbotfx.model.ChatMessageHelper;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import de.vrauchhaupt.chatbotfx.model.SceneJson;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class StoryEvolver extends AbstractProgram {

    Map<LlmModelCardJson, Long> llmDurationTotal = new HashMap<>();
    Map<LlmModelCardJson, Long> llmPromptDurationTotal = new HashMap<>();
    Map<LlmModelCardJson, Long> txtToImageDurationTotal = new HashMap<>();

    public static void main(String... args) {
        AbstractProgram.initAndRun(StoryEvolver.class);
    }

    @Override
    protected void run() throws Exception {

        List<Path> sceneFiles = Files.list(SettingsManager.instance().getPathToLlmModelCards())
                .filter(x -> x.toString().toLowerCase().endsWith(".scene"))
                .toList();

        for (Path sceneFile : sceneFiles) {
            SceneJson scene = JsonHelper.loadFromFile(sceneFile, SceneJson.class);
            runWithScene(scene);
        }
        log("");
        log("#########################");
        log("##### SUMMARY LLM ######");
        log("########################");
        llmDurationTotal.forEach((key, value) -> log(key.getModelCardName() + "/" + key.getLlmModel() + " -> " + value + " sec. total"));

        log("");
        log("###############################");
        log("##### SUMMARY Prompt LLM ######");
        log("###############################");
        llmPromptDurationTotal.forEach((key, value) -> log(key.getModelCardName() + "/" + key.getLlmModel() + " -> " + value + " sec. total"));

        log("");
        log("##############################");
        log("##### SUMMARY Txt2IMage ######");
        log("##############################");
        txtToImageDurationTotal.forEach((key, value) -> log(key.getModelCardName() + "/" + key.getLlmModel() + " -> " + value + " sec. total"));
    }

    private void runWithScene(SceneJson scene) {
        log("--------------------------------------------------------------");
        log("- SCENE : " + scene.getDescription());
        log("--------------------------------------------------------------");
        for (LlmModelCardJson curModel : LlmModelCardManager.instance().getAvailableModelCards()) {
            runWithSceneAndModel(scene, curModel);
        }

    }

    private void runWithSceneAndModel(SceneJson scene, LlmModelCardJson curModel) {
        LlmModelCardManager.instance().llmModelHasChanged(curModel);
        List<OllamaChatMessage> scenery = scene.getSettingsMessages(curModel);
        List<OllamaChatMessage> history = new ArrayList<>();
        history.addAll(scenery);

        log("--------------------------------------------------------------");
        log("- MODEL : " + curModel.getModelCardName() + " - " + curModel.getLlmModel() + " / " + curModel.getTxt2ImgModel());
        log("--------------------------------------------------------------");

        for (int iPrompt = 0; iPrompt < scene.getMessages().size(); iPrompt++) {
            OllamaChatMessage promptToWork = ChatMessageHelper.createReplacedChatMessage(scene.getMessages().get(iPrompt), curModel);
            log("#### Prompt " + iPrompt);
            Date startDate = logStart();
            log("<" + promptToWork.getRole().getRoleName().toUpperCase() + "> : " + promptToWork.getResponse());
            history.add(promptToWork);

            OllamaChatRequest chatRequest = new OllamaChatRequest(curModel.getLlmModel(), false, history)
                    .withUseTools(false)
                    .withOptions(options);

            OllamaChatResult chat = null;
            try {
                chat = OllamaManager.instance().chat(chatRequest, null);
            } catch (Exception e) {
                log("Failed to chat with AI");
                log(e);
                continue;
            }
            log("#### Answer");
            log(chat.getResponseModel().getMessage().getResponse());
            long duration = logEnd(startDate);
            llmDurationTotal.put(curModel, llmDurationTotal.computeIfAbsent(curModel, x -> 0L) + duration);
            history = new ArrayList<>(chat.getChatHistory());

            log("#### Txt2ImgPrompt ");
            startDate = logStart();
            String txt2ImagePrompt = null;
            try {
                txt2ImagePrompt = createTxt2ImagePrompt(scene, curModel, history);
            } catch (Exception e) {
                log("Could not create Txt2ImagePrompt");
                log(e);
                continue;
            }
            duration = logEnd(startDate);
            llmPromptDurationTotal.put(curModel, llmDurationTotal.computeIfAbsent(curModel, x -> 0L) + duration);

            startDate = logStart();
            imageIndex++;
            IMAGE_INDEX.put(imageIndex, Path.of("C:\\tmp\\promt-gen\\" + curModel.getModelCardName() + "_" + iPrompt + ".jpg"));
            StableDiffusionManager.instance().renderWithPrompt(imageIndex,
                    curModel.getTxt2ImgModel(),
                    curModel.getModelCardName(),
                    curModel.getTxt2ImgModelStyle(),
                    txt2ImagePrompt,
                    StableDiffusionManager.GENERATED_IMAGE_WIDTH * 4,
                    StableDiffusionManager.GENERATED_IMAGE_WIDTH * 5,
                    this);
            duration = logEnd(startDate);
            txtToImageDurationTotal.put(curModel, llmDurationTotal.computeIfAbsent(curModel, x -> 0L) + duration);
            log("------------------------------------------------------------------------------------------------------------------------");
            log("");
        }
    }
}


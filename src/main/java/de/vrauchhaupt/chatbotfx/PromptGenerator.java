package de.vrauchhaupt.chatbotfx;

import de.vrauchhaupt.chatbotfx.manager.*;
import de.vrauchhaupt.chatbotfx.model.ChatViewModel;
import de.vrauchhaupt.chatbotfx.model.DisplayRole;
import de.vrauchhaupt.chatbotfx.model.IndexedOllamaChatMessage;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.response.Model;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PromptGenerator implements IPrintFunction {
    private static final Map<Integer, Path> IMAGE_INDEX = new HashMap<>();
    private static PromptGenerator instance;

    public static void main(String... args) {
        try {
            instance = new PromptGenerator();
            instance.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void run() throws OllamaBaseException, IOException, InterruptedException {
        SettingsManager.instance().loadFromConfigFile();
        Options options = new OptionsBuilder()
                .setTemperature(1.3f)
                .setTopP(50)
                .setTopK(30)
                .setRepeatPenalty(1.3f)
                .setRepeatLastN(128)
                .build();

        LlmModelCardManager.instance().getSelectedLlModelCard();
        ChatViewModel.instance().loadMessagesFromFile(this);

        ArrayList<IndexedOllamaChatMessage> fullHistory = new ArrayList<>(ChatViewModel.instance().getFullHistory());
        while (fullHistory.size() > 15)
            fullHistory.remove(fullHistory.size() - 1);
        int index = 0;
        List<String> modelNames = OllamaManager.instance().listModels().stream()
                .map(Model::getModelName)
                .distinct()
                .sorted()
                .toList();
        List<String> txt2ImageModels = LlmModelCardManager.instance().getAvailableModelCards().stream()
                .map(LlmModelCardJson::getTxt2ImgModel)
                .filter(x -> x != null && !x.trim().isEmpty())
                .distinct()
                .sorted()
                .toList();

        for (String model : modelNames) {
            for (String txt2ImageModel : txt2ImageModels) {
                for (int maxMessages = 3; maxMessages <= fullHistory.size(); maxMessages++) {
                    System.out.println("--------------------------------------------------------------");
                    System.out.println("- MODEL : " + model + " - " + txt2ImageModel + " - " + maxMessages);
                    System.out.println("--------------------------------------------------------------");

                    StringBuilder story = new StringBuilder("Create a short description (less than 75 words) to create a picture for the following story. Do not tell the story, but describe the people and the scene so an image could be painted:");
                    int i = 0;
                    for (IndexedOllamaChatMessage indexedOllamaChatMessage : fullHistory) {
                        story.append("\n")
                                .append(indexedOllamaChatMessage.getChatMessage().getRole().getRoleName())
                                .append(": \"")
                                .append(indexedOllamaChatMessage.getChatMessage().getContent())
                                .append("\"");
                        if (i++ > maxMessages)
                            break;
                    }
                    OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model);
                    String imgPrompt = "A scene of people. The people are all 30 years or above:\n" + story;

                    OllamaChatMessage chatMessage = new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, imgPrompt);
                    OllamaChatRequestBuilder ollamaChatRequestBuilder = builder.withMessages(new ArrayList<>(List.of(chatMessage)));
                    OllamaAPI ollamaAPI = new OllamaAPI(SettingsManager.instance().getOllamaHost());
                    ollamaAPI.setVerbose(false);
                    ollamaAPI.setRequestTimeoutSeconds(1200L);
                    List<OllamaChatMessage> messages = ollamaChatRequestBuilder
                            .withOptions(options)
                            .build()
                            .getMessages();
                    OllamaChatResult chat = ollamaAPI.chat(model, messages);

                    String createdPrompt = chat.getResponse();
                    createdPrompt = createdPrompt.replace("\n\n", "\n");
                    System.out.println(createdPrompt);
                    System.out.println("--------------------------------------------------------------");
                    index++;
                    IMAGE_INDEX.put(index, Path.of("C:\\tmp\\promt-gen\\" + model + "_" + txt2ImageModel + "_" + maxMessages + ".jpg"));
                    StableDiffusionManager.instance().renderWithPrompt(index,
                            txt2ImageModel,
                            model,
                            null,
                            createdPrompt,
                            StableDiffusionManager.UPSCALED_GENERATED_IMAGE_WIDTH,
                            StableDiffusionManager.UPSCALED_GENERATED_IMAGE_HEIGHT,
                            this);
                    System.out.println("");
                    System.out.println("");
                }
            }
        }
    }

    @Override
    public void renderOnFxThread(DisplayRole displayRole, String textFragment, int chatMessageIndex) {

    }

    @Override
    public void renderNewLine(int chatMessageIndex) {

    }

    @Override
    public void addImage(int index, byte[] imageBytes, Path imageFile, String tooltip) {
        try {
            Path path = IMAGE_INDEX.get(index);
            if (path == null)
                return;
            Files.write(path, imageBytes);
            System.out.println("Image File ->" + path.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void fileNewImageRendering(String s) {

    }
}

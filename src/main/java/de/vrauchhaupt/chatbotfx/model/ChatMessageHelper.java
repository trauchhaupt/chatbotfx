package de.vrauchhaupt.chatbotfx.model;

import io.github.ollama4j.models.chat.OllamaChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageHelper {
    public static OllamaChatMessage createReplacedChatMessage(OllamaChatMessage original,
                                                              LlmModelCardJson curModel) {
        return new OllamaChatMessage(original.getRole(), createReplacedString(original.getContent(), curModel));
    }

    public static String createReplacedString(String original, LlmModelCardJson curModel) {
        if (original == null)
            return "";
        return original.replace("${NAME}", curModel.getModelCardName())
                .replace("${name}", curModel.getModelCardName());
    }

    public static List<OllamaChatMessage> limitToMaxAmout(
            SceneJson sceneJson,
            LlmModelCardJson curModel,
            List<OllamaChatMessage> historyOfMessages,
            int maxAmountOfMessages) {
        ArrayList<OllamaChatMessage> returnValue = new ArrayList<>(historyOfMessages);

        List<OllamaChatMessage> scenery = sceneJson.getSettingsMessages(curModel);
        returnValue.removeAll(scenery);

        while (returnValue.size() > maxAmountOfMessages) {
            returnValue.remove(0);
        }
        returnValue.addAll(0, scenery);
        return returnValue;
    }
}

package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.IMessaging;
import de.vrauchhaupt.chatbotfx.model.TtsSentence;
import io.github.ollama4j.models.chat.OllamaChatResponseModel;
import io.github.ollama4j.models.chat.OllamaChatTokenHandler;
import org.jsoup.Jsoup;

import java.util.List;

public class ChatbotLlmStreamHandler implements IMessaging, OllamaChatTokenHandler {

    private static final List<String> sentenceEndings = List.of(
            ".", ";", "!", "?",
            "!\"", ".\"", "?\"",
            "<br>", "*");
    private String lastSentences = "";
    private int chatMessageIndex;

    public ChatbotLlmStreamHandler() {
    }

    public ChatbotLlmStreamHandler setChatMessageIndex(int chatMessageIndex) {
        this.chatMessageIndex = chatMessageIndex;
        return this;
    }

    private void appendAnswer(String curSentence) {
        if (curSentence == null || curSentence.trim().equals(""))
            return;

        logLn("Original sentence '" + curSentence + "'");
        curSentence = cleanWith(curSentence).trim();
        TtsSentence ttsSentence = new TtsSentence(curSentence, chatMessageIndex);
        PrintingManager.instance().addToPrintingQueue(ttsSentence);
        PiperManager.instance().fileSentence(ttsSentence);
    }

    private String cleanWith(String aString) {
        String returnValue = Jsoup.parse(aString).text();
        returnValue = returnValue.replaceAll("\r?\n|\r", "");
        returnValue = returnValue.replaceAll("<[^>]*>", " ");
        returnValue = returnValue.replaceAll("<[^>]*}", " ");
        returnValue = returnValue.replaceAll("[^\\x00-\\x7F]", "");
        returnValue = returnValue.replaceAll("[<>|]", "");
        returnValue = returnValue.replace("E &#xDBC;&##R; = .'", "");
        return returnValue;
    }


    public void inputHasStopped() {
        lastSentences = "";
    }

    @Override
    public void accept(OllamaChatResponseModel responseModel) {
        String message = responseModel.getMessage().getResponse();
        String[] splittedMessages = message.split("\\n");
        for (String splittedMessage : splittedMessages) {
            if (splittedMessage.isEmpty() || splittedMessage.isBlank())
                continue;
            logLn("Original sentence '" + splittedMessage + "'");
            splittedMessage = cleanWith(splittedMessage).trim();
            TtsSentence ttsSentence = new TtsSentence(splittedMessage, chatMessageIndex);
            PrintingManager.instance().addToPrintingQueue(ttsSentence);
            PiperManager.instance().fileSentence(ttsSentence);
        }
    }
}

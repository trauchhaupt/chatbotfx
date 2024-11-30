package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.IMessaging;
import de.vrauchhaupt.chatbotfx.model.TtsSentence;
import io.github.ollama4j.models.generate.OllamaStreamHandler;
import org.jsoup.Jsoup;

import java.util.List;

public class ChatbotLlmStreamHandler implements OllamaStreamHandler, IMessaging {

    private static final List<String> sentenceEndings = List.of(
            ".", ";", "!", "?",
            "!\"", ".\"", "?\"",
            "<br>", "*");
    private String lastSentences = "";
    private int chatMessageIndex;


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

    @Override
    public void accept(String message) {
        String trimmedMessage = message.trim();
        String matchedEnding = sentenceEndings.stream()
                .filter(trimmedMessage::endsWith)
                .findFirst()
                .orElse(null);
        if (matchedEnding != null &
                !trimmedMessage.endsWith("...") &&
                !"*".equals(trimmedMessage) &&
                message.length() - lastSentences.length() > 1) {
            String curSentence = message.substring(lastSentences.length());
            appendAnswer(curSentence);
            lastSentences = message;
        }
    }

    public void inputHasStopped() {
        lastSentences = "";
    }
}

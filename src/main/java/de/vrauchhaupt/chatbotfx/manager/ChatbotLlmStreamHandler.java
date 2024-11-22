package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.TtsSentence;
import io.github.ollama4j.models.generate.OllamaStreamHandler;
import org.jsoup.Jsoup;

import java.util.List;

public class ChatbotLlmStreamHandler implements OllamaStreamHandler {

    private static final List<String> sentenceEndings = List.of(
            ".", ";", "!", "?",
            "!\"", ".\"", "?\"",
            "<br>", "*");
    private String lastMessage = "";
    private String lastSentences = "";

    private static void appendAnswer(String curSentence) {
        if (curSentence == null || curSentence.trim().equals(""))
            return;
        System.out.println("Original sentence '" + curSentence + "'");
        curSentence = cleanWith(curSentence, true).trim();
        TtsSentence ttsSentence = new TtsSentence(curSentence);
        PrintingManager.instance().addToPrintingQueue(ttsSentence);
        PiperManager.instance().fileSentence(ttsSentence);
    }

    private static String cleanWith(String aString, boolean wholeSentence) {
        String returnValue = Jsoup.parse(aString).text();
        returnValue = aString.replaceAll("\r?\n|\r", "");
        returnValue = returnValue.replaceAll("<[^>]*>", " ");
        returnValue = returnValue.replaceAll("<[^>]*}", " ");
        returnValue = returnValue.replaceAll("[^\\x00-\\x7F]", "");
        returnValue = returnValue.replaceAll("[<>|]", "");
        returnValue = returnValue.replace("E &#xDBC;&##R; = .'", "");
        /*if (wholeSentence)
            System.out.println("Cleaning  \n'" + aString + "'\nto\n'" + returnValue + "'");*/
        return returnValue;
    }

    @Override
    public void accept(String message) {
        //System.out.println("New Incoming Message '" + message + "'");
        String trimmedMessage = message.trim();
        String matchedEnding = sentenceEndings.stream()
                .filter(trimmedMessage::endsWith)
                .findFirst()
                .orElse(null);
        if (matchedEnding != null &
                !trimmedMessage.endsWith("...") &&
                !"*".equals(trimmedMessage) &&
                message.length() - lastSentences.length() > 1) {
            //System.out.println("New line because of sentence ending '" + matchedEnding + "'");
            String curSentence = message.substring(lastSentences.length());
            appendAnswer(curSentence);
            lastSentences = message;
        }
        lastMessage = message;
    }

    public void inputHasStopped() {
        lastMessage = "";
        lastSentences = "";
    }
}

package de.vrauchhaupt.chatbotfx;

import de.vrauchhaupt.chatbotfx.manager.IPrintFunction;
import de.vrauchhaupt.chatbotfx.manager.SettingsManager;
import de.vrauchhaupt.chatbotfx.model.ChatMessageHelper;
import de.vrauchhaupt.chatbotfx.model.DisplayRole;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import de.vrauchhaupt.chatbotfx.model.SceneJson;
import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.util.*;

public abstract class AbstractProgram implements IPrintFunction {
    protected static final Options options = new OptionsBuilder()
            .setTemperature(1.3f)
            .setTopP(0.6f)
            .setTopK(50)
            .setRepeatPenalty(1.3f)
            .setRepeatLastN(128)
            .build();

    protected static final long OLLAMA_TIMEOUT = 3600L;
    protected static int imageIndex = 0;
    protected static Ollama ollamaAPI = new Ollama(SettingsManager.instance().getOllamaHost());
    private static Class<? extends AbstractProgram> programClazz = null;
    private static PrintWriter logWriter = null;
    private static DateFormat dateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
    protected Map<Integer, Path> IMAGE_INDEX = new HashMap<>();

    private static void stdoutBlock(String myString) {
        char[] chars = myString.toCharArray();
        ArrayList<String> list = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (char character : chars) {
            if (count < 120 - 1) {
                builder.append(character);
                count++;
            } else {
                if (character == ' ') {
                    builder.append(character);
                    System.out.println(builder);
                    count = 0;
                    builder.setLength(0);
                } else {
                    builder.append(character);
                    count++;
                }
            }
        }
        System.out.println(builder);
    }


    public static void log(String logLine) {
        logWriter.write(logLine);
        logWriter.write("\n");
        logWriter.flush();
        stdoutBlock(logLine);
    }

    public static Date logStart() {
        Date date = new Date();
        log(dateFormat.format(date));
        return date;
    }

    public static long logEnd(Date dateStart) {
        Date dateEnd = new Date();
        long duration = dateEnd.getTime() - dateStart.getTime();
        duration = duration / 1000;
        log(dateFormat.format(dateEnd) + " - Duration = " + duration + "sec.");
        return duration;
    }

    public static void log(Exception e) {
        e.printStackTrace(logWriter);
        logWriter.flush();
        e.printStackTrace();
    }

    public static String createTxt2ImagePrompt(SceneJson sceneJson,
                                               LlmModelCardJson curModel,
                                               List<OllamaChatMessage> chatHistory) throws Exception {
        List<OllamaChatMessage> newHistory = ChatMessageHelper.limitToMaxAmout(sceneJson, curModel, chatHistory, 5);
        newHistory.add(new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, "Create an AI prompt of not more than 150 words. It shall be used to create a picture for the previous storyline. Do not retell or develop the story further. Simply describe the people and the scene"));

        OllamaChatRequest chatRequest = new OllamaChatRequest(curModel.getLlmModel(), false, newHistory)
                .withOptions(options)
                .withMessages(newHistory);
        OllamaChatResult chat = ollamaAPI.chat(chatRequest, null);
        String response = chat.getResponseModel().getMessage().getResponse();
        log(response);
        return response;
    }


    protected static void initAndRun(Class<? extends AbstractProgram> programClazz) {
        SettingsManager.instance().loadFromConfigFile();
        AbstractProgram.programClazz = programClazz;
        ollamaAPI.setRequestTimeoutSeconds(OLLAMA_TIMEOUT);
        File loggingFile = new File(programClazz.getSimpleName() + ".log");
        try (FileOutputStream logOutputStream = new FileOutputStream(loggingFile);
             PrintWriter tmpPrintWriter = new PrintWriter(logOutputStream)) {
            try {
                AbstractProgram.logWriter = tmpPrintWriter;
                AbstractProgram abstractProgram = programClazz.getConstructor(new Class[0]).newInstance();
                abstractProgram.run();
            } catch (Exception e) {
                log(e);
            }

        } catch (Exception loggingException) {
            loggingException.printStackTrace();
        }
    }

    protected abstract void run() throws Exception;

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
            Files.move(imageFile, path, StandardCopyOption.REPLACE_EXISTING);
            log("Image File ->" + path);
        } catch (IOException e) {
            log(e);
        }

    }

    @Override
    public void fileNewImageRendering(String s) {

    }
}

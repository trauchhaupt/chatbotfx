package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.ControlledThread;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import de.vrauchhaupt.chatbotfx.model.TtsSentence;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PiperManager extends AbstractManager {
    private static PiperManager INSTANCE = null;
    private final AtomicBoolean isPlayingSound = new AtomicBoolean();
    private final Queue<TtsSentence> queueOfTextsToProduce = new LinkedList<>();
    private final Queue<TtsSentence> queueOfTextsToPlay = new LinkedList<>();
    private final ObservableList<String> ttsModels = FXCollections.observableArrayList();
    private final List<Thread> currentThreads = new LinkedList<>();

    public PiperManager() {
        ThreadManager.instance().startEndlessThread("TTS Generation", this::generateTTS);
        ThreadManager.instance().startEndlessThread("TTS Playing", this::playSounds);
        SettingsManager.instance().pathToTtsModelFilesProperty().addListener((obs, oldv, newv) -> reloadTtsModels());
        reloadTtsModels();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static PiperManager instance() {
        if (INSTANCE == null)
            INSTANCE = new PiperManager();
        return INSTANCE;
    }

    public void reloadTtsModels() {
        if (!Files.isDirectory(SettingsManager.instance().getPathToTtsModelFiles())) {
            System.err.println("Path to TTS model files is invalid '" + SettingsManager.instance().getPathToTtsModelFiles() + "'");
            ttsModels.clear();
            return;
        }
        try {
            List<Path> modelConfigs = Files.list(SettingsManager.instance().getPathToTtsModelFiles())
                    .filter(x -> x.toString().endsWith(".onnx.json"))
                    .toList();
            List<Path> modelFiles = Files.list(SettingsManager.instance().getPathToTtsModelFiles())
                    .filter(x -> x.toString().endsWith(".onnx"))
                    .toList();
            List<String> foundModels = new LinkedList<>();
            for (Path modelFile : modelFiles) {
                String modelFileName = modelFile.getFileName().toString();
                String modelName = modelFileName.substring(0, modelFileName.length() - 5);
                if (modelConfigs.stream().noneMatch(x -> (modelName + ".onnx.json").equals(x.getFileName().toString()))) {
                    System.err.println("Could not find TTS Model config '" + modelName + ".onnx.json'.");
                    continue;
                }
                System.out.println("Found TTS model '" + modelName + "'");
                foundModels.add(modelName);
            }
            ttsModels.setAll(foundModels);
        } catch (IOException e) {
            throw new RuntimeException("Could not list tts model files at '" + SettingsManager.instance().getPathToTtsModelFiles() + "'", e);
        }
    }

    private void playSounds(ControlledThread thread) {
        while (!queueOfTextsToPlay.isEmpty()) {
            isPlayingSound.set(true);
            TtsSentence sentenceToPlay = queueOfTextsToPlay.poll();
            try (InputStream rawSoundIs = sentenceToPlay.getBytesAsInputStream()) {
                AudioFormat audioFormat = new AudioFormat(22000, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(audioFormat);
                line.open(audioFormat);
                line.start();

                byte[] buffer = new byte[1024];
                int numBytesRead;

                while ((numBytesRead = rawSoundIs.read(buffer)) != -1) {
                    line.write(buffer, 0, numBytesRead);
                }
                line.drain();
                line.close();
                sentenceToPlay.informSpoken();
            } catch (Exception e) {
                throw new RuntimeException("Could not play sound for sentence '" + sentenceToPlay.getText() + "'", e);
            } finally {
                isPlayingSound.set(false);
            }
        }
    }

    public boolean checkPiperIsAvailable() {
        return getPiperExe() != null;
    }

    public boolean checkTtsModelFilesExists() {
        return !ttsModels.isEmpty();
    }

    private Path getPiperExe() {
        Path pathToPiper = SettingsManager.instance().getPathToPiper();
        if (pathToPiper == null || !Files.isDirectory(pathToPiper))
            return null;
        Path piperExe = pathToPiper.resolve("piper.exe");
        if (Files.isRegularFile(piperExe))
            return piperExe;
        return null;
    }

    public void cancelWork() {
        queueOfTextsToProduce.clear();
        queueOfTextsToPlay.clear();
        for (Thread currentThread : currentThreads) {
            if (!currentThread.isAlive())
                continue;
            try {
                currentThread.interrupt();
            } catch (Exception e) {
                System.err.println("Could not interrupt thread in pipermananger '" + currentThread.getName() + "'");
                e.printStackTrace();
            }
        }
    }

    private void generateTTS(ControlledThread thread) {
        Path piperExe = getPiperExe();
        if (piperExe == null)
            return;
        // no models there
        if (ttsModels.isEmpty())
            return;

        LlmModelCardJson selectedLlModelCard = LlmModelCardManager.instance().getSelectedLlModelCard();
        final String ttsModel;
        if (selectedLlModelCard == null || !ttsModels.contains(selectedLlModelCard.getTtsModel())) {
            ttsModel = ttsModels.get(0);
        } else
            ttsModel = selectedLlModelCard.getTtsModel();

        List<String> command = List.of(piperExe.toAbsolutePath().toString(),
                "--model", SettingsManager.instance().getPathToTtsModelFiles() + "\\" + ttsModel + ".onnx",
                "--config", SettingsManager.instance().getPathToTtsModelFiles() + "\\" + ttsModel + ".onnx.json",
                "--output_raw"
        );
        while (!queueOfTextsToProduce.isEmpty()) {
            TtsSentence ttsSentence = queueOfTextsToProduce.remove();
            queueOfTextsToPlay.add(ttsSentence);
            currentThreads.add(ThreadManager.instance().startThread("Piper Execution",
                    () -> startPiperCommand(command, ttsSentence),
                    x -> currentThreads.remove(x)));
        }
    }

    private void startPiperCommand(List<String> command, TtsSentence ttsSentence) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(SettingsManager.instance().getPathToPiper().toAbsolutePath().toFile());
            Process piperProcess = processBuilder.start();

            Thread voiceCollectorThread = ThreadManager.instance().startThread("Piper voice collector",
                    () -> collectVoices(piperProcess, ttsSentence),
                    x -> currentThreads.remove(x));
            currentThreads.add(voiceCollectorThread);

            try (OutputStream outputStream = piperProcess.getOutputStream()) {
                String withoutAsterix = ttsSentence.getText().replace('*', ' ');
                String cleanStutter = withoutAsterix.replaceAll("\\b(\\w)-\\1?(\\w+)", "$1$2");
                if (!cleanStutter.equals(ttsSentence.getText()))
                    System.out.println("Removed stuttering from \n'" + ttsSentence.getText() + "'\nto\n'" + cleanStutter + "'");
                outputStream.write(cleanStutter.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            // wait, until the sound file has been processed
            while (piperProcess.isAlive())
                Thread.sleep(100);
            voiceCollectorThread.interrupt();
            piperProcess.destroy();
            ttsSentence.setAllBytesCaught(true);
        } catch (Exception e) {
            throw new RuntimeException("Could not generate TTS sounds with piper", e);
        }
    }

    private void collectVoices(Process piperProcess, TtsSentence ttsSentence) {
        try (InputStream rawSoundOutput = piperProcess.getInputStream()) {
            byte[] buffer = new byte[1024];
            int numBytesRead;
            while ((numBytesRead = rawSoundOutput.read(buffer)) != -1) {
                ttsSentence.addToByteBuffer(buffer, 0, numBytesRead);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not collect sounds from piper for TTS", e);
        }
    }

    public void fileSentence(TtsSentence ttsSentence) {
        if (!SettingsManager.instance().isTtsGeneration())
            return;
        queueOfTextsToProduce.add(ttsSentence);
        logLn("Adding to TTSQ '" + ttsSentence.getText() + "'");
    }

    @Override
    public boolean isWorking() {
        return !queueOfTextsToPlay.isEmpty() ||
                !queueOfTextsToProduce.isEmpty() ||
                !currentThreads.isEmpty() ||
                isPlayingSound.get();

    }
}


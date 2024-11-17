package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.ControlledThread;
import de.vrauchhaupt.chatbotfx.model.TtsSentence;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PiperManager extends AbstractManager {
    private static final File PIPER_DIRECTORY = new File("F:/projects/piper/");
    private static PiperManager INSTANCE = null;
    private final AtomicBoolean isPlayingSound = new AtomicBoolean();
    private Queue<TtsSentence> queueOfTextsToProduce = new LinkedList<>();
    private Queue<TtsSentence> queueOfTextsToPlay = new LinkedList<>();


    public PiperManager() {
        ThreadManager.instance().startEndlessThread("TTS Generation", this::generateTTS);
        ThreadManager.instance().startEndlessThread("TTS Playing", this::playSounds);

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

    private void generateTTS(ControlledThread thread) {
        List<String> command = List.of(PIPER_DIRECTORY.getAbsolutePath() + "/piper.exe",
                "--model", "en_GB-cori-high.onnx",
                "--config", "en_GB-cori-high.onnx.json",
                "--output_raw"
        );
        while (!queueOfTextsToProduce.isEmpty()) {
            TtsSentence ttsSentence = queueOfTextsToProduce.remove();
            queueOfTextsToPlay.add(ttsSentence);
            ThreadManager.instance().startThread("Piper Execution", () -> startPiperCommand(command, ttsSentence));
        }
    }

    private void startPiperCommand(List<String> command, TtsSentence ttsSentence) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(PIPER_DIRECTORY);
            Process piperProcess = processBuilder.start();

            Thread voiceCollectorThread = ThreadManager.instance().startThread("Piper voice collector", () -> collectVoices(piperProcess, ttsSentence));

            try (OutputStream outputStream = piperProcess.getOutputStream()) {
                String cleanStutter = ttsSentence.getText().replaceAll("\\b(\\w)-\\1?(\\w+)", "$1$2");
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
        queueOfTextsToProduce.add(ttsSentence);
        logLn("Adding to TTSQ '" + ttsSentence.getText() + "'");
    }

    @Override
    public boolean isWorking() {
        return !queueOfTextsToPlay.isEmpty() ||
                !queueOfTextsToProduce.isEmpty() ||
                isPlayingSound.get();

    }
}


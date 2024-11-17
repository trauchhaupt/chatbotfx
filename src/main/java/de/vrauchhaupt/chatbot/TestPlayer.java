package de.vrauchhaupt.chatbot;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class TestPlayer {
    public static void main (String... args)
    {

        File soundFile = new File("out.raw");
        AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);
        try (InputStream fis = new FileInputStream(soundFile);
        AudioInputStream ais = new AudioInputStream(fis,audioFormat,soundFile.length() )){


            SourceDataLine line = AudioSystem.getSourceDataLine(audioFormat);
            line.open(audioFormat);
            line.start();

            byte[] buffer = new byte[4096];
            int numBytesRead;
            while ((numBytesRead = ais.read(buffer)) != -1) {
                line.write(buffer, 0, numBytesRead);
            }

            line.drain();
            line.close();
            ais.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

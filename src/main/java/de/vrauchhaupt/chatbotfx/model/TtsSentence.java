package de.vrauchhaupt.chatbotfx.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TtsSentence {
    private final String text;
    private final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
    private final AtomicBoolean allBytesCaught = new AtomicBoolean(false);
    private final AtomicBoolean spoken = new AtomicBoolean(false);
    private final int chatMessageIndex;

    public TtsSentence(String text, int chatMessageIndex) {
        this.text = text;
        this.chatMessageIndex = chatMessageIndex;
    }

    public String getText() {
        return text;
    }

    public void addToByteBuffer(byte[] b, int off, int len) {
        byteBuffer.write(b, off, len);
    }

    public void setAllBytesCaught(boolean allBytesCaught) {
        this.allBytesCaught.set(allBytesCaught);
    }

    public InputStream getBytesAsInputStream() {
        try {
            while ( !this.allBytesCaught.get())
                Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        byte[] byteArray = byteBuffer.toByteArray();
        return new ByteArrayInputStream(byteArray);
    }

    public void informSpoken() {
        spoken.set(true);
    }

    public boolean wasSpoken() {
        return spoken.get();
    }

    public int getChatMessageIndex() {
        return chatMessageIndex;
    }

    public List<String> getWords() {
        return Arrays.stream(text.split("\\s+")).toList();
    }
}

package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.DisplayRole;

import java.nio.file.Path;

public interface IPrintFunction {
    void renderOnFxThread(DisplayRole displayRole, String textFragment, int chatMessageIndex);

    void renderNewLine(int chatMessageIndex);

    void addImage(int index, byte[] imageBytes, Path imageFile, String tooltip);

    void fileNewImageRendering(String s);
}

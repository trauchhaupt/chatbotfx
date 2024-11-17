package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.DisplayRole;

import java.nio.file.Path;

public interface IPrintFunction {
    void render(DisplayRole displayRole, String textFragment);

    void renderNewLine();

    void addImage(int index, byte[] imageBytes, Path imageFile);

    void fileNewImageRendering(String s);
}

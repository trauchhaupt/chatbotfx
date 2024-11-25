package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.manager.IPrintFunction;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

public class ChatContainer extends VBox {
    private CopyableTextFlow currentLineInChat = null;

    public ChatContainer() {
        super();
        setFillWidth(true);
        setSpacing(3);
        setPadding(new Insets(15, 5, 5, 5));
    }

    public void clearChat() {
        getChildren().clear();
        currentLineInChat = null;
    }

    public CopyableTextFlow newLineInFx(IPrintFunction printFunction, int chatMessageIndex) {
        currentLineInChat = new CopyableTextFlow(printFunction, chatMessageIndex);
        currentLineInChat.setPrefWidth(this.getWidth() - 10.0);
        currentLineInChat.setMinWidth(this.getWidth() - 10.0);
        getChildren().add(currentLineInChat);
        return currentLineInChat;
    }

    public CopyableTextFlow currentLine(IPrintFunction printFunction, int chatMessageIndex) {
        if (currentLineInChat == null)
            return newLineInFx(printFunction,chatMessageIndex);
        return currentLineInChat;
    }

    public void appendToLastText(String space) {
        if (currentLineInChat == null)
            return;
        currentLineInChat.appendToLastText(space);

    }
}

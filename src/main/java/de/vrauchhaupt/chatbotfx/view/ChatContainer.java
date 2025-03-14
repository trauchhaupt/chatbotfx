package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.manager.IPrintFunction;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

public class ChatContainer extends VBox implements IChatBoxViewComponent {
    private CopyableTextFlow currentLineInChat = null;

    public ChatContainer() {
        super();
        setFillWidth(true);
        setSpacing(3);
        setPadding(new Insets(15, 5, 5, 5));
    }

    public void clearChat() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::clearChat);
            return;
        }
        getChildren().clear();
        currentLineInChat = null;
    }

    public CopyableTextFlow newLineInFx(IPrintFunction printFunction, int chatMessageIndex) {
        assertFxThread();
        if (!getChildren().isEmpty()) {
            int previousMessageIndex = ((CopyableTextFlow) getChildren().getFirst()).getChatMessageIndex();
            if (previousMessageIndex > chatMessageIndex)
                throw new RuntimeException("The previous message index " + previousMessageIndex + " is bigger than the actual one");
        }
        currentLineInChat = new CopyableTextFlow(printFunction, chatMessageIndex);
        currentLineInChat.setPrefWidth(this.getWidth() - 10.0);
        currentLineInChat.setMinWidth(this.getWidth() - 10.0);
        getChildren().add(currentLineInChat);
        currentLineInChat.requestLayout();
        return currentLineInChat;
    }

    public CopyableTextFlow currentLine(IPrintFunction printFunction, int chatMessageIndex) {
        assertFxThread();
        if (currentLineInChat == null)
            return newLineInFx(printFunction, chatMessageIndex);
        return currentLineInChat;
    }

    public void appendToLastText(String space) {
        assertFxThread();
        if (currentLineInChat == null)
            return;
        currentLineInChat.appendToLastText(space);
    }
}

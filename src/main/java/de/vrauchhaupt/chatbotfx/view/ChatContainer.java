package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.helper.FxHelper;
import de.vrauchhaupt.chatbotfx.manager.IPrintFunction;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

public class ChatContainer extends VBox implements IChatBoxViewComponent {
    private CopyableTextFlow currentLineInChat = null;
    private ScrollPane scrollPaneChat = null;

    public ChatContainer() {
        super();
        setPadding(new Insets(15, 10, 10, 10));
        this.widthProperty().addListener((obs, oldV, newV) -> widthHasChanged());
    }

    private void widthHasChanged() {
        getChildren().stream()
                .filter(x -> x instanceof CopyableTextFlow)
                .map(x -> (CopyableTextFlow) x)
                .forEach(this::setCurrentLineInChatWidth);
        layoutChildren();
        requestLayout();
    }

    private void setCurrentLineInChatWidth(CopyableTextFlow copyableTextFlow) {
        if (getWidth() <= 0.0)
            return;
        double lineWidth = scrollPaneChat.getWidth() - 35.0;
        FxHelper.setAllWidths(copyableTextFlow, lineWidth);
        copyableTextFlow.requestLayout();
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

        getChildren().add(currentLineInChat);
        setCurrentLineInChatWidth(currentLineInChat);
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

    public void setScrollPaneParent(ScrollPane scrollPaneChat) {
        this.scrollPaneChat = scrollPaneChat;
    }
}

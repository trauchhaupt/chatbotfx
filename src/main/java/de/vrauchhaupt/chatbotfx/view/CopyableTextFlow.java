package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.manager.IPrintFunction;
import de.vrauchhaupt.chatbotfx.model.ChatViewModel;
import de.vrauchhaupt.chatbotfx.model.DisplayRole;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CopyableTextFlow extends TextFlow {
    private final IPrintFunction printFunction;
    private final int chatMessageIndex;
    private final MenuItem menuItemCopy = new MenuItem("Copy");
    private final MenuItem menuItemNewImage = new MenuItem("New Image");
    private final MenuItem menuItemRemoveAllAbove = new MenuItem("Remove Messages above");
    private final MenuItem menuItemRemove = new MenuItem("Remove");
    private final MenuItem menuItemMergeWithAbove = new MenuItem("Merge with above");
    private final MenuItem menuItemMergeWithBelow = new MenuItem("Merge with below");
    private final ContextMenu contextMenu = new ContextMenu(menuItemCopy,
            menuItemNewImage,
            menuItemRemoveAllAbove,
            menuItemRemove,
            menuItemMergeWithAbove,
            menuItemMergeWithBelow);

    public CopyableTextFlow(IPrintFunction printFunction, int chatMessageIndex) {
        super();
        this.chatMessageIndex = chatMessageIndex;
        this.printFunction = printFunction;

        menuItemCopy.setOnAction(this::menuItemCopyClicked);
        menuItemNewImage.setOnAction(this::menuItemNewImageClicked);
        menuItemRemoveAllAbove.setOnAction(this::menuItemRemoveAllAboveClicked);
        menuItemRemove.setOnAction(this::menuItemRemoveClicked);
        menuItemMergeWithAbove.setOnAction(this::menuItemMergeWithAboveClicked);
        menuItemMergeWithBelow.setOnAction(this::menuItemMergeWithBelowClicked);
        setOnContextMenuRequested(this::onContextMenuRequested);
    }

    private void menuItemMergeWithBelowClicked(ActionEvent actionEvent) {

        CopyableTextFlow nextTextFlow = getNextTextFlow();
        if (nextTextFlow == null || nextTextFlow.containsRoleText() || nextTextFlow.getChildren().isEmpty())
            return;
        parentContainer().getChildren().remove(nextTextFlow);
        Text first = (Text) nextTextFlow.getChildren().getFirst();
        first.setText(" " + first.getText());
        getChildren().addAll(nextTextFlow.getChildren());
    }

    private void saveCurrentMessageInModel(VBox parentContainer) {
        String newMessage = parentContainer.getChildren()
                .stream()
                .map(x -> (CopyableTextFlow) x)
                .filter(x -> x.chatMessageIndex == chatMessageIndex)
                .map(x -> x.valuesToString().trim())
                .collect(Collectors.joining(" "));
        ChatViewModel.instance().setMessageOfId(chatMessageIndex, newMessage);
    }

    private void menuItemMergeWithAboveClicked(ActionEvent actionEvent) {
        if (containsRoleText() || getChildren().isEmpty())
            return;
        CopyableTextFlow previousTextFlow = getPreviousTextFlow();
        if (previousTextFlow == null)
            return;
        parentContainer().getChildren().remove(this);
        Text first = (Text) this.getChildren().getFirst();
        first.setText(" " + first.getText());
        previousTextFlow.getChildren().addAll(this.getChildren());
    }

    public VBox parentContainer() {
        return (VBox) getParent();
    }

    public CopyableTextFlow getNextTextFlow() {
        int index = parentContainer().getChildren().indexOf(this);
        if (index < parentContainer().getChildren().size() - 1)
            return (CopyableTextFlow) parentContainer().getChildren().get(index + 1);
        return null;
    }

    public CopyableTextFlow getPreviousTextFlow() {
        int index = parentContainer().getChildren().indexOf(this);
        if (index > 0)
            return (CopyableTextFlow) parentContainer().getChildren().get(index - 1);
        return null;
    }

    public boolean containsRoleText() {
        return !getChildren().isEmpty() && getChildren().get(0) instanceof RoleText;
    }

    public DisplayRole getRole() {
        if (getChildren().isEmpty())
            return null;
        Node node = getChildren().get(0);
        if (node instanceof RoleText roleText)
            return roleText.getRole();
        return null;
    }

    private void menuItemRemoveAllAboveClicked(ActionEvent actionEvent) {
        VBox parentContainer = parentContainer();
        int myIndex = parentContainer.getChildren().indexOf(this);
        if (myIndex < 2)
            return;
        List<CopyableTextFlow> messagesAbove = parentContainer.getChildren().stream()
                .filter(x -> x instanceof CopyableTextFlow)
                .map(x -> (CopyableTextFlow) x)
                .filter(x -> x.getChatMessageIndex() < getChatMessageIndex())
                .collect(Collectors.toList());
        CopyableTextFlow firstMessage = (CopyableTextFlow) parentContainer.getChildren().get(0);
        DisplayRole roleFirstMessage = firstMessage.getRole();
        if (roleFirstMessage == DisplayRole.SYSTEM) {
            messagesAbove.remove(firstMessage);
        }
        Set<Integer> messageIndexesToDelete = messagesAbove.stream()
                .map(CopyableTextFlow::getChatMessageIndex)
                .collect(Collectors.toSet());
        ChatViewModel.instance().removeAllMessages(messageIndexesToDelete);
        parentContainer.getChildren().removeAll(messagesAbove);
    }

    private void menuItemRemoveClicked(ActionEvent actionEvent) {
        VBox parentContainer = parentContainer();
        if (getChildren().isEmpty()) {
            parentContainer.getChildren().remove(this);
            return;
        }
        if (!containsRoleText()) {
            parentContainer.getChildren().remove(this);
        } else {
            CopyableTextFlow nextTextFlow = getNextTextFlow();
            if (nextTextFlow == null || nextTextFlow.containsRoleText())
                parentContainer.getChildren().remove(this);
            else {
                parentContainer.getChildren().remove(nextTextFlow);
                getChildren().removeAll(getPureTextNodes());
                getChildren().addAll(nextTextFlow.getPureTextNodes());
            }
        }
        saveCurrentMessageInModel(parentContainer);
    }

    private void menuItemNewImageClicked(ActionEvent actionEvent) {
        printFunction.fileNewImageRendering(valuesToString());
    }

    private void menuItemCopyClicked(ActionEvent actionEvent) {
        triggerBlinkEffect();
        copyTextFlowToClipboard();
    }

    private void onContextMenuRequested(ContextMenuEvent contextMenuEvent) {
        VBox parentContainer = parentContainer();
        int myIndex = parentContainer.getChildren().indexOf(this);
        menuItemRemoveAllAbove.setDisable(myIndex < 2);
        menuItemMergeWithAbove.setDisable(containsRoleText() || getPreviousTextFlow() == null);
        CopyableTextFlow nextTextFlow = getNextTextFlow();
        menuItemMergeWithBelow.setDisable(nextTextFlow == null || nextTextFlow.containsRoleText());
        contextMenu.show(this, contextMenuEvent.getScreenX(), contextMenuEvent.getScreenY());
    }

    protected void addText(Text aText) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> addText(aText));
            return;
        }
        getChildren().add(aText);
        requestLayout();
    }

    protected List<Text> getTextNodes() {
        if (!Platform.isFxApplicationThread()) {
            System.err.println("NOT APPLICATION THREAD 1");
        }
        return getChildren().stream()
                .filter(x -> x instanceof Text)
                .map(x -> (Text) x)
                .toList();
    }

    protected List<Text> getPureTextNodes() {
        if (!Platform.isFxApplicationThread()) {
            System.err.println("NOT APPLICATION THREAD 2");
        }
        return getChildren().stream()
                .filter(x -> (x instanceof Text) && !(x instanceof RoleText))
                .map(x -> (Text) x)
                .toList();
    }

    private void triggerBlinkEffect() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> triggerBlinkEffect());
            return;
        }
        // Create a timeline for the blink effect
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(100), new KeyValue(opacityProperty(), 0.2)),
                new KeyFrame(Duration.millis(200), new KeyValue(opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(300), new KeyValue(opacityProperty(), 0.2)),
                new KeyFrame(Duration.millis(400), new KeyValue(opacityProperty(), 1.0))
        );
        timeline.setCycleCount(1); // Run the blink effect once
        timeline.play();
    }

    public String valuesToString() {
        if (!Platform.isFxApplicationThread()) {
            System.err.println("NOT APPLICATION THREAD 3");
        }
        StringBuilder textContent = new StringBuilder();
        extractText(textContent);
        return textContent.toString();
    }

    private void copyTextFlowToClipboard() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> copyTextFlowToClipboard());
            return;
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(valuesToString());
        clipboard.setContent(content);
    }

    protected void extractText(StringBuilder textContent) {
        if (!Platform.isFxApplicationThread()) {
            System.err.println("NOT APPLICATION THREAD 4");
        }
        for (Node node : getChildren()) {
            if (node instanceof RoleText) {
                continue;
            } else if (node instanceof Text textNode) {
                if (!textNode.getText().endsWith(" ") &&
                        !textNode.getText().startsWith(" "))
                    textContent.append(" ");
                textContent.append(textNode.getText());
            } else if (node instanceof TextFlow) {
                extractText(textContent); // Recursive call for nested TextFlow
            }
        }
    }

    public void appendToLastText(String newText) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendToLastText(newText));
            return;
        }
        List<Text> textNodes = getTextNodes();
        if (textNodes.isEmpty())
            return;
        Text lastText = textNodes.get(textNodes.size() - 1);
        lastText.setText(lastText.getText() + newText);
        requestLayout();
    }

    public int getChatMessageIndex() {
        return chatMessageIndex;
    }
}

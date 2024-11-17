package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.manager.IPrintFunction;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class CopyableTextFlow extends TextFlow {
    private final IPrintFunction printFunction;

    public CopyableTextFlow(IPrintFunction printFunction) {
        super();
        this.printFunction = printFunction;
        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) { // Double-click detected
                triggerBlinkEffect();
                copyTextFlowToClipboard(this);
            } else if (event.isShiftDown()) {
                printFunction.fileNewImageRendering(valuesToString());
            }
        });
    }

    private void triggerBlinkEffect() {
        // Create a timeline for the blink effect
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(this.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(100), new KeyValue(this.opacityProperty(), 0.2)),
                new KeyFrame(Duration.millis(200), new KeyValue(this.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(300), new KeyValue(this.opacityProperty(), 0.2)),
                new KeyFrame(Duration.millis(400), new KeyValue(this.opacityProperty(), 1.0))
        );
        timeline.setCycleCount(1); // Run the blink effect once
        timeline.play();
    }

    private String valuesToString() {
        StringBuilder textContent = new StringBuilder();
        extractText(this, textContent);
        return textContent.toString();
    }


    private void copyTextFlowToClipboard(TextFlow textFlow) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(valuesToString());
        clipboard.setContent(content);
    }


    public void extractText(TextFlow textFlow, StringBuilder textContent) {
        for (Node node : textFlow.getChildren()) {
            if (node instanceof Text textNode) {
                if (textNode.getStyleClass().contains("roleText"))
                    continue;
                textContent.append(((Text) node).getText());
            } else if (node instanceof TextFlow) {
                extractText((TextFlow) node, textContent); // Recursive call for nested TextFlow
            }
        }
    }
}

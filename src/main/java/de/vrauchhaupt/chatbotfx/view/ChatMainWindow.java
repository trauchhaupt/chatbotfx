package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.manager.*;
import de.vrauchhaupt.chatbotfx.model.ChatViewModel;
import de.vrauchhaupt.chatbotfx.model.DisplayRole;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChatMainWindow implements IPrintFunction {

    private static final float WAITING_CIRCLE_SIZE = 7;

    @FXML
    private Pane paneLoadingBackground;
    @FXML
    private Button buttonSend;
    @FXML
    private ChoiceBox<LlmModelCardJson> choiceBoxModel;
    @FXML
    private VBox containerChat;
    @FXML
    private StackPane paneLoadBlocker;
    @FXML
    private Group groupLoadingAnimation;

    @FXML
    private ScrollPane scrollPaneChat;
    @FXML
    private ScrollPane scrollPaneImages;
    @FXML
    private VBox containerImages;
    @FXML
    private TextField textFieldSystemInput;
    @FXML
    private TextField textFieldUserInput;
    @FXML
    private Button buttonReloadModels;
    @FXML
    private Button buttonSave;
    @FXML
    private Button buttonClear;
    @FXML
    private Button buttonLoad;

    private DisplayRole currentDisplayRole = null;
    private TextFlow currentLineInChat = null;
    private Transition loadingTransition = null;
    private final ChangeListener<LlmModelCardJson> modelCardSelectionChangeListener = (observable, oldValue, newValue) -> modelChanged(newValue);

    public ChatMainWindow() {

    }

    @FXML
    public void initialize() {
        setupLoadingBackground();
        buttonReloadModels.setOnAction(this::buttonReloadModelsClicked);
        buttonSend.setOnAction(this::buttonSendClicked);
        buttonClear.setOnAction(this::buttonClearClicked);
        buttonSave.setOnAction(this::buttonSaveClicked);
        buttonLoad.setOnAction(this::buttonLoadClicked);

        List<LlmModelCardJson> availableModelCards = new ArrayList<>();
        try {
            availableModelCards = new ArrayList<>(LlmModelCardManager.instance().getAvailableModelCards());
        } catch (Exception e) {
            System.err.println("Could not load available model cards, config seems to be invalid");
        }
        availableModelCards.sort(Comparator.naturalOrder());
        choiceBoxModel.getItems().addAll(availableModelCards);
        choiceBoxModel.setConverter(new StringConverter<>() {
            @Override
            public String toString(LlmModelCardJson object) {
                return object == null ? "" : (object.getModelCardName() + " (" + object.getLlmModel() + ")");
            }

            @Override
            public LlmModelCardJson fromString(String string) {
                if (string == null || string.trim().isEmpty())
                    return null;
                return choiceBoxModel.getItems().stream()
                        .filter(x -> x.getModelCardName().equals(string))
                        .findFirst()
                        .orElse(null);
            }
        });

        choiceBoxModel.valueProperty().addListener(modelCardSelectionChangeListener);
        if (LlmModelCardManager.instance().getSelectedLlModelCard() != null)
            choiceBoxModel.setValue(LlmModelCardManager.instance().getSelectedLlModelCard());
        PrintingManager.instance().setPrintFunction(this);

        Platform.runLater(() -> textFieldUserInput.requestFocus());
        containerChat.heightProperty().addListener((observable, oldValue, newValue) -> {
            scrollPaneChat.setVvalue(1.0); // Scroll to the bottom
        });

        containerImages.heightProperty().addListener((observable, oldValue, newValue) -> {
            scrollPaneImages.setVvalue(1.0); // Scroll to the bottom
        });
    }

    private void buttonReloadModelsClicked(ActionEvent actionEvent) {
        choiceBoxModel.valueProperty().removeListener(modelCardSelectionChangeListener);
        List<LlmModelCardJson> availableModelCards = new ArrayList<>(LlmModelCardManager.instance().reloadModelCards());
        choiceBoxModel.getItems().setAll(availableModelCards);
        choiceBoxModel.setValue(LlmModelCardManager.instance().getSelectedLlModelCard());
        choiceBoxModel.valueProperty().addListener(modelCardSelectionChangeListener);
    }

    private void buttonClearClicked(ActionEvent actionEvent) {
        ChatViewModel.instance().clearHistory();
        currentDisplayRole = null;
        containerChat.getChildren().clear();
        containerImages.getChildren().clear();
    }

    private void buttonLoadClicked(ActionEvent actionEvent) {
        buttonClearClicked(null);
        ChatViewModel.instance().loadMessagesFromFile(this);
        for (OllamaChatMessage ollamaChatMessage : ChatViewModel.instance().getFullHistory()) {
            render(DisplayRole.of(ollamaChatMessage.getRole()), ollamaChatMessage.getContent());
            renderNewLine();
        }
    }


    private void buttonSaveClicked(ActionEvent actionEvent) {
        ChatViewModel.instance().saveMessagesToFile();
    }

    private void setupLoadingBackground() {

        Arc backgroundCircle = new Arc(WAITING_CIRCLE_SIZE, WAITING_CIRCLE_SIZE, WAITING_CIRCLE_SIZE, WAITING_CIRCLE_SIZE, 0, 360);
        backgroundCircle.setStroke(Color.BLACK);
        backgroundCircle.setStrokeWidth(1);
        backgroundCircle.setFill(Color.LIGHTGRAY);

        Arc loadingCircle = new Arc(WAITING_CIRCLE_SIZE, WAITING_CIRCLE_SIZE, WAITING_CIRCLE_SIZE, WAITING_CIRCLE_SIZE, 0, 35);
        loadingCircle.setType(ArcType.ROUND);
        loadingCircle.setFill(Color.web("#333333").brighter().brighter());

        groupLoadingAnimation.getChildren().addAll(backgroundCircle, loadingCircle);

        loadingTransition = new Transition() {
            {
                setCycleDuration(Duration.seconds(2)); // Duration of one cycle
                setCycleCount(INDEFINITE);  // Make the transition loop indefinitely
            }

            @Override
            protected void interpolate(double frac) {
                loadingCircle.setStartAngle(90 - (360.0 * frac));
            }
        };
    }

    private void modelChanged(LlmModelCardJson newValue) {
        buttonClearClicked(null);
        doWithBlocking(() -> {
            render(DisplayRole.TOOL, "Loading model '" + newValue.getModelCardName() + "' ...");
            LlmModelCardManager.instance().selectedLlModelCardProperty().set(newValue);

            Platform.runLater(() -> {
                buttonClearClicked(null);
                Path modelCardsDirectory = SettingsManager.instance().getPathToLlmModelCards();
                Path path = modelCardsDirectory.resolve(newValue.getModelCardName() + ".png");
                if (Files.exists(path)) {
                    try {
                        addImage(ChatViewModel.instance().getCurImageIndex(), Files.readAllBytes(path), null);
                        ChatViewModel.instance().increaseCurImageIndex();
                    } catch (IOException e) {
                        throw new RuntimeException("Could not read image bytes from " + path.toAbsolutePath(), e);
                    }
                }
            });
            render(DisplayRole.TOOL, "Model '" + newValue.getModelCardName() + "' loaded");

        });

    }

    private void doWithBlocking(Runnable runnable) {
        Platform.runLater(() -> {
            paneLoadBlocker.setVisible(true);
            loadingTransition.play();
            textFieldSystemInput.setText("");
            textFieldUserInput.setText("");
            textFieldUserInput.requestFocus();
            setEnabled(false);
            Thread returnValue = new Thread(() -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    waitForAllServicesDoneAndUnblock();
                }
            });
            returnValue.start();
        });
    }

    public void waitForAllServicesDoneAndUnblock() {
        ThreadManager.instance().startThread("Unblock UI", () -> waitForAllServicesDoneAndUnblockInternally());
    }

    private void waitForAllServicesDoneAndUnblockInternally() {
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            return;
            // nothing to do
        }
        if (PrintingManager.instance().isWorking() || PiperManager.instance().isWorking()) {
            waitForAllServicesDoneAndUnblock();
            return;
        }
        Platform.runLater(() -> {
            paneLoadBlocker.setVisible(false);
            setEnabled(true);
            textFieldUserInput.requestFocus();
            loadingTransition.stop();
        });
    }

    public void setEnabled(boolean enabled) {
        buttonSend.setDisable(!enabled);
        choiceBoxModel.setDisable(!enabled);
    }

    @Override
    public void render(DisplayRole displayRole, String textFragment) {
        Platform.runLater(() -> {
            if (currentDisplayRole != displayRole) {
                TextFlow newLine = newLineInFx();
                newLine.setStyle("-fx-padding: 2px 0 0 0;");
                Text roleText = new Text(displayRole.name() + " : ");
                roleText.getStyleClass().add("roleText");
                roleText.setStyle("-fx-font-weight: bold; -fx-text-fill: " + displayRole.getBackgroundColor() + ";");
                currentLineInChat.getChildren().add(roleText);
                currentDisplayRole = displayRole;
            }
            currentLineInChat.getChildren().add(new Text(textFragment));
        });
    }

    private TextFlow newLineInFx() {
        currentLineInChat = new CopyableTextFlow(this);
        currentLineInChat.setPrefWidth(containerChat.getWidth() - 10.0);
        currentLineInChat.setMinWidth(containerChat.getWidth() - 10.0);
        containerChat.getChildren().add(currentLineInChat);
        return currentLineInChat;
    }

    @Override
    public void renderNewLine() {
        Platform.runLater(this::newLineInFx);
    }

    @Override
    public void addImage(int index, byte[] imageBytes, Path imageFile) {
        Platform.runLater(() -> {
            LoadableImageView imageView = containerImages.getChildren()
                    .stream()
                    .filter(x -> x instanceof LoadableImageView)
                    .map(x -> (LoadableImageView) x)
                    .filter(x -> x.getId().equals("image_" + index))
                    .findFirst()
                    .orElse(null);

            if (imageView == null) {
                imageView = new LoadableImageView();
                imageView.setId("image_" + index);
                containerImages.getChildren().add(imageView);
            }

            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                imageView.setImage(new Image(bis), imageFile);
            } catch (Exception e) {
                System.err.println("Could not add image " + index);
                e.printStackTrace();
            }
        });
    }

    private void buttonSendClicked(ActionEvent actionEvent) {
        if (!SettingsManager.instance().assertAllSettingsValid())
            return;

        final String curSystemPrompt = (textFieldSystemInput.getText() == null ? "" : textFieldSystemInput.getText()).trim();
        final String curUserPrompt = (textFieldUserInput.getText() == null ? "" : textFieldUserInput.getText()).trim();
        String tmpSystemPromptForImage = curSystemPrompt;
        if (tmpSystemPromptForImage.isEmpty() && ChatViewModel.instance().getFullHistory().isEmpty())
            tmpSystemPromptForImage = LlmModelCardManager.instance().getSelectedLlModelCard().getSystem();

        if (!tmpSystemPromptForImage.isEmpty()) {
            render(DisplayRole.SYSTEM, tmpSystemPromptForImage);
            fileNewImageRendering(tmpSystemPromptForImage);
        }
        if (!curUserPrompt.isEmpty())
            render(DisplayRole.USER, curUserPrompt);

        doWithBlocking(() -> {
            ChatViewModel.instance().ask(curSystemPrompt, curUserPrompt);
        });
    }

    @Override
    public void fileNewImageRendering(String tmpSystemPromptForImage) {
        LoadableImageView imageView = new LoadableImageView();
        imageView.setId("image_" + ChatViewModel.instance().getCurImageIndex());
        containerImages.getChildren().add(imageView);
        StableDiffusionManager.instance().renderWithPrompt(
                ChatViewModel.instance().getCurImageIndex(),
                LlmModelCardManager.instance().getSelectedLlModelCard(),
                tmpSystemPromptForImage,
                this);
        ChatViewModel.instance().increaseCurImageIndex();
    }
}

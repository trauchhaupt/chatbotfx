package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.ChatBot;
import de.vrauchhaupt.chatbotfx.manager.*;
import de.vrauchhaupt.chatbotfx.model.*;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChatMainWindow implements IPrintFunction, IChatBoxViewComponent {

    private static final float WAITING_CIRCLE_SIZE = 7;


    @FXML
    private MenuItem menuItemBaseSettings;
    @FXML
    private MenuItem menuItemReloadModels;
    @FXML
    private CheckMenuItem chechMenuItemAutoScroll;
    @FXML
    private CheckMenuItem menuItemTts;
    @FXML
    private CheckMenuItem menuItemTxt2Img;

    @FXML
    private Button buttonSend;
    @FXML
    private Button buttonCancel;
    @FXML
    private ChoiceBox<LlmModelCardJson> choiceBoxModel;
    @FXML
    private ChatContainer containerChat;
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
    private Button buttonSave;
    @FXML
    private Button buttonClear;
    @FXML
    private Button buttonLoad;
    @FXML
    private Label messageCounter;

    private DisplayRole currentDisplayRole = null;
    private Transition loadingTransition = null;
    private boolean oldJobInProgress = false;
    private boolean blocking = false;
    private final ChangeListener<LlmModelCardJson> modelCardSelectionChangeListener = (observable, oldValue, newValue) -> modelChanged(newValue);

    public ChatMainWindow() {

    }

    @FXML
    public void initialize() {
        setupLoadingBackground();
        buttonCancel.setOnAction(this::buttonCancelClicked);
        buttonSend.setOnAction(this::buttonSendClicked);
        buttonClear.setOnAction(this::buttonClearClicked);
        buttonSave.setOnAction(this::buttonSaveClicked);
        buttonLoad.setOnAction(this::buttonLoadClicked);

        List<LlmModelCardJson> availableModelCards = new ArrayList<>();
        try {
            availableModelCards = new ArrayList<>(LlmModelCardManager.instance().getAvailableModelCards());
        } catch (Exception e) {
            exceptionHappend("Could not load available model cards, config seems to be invalid", e);
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
            if (chechMenuItemAutoScroll.isSelected())
                scrollPaneChat.setVvalue(1.0); // Scroll to the bottom
        });

        scrollPaneImages.setPrefWidth(StableDiffusionManager.GENERATED_IMAGE_WIDTH + 30);

        containerImages.heightProperty().addListener((observable, oldValue, newValue) -> {
            scrollPaneImages.setVvalue(1.0); // Scroll to the bottom
        });

        menuItemBaseSettings.setOnAction(x -> SettingsManager.instance().showSettingsWindow());
        menuItemReloadModels.setOnAction(this::buttonReloadModelsClicked);
        menuItemTts.selectedProperty().bindBidirectional(SettingsManager.instance().ttsGenerationProperty());
        menuItemTxt2Img.selectedProperty().bindBidirectional(SettingsManager.instance().text2ImageGenerationProperty());

        ThreadManager.instance().startEndlessThread("Block/Unblock UI", this::controllWorkingInProgress);
    }


    private void buttonCancelClicked(ActionEvent actionEvent) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> buttonCancelClicked(actionEvent));
            return;
        }
        PrintingManager.instance().cancelWork();
        OllamaManager.instance().cancelWork();
        PiperManager.instance().cancelWork();
    }

    public void buttonReloadModelsClicked(ActionEvent actionEvent) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> buttonReloadModelsClicked(actionEvent));
            return;
        }
        choiceBoxModel.valueProperty().removeListener(modelCardSelectionChangeListener);
        PiperManager.instance().reloadTtsModels();
        LlmModelCardManager.instance().reloadLlmModelFiles();
        List<LlmModelCardJson> availableModelCards = new ArrayList<>(LlmModelCardManager.instance().reloadModelCards());
        choiceBoxModel.getItems().setAll(availableModelCards);
        choiceBoxModel.setValue(LlmModelCardManager.instance().getSelectedLlModelCard());
        choiceBoxModel.valueProperty().addListener(modelCardSelectionChangeListener);
    }

    private void buttonClearClicked(ActionEvent actionEvent) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> buttonClearClicked(actionEvent));
            return;
        }
        ChatViewModel.instance().clearHistory();
        currentDisplayRole = null;
        containerChat.clearChat();
        containerImages.getChildren().clear();
        renderModelImage();
    }

    private void buttonLoadClicked(ActionEvent actionEvent) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> buttonLoadClicked(actionEvent));
            return;
        }

        buttonClearClicked(null);
        ChatViewModel.instance().loadMessagesFromFile(this);
        for (IndexedOllamaChatMessage ollamaChatMessage : ChatViewModel.instance().getFullHistory()) {
            renderOnFxThread(DisplayRole.of(ollamaChatMessage.getChatMessage().getRole()),
                    ollamaChatMessage.getChatMessage().getContent(),
                    ollamaChatMessage.getId());
            renderNewLine(ollamaChatMessage.getId());
        }
        Platform.runLater(() -> {
            scrollPaneChat.requestLayout();
            Platform.runLater(() -> scrollPaneChat.setVvalue(1.0));
        });
    }


    private void buttonSaveClicked(ActionEvent actionEvent) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> buttonSaveClicked(actionEvent));
            return;
        }
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

    private void renderModelImage() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::renderModelImage);
            return;
        }
        LlmModelCardJson selectedLlModelCard = LlmModelCardManager.instance().getSelectedLlModelCard();
        if (selectedLlModelCard == null)
            return;
        Path modelCardsDirectory = SettingsManager.instance().getPathToLlmModelCards();
        Path path = modelCardsDirectory.resolve(selectedLlModelCard.getModelCardName() + ".png");
        if (Files.exists(path)) {
            try {
                addImage(ChatViewModel.instance().getCurImageIndex(),
                        Files.readAllBytes(path),
                        null, // file shall not be deleted
                        selectedLlModelCard.getSystem());
                ChatViewModel.instance().increaseCurImageIndex();
            } catch (IOException e) {
                exceptionHappend("Could not read image bytes from " + path.toAbsolutePath(), e);
            }
        }
    }

    private void modelChanged(LlmModelCardJson newValue) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> modelChanged(newValue));
            return;
        }
        buttonClearClicked(null);
        doWithBlocking("load model '" + newValue.getModelCardName() + "'", () -> {
            renderOnFxThread(DisplayRole.TOOL, "Loading model '" + newValue.getModelCardName() + "' ...", -1);
            LlmModelCardManager.instance().selectedLlModelCardProperty().set(newValue);
            Platform.runLater(() -> {
                ChatBot.mainStage.setTitle("ChatBot - " + newValue.getModelCardName() + " (" + newValue.getLlmModel() + ")");
                buttonClearClicked(null);
            });
            renderOnFxThread(DisplayRole.TOOL, "Model '" + newValue.getModelCardName() + "' loaded", -1);
        });

    }

    private void doWithBlocking(String action, Runnable runnable) {
        if (blocking) {
            throw new RuntimeException("Already blocked");
        }
        if (oldJobInProgress) {
            throw new RuntimeException("Another job is in progress");
        }
        blocking = true;
        Platform.runLater(() -> {
            textFieldSystemInput.setText("");
            textFieldUserInput.setText("");
            textFieldUserInput.requestFocus();
            new Thread(() -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    exceptionHappend("Could not '" + action + "'", e);
                } finally {
                    blocking = false;
                }
            }).start();
        });
    }


    private void controllWorkingInProgress(ControlledThread controlledThread) {
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            return;
            // nothing to do
        }

        boolean jobInProgress = blocking ||
                PrintingManager.instance().isWorking() ||
                PiperManager.instance().isWorking() ||
                OllamaManager.instance().isWorking();
        if (oldJobInProgress == jobInProgress)
            return;
        oldJobInProgress = jobInProgress;
        setEnabled(!jobInProgress);
    }

    public synchronized void setEnabled(boolean enabled) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setEnabled(enabled));
            return;
        }
        buttonCancel.setDisable(enabled);
        buttonSend.setDisable(!enabled);
        choiceBoxModel.setDisable(!enabled);
        paneLoadBlocker.setVisible(!enabled);
        if (!enabled)
            loadingTransition.playFromStart();
        else
            loadingTransition.stop();
        scrollPaneChat.requestLayout();
    }

    @Override
    public synchronized void renderOnFxThread(DisplayRole displayRole, String textFragment, int chatMessageIndex) {
        Platform.runLater(() -> {
            if (textFragment == null || textFragment.trim().isEmpty()) {
                containerChat.appendToLastText(" "); // problems with boundsInParent() when there are empty texts
                return;
            }
            if (currentDisplayRole != displayRole) {
                CopyableTextFlow newLine = containerChat.newLineInFx(this, chatMessageIndex);
                newLine.setStyle("-fx-padding: 2px 0 0 0;");
                containerChat.currentLine(this, chatMessageIndex).addText(new RoleText(displayRole));
                currentDisplayRole = displayRole;
            }
            containerChat.currentLine(this, chatMessageIndex).addText(new Text(textFragment));
            messageCounter.setText("  " + ChatViewModel.instance().getFullHistorySize() + " Messages");
        });
    }

    @Override
    public synchronized void renderNewLine(int chatMessageIndex) {
        Platform.runLater(() -> containerChat.newLineInFx(this, chatMessageIndex));
    }

    @Override
    public synchronized void addImage(int index, byte[] imageBytes, Path imageFile, String tooltip) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> addImage(index, imageBytes, imageFile, tooltip));
            return;
        }

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
        if (tooltip != null) {
            Tooltip tooltipOverlay = new Tooltip(tooltip);
            tooltipOverlay.setMaxWidth(800);
            tooltipOverlay.setWrapText(true);
            Tooltip.install(imageView, tooltipOverlay);
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
            imageView.setImage(new Image(bis), imageFile);
        } catch (Exception e) {
            exceptionHappend("Could not add image " + index, e);
        }
    }

    private void exceptionHappend(String errorMsgForUser, Exception e) {
        e.printStackTrace();
        renderOnFxThread(DisplayRole.TOOL, errorMsgForUser + ", because of " + e.getMessage(), -1);
    }

    private void buttonSendClicked(ActionEvent actionEvent) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> buttonSendClicked(actionEvent));
            return;
        }

        if (!SettingsManager.instance().assertAllSettingsValid())
            return;

        final String curSystemPrompt = alterInput(textFieldSystemInput.getText());
        final String curUserPrompt = alterInput(textFieldUserInput.getText());


        String tmpSystemPromptForImage = curSystemPrompt;
        if (tmpSystemPromptForImage.isEmpty() && ChatViewModel.instance().getFullHistory().isEmpty())
            tmpSystemPromptForImage = LlmModelCardManager.instance().getSelectedLlModelCard().getSystem().replace("${NAME}", LlmModelCardManager.instance().getSelectedLlModelCard().getModelCardName());

        if (!tmpSystemPromptForImage.isEmpty()) {
            renderOnFxThread(DisplayRole.SYSTEM, tmpSystemPromptForImage, IndexedOllamaChatMessage.newId());
            if (SettingsManager.instance().isText2ImageGeneration())
                fileNewImageRendering(tmpSystemPromptForImage);
        }
        if (!curUserPrompt.isEmpty())
            renderOnFxThread(DisplayRole.USER, curUserPrompt, IndexedOllamaChatMessage.newId());

        doWithBlocking("ask AI assistant", () -> ChatViewModel.instance().ask(curSystemPrompt, curUserPrompt));
    }

    private String alterInput(String text) {
        if (text == null)
            return "";
        text = text.trim();
        text = text.replace("${NAME}", SettingsManager.instance().getSelectedLlmModelCard());
        text = text.replace("${name}", SettingsManager.instance().getSelectedLlmModelCard());
        return text;
    }

    @Override
    public void fileNewImageRendering(String tmpSystemPromptForImage) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> fileNewImageRendering(tmpSystemPromptForImage));
            return;
        }
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

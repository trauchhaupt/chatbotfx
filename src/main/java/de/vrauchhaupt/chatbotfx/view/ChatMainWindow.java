package de.vrauchhaupt.chatbotfx.view;

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
import javafx.scene.layout.Pane;
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

public class ChatMainWindow implements IPrintFunction {

    private static final float WAITING_CIRCLE_SIZE = 7;

    @FXML
    private MenuItem menuItemBaseSettings;
    @FXML
    private MenuItem menuItemReloadModels;
    @FXML
    private CheckMenuItem menuItemTts;
    @FXML
    private CheckMenuItem menuItemTxt2Img;

    @FXML
    private Pane paneLoadingBackground;
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

        menuItemBaseSettings.setOnAction(x -> SettingsManager.instance().showSettingsWindow());
        menuItemReloadModels.setOnAction(this::buttonReloadModelsClicked);
        menuItemTts.selectedProperty().bindBidirectional(SettingsManager.instance().ttsGenerationProperty());
        menuItemTxt2Img.selectedProperty().bindBidirectional(SettingsManager.instance().text2ImageGenerationProperty());

        ThreadManager.instance().startEndlessThread("Block/Unblock UI", this::controllWorkingInProgress);
    }


    private void buttonCancelClicked(ActionEvent actionEvent) {
        PrintingManager.instance().cancelWork();
        OllamaManager.instance().cancelWork();
        PiperManager.instance().cancelWork();
    }

    public void buttonReloadModelsClicked(ActionEvent actionEvent) {
        choiceBoxModel.valueProperty().removeListener(modelCardSelectionChangeListener);
        PiperManager.instance().reloadTtsModels();
        LlmModelCardManager.instance().reloadLlmModelFiles();
        List<LlmModelCardJson> availableModelCards = new ArrayList<>(LlmModelCardManager.instance().reloadModelCards());
        choiceBoxModel.getItems().setAll(availableModelCards);
        choiceBoxModel.setValue(LlmModelCardManager.instance().getSelectedLlModelCard());
        choiceBoxModel.valueProperty().addListener(modelCardSelectionChangeListener);
    }

    private void buttonClearClicked(ActionEvent actionEvent) {
        ChatViewModel.instance().clearHistory();
        currentDisplayRole = null;
        containerChat.clearChat();
        containerImages.getChildren().clear();
    }

    private void buttonLoadClicked(ActionEvent actionEvent) {
        buttonClearClicked(null);
        ChatViewModel.instance().loadMessagesFromFile(this);
        for (IndexedOllamaChatMessage ollamaChatMessage : ChatViewModel.instance().getFullHistory()) {
            render(DisplayRole.of(ollamaChatMessage.getChatMessage().getRole()),
                    ollamaChatMessage.getChatMessage().getContent(),
                    ollamaChatMessage.getId());
            renderNewLine(ollamaChatMessage.getId());
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
            render(DisplayRole.TOOL, "Loading model '" + newValue.getModelCardName() + "' ...", -1);
            try {
                LlmModelCardManager.instance().selectedLlModelCardProperty().set(newValue);
            } catch (Exception e) {
                e.printStackTrace();
                render(DisplayRole.TOOL, "Could not load model '" + newValue.getModelCardName() + "', because of " + e.getMessage(), -1);
                return;
            }

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
            render(DisplayRole.TOOL, "Model '" + newValue.getModelCardName() + "' loaded", -1);
        });

    }

    private void doWithBlocking(Runnable runnable) {
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
                    e.printStackTrace();
                } finally {
                    blocking = false;
                }
            }).start();
        });
    }


    private void controllWorkingInProgress(ControlledThread controlledThread) {
        try {
            Thread.sleep(1000);
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
        Platform.runLater(() -> {
            setEnabled(!jobInProgress);
        });
    }

    public void setEnabled(boolean enabled) {
        buttonCancel.setDisable(enabled);
        buttonSend.setDisable(!enabled);
        choiceBoxModel.setDisable(!enabled);
        paneLoadBlocker.setVisible(!enabled);
        if (!enabled)
            loadingTransition.playFromStart();
        else
            loadingTransition.stop();
    }

    @Override
    public void render(DisplayRole displayRole, String textFragment, int chatMessageIndex) {
        if (textFragment == null || textFragment.trim().isEmpty()) {
            containerChat.appendToLastText(" "); // problems with boundsInParent() when there are empty texts
            return;
        }
        Platform.runLater(() -> {
            if (currentDisplayRole != displayRole) {
                CopyableTextFlow newLine = containerChat.newLineInFx(this, chatMessageIndex);
                newLine.setStyle("-fx-padding: 2px 0 0 0;");
                containerChat.currentLine(this, chatMessageIndex).addText(new RoleText(displayRole));
                currentDisplayRole = displayRole;
            }
            containerChat.currentLine(this, chatMessageIndex).addText(new Text(textFragment));
        });
    }

    @Override
    public void renderNewLine( int chatMessageIndex) {
        Platform.runLater(() -> containerChat.newLineInFx(this, chatMessageIndex));
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
            render(DisplayRole.SYSTEM, tmpSystemPromptForImage, IndexedOllamaChatMessage.newId());
            if (SettingsManager.instance().isText2ImageGeneration())
                fileNewImageRendering(tmpSystemPromptForImage);
        }
        if (!curUserPrompt.isEmpty())
            render(DisplayRole.USER, curUserPrompt, IndexedOllamaChatMessage.newId());

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

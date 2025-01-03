package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.ChatBot;
import de.vrauchhaupt.chatbotfx.manager.OllamaManager;
import de.vrauchhaupt.chatbotfx.manager.SettingsManager;
import de.vrauchhaupt.chatbotfx.manager.StableDiffusionManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SettingsWindow {

    @FXML
    private Button buttonSave;
    @FXML
    private Button buttonQuit;
    @FXML
    private Label warningPathToModelCards;
    @FXML
    private TextField textFieldPathToModelCards;
    @FXML
    private Button buttonPathToModelCards;
    @FXML
    private Label warningPathToLlmModelFiles;
    @FXML
    private TextField textFieldPathToLlmModelFiles;
    @FXML
    private Button buttonPathToLlmModelFiles;
    @FXML
    private Label warningUrlToOllamaHost;
    @FXML
    private TextField textFieldStripMessagesForLlm;
    @FXML
    private Label warningStripMessagesForLlm;
    @FXML
    private TextField textFieldUrlToOllamaHost;
    @FXML
    private Label warningPathToPiper;
    @FXML
    private TextField textFieldPathToPiper;
    @FXML
    private Button buttonPathToPiper;
    @FXML
    private TextField textFieldPathToTtsModels;
    @FXML
    private Label warningPathToTtsModels;
    @FXML
    private Button buttonPathToTtsModels;
    @FXML
    private Label warningUrlToWebuiForgeHost;
    @FXML
    private TextField textFieldUrlToWebuiForgeHost;

    @FXML
    public void initialize() {
        invisibleIfNoText(warningPathToLlmModelFiles);
        invisibleIfNoText(warningPathToModelCards);
        invisibleIfNoText(warningPathToPiper);
        invisibleIfNoText(warningPathToTtsModels);
        invisibleIfNoText(warningUrlToWebuiForgeHost);
        invisibleIfNoText(warningUrlToOllamaHost);

        createFileSelection(textFieldPathToLlmModelFiles, buttonPathToLlmModelFiles);
        createFileSelection(textFieldPathToPiper, buttonPathToPiper);
        createFileSelection(textFieldPathToTtsModels, buttonPathToTtsModels);
        createFileSelection(textFieldPathToModelCards, buttonPathToModelCards);
        textFieldPathToPiper.setText(SettingsManager.instance().getPathToPiper() == null ? "" : SettingsManager.instance().getPathToPiper().toAbsolutePath().toString());
        textFieldPathToTtsModels.setText(SettingsManager.instance().getPathToTtsModelFiles() == null ? "" : SettingsManager.instance().getPathToTtsModelFiles().toAbsolutePath().toString());
        textFieldPathToLlmModelFiles.setText(SettingsManager.instance().getPathToLlmModelFiles() == null ? "" : SettingsManager.instance().getPathToLlmModelFiles().toAbsolutePath().toString());
        textFieldStripMessagesForLlm.setText(SettingsManager.instance().getMessagesToStripForLLM() + "");
        textFieldPathToModelCards.setText(SettingsManager.instance().getPathToLlmModelCards() == null ? "" : SettingsManager.instance().getPathToLlmModelCards().toAbsolutePath().toString());
        textFieldUrlToWebuiForgeHost.setText(SettingsManager.instance().getWebuiForgeHost());
        textFieldStripMessagesForLlm.setText(SettingsManager.instance().getMessagesToStripForLLM() + "");
        textFieldUrlToOllamaHost.setText(SettingsManager.instance().getOllamaHost());

        textFieldPathToLlmModelFiles.textProperty().addListener((obs, oldV, newV) -> isPathToLlmModelFilesValid());
        isPathToLlmModelFilesValid();
        textFieldStripMessagesForLlm.textProperty().addListener((obs, oldV, newV) -> isStripMessagesForLlmValid());
        isStripMessagesForLlmValid();
        textFieldUrlToOllamaHost.textProperty().addListener((obs, oldV, newV) -> isOllamaHostValid());
        isOllamaHostValid();
        textFieldUrlToWebuiForgeHost.textProperty().addListener((obs, oldV, newV) -> isWebuiForgeHostValid());
        isWebuiForgeHostValid();
        textFieldPathToPiper.textProperty().addListener((obs, oldV, newV) -> isPathToPiperValid());
        isPathToPiperValid();
        textFieldPathToTtsModels.textProperty().addListener((obs, oldV, newV) -> isPathToTtsModelsValid());
        isPathToTtsModelsValid();
        textFieldPathToModelCards.textProperty().addListener((obs, oldV, newV) -> isPathToModelCardsValid());
        isPathToModelCardsValid();

        checkButtonSaveState();
        buttonQuit.setOnAction(this::buttonQuitClicked);
        Platform.runLater(() -> textFieldPathToModelCards.requestFocus());

    }

    private void checkButtonSaveState() {
        buttonSave.setDisable(!isValid());
    }


    public boolean isValid() {
        if (warningPathToLlmModelFiles.getText() != null && !warningPathToLlmModelFiles.getText().isEmpty())
            return false;
        if (warningPathToModelCards.getText() != null && !warningPathToModelCards.getText().isEmpty())
            return false;
        if (warningPathToPiper.getText() != null && !warningPathToPiper.getText().isEmpty())
            return false;
        if (warningPathToTtsModels.getText() != null && !warningPathToTtsModels.getText().isEmpty())
            return false;
        if (warningStripMessagesForLlm.getText() != null && !warningStripMessagesForLlm.getText().isEmpty())
            return false;
        if (warningUrlToOllamaHost.getText() != null && !warningUrlToOllamaHost.getText().isEmpty())
            return false;
        if (warningUrlToWebuiForgeHost.getText() != null && !warningUrlToWebuiForgeHost.getText().isEmpty())
            return false;

        return true;
    }

    private void invisibleIfNoText(Label labelWarning) {
        labelWarning.textProperty().addListener((obs, oldV, newV) -> invisibleIfNoTextInternal(labelWarning, newV));
        invisibleIfNoTextInternal(labelWarning, labelWarning.getText());
    }

    private void invisibleIfNoTextInternal(Label labelWarning, String newV) {
        labelWarning.setStyle("-fx-text-fill: red");
        labelWarning.setVisible(newV != null && !newV.isEmpty());
        checkButtonSaveState();
    }

    private void validateDirectoryExists(TextField textFieldPath, Label labelWarning, Predicate<Path> checkOnDirectoryFiles, String fileToSearchDescription) {
        if (textFieldPathToLlmModelFiles.getText() == null || textFieldPathToLlmModelFiles.getText().isEmpty()) {
            labelWarning.setText("The path must be set");
            return;
        }
        Path path = new File(textFieldPath.getText()).toPath();
        if (!Files.exists(path)) {
            labelWarning.setText("The path does not exist");
            return;
        }

        if (!Files.isDirectory(path)) {
            path = path.getParent();
            textFieldPath.setText(path.toAbsolutePath().toString());
        }

        try (Stream<Path> fileList = Files.list(path)) {
            if (!fileList.anyMatch(checkOnDirectoryFiles)) {
                labelWarning.setText("The directory does not contain any valid file (" + fileToSearchDescription + ")");
                return;
            }
        } catch (IOException e) {
            labelWarning.setText(e.getMessage());
            return;
        }
        labelWarning.setText("");
    }

    private void isWebuiForgeHostValid() {
        if (!StableDiffusionManager.instance().checkWebUiForgeIsRunning()) {
            warningUrlToWebuiForgeHost.setText("The WebUI_Forge Server seems not to be running. Try starting it with 'run.bat' or check the host / port.");
        } else {
            warningUrlToWebuiForgeHost.setText("");
        }
        checkButtonSaveState();
    }

    private void isOllamaHostValid() {
        if (!OllamaManager.instance().checkOllamaServerRunning()) {
            warningUrlToOllamaHost.setText("The OLLAMA Server seems not to be running. Try starting it with 'ollama serve' or check the host / port.");
        } else {
            warningUrlToOllamaHost.setText("");
        }
        checkButtonSaveState();
    }

    private void isStripMessagesForLlmValid() {
        try {
            Integer.parseInt(textFieldStripMessagesForLlm.getText());
            warningStripMessagesForLlm.setText("");
        } catch (NumberFormatException e) {
            warningStripMessagesForLlm.setText("Please enter a number");
        }
    }


    private void isPathToLlmModelFilesValid() {
        validateDirectoryExists(textFieldPathToLlmModelFiles, warningPathToLlmModelFiles, x -> x.toString().endsWith(".gguf"), "*.gguf");
        checkButtonSaveState();
    }

    private void isPathToPiperValid() {
        validateDirectoryExists(textFieldPathToPiper, warningPathToPiper, x -> "piper.exe".equals(x.getFileName().toString()), "piper.exe");
        checkButtonSaveState();
    }

    private void isPathToTtsModelsValid() {
        validateDirectoryExists(textFieldPathToTtsModels, warningPathToTtsModels, x -> x.toString().endsWith(".onnx"), "*.onnx");
        checkButtonSaveState();
    }

    private void isPathToModelCardsValid() {
        validateDirectoryExists(textFieldPathToModelCards, warningPathToModelCards, x -> x.toString().endsWith("json"), "*.json");
        checkButtonSaveState();
    }

    private void createFileSelection(TextField directoryField, Button buttonPath) {
        buttonPath.setText("Select");
        buttonPath.setOnAction(x -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            String initialPath = directoryField.getText();
            if (initialPath != null && !initialPath.isEmpty()) {
                File initialDir = new File(initialPath);
                if (initialDir.exists() && initialDir.isDirectory()) {
                    directoryChooser.setInitialDirectory(initialDir);
                }
            }
            File selectedDirectory = directoryChooser.showDialog(ChatBot.mainStage);
            if (selectedDirectory != null) {
                // Update the TextField with the selected directory
                directoryField.setText(selectedDirectory.getAbsolutePath());
            }
        });
    }

    public void setOnSaveClicked(EventHandler<ActionEvent> onSaveClicked) {
        buttonSave.setOnAction(onSaveClicked);
    }

    private void buttonQuitClicked(ActionEvent actionEvent) {
        System.exit(0);
    }

    public void persist() {
        SettingsManager.instance().setPathToPiper(new File(textFieldPathToPiper.getText()).toPath());
        SettingsManager.instance().setPathToTtsModelFiles(new File(textFieldPathToTtsModels.getText()).toPath());
        SettingsManager.instance().setPathToLlmModelFiles(new File(textFieldPathToLlmModelFiles.getText()).toPath());
        SettingsManager.instance().setPathToLlmModelCards(new File(textFieldPathToModelCards.getText()).toPath());
        try {
            int amountOfMessages = Integer.parseInt(textFieldStripMessagesForLlm.getText());
            SettingsManager.instance().setMessagesToStripForLLM(amountOfMessages);
        } catch (NumberFormatException ignore) {
            // intentionally do not react on this, then the input is invalid
        }
        SettingsManager.instance().setWebuiForgeHost(textFieldUrlToWebuiForgeHost.getText());
        SettingsManager.instance().setOllamaHost(textFieldUrlToOllamaHost.getText());
    }
}

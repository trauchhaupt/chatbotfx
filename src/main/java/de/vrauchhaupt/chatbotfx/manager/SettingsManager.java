package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.ChatBot;
import de.vrauchhaupt.chatbotfx.helper.JsonHelper;
import de.vrauchhaupt.chatbotfx.model.SettingsJson;
import de.vrauchhaupt.chatbotfx.view.SettingsWindow;
import jakarta.validation.constraints.NotNull;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static de.vrauchhaupt.chatbotfx.helper.JsonHelper.objectWriter;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SettingsManager extends AbstractManager {

    private static final String DEFAULT_OLLAMA_HOST = "http://localhost:11434/";
    private static final String DEFAULT_WEBUI_FORGE_HOST = "http://localhost:7860/";
    private static final Path SETTINGS_FILE = Paths.get(".", "chatbot_config.json");

    private static SettingsManager INSTANCE = null;
    private final SimpleObjectProperty<Path> pathToPiper = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<Path> pathToLlmModelCards = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<Path> pathToLlmModelFiles = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<Path> pathToTtsModelFiles = new SimpleObjectProperty<>();
    private final SimpleStringProperty selectedLlmModelCard = new SimpleStringProperty();
    private final SimpleStringProperty ollamaHost = new SimpleStringProperty(DEFAULT_OLLAMA_HOST);
    private final SimpleStringProperty webuiForgeHost = new SimpleStringProperty(DEFAULT_WEBUI_FORGE_HOST);

    private boolean isLoadingInProgress = false;
    private final ChangeListener saveToFileListener = (obs, oldV, newV) -> {
        if (isLoadingInProgress)
            return;
        saveToFile();
    };

    private SettingsManager() {
        pathToPiper.addListener(saveToFileListener);
        pathToLlmModelCards.addListener(saveToFileListener);
        pathToLlmModelFiles.addListener(saveToFileListener);
        pathToTtsModelFiles.addListener(saveToFileListener);
        selectedLlmModelCard.addListener(saveToFileListener);
        ollamaHost.addListener(saveToFileListener);
        webuiForgeHost.addListener(saveToFileListener);
    }

    public static SettingsManager instance() {
        if (INSTANCE == null)
            INSTANCE = new SettingsManager();
        return INSTANCE;
    }

    public void loadFromConfigFile() {
        if (!Files.exists(SETTINGS_FILE))
            saveToFile();
        SettingsJson tmpSettingsJson = JsonHelper.loadFromFile(SETTINGS_FILE, SettingsJson.class);
        if (tmpSettingsJson != null)
            fromJsonObject(tmpSettingsJson);
        logLn("Settings loaded");
    }

    private void saveToFile() {
        try {
            objectWriter().writeValue(SETTINGS_FILE.toFile(), toJsonObject());
        } catch (IOException e) {
            throw new RuntimeException("Could not save settings to '" + SETTINGS_FILE.toAbsolutePath() + "'", e);
        }
    }

    @Override
    public boolean isWorking() {
        return false;
    }

    public Path getPathToPiper() {
        return pathToPiper.get();
    }

    public SettingsManager setPathToPiper(Path pathToPiper) {
        this.pathToPiper.set(pathToPiper);
        return this;
    }

    @NotNull
    public Path getPathToLlmModelCards() {
        if (pathToLlmModelCards.get() == null)
            setPathToLlmModelCards(null);
        return pathToLlmModelCards.get();
    }

    public SettingsManager setPathToLlmModelCards(Path pathToLlmModelCards) {
        if (pathToLlmModelCards == null)
            pathToLlmModelCards = Path.of("model_cards");
        if (!Files.exists(pathToLlmModelCards))
            try {
                Files.createDirectory(pathToLlmModelCards);
            } catch (IOException e) {
                throw new RuntimeException("Could not create model card directory '" + pathToLlmModelCards.toAbsolutePath() + "'", e);
            }
        this.pathToLlmModelCards.set(pathToLlmModelCards);
        return this;
    }

    @NotNull
    public Path getPathToLlmModelFiles() {
        if (pathToLlmModelFiles.get() == null)
            setPathToLlmModelFiles(null);
        return pathToLlmModelFiles.get();
    }

    public SettingsManager setPathToLlmModelFiles(Path pathToLlmModelFiles) {
        if (pathToLlmModelFiles == null)
            pathToLlmModelFiles = Path.of("llm_models");
        if (!Files.exists(pathToLlmModelFiles))
            try {
                Files.createDirectory(pathToLlmModelFiles);
            } catch (IOException e) {
                throw new RuntimeException("Could not create llm model files directory '" + pathToLlmModelFiles.toAbsolutePath() + "'", e);
            }
        this.pathToLlmModelFiles.set(pathToLlmModelFiles);
        return this;
    }

    @NotNull
    public Path getPathToTtsModelFiles() {
        if (pathToTtsModelFiles.get() == null)
            setPathToTtsModelFiles(null);
        return pathToTtsModelFiles.get();
    }

    public SettingsManager setPathToTtsModelFiles(Path pathToTtsModelFiles) {
        if (pathToTtsModelFiles == null)
            pathToTtsModelFiles = Path.of("tts");
        if (!Files.exists(pathToTtsModelFiles))
            try {
                Files.createDirectory(pathToTtsModelFiles);
            } catch (IOException e) {
                throw new RuntimeException("Could not create tts model files directory '" + pathToTtsModelFiles.toAbsolutePath() + "'", e);
            }
        this.pathToTtsModelFiles.set(pathToTtsModelFiles);
        return this;
    }

    public String getSelectedLlmModelCard() {
        return selectedLlmModelCard.get();
    }

    public SettingsManager setSelectedLlmModelCard(String modelCardName) {
        selectedLlmModelCard.set(modelCardName);
        return this;
    }

    public SimpleStringProperty selectedLlmModelCardProperty() {
        return selectedLlmModelCard;
    }

    @NotNull
    public String getOllamaHost() {
        if (ollamaHost.get() == null || ollamaHost.get().isEmpty())
            setOllamaHost(null);
        return ollamaHost.get();
    }

    public SettingsManager setOllamaHost(String ollamaHost) {
        if (ollamaHost == null || ollamaHost.isEmpty())
            ollamaHost = DEFAULT_OLLAMA_HOST;
        this.ollamaHost.set(ollamaHost);
        return this;
    }

    public SimpleStringProperty ollamaHostProperty() {
        return ollamaHost;
    }

    public String getWebuiForgeHost() {
        return webuiForgeHost.get();
    }

    public SettingsManager setWebuiForgeHost(String webuiForgeHost) {
        if (webuiForgeHost == null || webuiForgeHost.isEmpty())
            webuiForgeHost = DEFAULT_WEBUI_FORGE_HOST;
        this.webuiForgeHost.set(webuiForgeHost);
        return this;
    }

    public SimpleStringProperty webuiForgeHostProperty() {
        return webuiForgeHost;
    }

    public SettingsJson toJsonObject() {
        return new SettingsJson()
                .setPathToPiper(getPathToPiper() == null ? null : getPathToPiper().toAbsolutePath().toString())
                .setPathToLlmModelCards(getPathToLlmModelCards().toAbsolutePath().toString())
                .setPathToLlmModelFiles(getPathToLlmModelFiles().toAbsolutePath().toString())
                .setPathToTtsModelFiles(getPathToTtsModelFiles().toAbsolutePath().toString())
                .setOllamaHost(getOllamaHost())
                .setSelectedLlmModelCard(getSelectedLlmModelCard());

    }

    public void fromJsonObject(SettingsJson settingsJson) {
        isLoadingInProgress = true;
        try {
            setPathToPiper(settingsJson.getPathToPiper() == null ? null : Paths.get(settingsJson.getPathToPiper()));
            setPathToLlmModelFiles(settingsJson.getPathToLlmModelFiles() == null ? null : Paths.get(settingsJson.getPathToLlmModelFiles()));
            setPathToLlmModelCards(settingsJson.getPathToLlmModelCards() == null ? null : Paths.get(settingsJson.getPathToLlmModelCards()));
            setPathToTtsModelFiles(settingsJson.getPathToTtsModelFiles() == null ? null : Paths.get(settingsJson.getPathToTtsModelFiles()));
            setSelectedLlmModelCard(settingsJson.getSelectedLlmModelCard());
            setOllamaHost(settingsJson.getOllamaHost());
        } catch (Exception e) {
            throw new RuntimeException("Could not load settings from SettingsJson", e);
        } finally {
            isLoadingInProgress = false;
        }
    }


    public boolean checkAllSettingsValid() {
        if (!OllamaManager.instance().checkOllamaServerRunning())
            return false;
        if (!PiperManager.instance().checkPiperIsAvailable())
            return false;
        if (!StableDiffusionManager.instance().checkWebUiForgeIsRunning())
            return false;
        if (!LlmModelCardManager.instance().checkIfLlmModelFilesExist())
            return false;
        if (!LlmModelCardManager.instance().checkIfModelCardsExist())
            return false;
        return true;
    }

    public boolean assertAllSettingsValid() {
        boolean tmpValid = checkAllSettingsValid();
        if (tmpValid)
            return true;
        while (!tmpValid) {
            try {
                URL resource = getClass().getResource("/de/vrauchhaupt/chatbotfx/view/SettingsWindow.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent settingsMainNode = loader.load();
                SettingsWindow settingsWindow = loader.getController();

                // Create a new stage for the dialog
                Stage dialogStage = new Stage();
                settingsWindow.setOnSaveClicked(x ->
                {
                    if (!settingsWindow.isValid())
                        return;
                    settingsWindow.persist();
                    dialogStage.close();
                });
                dialogStage.setTitle("Settings");
                dialogStage.initModality(Modality.WINDOW_MODAL);
                dialogStage.initOwner(ChatBot.mainStage);
                dialogStage.setScene(new Scene(settingsMainNode));
                dialogStage.showAndWait();
                tmpValid = checkAllSettingsValid();

            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        loadFromConfigFile();
        LlmModelCardManager.instance().reloadModelCards();
        return true;
    }
}

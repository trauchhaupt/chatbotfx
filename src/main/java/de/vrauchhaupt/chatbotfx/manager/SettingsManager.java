package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.helper.JsonHelper;
import de.vrauchhaupt.chatbotfx.model.SettingsJson;
import jakarta.validation.constraints.NotNull;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static de.vrauchhaupt.chatbotfx.helper.JsonHelper.objectWriter;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SettingsManager extends AbstractManager {

    private static final Path SETTINGS_FILE = Paths.get(".", "chatbot_config.json");

    private static SettingsManager INSTANCE = null;
    private final SimpleObjectProperty<Path> pathToPiper = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<Path> pathToLlmModelCards = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<Path> pathToLlmModelFiles = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<Path> pathToTtsModelFiles = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<String> selectedLlmModelCard = new SimpleObjectProperty<>();

    private final ChangeListener saveToFileListener = (obs, oldV, newV) -> saveToFile();

    private SettingsManager() {
        loadFromConfigFile();
        pathToPiper.addListener(saveToFileListener);
        pathToLlmModelCards.addListener(saveToFileListener);
        pathToLlmModelFiles.addListener(saveToFileListener);
        pathToTtsModelFiles.addListener(saveToFileListener);
        selectedLlmModelCard.addListener(saveToFileListener);
    }

    public static SettingsManager instance() {
        if (INSTANCE == null)
            INSTANCE = new SettingsManager();
        return INSTANCE;
    }

    private void loadFromConfigFile() {
        SettingsJson tmpSettingsJson = JsonHelper.loadFromFile(SETTINGS_FILE, SettingsJson.class);
        if (tmpSettingsJson != null)
            fromJsonObject(tmpSettingsJson);
        if (!Files.exists(SETTINGS_FILE))
            saveToFile();
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

    @NotNull
    public Path getPathToPiper() {
        if (pathToPiper.get() == null)
            setPathToPiper(null);
        return pathToPiper.get();
    }

    public SettingsManager setPathToPiper(Path pathToPiper) {
        if (pathToPiper == null)
            pathToPiper = Path.of("piper");
        if (!Files.exists(pathToPiper))
            throw new RuntimeException("Piper is not installed at '" + pathToPiper.toAbsolutePath() + "'. Please review your config.");
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

    public SimpleObjectProperty<String> selectedLlmModelCardProperty() {
        return selectedLlmModelCard;
    }

    public SettingsJson toJsonObject() {
        return new SettingsJson()
                .setPathToPiper(getPathToPiper().toAbsolutePath().toString())
                .setPathToLlmModelCards(getPathToLlmModelCards().toAbsolutePath().toString())
                .setPathToLlmModelFiles(getPathToLlmModelFiles().toAbsolutePath().toString())
                .setPathToTtsModelFiles(getPathToTtsModelFiles().toAbsolutePath().toString())
                .setSelectedLlmModelCard(getSelectedLlmModelCard());

    }

    public void fromJsonObject(SettingsJson settingsJson) {
        setPathToPiper(settingsJson.getPathToPiper() == null ? null : Paths.get(settingsJson.getPathToPiper()));
        setPathToLlmModelFiles(settingsJson.getPathToLlmModelFiles() == null ? null : Paths.get(settingsJson.getPathToLlmModelFiles()));
        setPathToLlmModelCards(settingsJson.getPathToLlmModelCards() == null ? null : Paths.get(settingsJson.getPathToLlmModelCards()));
        setPathToTtsModelFiles(settingsJson.getPathToTtsModelFiles() == null ? null : Paths.get(settingsJson.getPathToTtsModelFiles()));
        setSelectedLlmModelCard(settingsJson.getSelectedLlmModelCard() );

    }
}

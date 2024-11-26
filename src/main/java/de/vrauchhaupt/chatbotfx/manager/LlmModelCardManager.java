package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.helper.JsonHelper;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import io.github.ollama4j.models.response.Model;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class LlmModelCardManager extends AbstractManager {
    private static LlmModelCardManager INSTANCE = null;
    private final ObjectProperty<LlmModelCardJson> selectedLlModelCard = new SimpleObjectProperty<>();
    private final ObjectProperty<Model> usedLlmModel = new SimpleObjectProperty<>();
    private List<LlmModelCardJson> availableModelCards = null;
    private List<Model> availableLlmOllamaModels = null;
    private Map<String, Path> availableLlmModelFiles = null;

    private LlmModelCardManager() {
        selectedLlModelCard.addListener((observable, oldValue, newValue) -> llmModelHasChanged(newValue));
        SettingsManager.instance().selectedLlmModelCardProperty().addListener((observable, oldValue, newValue) -> llmModelCardChangedInSettings());
        llmModelCardChangedInSettings();
    }

    public static LlmModelCardManager instance() {
        if (INSTANCE == null)
            INSTANCE = new LlmModelCardManager();
        return INSTANCE;
    }

    private void llmModelHasChanged(LlmModelCardJson newValue) {
        if (newValue == null) {
            SettingsManager.instance().setSelectedLlmModelCard(null);
            usedLlmModel.set(null);
        } else {
            Model llmModelForModelCard = findLlmModelForModelCard(newValue);
            if (llmModelForModelCard != null) {
                usedLlmModel.set(llmModelForModelCard);
                SettingsManager.instance().setSelectedLlmModelCard(newValue.getModelCardName());
            } else {
                Map.Entry<String, Path> llmModelFileForModelCard = findLlmModelFileForModelCard(newValue);
                OllamaManager.instance().loadModel(newValue, llmModelFileForModelCard, x -> {
                            availableLlmOllamaModels = null;
                            Model tmpLlmModelForModelCard = findLlmModelForModelCard(newValue);
                            if (tmpLlmModelForModelCard == null) {
                                logLn("Could not load llm model '" + newValue.getLlmModel() + "' in ollama");
                            } else {
                                usedLlmModel.set(llmModelForModelCard);
                                SettingsManager.instance().setSelectedLlmModelCard(newValue.getModelCardName());
                            }
                        },
                        () -> logLn("Failed to load model " + newValue.getLlmModel()));

            }
        }
    }

    public void llmModelCardChangedInSettings() {
        if (SettingsManager.instance().isLoadingInProgress())
            return;
        String selectedModelCardName = SettingsManager.instance().getSelectedLlmModelCard();
        if (selectedModelCardName == null) {
            selectedLlModelCard.set(null);
            return;
        }
        if (selectedLlModelCard.get() != null && selectedModelCardName.equals(selectedLlModelCard.get().getModelCardName()))
            return;
        LlmModelCardJson mappedModelJson = getAvailableModelCards().stream()
                .filter(x -> x.getModelCardName().equals(selectedModelCardName))
                .findFirst()
                .orElse(null);
        selectedLlModelCard.set(mappedModelJson);
    }


    private List<Model> getAvailableLlmOllamaModels() {
        if (availableLlmOllamaModels == null) {
            availableLlmOllamaModels = new ArrayList<>(OllamaManager.instance().listModels());
            for (Model model : availableLlmOllamaModels) {
                logLn("Found LLM Model '" + model.getModelName() + "'");
            }
        }
        return availableLlmOllamaModels;
    }

    private Map<String, Path> getAvailableLlmModelFiles() {
        if (availableLlmModelFiles == null) {
            Path pathToModelCards = SettingsManager.instance().getPathToLlmModelFiles();
            logLn("LLM Models (file) : " + pathToModelCards.toAbsolutePath());
            logLn("-------------------");
            try (Stream<Path> dirListing = Files.list(pathToModelCards)) {
                availableLlmModelFiles = new HashMap<>();
                dirListing.forEach(x -> addToModelFileIndex(availableLlmModelFiles, x));

            } catch (IOException e) {
                throw new RuntimeException("Could not load model cards from '" + pathToModelCards.toAbsolutePath() + "'", e);
            }
            logLn();
        }
        return availableLlmModelFiles;
    }

    public LlmModelCardJson getSelectedLlModelCard() {
        return selectedLlModelCard.get();
    }

    public ObjectProperty<LlmModelCardJson> selectedLlModelCardProperty() {
        return selectedLlModelCard;
    }

    public Model getUsedLlmModel() {
        return usedLlmModel.get();
    }

    public ObjectProperty<Model> usedLlmModelProperty() {
        return usedLlmModel;
    }

    private void addToModelFileIndex(Map<String, Path> index, Path path) {
        if (path == null)
            return;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            return;
        if (!path.getFileName().toString().toLowerCase().endsWith(".gguf"))
            return;
        String fileName = path.getFileName().toString();
        String modelName = fileName.substring(0, fileName.length() - 5);
        index.put(modelName, path);
    }

    public List<LlmModelCardJson> getAvailableModelCards() {
        if (availableModelCards == null) {
            availableModelCards = new ArrayList<>();
            Path pathToModelCards = SettingsManager.instance().getPathToLlmModelCards();
            try (Stream<Path> dirListing = Files.list(pathToModelCards)) {
                dirListing.forEach(this::deserializeAndEndToAvailableModelCards);
            } catch (Exception e) {
                throw new RuntimeException("Could not load model cards from '" + pathToModelCards.toAbsolutePath() + "'", e);
            }
            if (availableModelCards.isEmpty()) {
                if (!getAvailableLlmOllamaModels().isEmpty()) {
                    LlmModelCardJson defaultCard = new LlmModelCardJson()
                            .setModelCardName("Default")
                            .setSystem("You are a robot answering questions.")
                            .setLlmModel(getAvailableLlmOllamaModels().get(0).getModelName());
                    Path defaultModelCard = pathToModelCards.resolve("Default.json");
                    try {
                        JsonHelper.objectWriter().writeValue(defaultModelCard.toFile(), defaultCard);
                        deserializeAndEndToAvailableModelCards(defaultModelCard);
                    } catch (IOException e) {
                        throw new RuntimeException("Cannot write default model card to '" + defaultModelCard.toAbsolutePath() + "'", e);
                    }
                    availableModelCards.add(defaultCard);
                } else if (!getAvailableLlmModelFiles().isEmpty()) {
                    LlmModelCardJson defaultCard = new LlmModelCardJson()
                            .setModelCardName("Default")
                            .setSystem("You are a robot answering questions.")
                            .setLlmModel(getAvailableLlmModelFiles().keySet().iterator().next());
                    Path defaultModelCard = pathToModelCards.resolve("Default.json");
                    try {
                        JsonHelper.objectWriter().writeValue(defaultModelCard.toFile(), defaultCard);
                        deserializeAndEndToAvailableModelCards(defaultModelCard);
                    } catch (IOException e) {
                        throw new RuntimeException("Cannot write default model card to '" + defaultModelCard.toAbsolutePath() + "'", e);
                    }
                    availableModelCards.add(defaultCard);
                } else {
                    throw new RuntimeException("There is no model card in '" + SettingsManager.instance().getPathToLlmModelCards().toAbsolutePath() +
                            "' and no gguf files in '" + SettingsManager.instance().getPathToLlmModelFiles().toAbsolutePath() + "'");
                }
            }
        }
        return availableModelCards;
    }

    private Model findLlmModelForModelCard(LlmModelCardJson modelCardJson) {
        if (modelCardJson == null)
            return null;
        return getAvailableLlmOllamaModels().stream()
                .filter(x -> x.getModelName().equals(modelCardJson.getLlmModel()))
                .findFirst()
                .orElse(null);
    }

    private Map.Entry<String, Path> findLlmModelFileForModelCard(LlmModelCardJson modelCardJson) {
        if (modelCardJson == null)
            return null;
        return getAvailableLlmModelFiles().entrySet()
                .stream()
                .filter(x -> x.getKey().equals(modelCardJson.getLlmModel()))
                .findFirst()
                .orElse(null);
    }

    private void deserializeAndEndToAvailableModelCards(Path path) {
        if (path == null)
            return;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            return;
        if (!path.getFileName().toString().toLowerCase().endsWith(".json"))
            return;

        try {
            LlmModelCardJson modelCardJson = JsonHelper.loadFromFile(path, LlmModelCardJson.class);
            if (modelCardJson != null) {
                Model model = findLlmModelForModelCard(modelCardJson);
                Map.Entry<String, Path> modelFile = findLlmModelFileForModelCard(modelCardJson);
                if (model == null && modelFile == null)
                    logLn(modelCardJson.getModelCardName() + " refers to non existing model '" + modelCardJson + "'");
                else {
                    availableModelCards.add(modelCardJson);
                    logLn("Found model card '" + modelCardJson.getModelCardName() + "'");
                }
            }
        } catch (Exception e) {
            ThreadManager.instance().uncaughtException(null, new RuntimeException("Could not load model card from '" + path.toAbsolutePath() + "'", e));
        }
    }

    @Override
    public boolean isWorking() {
        return false;
    }

    public List<LlmModelCardJson> reloadModelCards() {
        availableModelCards = null;
        LlmModelCardJson selectedLlModelCard1 = getSelectedLlModelCard();
        List<LlmModelCardJson> reloadedCards = getAvailableModelCards();
        if (selectedLlModelCard1 != null) {
            LlmModelCardJson reloadedSelectedCard = reloadedCards.stream()
                    .filter(x -> x.getModelCardName().equals(selectedLlModelCard1.getModelCardName()))
                    .findFirst()
                    .orElse(null);
            selectedLlModelCard.set(reloadedSelectedCard);
        }
        return reloadedCards;
    }

    public Map<String, Path> reloadLlmModelFiles() {
        availableLlmModelFiles = null;
        return getAvailableLlmModelFiles();
    }

    public boolean checkIfModelCardsExist() {
        return availableModelCards != null && !availableModelCards.isEmpty();
    }

    public boolean checkIfLlmModelFilesExist() {
        return availableLlmModelFiles != null && !availableLlmModelFiles.isEmpty();
    }
}

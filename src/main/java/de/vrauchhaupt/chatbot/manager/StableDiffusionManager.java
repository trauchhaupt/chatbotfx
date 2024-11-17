package de.vrauchhaupt.chatbot.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.vrauchhaupt.chatbot.helper.JsonHelper;
import de.vrauchhaupt.chatbot.model.ChatViewModel;
import de.vrauchhaupt.chatbot.model.LlmModelCardJson;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class StableDiffusionManager extends AbstractManager {

    public static final int GENERATED_IMAGE_WIDTH = 256;
    public static final int GENERATED_IMAGE_HEIGHT = 384;
    private static final String apiUrl = "http://127.0.0.1:7860/sdapi/v1/txt2img";

    private static StableDiffusionManager INSTANCE = null;

    public static StableDiffusionManager instance() {
        if (INSTANCE == null)
            INSTANCE = new StableDiffusionManager();
        return INSTANCE;
    }

    public void renderWithPrompt(int index, LlmModelCardJson modelCardJson, String prompt, IPrintFunction imageConsumer) {
        if (modelCardJson.getTxt2ImgModel() == null || modelCardJson.getTxt2ImgModel().isEmpty())
            return;
        new Thread(() -> {
            try {
                renderWithPromptInternally(index, modelCardJson, prompt, imageConsumer);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    }

    private void renderWithPromptInternally(int index, LlmModelCardJson modelCardJson, String prompt, IPrintFunction imageConsumer) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            ObjectMapper objectMapper = JsonHelper.objectMapper();
            logLn("Aquiring image # '" + index + "'");

            ObjectNode payload = JsonNodeFactory.instance.objectNode();
            payload.put("prompt", prompt );
            payload.put("negative_promt", "low quality, blurry, bad anatomy, distorted faces, closed eyes");
            payload.put("sd_model_name", modelCardJson.getTxt2ImgModel());
            if (modelCardJson.getTxt2ImgModelStyle() != null && !modelCardJson.getTxt2ImgModelStyle().isEmpty()) {
                ArrayNode stylesNode = objectMapper.createArrayNode();
                stylesNode.add(modelCardJson.getTxt2ImgModelStyle());
                payload.put("styles", stylesNode);
            }
            payload.put("steps", 20);
            payload.put("sampler_name", "Euler");
            payload.put("cfg_scale", 7);
            payload.put("width", GENERATED_IMAGE_WIDTH);
            payload.put("height", GENERATED_IMAGE_HEIGHT);
            payload.put("enable_hr", false);
            payload.put("restore_faces", true);

            String payloadString = payload.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json; utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadString, StandardCharsets.UTF_8))
                    .build();

            // Send the request and get the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logLn("Image " + index + " is finished");
                // Parse the JSON response
                ObjectNode jsonResponse = (ObjectNode) objectMapper.readTree(response.body());
                String base64Image = jsonResponse.get("images").get(0).asText();

                // Delete old images
                if (index == 1) {
                    ChatViewModel.instance().deleteExistingRuntimeImages();
                }

                // Decode the image from Base64
                byte[] imageBytes = Base64.getDecoder().decode(base64Image);
                Path imageFile = SettingsManager.instance().getPathToLlmModelCards().resolve(modelCardJson.getModelCardName() + "_" + String.format("%04d", index) + ".jpg");
                Files.deleteIfExists(imageFile);
                Files.write(imageFile, imageBytes);

                try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                    imageConsumer.addImage(index, imageBytes, imageFile);
                }
            } else {
                throw new RuntimeException("Failed to generate image. Response code: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate image.", e);
        }
    }


    @Override
    public boolean isWorking() {
        return false;
    }
}



package de.vrauchhaupt.chatbotfx.model;

import java.util.Comparator;
import java.util.Objects;

public class LlmModelCardJson extends AbstractJson implements Comparable<LlmModelCardJson> {
    private String modelCardName = "";
    private String llmModel = "";
    private float temperature = 1.0f;
    private float top_p = 0.9f;
    private int top_k = 50;
    private String system = "";
    private String txt2ImgModel = "";
    private String txt2ImgModelStyle = "";

    public String getLlmModel() {
        return llmModel;
    }

    public LlmModelCardJson setLlmModel(String llmModel) {
        this.llmModel = llmModel;
        return this;
    }

    public String getModelCardName() {
        return modelCardName;
    }

    public LlmModelCardJson setModelCardName(String modelCardName) {
        this.modelCardName = modelCardName;
        return this;
    }

    public float getTemperature() {
        return temperature;
    }

    public LlmModelCardJson setTemperature(float temperature) {
        this.temperature = temperature;
        return this;
    }

    public float getTop_p() {
        return top_p;
    }

    public LlmModelCardJson setTop_p(float top_p) {
        this.top_p = top_p;
        return this;
    }

    public int getTop_k() {
        return top_k;
    }

    public LlmModelCardJson setTop_k(int top_k) {
        this.top_k = top_k;
        return this;
    }

    public String getSystem() {
        return system;
    }

    public LlmModelCardJson setSystem(String system) {
        this.system = system;
        return this;
    }

    public String getTxt2ImgModel() {
        return txt2ImgModel;
    }

    public LlmModelCardJson setTxt2ImgModel(String txt2ImgModel) {
        this.txt2ImgModel = txt2ImgModel;
        return this;
    }

    public String getTxt2ImgModelStyle() {
        return txt2ImgModelStyle;
    }

    public LlmModelCardJson setTxt2ImgModelStyle(String txt2ImgModelStyle) {
        this.txt2ImgModelStyle = txt2ImgModelStyle;
        return this;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        LlmModelCardJson that = (LlmModelCardJson) object;
        return Objects.equals(modelCardName, that.modelCardName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(modelCardName);
    }

    @Override
    public int compareTo(LlmModelCardJson o) {
        return Comparator.comparing(LlmModelCardJson::getModelCardName).compare(this, o);
    }
}

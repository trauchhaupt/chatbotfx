module chatbotfx {
    requires jakarta.validation;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.desktop;
    requires java.net.http;
    requires java.logging;
    requires ollama4j;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires org.jsoup;

    exports de.vrauchhaupt.chatbotfx to javafx.controls,javafx.graphics, javafx.fxml;
    exports de.vrauchhaupt.chatbotfx.view to javafx.controls,javafx.graphics, javafx.fxml;
    exports de.vrauchhaupt.chatbotfx.manager to javafx.controls, javafx.fxml, javafx.graphics;
    exports de.vrauchhaupt.chatbotfx.model to com.fasterxml.jackson.databind;

    opens de.vrauchhaupt.chatbotfx.view;
    opens de.vrauchhaupt.chatbotfx.manager;
}
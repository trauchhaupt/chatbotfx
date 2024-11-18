module chatbotfx {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires jakarta.validation;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.desktop;
    requires java.net.http;
    requires java.logging;
    requires ollama4j;

    exports de.vrauchhaupt.chatbot to javafx.controls,javafx.graphics, javafx.fxml;
    exports de.vrauchhaupt.chatbot.view to javafx.controls,javafx.graphics, javafx.fxml;
    exports de.vrauchhaupt.chatbot.manager to javafx.controls, javafx.fxml, javafx.graphics;
    exports de.vrauchhaupt.chatbot.model to com.fasterxml.jackson.databind;

    opens de.vrauchhaupt.chatbot.view;
    opens de.vrauchhaupt.chatbot.manager;
}
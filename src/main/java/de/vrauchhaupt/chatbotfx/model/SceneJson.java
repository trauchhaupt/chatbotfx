package de.vrauchhaupt.chatbotfx.model;

import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;

import java.util.ArrayList;
import java.util.List;

public class SceneJson extends AbstractJson {
    private final List<PersonJson> persons = new ArrayList<>();
    private final List<OllamaChatMessage> messages = new ArrayList<>();
    private String description;

    public SceneJson() {

    }

    public String getDescription() {
        return description;
    }

    public SceneJson setDescription(String description) {
        this.description = description;
        return this;
    }

    public List<PersonJson> getPersons() {
        return persons;
    }

    public SceneJson setPersons(List<PersonJson> persons) {
        this.persons.clear();
        if (this.persons != null)
            this.persons.addAll(persons);
        return this;
    }

    public SceneJson addPerson(PersonJson person) {
        this.persons.add(person);
        return this;
    }

    public List<OllamaChatMessage> getMessages() {
        return messages;
    }

    public SceneJson setMessages(List<OllamaChatMessage> messages) {
        this.messages.clear();
        if (messages != null)
            this.messages.addAll(messages);
        return this;
    }

    public SceneJson addMessage(OllamaChatMessage message) {
        this.messages.add(message);
        return this;
    }

    public List<OllamaChatMessage> getSettingsMessages(LlmModelCardJson curModel) {

        StringBuilder personsPrompt = new StringBuilder("The following persons occur:");
        int iNumber = 1;
        for (PersonJson person : persons) {
            personsPrompt.append("\n")
                    .append(iNumber++)
                    .append(". ")
                    .append(ChatMessageHelper.createReplacedString(person.getName(), curModel))
                    .append(":")
                    .append(ChatMessageHelper.createReplacedString(person.getDescription(), curModel));
        }

        return List.of(new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, personsPrompt.toString()),
                new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, "Scene: " + ChatMessageHelper.createReplacedString(description, curModel)));
    }
}

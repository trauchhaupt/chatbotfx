package de.vrauchhaupt.chatbot.model;

import de.vrauchhaupt.chatbot.helper.JsonHelper;

public abstract class AbstractJson {

    @Override
    public String toString() {
        return JsonHelper.serialize(this);
    }
}

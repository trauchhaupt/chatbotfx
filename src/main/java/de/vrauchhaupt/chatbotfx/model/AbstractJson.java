package de.vrauchhaupt.chatbotfx.model;

import de.vrauchhaupt.chatbotfx.helper.JsonHelper;

public abstract class AbstractJson {

    @Override
    public String toString() {
        return JsonHelper.serialize(this);
    }
}

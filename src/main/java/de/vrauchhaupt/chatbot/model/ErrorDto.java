package de.vrauchhaupt.chatbot.model;

public class ErrorDto {
    private final String errorText;
    private final Throwable e;

    public ErrorDto(String errorText, Throwable e) {
        this.errorText = errorText;
        this.e = e;
    }

    public String getErrorText() {
        return errorText;
    }

    public Throwable getException() {
        return e;
    }
}

package de.vrauchhaupt.chatbotfx;

public interface IMessaging {
    default void out(String text, int chatMessageIndex) {
        System.out.print(text);
    }

    default void nextLine(int chatMessageIndex) {
        System.out.println();
    }

    default void logLn(String text) {
        System.out.println(text);
    }

    default void logLn(String text, Exception e) {
        System.out.println(text);
        e.printStackTrace();
    }

    default void logLn() {
        System.out.println();
    }

    default void log(String text) {
        System.out.print(text);
    }
}

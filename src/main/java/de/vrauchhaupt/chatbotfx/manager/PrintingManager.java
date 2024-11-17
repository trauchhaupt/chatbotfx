package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.ControlledThread;
import de.vrauchhaupt.chatbotfx.model.DisplayRole;
import de.vrauchhaupt.chatbotfx.model.TtsSentence;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrintingManager extends AbstractManager {
    private static PrintingManager INSTANCE = null;
    private final AtomicBoolean currentlyPrinting = new AtomicBoolean(false);
    private final Queue<TtsSentence> queueToWrite = new LinkedList<>();
    private IPrintFunction printFunction = null;

    public PrintingManager() {
        ThreadManager.instance().startEndlessThread("Sentence Printing Thread", x -> printSentences(x));
    }

    public static PrintingManager instance() {
        if (INSTANCE == null)
            INSTANCE = new PrintingManager();
        return INSTANCE;
    }

    public void addToPrintingQueue(TtsSentence ttsSentence) {
        logLn("Adding to PQ '" + ttsSentence.getText() + "'");
        queueToWrite.add(ttsSentence);
    }

    private void printSentences(ControlledThread thread) {
        while (!queueToWrite.isEmpty() && thread.shallThreadRun()) {
            currentlyPrinting.set(true);
            TtsSentence ttsSentence = queueToWrite.poll();
            logLn("Printing PQ '" + ttsSentence.getText() + "'");
            int textwidth = 0;
            boolean firstWord = true;
            for (String word : ttsSentence.getWords()) {
                if (!firstWord) {
                    out(" ");
                } else {
                    firstWord = false;
                }
                out(word);

                textwidth = textwidth + word.length();
                thread.waitAWhile();
            }
            nextLine();
            currentlyPrinting.set(false);

            // wait until the sentence was spoken
            while (!ttsSentence.wasSpoken() && thread.shallThreadRun())
                thread.waitAWhile();
        }
    }

    public boolean isWorking() {
        return queueToWrite.isEmpty() && currentlyPrinting.get();
    }

    @Override
    public void out(String text) {
        if (printFunction != null)
            printFunction.render(DisplayRole.ASSISTANT, text);
        else
            super.out(text);
    }

    @Override
    public void nextLine() {
        if (printFunction != null)
            printFunction.renderNewLine();
        else
            super.nextLine();
    }

    public void setPrintFunction(IPrintFunction printFunction) {
        this.printFunction = printFunction;
    }
}

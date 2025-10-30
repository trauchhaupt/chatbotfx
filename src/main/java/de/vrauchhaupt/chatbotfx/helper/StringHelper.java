package de.vrauchhaupt.chatbotfx.helper;

import java.util.ArrayList;

public class StringHelper {
    public static String toBlock(String myString) {
        char[] chars = myString.toCharArray();
        ArrayList<String> list = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (char character : chars) {
            if (count < 120 - 1) {
                builder.append(character);
                count++;
            } else {
                if (Character.isWhitespace(character)) {
                    builder.append(character);
                    list.add(builder.toString());
                    count = 0;
                    builder.setLength(0);
                } else {
                    builder.append(character);
                    count++;
                }
            }
        }
        if (!builder.toString().isEmpty())
            list.add(builder.toString());
        return String.join("\n", list);
    }

    public static void stdoutBlock(String myString) {
        System.out.println(toBlock(myString));
    }
}

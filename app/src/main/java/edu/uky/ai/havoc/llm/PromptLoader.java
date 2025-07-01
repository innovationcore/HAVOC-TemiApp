package edu.uky.ai.havoc.llm;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class PromptLoader {

    public static String loadPromptFromRaw(Context context, int resId) {
        InputStream inputStream = context.getResources().openRawResource(resId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            return "You are a helpful assistant.";
        }

        return stringBuilder.toString().trim(); // trim to remove trailing newline
    }
}

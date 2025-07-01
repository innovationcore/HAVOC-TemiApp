package edu.uky.ai.havoc;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import edu.uky.ai.havoc.R;

/**
 * Loads and provides access to application configuration from config.properties
 * and raw text resources.
 */
public class Config {
    private static final String TAG = "Config";
    private static final Properties properties = new Properties();

    /**
     * Initializes the Config class by loading properties from the config file.
     * This should be called once, typically in the MainActivity's onCreate method.
     * @param context The application context.
     */
    public static void load(Context context) {
        try {
            Resources resources = context.getResources();
            // The config file should be placed in `res/raw/config.properties`
            InputStream rawResource = resources.openRawResource(R.raw.config);
            properties.load(rawResource);
            Log.i(TAG, "Configuration loaded successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config.properties. Make sure the file exists in res/raw/", e);
            // You might want to throw a runtime exception here to halt the app
            // if the config is essential for operation.
        }
    }

    /**
     * Loads a string from a raw resource file, preserving the error-handling
     * behavior of the original PromptLoader.
     * @param context The application context.
     * @param resId The resource ID of the raw file.
     * @return The content of the file as a string.
     */
    private static String loadPromptFromRaw(Context context, int resId) {
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
            Log.e(TAG, "Failed to load prompt from raw resource ID: " + resId, e);
            // This default return value is based on the original PromptLoader's behavior
            return "You are a helpful assistant.";
        }

        return stringBuilder.toString().trim(); // trim to remove trailing newline
    }

    private static String getProperty(String name) {
        String value = properties.getProperty(name);
        if (value == null) {
            Log.e(TAG, "Configuration property not found: " + name);
            // Return an empty string to avoid null pointer exceptions downstream
            return "";
        }
        return value.trim();
    }

    // New public methods to get system prompts
    public static String getTalkerSystemPrompt(Context context) {
        return loadPromptFromRaw(context, R.raw.talker_system_prompt);
    }

    public static String getPlannerSystemPrompt(Context context) {
        return loadPromptFromRaw(context, R.raw.planner_system_prompt);
    }


    // --- Existing methods for property access ---
    public static String getHomeLocation() {
        return getProperty("home_location");
    }

    public static String getWorkLocation() {
        return getProperty("work_location");
    }

    public static List<String> getPatrolLocations() {
        String locationsString = getProperty("patrol_locations");
        // The work location is always the last stop in a patrol.
        String workLocation = getWorkLocation();
        List<String> locations = new java.util.ArrayList<>(Arrays.asList(locationsString.split("\\s*,\\s*")));
        locations.add(workLocation);
        return locations;
    }

    public static String getWebRtcServerUrl() {
        return getProperty("webrtc_server_url");
    }


    public static String getLlmApiKey() {
        return getProperty("llm_api_key");
    }

    public static String getPlannerUrl() {
        return getProperty("planner_url");
    }

    public static String getPlannerModel() {
        return getProperty("planner_model");
    }

    public static String getTalkerUrl() {
        return getProperty("talker_url");
    }

    public static String getTalkerModel() {
        return getProperty("talker_model");
    }
}
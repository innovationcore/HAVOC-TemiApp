package edu.uky.ai.roguetemi;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Loads and provides access to application configuration from config.properties.
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

    private static String getProperty(String name) {
        String value = properties.getProperty(name);
        if (value == null) {
            Log.e(TAG, "Configuration property not found: " + name);
            // Return an empty string to avoid null pointer exceptions downstream
            return "";
        }
        return value.trim();
    }

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

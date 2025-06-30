package edu.uky.ai.roguetemi.llm;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.List;

public class LlmWrapper {
    private JSONObject systemJson = new JSONObject();
    private JSONObject responseJson = new JSONObject();
    private JSONArray history = new JSONArray();
    private String url;
    private String model;
    private List<String> requiredTools;

    // Public Methods
    public void clearHistory() {
        this.history = new JSONArray();
        // Re-add system prompt if it exists
        if (systemJson.has("content")) {
            this.history.put(systemJson);
        }
    }

    // Getters and Setters
    public void setSystemPrompt(String systemPrompt) {
        this.systemJson = new JSONObject();
        try {
            this.systemJson.put("role", "system");
            // Escape apostrophes to prevent JSON parsing issues
            String escapedPrompt = systemPrompt.replace("'", "\\'");
            this.systemJson.put("content", escapedPrompt);
        } catch (JSONException e) {
            android.util.Log.e("LlmWrapper", "Error setting system prompt: " + e.getMessage());
        }
        // Ensure system prompt is first in history
        clearHistory();
    }

    public void addUserPrompt(String userPrompt) {
        JSONObject userMessage = new JSONObject();
        try {
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            history.put(userMessage);
        } catch (JSONException e) {
            android.util.Log.e("LlmWrapper", "Error adding user prompt: " + e.getMessage());
        }
    }

    public void addAssistantPrompt(String assPrompt) {
        JSONObject assMessage = new JSONObject();
        try {
            assMessage.put("role", "assistant");
            assMessage.put("content", assPrompt);
            history.put(assMessage);
        } catch (JSONException e) {
            android.util.Log.e("LlmWrapper", "Error adding assistant prompt: " + e.getMessage());
        }
    }

    public void setResponseJson(JSONObject responseJson) {
        this.responseJson = responseJson;
    }

    public void setLLMMessages(JSONArray history) {
        this.history = history;
        // Ensure system prompt is preserved if it exists
        if (systemJson.has("content") && history.length() > 0) {
            try {
                history.put(0, systemJson);
            } catch (JSONException e) {
                android.util.Log.e("LlmWrapper", "Error preserving system prompt in history: " + e.getMessage());
            }
        }
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setRequiredTools(List<String> requiredTools) {
        this.requiredTools = requiredTools;
    }

    public JSONObject getSystemPrompt() {
        return systemJson;
    }

    public JSONObject getResponseJson() {
        return responseJson;
    }

    public JSONArray getHistory() {
        return history;
    }

    public String getUrl() {
        return url;
    }

    public String getModel() {
        return model;
    }

    public List<String> getRequiredTools() {
        return requiredTools;
    }
}
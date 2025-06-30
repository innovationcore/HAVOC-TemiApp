package edu.uky.ai.roguetemi.llm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import edu.uky.ai.roguetemi.Config;

public class LlmConnector {
    private static final String TAG = "LlmConnector";

    public LlmConnector() {
    }

    // --- Tool Definition Methods ---

    private static JSONObject defineParsePlanTool() {
        Log.v(TAG, "Defining parse_plan tool");
        JSONObject tool = new JSONObject();
        try {
            tool.put("type", "function");

            JSONObject functionDetails = new JSONObject();
            functionDetails.put("name", "parse_plan");
            functionDetails.put("description", "Convert a natural language plan into a structured JSON object.");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");

            JSONObject properties = new JSONObject();
            JSONObject parsedPlanProp = new JSONObject();
            parsedPlanProp.put("type", "array");
            parsedPlanProp.put("description", "An array of actions, each being either a 'move' or 'speak' action.");

            JSONArray items = new JSONArray();
            JSONObject moveAction = new JSONObject();
            moveAction.put("type", "object");
            JSONObject moveProps = new JSONObject();
            moveProps.put("type", new JSONObject().put("const", "move"));
            moveProps.put("destination", new JSONObject().put("type", "string"));
            moveAction.put("properties", moveProps);
            moveAction.put("required", new JSONArray().put("type").put("destination"));
            JSONObject speakAction = new JSONObject();
            speakAction.put("type", "object");
            JSONObject speakProps = new JSONObject();
            speakProps.put("type", new JSONObject().put("const", "speak"));
            speakProps.put("message", new JSONObject().put("type", "string"));
            speakProps.put("wait_for_response", new JSONObject().put("type", "boolean"));
            speakAction.put("properties", speakProps);
            speakAction.put("required", new JSONArray().put("type").put("message").put("wait_for_response"));
            items.put(moveAction).put(speakAction);
            parsedPlanProp.put("items", new JSONObject().put("oneOf", items));
            properties.put("parsed_plan", parsedPlanProp);

            parameters.put("properties", properties);
            parameters.put("required", new JSONArray().put("parsed_plan"));

            functionDetails.put("parameters", parameters);
            tool.put("function", functionDetails);
            Log.v(TAG, "parse_plan tool defined successfully");
        } catch (JSONException e) {
            Log.e(TAG, "Error defining parse_plan tool", e);
        }
        return tool;
    }

    private static JSONObject defineConversationOverTool() {
        Log.v(TAG, "Defining conversation_over tool");
        JSONObject tool = new JSONObject();
        try {
            tool.put("type", "function");

            JSONObject functionDetails = new JSONObject();
            functionDetails.put("name", "conversation_over");
            functionDetails.put("description", "Call this tool to end the current conversation segment when appropriate. Include a short farewell or acknowledgment in the 'message' parameter.");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");

            JSONObject properties = new JSONObject();
            JSONObject messageProp = new JSONObject();
            messageProp.put("type", "string");
            messageProp.put("description", "A short farewell or a restatement of the user's command.");
            properties.put("message", messageProp);

            parameters.put("properties", properties);
            parameters.put("required", new JSONArray().put("message"));

            functionDetails.put("parameters", parameters);
            tool.put("function", functionDetails);
            Log.v(TAG, "conversation_over tool defined successfully");
        } catch (JSONException e) {
            Log.e(TAG, "Error defining conversation_over tool", e);
        }
        return tool;
    }

    // --- Map to connect tool name strings to their definition suppliers ---
    private static final Map<String, Supplier<JSONObject>> TOOL_DEFINERS = new HashMap<>();

    static {
        TOOL_DEFINERS.put("parse_plan", LlmConnector::defineParsePlanTool);
        TOOL_DEFINERS.put("conversation_over", LlmConnector::defineConversationOverTool);
        Log.d(TAG, "TOOL_DEFINERS initialized with " + TOOL_DEFINERS.size() + " tools");
    }

    /**
     * Sends a query to the LLM API based on the configuration in the provided LlmWrapper.
     * Supports different tool usage settings: none (Planner), required (Parser), or auto (Talker).
     *
     * @param llmWrapper The LlmWrapper containing the model, URL, messages, and required tools.
     * @return A JSONObject representing either the executed tool's result wrapped in {"content": ...},
     *         the LLM's direct message object, or an error object.
     */
    public JSONObject query(LlmWrapper llmWrapper) {
        Log.d(TAG, "Starting LLM query");
        String url = llmWrapper.getUrl();
        String model = llmWrapper.getModel();
        JSONArray messages = llmWrapper.getHistory();
        List<String> requiredToolNames = llmWrapper.getRequiredTools();
        try {
            Log.i(TAG, "Executing async HTTP request to " + url);
            @SuppressLint("StaticFieldLeak") JSONObject result = new AsyncTask<Void, Void, JSONObject>() {
                @Override
                protected JSONObject doInBackground(Void... voids) {
                    return executeHttpRequest(url, buildRequestBody(model, messages, requiredToolNames));
                }
            }.execute().get();
            Log.d(TAG, "Async HTTP request completed");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error executing async HTTP request", e);
            try {
                JSONObject error = new JSONObject().put("error", "Async HTTP request failed: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                Log.w(TAG, "Returning error: " + error.toString());
                return error;
            } catch (JSONException e2) {
                Log.e(TAG, "Error creating error JSON for async request", e2);
                return new JSONObject();
            }
        }
    }

    private JSONObject buildRequestBody(String model, JSONArray messages, List<String> requiredToolNames) {
        Log.d(TAG, "Building request body for model: " + model);
        try {
            JSONArray toolsJsonArray = null;
            String toolChoice = null;
            if (requiredToolNames != null && !requiredToolNames.isEmpty()) {
                toolsJsonArray = new JSONArray();
                for (String toolName : requiredToolNames) {
                    Supplier<JSONObject> definer = TOOL_DEFINERS.get(toolName);
                    if (definer != null) {
                        toolsJsonArray.put(definer.get());
                        Log.v(TAG, "Included tool definition: " + toolName);
                    } else {
                        Log.w(TAG, "Requested tool definition '" + toolName + "' not found in TOOL_DEFINERS");
                    }
                }
                if (model.isEmpty()) {
                    toolChoice = requiredToolNames.contains("parse_plan") ? "required" : "auto";
                    Log.v(TAG, "Set tool_choice to: " + toolChoice);
                }
            }

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
//            requestBody.put("max_tokens", 800);
            requestBody.put("temperature", 0.5);

            if (toolsJsonArray != null && toolsJsonArray.length() > 0) {
                requestBody.put("tools", toolsJsonArray);
                requestBody.put("tool_choice", toolChoice);
            }

            Log.v(TAG, "Request body built: " + requestBody.toString(2));
            return requestBody;
        } catch (JSONException e) {
            Log.e(TAG, "Error building request body", e);
            try {
                JSONObject error = new JSONObject().put("error", "Unexpected JSON exception: " + e.getMessage());
                Log.w(TAG, "Returning error: " + error.toString());
                return error;
            } catch (JSONException e2) {
                Log.e(TAG, "Error creating error JSON for request body", e2);
                return new JSONObject();
            }
        }
    }

    private JSONObject executeHttpRequest(String url, JSONObject requestBody) {
        Log.d(TAG, "Starting HTTP request to " + url);
        HttpURLConnection connection = null;
        try {
            Log.v(TAG, "Setting up connection");
            URL apiUrl = new URL(url);
            connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + Config.getLlmApiKey());
            Log.i(TAG, "Using API key: " + Config.getLlmApiKey());
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            Log.v(TAG, "Connection configured with timeout: connect=" + connection.getConnectTimeout() + "ms, read=" + connection.getReadTimeout() + "ms");

            Log.v(TAG, "Writing request body");
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                Log.v(TAG, "Request body written: " + input.length + " bytes");
            }

            Log.v(TAG, "Getting response code");
            int statusCode = connection.getResponseCode();
            Log.i(TAG, "Received HTTP response code: " + statusCode);

            InputStreamReader isr;
            if (statusCode >= 200 && statusCode < 300) {
                Log.v(TAG, "Reading success response stream");
                isr = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
            } else {
                Log.w(TAG, "Reading error response stream for HTTP " + statusCode);
                isr = new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8);
            }

            Log.v(TAG, "Reading response content");
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(isr)) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
            Log.i(TAG, "Raw response: " + (response.length() > 0 ? response.toString() : "<empty>"));

            if (statusCode < 200 || statusCode >= 300) {
                Log.e(TAG, "HTTP request failed with status " + statusCode);
                try {
                    JSONObject errorJson = new JSONObject(response.toString());
                    JSONObject error = new JSONObject().put("error", errorJson).put("http_status", statusCode);
                    Log.w(TAG, "Returning error response: " + error.toString());
                    return error;
                } catch (JSONException jsonEx) {
                    Log.e(TAG, "Error parsing error response JSON", jsonEx);
                    try {
                        JSONObject error = new JSONObject()
                                .put("error", "API request failed with status " + statusCode)
                                .put("details", response.toString())
                                .put("http_status", statusCode);
                        Log.w(TAG, "Returning fallback error response: " + error.toString());
                        return error;
                    } catch (JSONException e) {
                        Log.e(TAG, "Error creating fallback error JSON", e);
                        return new JSONObject();
                    }
                }
            }

            Log.v(TAG, "Parsing response JSON");
            try {
                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONObject result = new JSONObject().put("response_body", jsonResponse);
                Log.i(TAG, "Successfully parsed response: " + result.toString(2));
                return result;
            } catch (JSONException jsonEx) {
                Log.e(TAG, "Error parsing successful API response", jsonEx);
                try {
                    JSONObject error = new JSONObject()
                            .put("error", "Error parsing successful API response")
                            .put("details", response.toString());
                    Log.w(TAG, "Returning error for response parsing: " + error.toString());
                    return error;
                } catch (JSONException e) {
                    Log.e(TAG, "Error creating error JSON for response parsing", e);
                    return new JSONObject();
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "IOException during HTTP request", e);
            try {
                JSONObject error = new JSONObject()
                        .put("error", "HTTP request failed: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                Log.w(TAG, "Returning error: " + error.toString());
                return error;
            } catch (JSONException e2) {
                Log.e(TAG, "Error creating error JSON for IOException", e2);
                return new JSONObject();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception during HTTP request", e);
            try {
                JSONObject error = new JSONObject()
                        .put("error", "Unexpected exception: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                Log.w(TAG, "Returning error: " + error.toString());
                return error;
            } catch (JSONException e2) {
                Log.e(TAG, "Error creating error JSON for unexpected exception", e2);
                return new JSONObject();
            }
        } finally {
            if (connection != null) {
                Log.v(TAG, "Disconnecting HTTP connection");
                connection.disconnect();
            }
        }
    }
}
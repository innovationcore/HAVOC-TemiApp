package edu.uky.ai.roguetemi.llm;

import android.content.Context;
import android.util.Log;

import com.robotemi.sdk.Robot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.uky.ai.roguetemi.MainActivity;
import edu.uky.ai.roguetemi.R;
import edu.uky.ai.roguetemi.Config;
import edu.uky.ai.roguetemi.actions.MoveAction;
import edu.uky.ai.roguetemi.actions.ConversationAction;
import edu.uky.ai.roguetemi.actions.SpeakActionWithoutResponse;
import edu.uky.ai.roguetemi.actions.TemiAction;

public class Planner {
    private static final String TAG = "Planner";
    private final Context context;
    private final LlmConnector llmConnector;
    private final Robot temi;

    public Planner(Context context, Robot robot) {
        this.context = context;
        this.temi = robot;
        this.llmConnector = new LlmConnector();
    }

public List<TemiAction> generatePlan(String actionHistory) {
    Log.i(TAG, "Generating plan based on action history. Current location: " + MainActivity.currentLocation);

    // Build planning prompt
    String planningPrompt = String.format(
            "Here is a log of past events:\n%s\nTemi's current location is: %s.\n\nBased only on the full history provided, create the next step-by-step plan for Temi.",
            actionHistory, MainActivity.currentLocation
    );

    // Query Planner LLM
    LlmWrapper plannerWrapper = new LlmWrapper();
    plannerWrapper.setUrl(Config.getPlannerUrl());
    plannerWrapper.setModel(Config.getPlannerModel());
    plannerWrapper.setRequiredTools(Collections.emptyList());
    plannerWrapper.setSystemPrompt(PromptLoader.loadPromptFromRaw(context, R.raw.planner_system_prompt));
    plannerWrapper.addUserPrompt(planningPrompt);

    JSONObject plannerResponse = llmConnector.query(plannerWrapper); // This is the full response from the LLM
    // Log the pretty-printed JSON response for easier debugging
    try {
        Log.i(TAG, "Planner full response: " + (plannerResponse != null ? plannerResponse.toString(2) : "null"));
    } catch (JSONException e) {
        Log.e(TAG, "Error pretty-printing plannerResponse JSON: " + e.getMessage());
    }


    List<TemiAction> actions = new ArrayList<>();
    String rawLlmContent = "";

    if (plannerResponse == null) {
        Log.e(TAG, "Planner response was null.");
        return actions; // Return empty list if no response
    }

    try {
        // 1. Navigate to the actual content string from the LLM response
        if (plannerResponse.has("response_body")) {
            JSONObject responseBody = plannerResponse.getJSONObject("response_body");
            if (responseBody.has("choices")) {
                JSONArray choicesArray = responseBody.getJSONArray("choices");
                if (choicesArray.length() > 0) {
                    JSONObject firstChoice = choicesArray.getJSONObject(0); // Get the first choice
                    if (firstChoice.has("message")) {
                        JSONObject messageObject = firstChoice.getJSONObject("message");
                        if (messageObject.has("content")) {
                            rawLlmContent = messageObject.getString("content");
                        } else {
                            Log.e(TAG, "Planner response: 'message' object missing 'content' field.");
                            return actions; // No content to parse
                        }
                    } else {
                        Log.e(TAG, "Planner response: First choice missing 'message' object.");
                        return actions; // No message to parse
                    }
                } else {
                    Log.e(TAG, "Planner response: 'choices' array is empty.");
                    return actions; // No choices to parse
                }
            } else {
                Log.e(TAG, "Planner response: 'response_body' missing 'choices' array.");
                return actions; // No choices to parse
            }
        } else {
            Log.e(TAG, "Planner response: Missing 'response_body'.");
            return actions; // No response_body to parse
        }

        Log.i(TAG, "Raw LLM content (with potential reasoning): " + rawLlmContent);

        // 2. Remove reasoning to isolate the JSON plan string
        // This assumes your removeReasoning method correctly extracts the JSON array string.
        // Example: if content is "<think>...</think>\n\n[{\"type\": ...}]", it should return "[{\"type\": ...}]"
        String jsonPlanString = removeReasoning(rawLlmContent);
        Log.i(TAG, "📝JSON plan string (after removeReasoning):\n" + jsonPlanString);

        // !!! CRITICAL: Remove the System.exit(0) line that was here !!!
        // System.exit(0); // THIS LINE MUST BE REMOVED

        // 3. Parse the isolated JSON plan string into a JSONArray
        if (jsonPlanString == null || jsonPlanString.trim().isEmpty()) {
            Log.w(TAG, "JSON plan string is empty after removing reasoning.");
            return actions;
        }

        JSONArray actionsArray = new JSONArray(jsonPlanString.trim()); // Use trim() to remove leading/trailing whitespace

        // 4. Iterate through the JSONArray and create TemiAction objects
        for (int i = 0; i < actionsArray.length(); i++) {
            JSONObject actionJson = actionsArray.getJSONObject(i);
            String type = actionJson.optString("type"); // Use optString for safety, returns empty string if not found

            switch (type) {
                case "move":
                    String destination = actionJson.optString("destination", "unknown location"); // Default if not found
                    actions.add(new MoveAction(destination, temi));
                    Log.d(TAG, "Added MoveAction to: " + destination);
                    break;
                case "speak":
                    String message = actionJson.optString("message", "");
                    // Default to true for waitForResponse if not explicitly set or if value is not a boolean
                    boolean waitForResponse = actionJson.optBoolean("wait_for_response", true);
                    if (!message.isEmpty()) {
                        if (waitForResponse) {
                            actions.add(new ConversationAction(message, null, MainActivity.currentLocation, temi, context));
                            Log.d(TAG, "Added ConversationAction (waits for response): \"" + message + "\"");
                        } else {
                            actions.add(new SpeakActionWithoutResponse(message, temi));
                            Log.d(TAG, "Added SpeakActionWithoutResponse: \"" + message + "\"");
                        }
                    } else {
                        Log.w(TAG, "Speak action found with an empty message. Action skipped.");
                    }
                    break;
                default:
                    if (type.isEmpty()) {
                        Log.w(TAG, "Action found with missing or empty 'type' field in plan: " + actionJson.toString());
                    } else {
                        Log.w(TAG, "Unknown action type in plan: '" + type + "'. Action skipped: " + actionJson.toString());
                    }
                    break;
            }
        }
    } catch (JSONException e) {
        Log.e(TAG, "Error processing planner response JSON: " + e.getMessage() + "\nProblematic content/string: " + rawLlmContent, e);
        // Depending on desired behavior, you might want to return the actions parsed so far,
        // or an empty list to indicate failure.
    }

    Log.i(TAG, "Parsed " + actions.size() + " actions from the plan.");
    if (actions.isEmpty()) {
        Log.w(TAG, "No actions were parsed from the planner response.");
    }
    for (TemiAction action : actions) {
        Log.d(TAG, "Queued Action: " + action.getActionName());
    }
    return actions;
}

    /**
     * Helper method to remove reasoning/thought process from the LLM content
     * and extract the clean JSON array string.
     * You'll need to ensure this method is robust based on the exact format of your LLM's output.
     *
     * @param rawContent The raw content string from the LLM.
     * @return The cleaned JSON array string, or the original string if JSON part isn't found.
     */
    private String removeReasoning(String rawContent) {
        if (rawContent == null) {
            return "";
        }
        // This example looks for the last occurrence of "</think>" and takes what's after it.
        // Then it tries to find the JSON array. A more robust solution might be needed
        // depending on variations in the LLM output.
        int thinkEndIndex = rawContent.lastIndexOf("</think>");
        String potentialJson = rawContent;

        if (thinkEndIndex != -1) {
            potentialJson = rawContent.substring(thinkEndIndex + "</think>".length());
        }

        // Find the start of the JSON array '[' and end ']'
        int startIndex = potentialJson.indexOf('[');
        int endIndex = potentialJson.lastIndexOf(']');

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return potentialJson.substring(startIndex, endIndex + 1).trim();
        }

        Log.w(TAG, "Could not clearly isolate JSON array from raw content. Check LLM output format. Raw content was: " + rawContent);
        // Fallback: If the raw content itself looks like a JSON array, return it trimmed.
        // This is a basic fallback and might not always be correct.
        String trimmedContent = rawContent.trim();
        if (trimmedContent.startsWith("[") && trimmedContent.endsWith("]")) {
            return trimmedContent;
        }
        // If no JSON array is found after trying, return an empty array string or an empty string
        // to prevent parsing errors later. Empty array string is safer for new JSONArray().
        return "[]";
    }
}
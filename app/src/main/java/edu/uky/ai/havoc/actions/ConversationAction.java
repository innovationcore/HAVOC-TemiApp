package edu.uky.ai.havoc.actions;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import edu.uky.ai.havoc.Config;
import edu.uky.ai.havoc.llm.LlmConnector;
import edu.uky.ai.havoc.llm.LlmWrapper;

public class ConversationAction implements TemiAction {
    private static final String TAG = "ConversationAction";
    private static final long RESPONSE_TIMEOUT_MS = 20_000; // 20 seconds for user to respond
    private final @Nullable String temiMessage;
    private final @Nullable String userMessage;
    private final String currentLocation;
    private final Robot temi;
    private final Context context;
    private LlmConnector llmConnector;
    private CompletableFuture<String> userResponseFuture; // For getUserResponse internal blocking
    private LlmWrapper talkerWrapper;
    private boolean conversationOverCalled = false;

    public ConversationAction(@Nullable String temiMessage, @Nullable String userMessage, String currentLocation, Robot robot, Context context) {
        this.temiMessage = temiMessage;
        this.userMessage = userMessage;
        this.currentLocation = currentLocation;
        this.temi = robot;
        this.context = context;
        this.llmConnector = new LlmConnector();
    }

    @Override
    public String getActionName() {
        return "Conversation" + (temiMessage != null ? ": Temi said \"" + temiMessage + "\"" : ": User said \"" + userMessage + "\"");
    }

    @Override
    public void execute(ActionCompletionListener listener) {
        Log.d(TAG, "Executing ConversationAction: TemiMessage=" + temiMessage + ", UserMessage=" + userMessage);
//        temi.addTtsListener(this);

        String conversationLog = "Error: Conversation did not run as expected.";
        boolean success = false;

        try {
            if ((temiMessage != null && userMessage == null) || (userMessage != null && temiMessage == null)) {
                talkerWrapper = new LlmWrapper();
                talkerWrapper.setUrl(Config.getTalkerUrl());
                talkerWrapper.setModel(Config.getTalkerModel());
                talkerWrapper.setRequiredTools(Collections.singletonList("conversation_over"));
                talkerWrapper.setSystemPrompt(Config.getTalkerSystemPrompt(context));

                conversationLog = runTemiConversation();
                success = true;
            } else {
                Log.e(TAG, "Invalid input: temiMessage and userMessage cannot both be non-null or both be null");
                conversationLog = "Error: Invalid input for ConversationAction";
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Exception during ConversationAction execution: " + e.getMessage(), e);
            conversationLog = "Error: Exception during conversation - " + e.getMessage();
        } finally {
            if (listener != null) {
                listener.onActionCompleted(getActionName(), success, conversationLog);
            }
        }
    }

    private String runTemiConversation() {
        if (temiMessage != null) { // Conversation starts with Temi speaking
            talkerWrapper.addAssistantPrompt(temiMessage); // Log what Temi is about to say

            this.userResponseFuture = new CompletableFuture<>();
            temi.askQuestion(temiMessage);
            Log.d(TAG, "Waiting for user response...");
            try {
                this.userResponseFuture.get(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                Log.d(TAG, "User responded.");
            } catch (TimeoutException e) {
                Log.w(TAG, "Timeout waiting for initial user response. Ending conversation.");
                talkerWrapper.addUserPrompt("(no response)"); // Add to history for logging
                return buildConversationLog(); // Exit gracefully
            } catch (ExecutionException | InterruptedException e) {
                this.userResponseFuture.completeExceptionally(e);
                throw new RuntimeException(e);
            }
        } else { // Conversation starts with a user message
            talkerWrapper.addUserPrompt(userMessage);
        }

        final int maxTurns = 10;
        int turnCount = 0;

        while (turnCount < maxTurns) {
            turnCount++;
            Log.i(TAG, "--- Temi Turn " + turnCount + " ---");

            JSONObject llmResponseJson = llmConnector.query(talkerWrapper);
            talkerWrapper.setResponseJson(llmResponseJson); // For LLMWrapper's internal history if needed

            String assistantResponse = "";
            try {
                Log.i(TAG, "LLM response: " + llmResponseJson.toString(2));
                JSONObject responseBody = llmResponseJson.getJSONObject("response_body");

                if (responseBody.has("choices") && (responseBody.getJSONArray("choices").length() > 0)) {
                    JSONObject choice = responseBody.getJSONArray("choices").getJSONObject(0);
                    String finishReason = choice.getString("finish_reason");
                    Log.i(TAG, "Finish reason: " + finishReason);

                    if ("stop".equals(finishReason)) {
                        if (choice.has("message") && choice.getJSONObject("message").has("content")) {
                            String content = choice.getJSONObject("message").getString("content");
                            if (!content.isEmpty() && !"null".equalsIgnoreCase(content)) {
                                assistantResponse = content;
                            } else {
                                Log.e(TAG, "Finish reason 'stop', but content is null/empty.");
                                assistantResponse = "I'm sorry, I encountered an issue processing that."; // Fallback
                            }
                        } else {
                            Log.e(TAG, "Finish reason 'stop', but 'message' or 'content' missing.");
                            assistantResponse = "I'm sorry, I couldn't formulate a response."; // Fallback
                        }
                    } else if ("tool_calls".equals(finishReason)) {
                        Log.i(TAG, "Finish reason is 'tool_calls', checking for conversation_over tool...");
                        if (choice.has("message") && choice.getJSONObject("message").has("tool_calls")) {
                            JSONArray toolCalls = choice.getJSONObject("message").getJSONArray("tool_calls");
                            boolean toolFoundAndProcessed = false;
                            for (int i = 0; i < toolCalls.length(); i++) {
                                JSONObject toolCall = toolCalls.getJSONObject(i);
                                if (toolCall.has("function")) {
                                    JSONObject function = toolCall.getJSONObject("function");
                                    String toolName = function.optString("name");

                                    if ("conversation_over".equals(toolName)) {
                                        Log.i(TAG, "Tool call 'conversation_over' found.");
                                        String argumentsString = function.optString("arguments");
                                        if (!argumentsString.isEmpty()) {
                                            JSONObject arguments = new JSONObject(argumentsString); // Parse the arguments string
                                            if (arguments.has("message")) {
                                                assistantResponse = arguments.getString("message");
                                                conversationOverCalled = true;
                                                Log.i(TAG, "Tool call 'conversation_over' - Farewell message: " + assistantResponse);
                                                toolFoundAndProcessed = true;
                                                break;
                                            } else {
                                                Log.e(TAG, "No 'message' in 'conversation_over' tool call arguments.");
                                            }
                                        } else {
                                            Log.e(TAG, "No 'arguments' in 'conversation_over' tool call function.");
                                        }
                                    } else {
                                        Log.w(TAG, "Encountered a tool call, but it's not 'conversation_over': " + toolName);
                                    }
                                }
                            }
                            if (!toolFoundAndProcessed) {
                                Log.e(TAG, "Finish reason is 'tool_calls', but 'conversation_over' tool with 'message' not found or processed.");
                                assistantResponse = "I was trying to end the conversation but encountered an issue.";
                            }
                        } else {
                            Log.e(TAG, "Finish reason is 'tool_calls', but 'tool_calls' array is missing in message.");
                            assistantResponse = "I'm sorry, there was an error processing tool instructions.";
                        }
                    } else {
                        Log.w(TAG, "Unexpected or unhandled finish_reason: " + finishReason);
                        assistantResponse = "I'm not sure how to respond to that.";
                    }
                } else {
                    Log.e(TAG, "LLM response does not have 'choices' or it's empty.");
                    assistantResponse = "I'm sorry, I didn't receive a valid response.";
                }

                // Log what Temi is about to say BEFORE speaking and waiting
                talkerWrapper.addAssistantPrompt(assistantResponse);

                Log.i(TAG, "Temi says: " + assistantResponse);

                if (conversationOverCalled) {
                    TtsRequest ttsRequest = TtsRequest.create(assistantResponse, false);
                    temi.speak(ttsRequest);
                    Log.d(TAG, "Conversation_over was called, breaking loop.");
                    temi.finishConversation();
                    break;
                }

                // Listen for a response if conversation is not over
                this.userResponseFuture = new CompletableFuture<>();
                temi.askQuestion(assistantResponse);
                Log.d(TAG, "Waiting for user response...");
                this.userResponseFuture.get(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                Log.d(TAG, "User responded.");

            } catch (TimeoutException e) {
                Log.w(TAG, "Timeout waiting for user response in loop (turn " + turnCount + "). Ending conversation.");
                talkerWrapper.addUserPrompt("(no response)");
                break; // Exit the loop
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted waiting for turn completion in loop (turn " + turnCount + ").");
                Thread.currentThread().interrupt();
                if (this.userResponseFuture != null && !this.userResponseFuture.isDone()) {
                    this.userResponseFuture.completeExceptionally(e);
                }
                throw new RuntimeException("Interrupted during Temi turn cycle in conversation loop.", e);
            } catch (ExecutionException e) {
                Log.e(TAG, "Turn cycle execution failed in loop (turn " + turnCount + ", likely TTS error): " + e.getCause().getMessage());
                throw new RuntimeException("Temi turn cycle failed in conversation loop.", e.getCause());
            } catch (JSONException e) {
                Log.e(TAG, "JSONException parsing LLM response: " + e.getMessage());
                throw new RuntimeException("Failed to parse LLM response.", e);
            }
        }

        return buildConversationLog();
    }

    private String buildConversationLog() {
        String locationName = currentLocation != null ?
                currentLocation.substring(0, 1).toUpperCase() + currentLocation.substring(1) : "User";
        StringBuilder conversationLog = new StringBuilder("Temi had the following conversation:\n");
        JSONArray messages = talkerWrapper.getHistory();
        for (int i = 0; i < messages.length(); i++) {
            try {
                JSONObject msg = messages.getJSONObject(i);
                String role = msg.getString("role");
                String content = msg.optString("content", "").trim();
                if (content.isEmpty() && !role.equals("system")) continue;

                if (role.equals("assistant")) {
                    conversationLog.append("\t-Temi said: ").append(content).append("\n");
                } else if (role.equals("user")) {
                    if ("(no response)".equals(content)) {
                        conversationLog.append("\t-[").append(locationName).append(" did not respond.]\n");
                    } else {
                        conversationLog.append("\t-").append(locationName).append(" said: ").append(content).append("\n");
                    }
                } else if (role.equals("system") && content.contains("Max turns reached")) {
                    conversationLog.append("\t[System: ").append(content).append("]\n");
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing message for log: " + e.getMessage());
            }
        }
        String finalLog = conversationLog.toString();
        Log.i(TAG, "Conversation log:\n" + finalLog);
        return finalLog;
    }

    public void setUserReply(String reply) {
        if (userResponseFuture != null && !userResponseFuture.isDone()) {
            userResponseFuture.complete(reply);
            Log.d(TAG, "User reply set: " + reply);
            talkerWrapper.addUserPrompt(reply);
        } else if (userResponseFuture != null && userResponseFuture.isDone()) {
            Log.w(TAG, "setUserReply called, but future was already completed.");
        } else {
            Log.e(TAG, "setUserReply called, but userResponseFuture is null (possibly before TTS completion logic).");
        }
    }

    @Override
    public String getResult() {
        return "";
    }

    public String getMessage() {
        return temiMessage != null ? temiMessage : userMessage;
    }
}
package edu.uky.ai.havoc.actions;

/**
 * Callback interface to signal when an action has completed.
 */
public interface ActionCompletionListener {
    /**
     * Called when an action completes.
     *
     * @param actionName The name of the action that completed.
     * @param success    Whether the action was successful.
     * @param result     Optional result string to return to the LLM if success is true, or null if failure.
     */
    void onActionCompleted(String actionName, boolean success, String result);
}

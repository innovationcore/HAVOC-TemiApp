// ActionManager.java
package edu.uky.ai.roguetemi.actions;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manager class to execute actions received from the LLM.
 * Actions are executed on a background thread, and completion callbacks are posted to the main thread.
 */
public class ActionManager {

    private static final String TAG = "ActionManager";
    private final ActionCompletionListener completionListener;
    private volatile TemiAction currentAction; // currentAction is accessed from multiple threads

    // Single thread executor to ensure actions are executed one at a time in the order they are submitted
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();
    // Handler to post results back to the main thread
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public ActionManager(ActionCompletionListener completionListener) {
        this.completionListener = completionListener;
    }

    /**
     * Submits the given TemiAction for execution on a background thread.
     * The currentAction is set immediately. The action's execute() method will run asynchronously.
     *
     * @param action The action to execute.
     */
    public void executeAction(TemiAction action) {
        if (action == null) {
            Log.e(TAG, "Cannot execute a null action.");
            // Optionally, notify listener of this error if appropriate
            // completionListener.onActionCompleted("null_action", false, "Attempted to execute a null action.");
            return;
        }

        final String actionNameForLogging = action.getActionName() != null ? action.getActionName() : "Unnamed Action";
        Log.d(TAG, "Queuing action for execution: " + actionNameForLogging);

        // Set currentAction immediately. This reflects the action that is about to be or is being processed.
        this.currentAction = action;

        actionExecutor.submit(() -> {
            final String actionName = action.getActionName() != null ? action.getActionName() : "Unnamed Action";
            Log.i(TAG, "Starting execution of action: " + actionName + " on thread: " + Thread.currentThread().getName());

            // Create a wrapper for the completion listener to ensure it's called on the main thread
            // and to manage the currentAction state.
            ActionCompletionListener mainThreadAwareListener = (name, success, result) -> {
                mainThreadHandler.post(() -> {
                    Log.i(TAG, "Action completed: " + name + ", Success: " + success + ". Posting to main thread.");
                    // Only nullify currentAction if the completed action is indeed the one we marked as current.
                    // This prevents race conditions if actions were somehow completed out of order
                    // or if a new action was set before an old one's callback fires (less likely with single executor).
                    if (this.currentAction == action) {
                        this.currentAction = null;
                    }
                    completionListener.onActionCompleted(name, success, result);
                });
            };

            try {
                action.execute(mainThreadAwareListener);
            } catch (Exception e) {
                Log.e(TAG, "Unhandled exception during action.execute() for: " + actionName, e);
                // If action.execute() itself throws an unhandled exception,
                // ensure the listener is still called with a failure.
                final String errorMessage = "Unhandled exception in action " + actionName + ": " + e.getMessage();
                mainThreadHandler.post(() -> {
                    if (this.currentAction == action) {
                        this.currentAction = null;
                    }
                    completionListener.onActionCompleted(actionName, false, errorMessage);
                });
            }
        });
    }

    /**
     * Gets the action that is currently being executed or was last submitted for execution.
     * This can be null if no action is active or if the last action has completed and
     * its callback has cleared it.
     *
     * @return The current TemiAction, or null.
     */
    public TemiAction getCurrentAction() {
        return currentAction;
    }

    /**
     * Shuts down the action executor. This should be called when the ActionManager
     * is no longer needed (e.g., in Activity's onDestroy) to release resources.
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down ActionManager executor.");
        actionExecutor.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!actionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                actionExecutor.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!actionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.e(TAG, "Action executor did not terminate.");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            actionExecutor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
        Log.i(TAG, "ActionManager executor shutdown complete.");
    }
}
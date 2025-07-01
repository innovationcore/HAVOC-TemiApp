// TemiAction.java
package edu.uky.ai.havoc.actions;

import androidx.annotation.Nullable;

/**
 * Interface representing a generic action that Temi can execute.
 */
public interface TemiAction {
    /**
     * Returns the name of the action.
     */
    String getActionName();

    /**
     * Executes the action. When the action is complete,
     * the provided listener should be notified.
     *
     * @param listener Callback for completion.
     */
    void execute(ActionCompletionListener listener);

    default @Nullable String getResult() {
        return null;
    }
}

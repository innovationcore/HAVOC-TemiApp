package edu.uky.ai.havoc.actions;

import android.util.Log;

import androidx.annotation.NonNull;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;

import edu.uky.ai.havoc.MainActivity;


/**
 * An example action for moving Temi to a specific location.
 */
public class MoveAction implements TemiAction, OnGoToLocationStatusChangedListener {
    private static final String TAG = "MoveAction";
    private final String destination;
    private final Robot temi;
    private ActionCompletionListener completionListener;

    public MoveAction(String destination, Robot temi) {
        this.destination = destination;
        this.temi = temi;
    }

    @Override
    public String getActionName() {
        return "Move to " + destination;
    }

    @Override
    public void execute(ActionCompletionListener listener) {
        this.completionListener = listener;
        Log.d(TAG, "Executing move action to: " + destination);
        // Register this action as a listener to receive go-to-location status updates.
        temi.addOnGoToLocationStatusChangedListener(this);
        // Command Temi to move to the specified destination.
        temi.goTo(destination);

//        // Fake completion after 10 seconds for debugging
//        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//            if (completionListener != null) {
//                Log.d(TAG, "DEBUG: Triggering fake action complete for MoveAction");
//                completionListener.onActionCompleted(getActionName(), true, getResult());
//            }
//            temi.removeOnGoToLocationStatusChangedListener(this);
//            MainActivity.currentLocation = destination;
//        }, 10_000);
    }

    @Override
    public void onGoToLocationStatusChanged(@NonNull String goToLocation, @NonNull String status, int i, @NonNull String desc) {
        Log.d(TAG, "onGoToLocationStatusChanged: location=" + goToLocation + ", status=" + status + ", desc=" + desc);
        // Check that the callback is for our destination and that the status indicates completion.
        if (goToLocation.equals(destination) && status.equals("complete")) {
            MainActivity.currentLocation = goToLocation;
            if (completionListener != null) {
                completionListener.onActionCompleted(getActionName(), true, getResult());
            }
            // Remove this listener since the move action has completed.
            temi.removeOnGoToLocationStatusChangedListener(this);
        }
        else if (status.equals("abort")) {
            if (desc.equals("Abort by user")) {
                if (completionListener != null) {
                    completionListener.onActionCompleted(getActionName(), true, "Move action aborted by user.");
                }
                return;
            }
            // Temi is stuck
            temi.stopMovement();
            temi.repose();
            temi.turnBy(90);
            temi.skidJoy(1, 0, true);

        }
    }

    @Override
    public String getResult() {
        return "Temi moved to " + destination;
    }

    public String getDestination() {
        return destination;
    }

}

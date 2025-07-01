package edu.uky.ai.havoc.statemachine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.widget.ImageView;

import com.robotemi.sdk.Robot;

import java.util.List;

import edu.uky.ai.havoc.Config;
import edu.uky.ai.havoc.R;
import edu.uky.ai.havoc.streaming.WebRTCStreamingManager;

public class RogueTemiExtended extends RogueTemiCore {
    private static final String TAG = "TemiEscortExtended";
    private final Context context;
    private Robot temi;
    private AlertDialog dialog;
    private final ImageView faceImageView;
    private WebRTCStreamingManager streamingManager;

    // Constructor
    public RogueTemiExtended(Context context, Robot robot, ImageView faceImageView, WebRTCStreamingManager streamingManager) {
        super();  // Call the parent constructor
        this.context = context;
        this.temi = robot;
        this.faceImageView = faceImageView;
        this.streamingManager = streamingManager;
    }

    @Override
    public boolean timeToPatrol() {
        boolean wasEventProcessed = super.timeToPatrol();
        if (wasEventProcessed) {
            Log.d(TAG, "Transitioned to Patrol state.");
            onStateChanged(getState());
            List<String> patrolLocations = Config.getPatrolLocations();
            streamingManager.setShouldRecord(true);
            boolean success = temi.patrol(patrolLocations, false, 1, 5); // 1 round, 5 seconds wait
            if (!success) {
                Log.e(TAG, "Patrol command failed.");
                return false;
            }
        }
        return wasEventProcessed;
    }

    @Override
    public boolean emptyQueue() {
        boolean wasEventProcessed = super.emptyQueue();
        if (wasEventProcessed) {
            temi.goTo(Config.getWorkLocation());
        }
        return wasEventProcessed;
    }

    //    From here down is code to handle the face image changing when the state changes

    public void onStateChanged(State newState) {
        ((Activity) context).runOnUiThread(() -> {
            if (newState == State.Detecting) {
                faceImageView.setImageResource(R.drawable.detecting_face);  // Detecting state image
                streamingManager.startStreaming();
            } else if (newState == State.HomeBase) {
                faceImageView.setImageResource(R.drawable.homebase_face);  // Default face
                streamingManager.stopStreaming();
            } else {
                faceImageView.setImageResource(R.drawable.face);  // Default face
                streamingManager.startStreaming();
            }

        });
    }
    /* ----------------------
     * Face-Changing Transitions
     * ---------------------- */

    @Override
    public boolean arrivedAtEntrance() {
        boolean wasEventProcessed = super.arrivedAtEntrance();
        if (wasEventProcessed) {
            Log.d(TAG, "Transitioned to Detecting via arrivedAtEntrance");
            onStateChanged(getState());
        }
        return wasEventProcessed;
    }

    @Override
    public boolean patrolComplete(){
        boolean wasEventProcessed = super.patrolComplete();
        if (wasEventProcessed) {
            streamingManager.setShouldRecord(false);
            onStateChanged(getState());
        }
        return wasEventProcessed;
    }

    @Override
    public boolean personDetected() {
        boolean wasEventProcessed = super.personDetected();
        if (wasEventProcessed) {
            Log.d(TAG, "Transitioned from Detecting via personDetected");
            onStateChanged(getState());
        }
        return wasEventProcessed;
    }

    @Override
    public boolean timeBetween5pmAnd9am() {
        boolean wasEventProcessed = super.timeBetween5pmAnd9am();
        if (wasEventProcessed) {
            Log.d(TAG, "Transitioned from Detecting via timeBetween5pmAnd9am");
            onStateChanged(getState());
        }
        return wasEventProcessed;
    }
    @Override
    public boolean timeBetween9amAnd5pm() {
        boolean wasEventProcessed = super.timeBetween9amAnd5pm();
        if (wasEventProcessed) {
            Log.d(TAG, "Transitioned from Detecting via timeBetween9amAnd5pm");
            onStateChanged(getState());
        }
        return wasEventProcessed;
    }
    @Override
    public boolean arrivedAtHome() {
        boolean wasEventProcessed = super.arrivedAtHome();
        if (wasEventProcessed) {
            Log.d(TAG, "Transitioned from Detecting via arrivedAtHome");
            onStateChanged(getState());
        }
        return wasEventProcessed;
    }
}

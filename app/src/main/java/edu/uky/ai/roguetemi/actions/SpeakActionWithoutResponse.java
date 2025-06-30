package edu.uky.ai.roguetemi.actions;

import android.util.Log;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.Robot.TtsListener;

public class SpeakActionWithoutResponse implements TemiAction, TtsListener {
    private static final String TAG = "SpeakActionWithoutResponse";
    private final String message;
    private final Robot temi;
    private ActionCompletionListener completionListener;
    private boolean hasSpoken = false;

    public SpeakActionWithoutResponse(String message, Robot robot) {
        this.message = message;
        this.temi = robot;
    }

    @Override
    public String getActionName() {
        return "Speak: " + message;
    }

    @Override
    public void execute(ActionCompletionListener listener) {
        Log.d(TAG, "Executing SpeakActionWithoutResponse: " + message);
        this.completionListener = listener;
        temi.addTtsListener(this);
        TtsRequest ttsRequest = TtsRequest.create(message, false);
        temi.speak(ttsRequest);

        // Fake completion after 5 seconds for debugging
//        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//            if (completionListener != null) {
//                Log.d(TAG, "DEBUG: Triggering fake action complete for MoveAction");
//                completionListener.onActionCompleted(getActionName(), true, getResult());
//            }
//        }, 5_000);
    }

    @Override
    public void onTtsStatusChanged(TtsRequest ttsRequest) {
        if (ttsRequest.getStatus() == TtsRequest.Status.COMPLETED && !hasSpoken) {
            Log.d(TAG, "TTS complete.");
            temi.removeTtsListener(this);
            hasSpoken = true;
            if (completionListener != null) {
                completionListener.onActionCompleted(getActionName(), true, getResult());
            }
        }
    }

    @Override
    public String getResult() {
        return "Temi said \"" + message + "\"";
    }

    public String getMessage() {
        return message;
    }
}
//package edu.uky.ai.roguetemi.actions;
//
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//import com.robotemi.sdk.Robot;
//import com.robotemi.sdk.TtsRequest;
//import com.robotemi.sdk.listeners.OnMovementStatusChangedListener;
//
///**
// * Action causing Temi to spin 360 degrees in place.
// */
//public class SpinAction implements TemiAction, OnMovementStatusChangedListener {
//    private static final String TAG = "SpinAction";
//    private final Robot temi;
//    private ActionCompletionListener completionListener;
//    private boolean rotationStarted = false;
//
//    public SpinAction(Robot robot) {
//        this.temi = robot;
//    }
//
//    @Override
//    public String getActionName() {
//        return "Spin 360 degrees";
//    }
//
//    @Override
//    public void execute(ActionCompletionListener listener) {
//        Log.d(TAG, "Executing spin action.");
//
//        this.completionListener = listener;
//
//        // Register movement status listener
//        temi.addOnMovementStatusChangedListener(this);
//
//        // Start rotating Temi 720 degrees (2 full spins)
//        temi.turnBy(720);
//        rotationStarted = true;
//
//        // DEBUG fallback: trigger after timeout
////        new Handler(Looper.getMainLooper()).postDelayed(() -> {
////            if (completionListener != null) {
////                Log.d(TAG, "DEBUG: Triggering fallback spin completion.");
////                completionListener.onActionCompleted(getActionName(), true, getResult());
////            }
////            temi.removeOnMovementStatusChangedListener(this);
////        }, 5_000);
//    }
//
//    @Override
//    public void onMovementStatusChanged(String type, String status) {
//        Log.d(TAG, "Movement status changed: " + type + " - " + status);
//
//        if (rotationStarted && type.equals("turnBy") && status.equals("complete")) {
//            Log.d(TAG, "Spin action completed.");
//            if (completionListener != null) {
//                completionListener.onActionCompleted(getActionName(), true, getResult());
//            }
//            temi.removeOnMovementStatusChangedListener(this);
//        } else if (rotationStarted && status.equals("abort")) {
//            Log.e(TAG, "Spin action aborted.");
//            temi.speak(TtsRequest.create("I can't spin here. I'm giving up.", false));
//            if (completionListener != null) {
//                completionListener.onActionCompleted(getActionName(), false, null);
//            }
//            temi.removeOnMovementStatusChangedListener(this);
//        }
//    }
//
//    @Override
//    public String getResult() {
//        return "[Temi spun 360 degrees]";
//    }
//}

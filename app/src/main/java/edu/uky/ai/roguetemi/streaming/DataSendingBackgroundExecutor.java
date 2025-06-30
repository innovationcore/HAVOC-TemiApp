package edu.uky.ai.roguetemi.streaming;

import android.util.Log;

import com.robotemi.sdk.Robot;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataSendingBackgroundExecutor {
    private static final String TAG = "LocationBackgroundExecutor";
    private ScheduledExecutorService executorService;
    private final Robot temi;

    public DataSendingBackgroundExecutor(Robot temi) {
        this.temi = temi;
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void startTask() {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadScheduledExecutor();
        }
        try {
            executorService.scheduleWithFixedDelay(() -> {
                try {
                    WebRTCStreamingManager.sendData(temi.getPosition());
                } catch (Exception ex) {
                    // Handle exceptions to prevent task termination
                    Log.e(TAG, "Error during location sending", ex);
                }
            }, 0, 500, TimeUnit.MILLISECONDS); // Initial delay of 0 milliseconds, then 500 milliseconds after the task finishes
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopTask() {
        if (executorService != null && !executorService.isShutdown()) {
            Log.d(TAG, "Stopping continuous smelling...");
            try {
                executorService.shutdown();
            } catch (RejectedExecutionException ree) {
                Log.d(TAG, "Continuous smelling not running.");
            }

        }
    }
}

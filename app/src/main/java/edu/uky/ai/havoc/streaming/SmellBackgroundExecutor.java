package edu.uky.ai.havoc.streaming;

import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import edu.uky.ai.havoc.MainActivity;

public class SmellBackgroundExecutor {
    private static final String TAG = "SmellBackgroundExecutor";
    private ScheduledExecutorService executorService;

    public SmellBackgroundExecutor() {
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void startTask() {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadScheduledExecutor();
        }
        try {
            executorService.scheduleWithFixedDelay(() -> {
                try {
                    // Your background task logic goes here
//                    Log.d(TAG, "Smelling...");
                    MainActivity.smellSensor.readData();
                } catch (Exception ex) {
                    // Handle exceptions to prevent task termination
                    Log.e(TAG, "Error during sensor reading", ex);
                }
            }, 5, 3, TimeUnit.SECONDS); // Initial delay of 5 seconds, then 3 seconds after the task finishes
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

    public boolean isRunning() {
        return !executorService.isShutdown();
    }
}
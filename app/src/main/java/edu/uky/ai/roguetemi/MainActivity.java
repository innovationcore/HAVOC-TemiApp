package edu.uky.ai.roguetemi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.robotemi.sdk.BatteryData;
import com.robotemi.sdk.NlpResult;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.SttLanguage;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.activitystream.ActivityStreamPublishMessage;
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener;
import com.robotemi.sdk.listeners.OnDetectionDataChangedListener;
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener;
import com.robotemi.sdk.listeners.OnRobotReadyListener;
import com.robotemi.sdk.listeners.OnConversationStatusChangedListener;
import com.robotemi.sdk.model.DetectionData;
import com.robotemi.sdk.voice.WakeupOrigin;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;

import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.MediaStream;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.LinkedList;
import java.util.Queue;

import edu.uky.ai.roguetemi.actions.ActionCompletionListener;
import edu.uky.ai.roguetemi.actions.ActionManager;
import edu.uky.ai.roguetemi.actions.ConversationAction;
import edu.uky.ai.roguetemi.actions.TemiAction;
import edu.uky.ai.roguetemi.llm.Planner;
import edu.uky.ai.roguetemi.statemachine.RogueTemiCore;
import edu.uky.ai.roguetemi.statemachine.RogueTemiExtended;
import edu.uky.ai.roguetemi.streaming.DataSendingBackgroundExecutor;
import edu.uky.ai.roguetemi.streaming.SmellBackgroundExecutor;
import edu.uky.ai.roguetemi.streaming.SmellSensorUtils;
import edu.uky.ai.roguetemi.streaming.WebRTCStreamingManager;
//import edu.uky.ai.roguetemi.llm.Talker;

public class MainActivity extends AppCompatActivity implements
        Robot.AsrListener,
        Robot.NlpListener,
        OnRobotReadyListener,
        Robot.WakeupWordListener,
        Robot.ActivityStreamPublishListener,
        Robot.TtsListener,
        OnBeWithMeStatusChangedListener,
        OnDetectionStateChangedListener,
        OnDetectionDataChangedListener,
        OnConversationStatusChangedListener,
        OnGoToLocationStatusChangedListener,
        ActionCompletionListener {
    public static final String TAG = "MainActivity";
    public static String currentLocation;

    private static final long CHECK_INTERVAL = 30;
    private boolean isActionInProgress = false;
    private boolean isRobotReady = false;
    private WebRTCStreamingManager streamingManager;
    private EglBase eglBase;
    private long nextPatrolTime = 0;
    private boolean shouldStartPatrolWhenReady = false;

    private final ScheduledExecutorService timeChecker = Executors.newSingleThreadScheduledExecutor();
    private final Robot temi = Robot.getInstance();
    private RogueTemiExtended stateManager;
    private ActionManager actionManager;
    private final Queue<TemiAction> actionQueue = new LinkedList<>();
    private Planner planner;
    private String history = "";
    // Stores the timestamp of the last time Temi was asked to wake up
    private long lastWakeupTime = 0;

    private long detectionStartTime = 0;
    private static final long WAKEUP_COOLDOWN_MS = 15_000;
    private static final long DETECTION_THRESHOLD_MS = 1500; // 1.5 seconds

    //    private final BlockingQueue<String> userInputQueue = new LinkedBlockingQueue<>(); // Queue for user input
    public static final SmellSensorUtils smellSensor = new SmellSensorUtils();
    public static final SmellBackgroundExecutor smellBackgroundExecutor = new SmellBackgroundExecutor();
    public DataSendingBackgroundExecutor dataSendingBackgroundExecutor;
    private static final int PERMISSIONS_REQUEST_CODE = 1240;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        temi.requestToBeKioskApp();
        super.onCreate(savedInstanceState);
        Config.load(this);
        currentLocation = Config.getHomeLocation();
        
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
        }
        isRobotReady = false;

        // initialize temis face
        setContentView(R.layout.activity_main);
        ImageView face = findViewById(R.id.homebase_face);
        face.setImageResource(R.drawable.homebase_face);


        actionManager = new ActionManager(this);
        planner = new Planner(this, temi);
        eglBase = EglBase.create();
        streamingManager = new WebRTCStreamingManager(this, eglBase);

        streamingManager.setCallback(new WebRTCStreamingManager.WebRTCCallback() {
            @Override
            public void onConnectionEstablished() {
                Log.d(TAG, "WebRTC connection established");
            }

            @Override
            public void onConnectionFailed(String error) {
                Log.e(TAG, "WebRTC connection failed: " + error);
            }
            @Override
            public void onRemoteStreamAdded(MediaStream stream) {
                Log.d(TAG, "Remote stream received");
            }
        });

        stateManager = new RogueTemiExtended(this, temi, face, streamingManager);
//        Should work but not worried about it right now
//        CommandHttpServer httpServer = new CommandHttpServer(this);
//        try {
//            httpServer.start();
//            Log.d(TAG, "HTTP server started on port 8080.");
//        } catch (IOException e) {
//            Log.e(TAG, "Failed to start HTTP server", e);
//        }
        streamingManager.startStreaming();

    }

    @Override
    protected void onStart() {
        super.onStart();
        temi.addOnRobotReadyListener(this);
        temi.addNlpListener(this);
        temi.addOnBeWithMeStatusChangedListener(this);
        temi.addWakeupWordListener(this);
        temi.addTtsListener(this);
        temi.addOnDetectionStateChangedListener(this);
        temi.addOnDetectionDataChangedListener(this);
        temi.addAsrListener(this);
        temi.addOnConversationStatusChangedListener(this);
        temi.addOnGoToLocationStatusChangedListener(this);
        Log.d(TAG, "Listeners added.");
    }

    @Override
    protected void onStop() {
        super.onStop();
        temi.removeOnRobotReadyListener(this);
        temi.removeNlpListener(this);
        temi.removeOnBeWithMeStatusChangedListener(this);
        temi.removeWakeupWordListener(this);
        temi.removeTtsListener(this);
        temi.removeOnDetectionStateChangedListener(this);
        temi.removeOnDetectionDataChangedListener(this);
        temi.removeAsrListener(this);
        temi.removeOnConversationStatusChangedListener(this);
        temi.removeOnGoToLocationStatusChangedListener(this);
        temi.cancelAllTtsRequests();
        temi.stopMovement();
        timeChecker.shutdown();
        streamingManager.stopStreaming();
//        userInputQueue.clear(); // Clear queue on stop to prevent stale input
    }

    @Override
    public void onRobotReady(boolean isReady) {
        isRobotReady = isReady;
        if (isRobotReady) {
            Log.d(TAG, "Temi is ready.");
//            Log.d(TAG, "Temi position: " + temi.getPosition());
            temi.stopMovement();

            boolean isDetectionEnabled = temi.isDetectionModeOn();
            Log.d(TAG, "Detection feature enabled: " + isDetectionEnabled);
            if (!isDetectionEnabled) {
                temi.setDetectionModeOn(true);
                // Enable detection
                Log.d(TAG, "Detection has been enabled manually.");
            }

            startScheduledTimeChecker();
            if (!smellSensor.init(this)){
                Log.e(TAG, "Smell sensor initialization failed.");
            }
            nextPatrolTime = computeNextPatrolTime();

            dataSendingBackgroundExecutor = new DataSendingBackgroundExecutor(temi);
            dataSendingBackgroundExecutor.startTask();
        }
    }

    private void executeNextAction() {
        if (isActionInProgress || actionQueue.isEmpty()) {
            return;
        }

        TemiAction nextAction = actionQueue.poll();
        if (nextAction != null) {
            isActionInProgress = true;
            Log.d(TAG, "Executing action: " + nextAction.getActionName());
            actionManager.executeAction(nextAction);
        } else {
            Log.d(TAG, "No action to execute.");
        }
    }

    private int getBatteryData() {
        BatteryData batteryData = temi.getBatteryData();
        if (batteryData == null) {
            Log.d(TAG, "batteryData is null");
            return -1;
        }
        int batteryPercentage = batteryData.getBatteryPercentage();
        String message = batteryPercentage + " percent battery " +
                (batteryData.isCharging() ? "and charging." : "and not charging.");
        Log.d(TAG, message);
        return batteryPercentage;
    }

    private void startScheduledTimeChecker() {
        timeChecker.scheduleWithFixedDelay(this::checkAndMoveTemi, 10, CHECK_INTERVAL, TimeUnit.SECONDS);
    }
    private long computeNextPatrolTime() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.HOUR_OF_DAY, 1);

//        Calendar cal = Calendar.getInstance();
//        cal.add(Calendar.MINUTE, 1);



        // Keep advancing until it's a weekday between 8am and 5pm
        while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
                cal.get(Calendar.HOUR_OF_DAY) < 8 ||
                cal.get(Calendar.HOUR_OF_DAY) >= 17) {
            cal.add(Calendar.HOUR_OF_DAY, 1);
        }
        Log.d(TAG, "Next patrol time: " + cal.getTime());
        return cal.getTimeInMillis();
    }
    private void checkAndMoveTemi() {
        try {
            if (!isRobotReady) {
                Log.e(TAG, "ERROR: Temi is not ready yet! Skipping movement.");
                return;
            }
            if (stateManager.getState() != RogueTemiCore.State.HomeBase) {
                if (!streamingManager.connected) {
                    Log.d(TAG, "WebRTC not connected, reconnecting.");
                    streamingManager.restartConnection();
                }
            }
            if (stateManager.getState() != RogueTemiCore.State.Detecting && stateManager.getState() != RogueTemiCore.State.HomeBase) {
                Log.d(TAG, "Temi is not detecting or at home base, skipping time check. Current state: " + stateManager.getStateFullName());
                return;
            }

            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            int day = calendar.get(Calendar.DAY_OF_WEEK);
            if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) {
                Log.d(TAG, "It's the weekend, Temi is off duty.");
                return;
            }

            Log.d(TAG, "Checking time: " + hour + ":" + minute + ", Current State: " + stateManager.getStateFullName());
            if (hour >= 8 && hour < 17) {
                int batteryPercentage = getBatteryData();
                if (batteryPercentage == -1) {
                    Log.e(TAG, "Battery data is null, skipping movement.");
                    return;
                }

                long now = System.currentTimeMillis();

                // Handle on-the-hour patrol if we're in Detecting
                if (now >= nextPatrolTime) {
                    if (stateManager.getState() == RogueTemiCore.State.Detecting) {
                        Log.d(TAG, "Patrolling on the hour.");
                        boolean result = stateManager.timeToPatrol();
                        handleTransition(result, "Patrolling");
                        nextPatrolTime = computeNextPatrolTime();
                        shouldStartPatrolWhenReady = false;
                        return;
                    } else {
                        Log.d(TAG, "Missed patrol hour, will patrol when back to Detecting.");
                        shouldStartPatrolWhenReady = true;
                    }
                }

                // Catch up patrol if missed the hour and just became free
                if (shouldStartPatrolWhenReady && stateManager.getState() == RogueTemiCore.State.Detecting) {
                    Log.d(TAG, "Catching up on missed patrol.");
                    boolean result = stateManager.timeToPatrol();
                    handleTransition(result, "Patrolling");
                    nextPatrolTime = computeNextPatrolTime();
                    shouldStartPatrolWhenReady = false;
                    return;
                }

                if (stateManager.getState() == RogueTemiCore.State.HomeBase) {
                    if (batteryPercentage < 35) {
                        Log.d(TAG, "Battery is low, staying at home base.");
                        return;
                    }
                    Log.d(TAG, "Moving to: " + Config.getWorkLocation());
                    handleTransition(stateManager.timeBetween9amAnd5pm(), "MovingToWork");
                    temi.goTo(Config.getWorkLocation());
                }
                if (stateManager.getState() == RogueTemiCore.State.Detecting) {
                    if (batteryPercentage < 16) {
                        Log.d(TAG, "Battery is low, going to home base.");
                        //                  Not the original purpose of this transition but it works
                        handleTransition(stateManager.timeBetween5pmAnd9am(), "ReturningToHome");
                        temi.goTo(Config.getHomeLocation());
                    }
                }
            } else {
                if (stateManager.getState() == RogueTemiCore.State.Detecting) {
                    Log.d(TAG, "Moving to: " + Config.getHomeLocation());
                    handleTransition(stateManager.timeBetween5pmAnd9am(), "ReturningToHome");
                    temi.goTo(Config.getHomeLocation());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "ERROR IN CHECKANDMOVETEMI", e);
        }

//
    }

    @Override
    public void onDetectionDataChanged(@NonNull DetectionData detectionData) {
        if (stateManager.getState() != RogueTemiCore.State.Detecting) {
            return;
        }

        long currentTimeMs = System.currentTimeMillis();

        if (detectionData.isDetected()) {
            // This is a new detection event, so we record its start time.
            if (detectionStartTime == 0) {
                Log.d("TemiDetection", "User detection started.");
                detectionStartTime = currentTimeMs;
            }

            // 1. Check if the user has been present long enough (THE FIX)
            boolean isDetectionThresholdMet = (currentTimeMs - detectionStartTime) >= DETECTION_THRESHOLD_MS;

            // 2. Check if the cooldown period has elapsed since the last wakeup
            boolean isCooldownOver = (currentTimeMs - lastWakeupTime) >= WAKEUP_COOLDOWN_MS;

            if (isDetectionThresholdMet && isCooldownOver) {
                Log.d("TemiDetection", "User detected long enough. Cooldown elapsed. Waking up Temi.");
                temi.askQuestion("Hello! How can I help you?");

                // Update the last wakeup time to enforce the cooldown
                lastWakeupTime = currentTimeMs;

                // Reset detection start time so we don't ask again until they leave and come back
                detectionStartTime = 0;
            }
        } else {
            // User is not detected, so reset the start time for the next event.
            if (detectionStartTime != 0) {
                Log.d("TemiDetection", "User detection ended.");
                detectionStartTime = 0;
            }
        }
    }

    @Override
    public void onGoToLocationStatusChanged(@NonNull String goToLocation, @NonNull String status, int i, @NonNull String desc) {
        Log.d(TAG, "Navigation Update - Location: " + goToLocation + ", Status: " + status + ", Description: " + desc);
        if (isRobotReady && stateManager.getState() != RogueTemiCore.State.LlmControl) {
            if (status.equals("complete")) {
                currentLocation = goToLocation;
                if (stateManager.getState() == RogueTemiCore.State.Patrolling) {
                    if (goToLocation.equals(Config.getWorkLocation())) {
                        handleTransition(stateManager.patrolComplete(), "Detecting");
                    }
                }
                else {
                    Log.d("MainActivity", "Arrived at " + goToLocation);
                    if (goToLocation.equals(Config.getWorkLocation())) {
                        //                    temi.speak(TtsRequest.create("Let's get this bread.", false));
                        handleTransition(stateManager.arrivedAtEntrance(), "Detecting");
                    } else if (goToLocation.equals(Config.getHomeLocation())) {
                        temi.speak(TtsRequest.create("Clocking out.", false));
                        handleTransition(stateManager.arrivedAtHome(), "HomeBase");
                    }
                }

            } else if (status.equals("abort")) {
                // Retry if aborted, turn 90 degrees and move forward a bit
                temi.stopMovement();
                temi.repose();
                // Make temi turn a random direction between -90 and 90 degrees, doesn't work very well
//                Random rand = new Random();
//                int randomAngle = rand.nextInt(181) - 90;
//                temi.turnBy(randomAngle);
                temi.turnBy(90);
                temi.skidJoy(1, 0, true);
                temi.goTo(goToLocation);

            }
        }
    }

    /**
     * Handles user input, either from ASR or HTTP, and updates the plan accordingly.
     *
     * @param userInput The user's input string.
     */
    public void handleUserInput(String userInput) {
        runOnUiThread(() -> {
            Log.d(TAG, "Handling user input: " + userInput);
            temi.finishConversation();


            if (stateManager.getState() == RogueTemiCore.State.Detecting ) {
                handleTransition(stateManager.personDetected(), "LlmControl");
                // If no action is in progress, create a new conversation action
                ConversationAction action = new ConversationAction(null, userInput, currentLocation, temi, this);
                actionQueue.offer(action);
                executeNextAction();
                return;
            }

            if (isActionInProgress && actionManager.getCurrentAction() instanceof ConversationAction) {
                ConversationAction action = (ConversationAction) actionManager.getCurrentAction();
                action.setUserReply(userInput);
                return;
            }

            // If an action other than a speak with response action is in progress, ignore what the user said for now
            if (isActionInProgress){
                //todo: handle this case mo betta
                return;
            }
        });
    }

    @Override
    public void onAsrResult(@NonNull String result, @NonNull SttLanguage sttLanguage) {
        Log.d(TAG, "ASR Response: " + result);
        handleUserInput(result);
    }

    @Override
    public void onConversationStatusChanged(int status, String text) {
        Log.d(TAG, "Conversation status changed: " + status + ", " + text);
        if (status == 0) {
            Log.d(TAG, "No user interaction.");
        }
    }

    @Override
    public void onActionCompleted(String actionName, boolean success, String result) {
        isActionInProgress = false;

        if (!success) {
            Log.w(TAG, "Action failed: " + actionName);
            //todo: replan
            history = "";
            boolean transitionSuccess = stateManager.emptyQueue();
            handleTransition(transitionSuccess, "MovingToEntrance");
            return;
        }

        history += (history.isEmpty() ? "" : "\n") + result;

//        If we just executed the conversation action, forget the current action queue and replan
        if (actionName.contains("Conversation")) {
            Log.i(TAG, "Conversation action completed. Replanning.");
            actionQueue.clear();
            List<TemiAction> newPlan = planner.generatePlan(history);
            for (TemiAction newAction : newPlan) {
                actionQueue.offer(newAction);
            }
            executeNextAction();
            return;
        }

        if (actionQueue.isEmpty()) {
            Log.i(TAG, "Action queue is empty — clearing history.");
            history = "";
            boolean transitionSuccess = stateManager.emptyQueue();
            handleTransition(transitionSuccess, "MovingToEntrance");
        } else {
            executeNextAction();
        }
    }

    private void handleTransition(boolean transitionSuccess, String expectedState) {
        if (!transitionSuccess) {
            Log.e(TAG, "Transition to " + expectedState + " failed.");
        } else {
            Log.d(TAG, "Transitioned to " + expectedState + " successfully.");
        }
    }

    @Override
    public void onPublish(@NonNull ActivityStreamPublishMessage activityStreamPublishMessage) {
    }

    @Override
    public void onTtsStatusChanged(@NonNull TtsRequest ttsRequest) {
    }

    @Override
    public void onBeWithMeStatusChanged(@NonNull String s) {
    }

    @Override
    public void onDetectionStateChanged(int i) {
    }

    @Override
    public void onNlpCompleted(@NonNull NlpResult nlpResult) {
    }

    private void logQueueState() {
        Log.d(TAG, "Action queue size: " + actionQueue.size());
        for (TemiAction action : actionQueue) {
            Log.d(TAG, " - " + action.getActionName());
        }
    }

    @Override
    public void onWakeupWord(@NonNull String s, int i, @NonNull WakeupOrigin wakeupOrigin) {

    }
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission is not granted: " + permission);
                return false;
            }
        }
        Log.d(TAG, "All permissions are granted.");
        return true;
    }

    private String[] getRequiredPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(permission);
            }
        }
        return permissions.toArray(new String[0]);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                Log.i(TAG, "Permissions granted by user.");
                // You might want to re-initialize components that depend on permissions here
                // if they failed previously.
            } else {
                Log.e(TAG, "Permissions not granted by user.");
                // Handle the case where the user denies permissions.
                // You might want to show a message and close the app.
            }
        }
    }
}
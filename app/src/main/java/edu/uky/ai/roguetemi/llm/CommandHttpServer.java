package edu.uky.ai.roguetemi.llm;

import android.util.Log;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import edu.uky.ai.roguetemi.MainActivity;
import fi.iki.elonen.NanoHTTPD;

public class CommandHttpServer extends NanoHTTPD {

    private final MainActivity mainActivity;
    private static final String TAG = "CommandHttpServer";

    public CommandHttpServer(MainActivity activity) {
        super("0.0.0.0", 8080);
        this.mainActivity = activity;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (Method.POST.equals(session.getMethod()) && "/command".equals(session.getUri())) {
            try {
                Map<String, String> body = new HashMap<>();
                session.parseBody(body);
                String json = body.get("postData");
                JSONObject obj = new JSONObject(json);
                String command = obj.getString("command");

                Log.d(TAG, "Received command: " + command);

                // ---- START DIAGNOSTIC CODE ----
                if (this.mainActivity == null) {
                    Log.e(TAG, "CRITICAL: mainActivity instance is NULL in CommandHttpServer!");
                } else {
                    Log.d(TAG, "mainActivity instance is NOT NULL. Attempting to call handleUserInput.");
                }
                // ---- END DIAGNOSTIC CODE ----

                // Delegate to MainActivity's handleUserInput
                mainActivity.handleUserInput(command);

                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"received\"}");

            } catch (Exception e) {
                Log.e(TAG, "Error processing command: " + e.getMessage());
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Endpoint not found.");
    }
}
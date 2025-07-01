package edu.uky.ai.havoc.streaming;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.robotemi.sdk.navigation.model.Position;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import edu.uky.ai.havoc.MainActivity;
import edu.uky.ai.havoc.Config;

public class WebRTCStreamingManager {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Context context;
    private final EglBase eglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private CameraVideoCapturer videoCapturer;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private SurfaceTextureHelper surfaceTextureHelper;
    private final OkHttpClient httpClient = new OkHttpClient();
    public boolean connected = false;
    private static DataChannel dataChannel;
    private static boolean shouldRecord = false;
    private static String smellData = null;
    private static boolean newSmellData = false;

    public interface WebRTCCallback {
        void onConnectionEstablished();
        void onConnectionFailed(String error);
        void onRemoteStreamAdded(MediaStream stream);
    }

    private WebRTCCallback callback;

    public WebRTCStreamingManager(Context context, EglBase eglBase) {
        this.context = context;
        this.eglBase = eglBase;
        initializePeerConnectionFactory();
    }

    public void setCallback(WebRTCCallback callback) {
        this.callback = callback;
    }

    private void initializePeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setInjectableLogger((s, severity, s1) -> {}, Logging.Severity.LS_WARNING)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(new SoftwareVideoEncoderFactory())
                .setVideoDecoderFactory(new SoftwareVideoDecoderFactory())
                .createPeerConnectionFactory();
    }

    public void initializeLocalVideo() {
        Camera2Enumerator camera2Enumerator = new Camera2Enumerator(context);
        String[] deviceNames = camera2Enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (camera2Enumerator.isFrontFacing(deviceName)) {
                videoCapturer = camera2Enumerator.createCapturer(deviceName, null);
                break;
            }
        }

        if (videoCapturer == null) {
            if (callback != null) callback.onConnectionFailed("Failed to find camera");
            connected = false;
            return;
        }

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
        VideoSource videoSource = peerConnectionFactory.createVideoSource(false);
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        localVideoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource);
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        // localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource);
    }

    public void initializePeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                        if (callback != null) callback.onConnectionEstablished();
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED ||
                            iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED ||
                            iceConnectionState == PeerConnection.IceConnectionState.CLOSED) {
                        connected = false;
                        if (callback != null) callback.onConnectionFailed("ICE connection failed or lost");
                    }
                });
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {}

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

            @Override
            public void onAddStream(MediaStream mediaStream) {
                if (callback != null) callback.onRemoteStreamAdded(mediaStream);
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {}

            @Override
            public void onDataChannel(org.webrtc.DataChannel dataChannel) {}

            @Override
            public void onRenegotiationNeeded() {}
        });

        if (peerConnection != null) {
            if (localVideoTrack != null) {
                peerConnection.addTrack(localVideoTrack);
            }
            DataChannel.Init dcInit = new DataChannel.Init();
            dataChannel = peerConnection.createDataChannel("smell_position_data", dcInit);
            dataChannel.registerObserver(new DataChannel.Observer() {
                @Override
                public void onBufferedAmountChange(long previousAmount) {}

                @Override
                public void onStateChange() {}

                @Override
                public void onMessage(DataChannel.Buffer buffer) {}
            });
        }
    }

    public void setShouldRecord(boolean value) {
        shouldRecord = value;
    }

    public static void updateSmellData(String data) {
        smellData = data;
        newSmellData = true;
    }

    public static void sendData(Position position) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            try {
                JSONObject jsonObject = new JSONObject();
                if (newSmellData) {
                    String data = smellData;
                    if (data.startsWith("Data: ")) {
                        data = data.substring(6).trim();
                    }
                    String[] parts = data.split(";");
                    if (parts.length > 0) {
                        jsonObject.put("header", parts[0]);
                        JSONArray valuesArray = new JSONArray();
                        for (int i = 1; i < parts.length; i++) {
                            try {
                                valuesArray.put(Double.parseDouble(parts[i]));
                            } catch (NumberFormatException e) {
                                valuesArray.put(parts[i]);
                            }
                        }
                        jsonObject.put("values", valuesArray);
                    } else {
                        jsonObject.put("data", data);
                    }
                    jsonObject.put("should_record", shouldRecord);
                    newSmellData = false;
                }

                JSONObject positionObject = new JSONObject();
                positionObject.put("x", position.getX());
                positionObject.put("y", position.getY());
                jsonObject.put("current_position", positionObject);

                ByteBuffer buffer = ByteBuffer.wrap(jsonObject.toString().getBytes(StandardCharsets.UTF_8));
                dataChannel.send(new DataChannel.Buffer(buffer, false));
            } catch (Exception e) {
                // Error creating or sending JSON data.
            }
        }
    }

    public void createOffer() {
        if (peerConnection == null) return;

        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {
                        sendOfferToServer(peerConnection.getLocalDescription());
                    }

                    @Override
                    public void onCreateFailure(String s) {}

                    @Override
                    public void onSetFailure(String s) {
                        if (callback != null) callback.onConnectionFailed("Failed to set local description");
                        connected = false;
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String s) {
                if (callback != null) callback.onConnectionFailed("Failed to create offer");
                connected = false;
            }

            @Override
            public void onSetFailure(String s) {}
        }, constraints);
    }

    private void sendOfferToServer(SessionDescription offer) {
        try {
            JSONObject json = new JSONObject();
            json.put("sdp", offer.description);
            json.put("type", offer.type.canonicalForm());

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder().url(Config.getWebRtcServerUrl()).post(body).build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (callback != null) callback.onConnectionFailed("Failed to connect to server: " + e.getMessage());
                    connected = false;
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        if (callback != null) callback.onConnectionFailed("Server error: " + response.code());
                        connected = false;
                        response.close();
                        return;
                    }

                    try {
                        String responseBody = response.body().string();
                        JSONObject answerJson = new JSONObject(responseBody);
                        SessionDescription answer = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(answerJson.getString("type")),
                                answerJson.getString("sdp")
                        );

                        peerConnection.setRemoteDescription(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {}

                            @Override
                            public void onSetSuccess() {}

                            @Override
                            public void onCreateFailure(String s) {}

                            @Override
                            public void onSetFailure(String s) {
                                if (callback != null) callback.onConnectionFailed("Failed to set remote description");
                                connected = false;
                            }
                        }, answer);
                    } catch (Exception e) {
                        if (callback != null) callback.onConnectionFailed("Error parsing server response");
                        connected = false;
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Exception e) {
            if (callback != null) callback.onConnectionFailed("Error creating offer JSON");
        }
    }

    public void startStreaming() {
        if (!connected) {
            restartConnection();
        } else {
            if (videoCapturer != null) {
                try {
                    videoCapturer.startCapture(640, 480, 20);
                } catch (Exception e) {
                    // Failed to start video capture.
                }
            }
        }
    }

    public void stopStreaming() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (Exception e) {
                // Failed to stop video capture.
            }
        }
    }

    public void restartConnection() {
        cleanup();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            connected = true;
            initializePeerConnectionFactory();
            initializeLocalVideo();
            initializePeerConnection();
            if (videoCapturer != null) {
                videoCapturer.startCapture(640, 480, 20);
            }
            MainActivity.smellBackgroundExecutor.startTask();
            createOffer();
        }, 1000);
    }

    public void cleanup() {
        stopStreaming();
        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }
        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }
        if (videoCapturer != null) {
            videoCapturer.dispose();
            videoCapturer = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (dataChannel != null) {
            try {
                dataChannel.unregisterObserver();
                dataChannel.close();
                dataChannel.dispose();
            } catch (Exception e) {
                // Exception during DataChannel cleanup.
            }
            dataChannel = null;
        }
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
        MainActivity.smellBackgroundExecutor.stopTask();
        connected = false;
    }
}
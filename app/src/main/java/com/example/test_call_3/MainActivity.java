package com.example.test_call_3;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.content.Context;
import com.azure.android.communication.calling.CallState;
import com.azure.android.communication.calling.CallingCommunicationException;
import com.azure.android.communication.calling.ParticipantsUpdatedListener;
import com.azure.android.communication.calling.PropertyChangedEvent;
import com.azure.android.communication.calling.PropertyChangedListener;
import com.azure.android.communication.calling.StartCallOptions;
import com.azure.android.communication.calling.VideoDeviceInfo;
import com.azure.android.communication.common.CommunicationIdentifier;
import com.azure.android.communication.common.CommunicationTokenCredential;
import com.azure.android.communication.calling.CallAgent;
import com.azure.android.communication.calling.CallClient;
import com.azure.android.communication.calling.DeviceManager;
import com.azure.android.communication.calling.VideoOptions;
import com.azure.android.communication.calling.LocalVideoStream;
import com.azure.android.communication.calling.VideoStreamRenderer;
import com.azure.android.communication.calling.VideoStreamRendererView;
import com.azure.android.communication.calling.CreateViewOptions;
import com.azure.android.communication.calling.ScalingMode;
import com.azure.android.communication.calling.IncomingCall;
import com.azure.android.communication.calling.Call;
import com.azure.android.communication.calling.AcceptCallOptions;
import com.azure.android.communication.calling.ParticipantsUpdatedEvent;
import com.azure.android.communication.calling.RemoteParticipant;
import com.azure.android.communication.calling.RemoteVideoStream;
import com.azure.android.communication.calling.RemoteVideoStreamsEvent;
import com.azure.android.communication.calling.RendererListener;
import com.azure.android.communication.common.CommunicationUserIdentifier;
import com.azure.android.communication.common.MicrosoftTeamsUserIdentifier;
import com.azure.android.communication.common.PhoneNumberIdentifier;
import com.azure.android.communication.common.UnknownIdentifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private CallAgent callAgent;
    private VideoDeviceInfo currentCamera;
    private LocalVideoStream currentVideoStream;
    private DeviceManager deviceManager;
    private IncomingCall incomingCall;
    private Call call;
    VideoStreamRenderer previewRenderer;
    VideoStreamRendererView preview;
    final Map<Integer, StreamData> streamData = new HashMap<>();
    private boolean renderRemoteVideo = true;
    private ParticipantsUpdatedListener remoteParticipantUpdatedListener;
    private PropertyChangedListener onStateChangedListener;

    final HashSet<String> joinedParticipants = new HashSet<>();

    Button switchSourceButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getAllPermissions();
        createAgent();

        handleIncomingCall();

        Button callButton = findViewById(R.id.call_button);
        callButton.setOnClickListener(l -> startCall());

        Button hangupButton = findViewById(R.id.hang_up);
        hangupButton.setOnClickListener(l -> hangUp());

        Button startVideo = findViewById(R.id.show_preview);
        startVideo.setOnClickListener(l -> turnOnLocalVideo());

        Button stopVideo = findViewById(R.id.hide_preview);
        stopVideo.setOnClickListener(l -> turnOffLocalVideo());

        switchSourceButton = findViewById(R.id.switch_source);
        switchSourceButton.setOnClickListener(l -> switchSource());

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    private void getAllPermissions() {
        String[] requiredPermissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE};
        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToAskFor.add(permission);
            }
        }
        if (!permissionsToAskFor.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToAskFor.toArray(new String[0]), 1);
        }
    }

    private void createAgent() {
        String userToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEwNiIsIng1dCI6Im9QMWFxQnlfR3hZU3pSaXhuQ25zdE5PU2p2cyIsInR5cCI6IkpXVCJ9.eyJza3lwZWlkIjoiYWNzOmQ2MWM2MDcwLTY3YjItNDAzMC1iZmMxLTUyYjY4ZDg0N2Q0ZF8wMDAwMDAxMy0wNzVmLTRmNmMtZDA3MS1jOTNhMGQwMGY1MGMiLCJzY3AiOjE3OTIsImNzaSI6IjE2NTk1OTM2NjUiLCJleHAiOjE2NTk2ODAwNjUsImFjc1Njb3BlIjoidm9pcCIsInJlc291cmNlSWQiOiJkNjFjNjA3MC02N2IyLTQwMzAtYmZjMS01MmI2OGQ4NDdkNGQiLCJpYXQiOjE2NTk1OTM2NjV9.q2Qrq1JGo2ojna_ao8nvGD-WYZ0oNt1CWTpieNIBqjWmFuih-JNk1ybhBOGfPp3O1xeOzwKVv9MWCdePdYy9Vq-x1KcVwkYsLPOtS4gbuwXqBrmDNMJRzn3SQLCTAubXTbnEcPRQW-sczKKCItNke2JEAjf4MHqdNz_BVd84UJMv9uAlZ7BRkhJDQ6Jo8aU71KZhFCVkqdKQBlq91k-aoFoMVihN74thkeTynkF8fS60C5lMFEo06spJbPaAt-xY1md11vpATSbE3QjTzQIx7UMsbmOL05-brOiOZ4Ju94nFhnVVW3O1sQO-CVbxmOdp_FvUP10y1S9xweRRBVruGg";
        Context context = this.getApplicationContext();

        try {
            CommunicationTokenCredential credential = new CommunicationTokenCredential(userToken);
            CallClient callClient = new CallClient();
            DeviceManager deviceManager = callClient.getDeviceManager(context).get();
            callAgent = callClient.createCallAgent(getApplicationContext(), credential).get();
        }
        catch (Exception ex){
            Toast.makeText(context, "Failed to create call agent.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCall() {
        Context context = this.getApplicationContext();
        EditText calleeIdView = findViewById(R.id.call_id);
        String calleeId = calleeIdView.getText().toString();
        ArrayList<CommunicationIdentifier> participants = new ArrayList<CommunicationIdentifier>();
        List<VideoDeviceInfo> cameras = deviceManager.getCameras();

        StartCallOptions options = new StartCallOptions();

        if(!cameras.isEmpty()) {
            currentCamera = getNextAvailableCamera(null);
            currentVideoStream = new LocalVideoStream(currentCamera, context);
            LocalVideoStream[] videoStreams = new LocalVideoStream[1];
            videoStreams[0] = currentVideoStream;
            VideoOptions videoOptions = new VideoOptions(videoStreams);
            options.setVideoOptions(videoOptions);
            showPreview(currentVideoStream);
        }

        participants.add(new CommunicationUserIdentifier(calleeId));

        call = callAgent.startCall(context, participants, options);

        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleCallOnStateChanged;
        call.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        call.addOnStateChangedListener(onStateChangedListener);
    }

    private VideoDeviceInfo getNextAvailableCamera(VideoDeviceInfo camera) {
        List<VideoDeviceInfo> cameras = deviceManager.getCameras();
        int currentIndex = 0;
        if (camera == null) {
            return cameras.isEmpty() ? null : cameras.get(0);
        }

        for (int i = 0; i < cameras.size(); i++) {
            if (camera.getId().equals(cameras.get(i).getId())) {
                currentIndex = i;
                break;
            }
        }
        int newIndex = (currentIndex + 1) % cameras.size();
        return cameras.get(newIndex);
    }

    private void showPreview(LocalVideoStream stream) {
        previewRenderer = new VideoStreamRenderer(stream, this);
        LinearLayout layout = findViewById(R.id.localvideocontainer);
        preview = previewRenderer.createView(new CreateViewOptions(ScalingMode.FIT));
        preview.setTag(0);
        runOnUiThread(() -> {
            layout.addView(preview);
            switchSourceButton.setVisibility(View.VISIBLE);
        });
    }

    public void switchSource() {
        if (currentVideoStream != null) {
            try {
                currentCamera = getNextAvailableCamera(currentCamera);
                currentVideoStream.switchSource(currentCamera).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleIncomingCall() {
        callAgent.addOnIncomingCallListener((incomingCall) -> {
            this.incomingCall = incomingCall;
            Executors.newCachedThreadPool().submit(this::answerIncomingCall);
        });
    }

    private void answerIncomingCall() {
        Context context = this.getApplicationContext();
        if(incomingCall == null){
            return;
        }
        AcceptCallOptions acceptCallOptions = new AcceptCallOptions();

        List<VideoDeviceInfo> cameras = deviceManager.getCameras();
        if(!cameras.isEmpty()) {
            currentCamera = getNextAvailableCamera(null);
            currentVideoStream = new LocalVideoStream(currentCamera, context);
            LocalVideoStream[] videoStreams = new LocalVideoStream[1];
            videoStreams[0] = currentVideoStream;
            VideoOptions videoOptions = new VideoOptions(videoStreams);
            acceptCallOptions.setVideoOptions(videoOptions);
            showPreview(currentVideoStream);
        }

        try {
            call = incomingCall.accept(context, acceptCallOptions).get();
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }

        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleCallOnStateChanged;
        call.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        call.addOnStateChangedListener(onStateChangedListener);
    }

    public void handleRemoteParticipantsUpdate(ParticipantsUpdatedEvent args) {
        handleAddedParticipants(args.getAddedParticipants());
        handleRemovedParticipants(args.getRemovedParticipants());
    }

    private void handleAddedParticipants(List<RemoteParticipant> participants) {

        for (RemoteParticipant remoteParticipant : participants) {
            if(!joinedParticipants.contains(getId(remoteParticipant))) {
                joinedParticipants.add(getId(remoteParticipant));

                if (renderRemoteVideo) {
                    for (RemoteVideoStream stream : remoteParticipant.getVideoStreams()) {
                        StreamData data = new StreamData(stream, null, null);
                        streamData.put(stream.getId(), data);
                        startRenderingVideo(data);
                    }
                }
                remoteParticipant.addOnVideoStreamsUpdatedListener(videoStreamsEventArgs -> videoStreamsUpdated(videoStreamsEventArgs));
            }
        }
    }

    private void handleRemovedParticipants(List<RemoteParticipant> participants) {

        for (RemoteParticipant remoteParticipant : participants) {
            if(joinedParticipants.contains(getId(remoteParticipant))) {
                joinedParticipants.remove(getId(remoteParticipant));
            }
        }
    }

    private void videoStreamsUpdated(RemoteVideoStreamsEvent videoStreamsEventArgs) {
        for(RemoteVideoStream stream : videoStreamsEventArgs.getAddedRemoteVideoStreams()) {
            StreamData data = new StreamData(stream, null, null);
            streamData.put(stream.getId(), data);
            if (renderRemoteVideo) {
                startRenderingVideo(data);
            }
        }

        for(RemoteVideoStream stream : videoStreamsEventArgs.getRemovedRemoteVideoStreams()) {
            stopRenderingVideo(stream);
        }
    }

    public String getId(final RemoteParticipant remoteParticipant) {
        final CommunicationIdentifier identifier = remoteParticipant.getIdentifier();
        if (identifier instanceof PhoneNumberIdentifier) {
            return ((PhoneNumberIdentifier) identifier).getPhoneNumber();
        } else if (identifier instanceof MicrosoftTeamsUserIdentifier) {
            return ((MicrosoftTeamsUserIdentifier) identifier).getUserId();
        } else if (identifier instanceof CommunicationUserIdentifier) {
            return ((CommunicationUserIdentifier) identifier).getId();
        } else {
            return ((UnknownIdentifier) identifier).getId();
        }
    }

    void startRenderingVideo(StreamData data){
        if (data.renderer != null) {
            return;
        }
        GridLayout layout = ((GridLayout)findViewById(R.id.remotevideocontainer));
        data.renderer = new VideoStreamRenderer(data.stream, this);
        data.renderer.addRendererListener(new RendererListener() {
            @Override
            public void onFirstFrameRendered() {
                String text = data.renderer.getSize().toString();
                Log.i("MainActivity", "Video rendering at: " + text);
            }

            @Override
            public void onRendererFailedToStart() {
                String text = "Video failed to render";
                Log.i("MainActivity", text);
            }
        });
        data.rendererView = data.renderer.createView(new CreateViewOptions(ScalingMode.FIT));
        data.rendererView.setTag(data.stream.getId());
        runOnUiThread(() -> {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(layout.getLayoutParams());
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            params.height = (int)(displayMetrics.heightPixels / 2.5);
            params.width = displayMetrics.widthPixels / 2;
            layout.addView(data.rendererView, params);
        });
    }

    void stopRenderingVideo(RemoteVideoStream stream) {
        StreamData data = streamData.get(stream.getId());
        if (data == null || data.renderer == null) {
            return;
        }
        runOnUiThread(() -> {
            GridLayout layout = findViewById(R.id.remotevideocontainer);
            for(int i = 0; i < layout.getChildCount(); ++ i) {
                View childView =  layout.getChildAt(i);
                if ((int)childView.getTag() == data.stream.getId()) {
                    layout.removeViewAt(i);
                }
            }
        });
        data.rendererView = null;
        // Dispose renderer
        data.renderer.dispose();
        data.renderer = null;
    }

    static class StreamData {
        RemoteVideoStream stream;
        VideoStreamRenderer renderer;
        VideoStreamRendererView rendererView;
        StreamData(RemoteVideoStream stream, VideoStreamRenderer renderer, VideoStreamRendererView rendererView) {
            this.stream = stream;
            this.renderer = renderer;
            this.rendererView = rendererView;
        }
    }

    private void handleCallOnStateChanged(PropertyChangedEvent args) {
        if (call.getState() == CallState.CONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is CONNECTED", Toast.LENGTH_SHORT).show());
            handleCallState();
        }
        if (call.getState() == CallState.DISCONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is DISCONNECTED", Toast.LENGTH_SHORT).show());
            if (previewRenderer != null) {
                previewRenderer.dispose();
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
        }
    }

    private void handleCallState() {
        handleAddedParticipants(call.getRemoteParticipants());
    }

    private void hangUp() {
        try {
            call.hangUp().get();
            switchSourceButton.setVisibility(View.INVISIBLE);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        if (previewRenderer != null) {
            previewRenderer.dispose();
        }
    }

    public void turnOnLocalVideo() {
        List<VideoDeviceInfo> cameras = deviceManager.getCameras();
        if(!cameras.isEmpty()) {
            try {
                currentVideoStream = new LocalVideoStream(currentCamera, this);
                showPreview(currentVideoStream);
                call.startVideo(this, currentVideoStream).get();
                switchSourceButton.setVisibility(View.VISIBLE);
            } catch (CallingCommunicationException acsException) {
                acsException.printStackTrace();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void turnOffLocalVideo() {
        try {
            LinearLayout container = findViewById(R.id.localvideocontainer);
            for (int i = 0; i < container.getChildCount(); ++i) {
                Object tag = container.getChildAt(i).getTag();
                if (tag != null && (int)tag == 0) {
                    container.removeViewAt(i);
                }
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
            previewRenderer.dispose();
            previewRenderer = null;
            call.stopVideo(this, currentVideoStream).get();
        } catch (CallingCommunicationException acsException) {
            acsException.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
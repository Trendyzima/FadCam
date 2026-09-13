package com.fadcam.pip;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reaction/commentary workspace: a source video plays in PIP while the live
 * front camera is recorded with microphone audio. On stop, the two recordings
 * are composited into one MP4 and saved to Movies/FadCam.
 */
public class CameraVideoPipActivity extends AppCompatActivity {
    private static final int PICK_VIDEO = 4001;
    private static final int REQUEST_CAMERA_AUDIO = 4002;

    private PreviewView cameraPreview;
    private PlayerView videoView;
    private ExoPlayer player;
    private Uri sourceUri;
    private File sourceFile;
    private File cameraFile;
    private Recording recording;
    private VideoCapture<Recorder> videoCapture;
    private Button commentaryButton;
    private TextView status;
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (hasCameraAudioPermission()) startCamera();
        else ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA_AUDIO);
    }

    private boolean hasCameraAudioPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        cameraPreview = new PreviewView(this);
        cameraPreview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(cameraPreview, new FrameLayout.LayoutParams(-1, -1));

        videoView = new PlayerView(this);
        videoView.setUseController(true);
        videoView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        videoView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        FrameLayout.LayoutParams pip = new FrameLayout.LayoutParams(dp(190), dp(320), Gravity.TOP | Gravity.END);
        pip.setMargins(0, dp(64), dp(12), 0);
        root.addView(videoView, pip);

        status = new TextView(this);
        status.setText("Load a video, then start commentary");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(13);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        status.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sp.setMargins(dp(12), dp(12), dp(12), 0);
        root.addView(status, sp);

        Button load = new Button(this);
        load.setText("LOAD VIDEO");
        load.setOnClickListener(v -> chooseVideo());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dp(52), Gravity.BOTTOM | Gravity.START);
        lp.setMargins(dp(12), 0, 0, dp(12));
        root.addView(load, lp);

        commentaryButton = new Button(this);
        commentaryButton.setText("START COMMENTARY");
        commentaryButton.setOnClickListener(v -> toggleCommentary());
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-2, dp(52), Gravity.BOTTOM | Gravity.END);
        rp.setMargins(0, 0, dp(12), dp(12));
        root.addView(commentaryButton, rp);

        setContentView(root);
    }

    private void chooseVideo() {
        if (recording != null) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_VIDEO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_VIDEO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        sourceUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        try {
            sourceFile = copySourceToCache(sourceUri);
            play(sourceUri);
            status.setText("Source loaded — position the video and press START COMMENTARY");
        } catch (Exception e) {
            sourceUri = null;
            Toast.makeText(this, "Could not read selected video", Toast.LENGTH_LONG).show();
        }
    }

    private File copySourceToCache(Uri uri) throws Exception {
        File file = new File(getCacheDir(), "pip_source_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(file)) {
            if (in == null) throw new IllegalStateException("No input stream");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        return file;
    }

    private void play(Uri uri) {
        if (player != null) player.release();
        player = new ExoPlayer.Builder(this).build();
        videoView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.seekTo(0);
        player.playWhenReady = false;
    }

    private void toggleCommentary() {
        if (recording != null) {
            stopCommentary();
            return;
        }
        if (sourceFile == null || !sourceFile.exists()) {
            Toast.makeText(this, "Load a video first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasCameraAudioPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA_AUDIO);
            return;
        }
        if (videoCapture == null) {
            Toast.makeText(this, "Camera is still starting", Toast.LENGTH_SHORT).show();
            return;
        }

        cameraFile = new File(getCacheDir(), "pip_camera_" + System.currentTimeMillis() + ".mp4");
        FileOutputOptions output = new FileOutputOptions.Builder(cameraFile).build();
        PendingRecording pending = videoCapture.getOutput().prepareRecording(this, output).withAudioEnabled();
        recording = pending.start(ContextCompat.getMainExecutor(this), this::onVideoRecordEvent);
        player.seekTo(0);
        player.play();
        commentaryButton.setText("STOP & SAVE");
        status.setText("● Recording commentary — source video + live camera + microphone");
    }

    private void stopCommentary() {
        if (recording != null) {
            recording.stop();
            recording = null;
            commentaryButton.setEnabled(false);
            commentaryButton.setText("PROCESSING…");
            status.setText("Finalizing recording…");
        }
    }

    private void onVideoRecordEvent(VideoRecordEvent event) {
        if (!(event instanceof VideoRecordEvent.Finalize)) return;
        VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;
        if (finalize.hasError()) {
            runOnUiThread(() -> {
                commentaryButton.setEnabled(true);
                commentaryButton.setText("START COMMENTARY");
                status.setText("Recording failed");
                Toast.makeText(this, "Camera recording failed: " + finalize.getError(), Toast.LENGTH_LONG).show();
            });
            return;
        }
        composeFinalVideo();
    }

    private void composeFinalVideo() {
        exportExecutor.execute(() -> {
            try {
                File output = new File(getCacheDir(), "pip_final_" + System.currentTimeMillis() + ".mp4");
                boolean sourceHasAudio = hasAudio(sourceFile);
                String camera = shell(cameraFile.getAbsolutePath());
                String source = shell(sourceFile.getAbsolutePath());
                String out = shell(output.getAbsolutePath());

                String command;
                if (sourceHasAudio) {
                    command = "-y -i " + source + " -i " + camera
                            + " -filter_complex \"[1:v]scale=iw*0.32:-2[cam];[0:v][cam]overlay=W-w-24:H-h-24:shortest=1[v];[0:a][1:a]amix=inputs=2:duration=first:dropout_transition=2[a]\""
                            + " -map \"[v]\" -map \"[a]\" -shortest -movflags +faststart " + out;
                } else {
                    command = "-y -i " + source + " -i " + camera
                            + " -filter_complex \"[1:v]scale=iw*0.32:-2[cam];[0:v][cam]overlay=W-w-24:H-h-24:shortest=1[v]\""
                            + " -map \"[v]\" -map 1:a? -shortest -movflags +faststart " + out;
                }

                if (!ReturnCode.isSuccess(FFmpegKit.execute(command).getReturnCode())) {
                    throw new IllegalStateException("Video composition failed");
                }
                saveToMediaStore(output);
                cleanup(sourceFile, cameraFile, output);
                runOnUiThread(() -> {
                    commentaryButton.setEnabled(true);
                    commentaryButton.setText("START COMMENTARY");
                    status.setText("Saved reaction video to Movies/FadCam");
                    Toast.makeText(this, "Reaction video saved", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    commentaryButton.setEnabled(true);
                    commentaryButton.setText("START COMMENTARY");
                    status.setText("Export failed — original recordings retained");
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean hasAudio(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
            return "yes".equalsIgnoreCase(hasAudio);
        } catch (Exception ignored) {
            return true;
        } finally {
            retriever.release();
        }
    }

    private void saveToMediaStore(File file) throws Exception {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "FadCam_Reaction_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FadCam");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Could not create MediaStore item");
        try {
            try (InputStream in = new java.io.FileInputStream(file);
                 java.io.OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Could not open output");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Video.Media.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            throw e;
        }
    }

    private void cleanup(File... files) {
        for (File file : files) if (file != null) file.delete();
    }

    private String shell(String path) { return "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD,
                                FallbackStrategy.higherQualityOrLowerThan(Quality.HD)))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, videoCapture);
            } catch (Exception e) {
                Toast.makeText(this, "Camera unavailable: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_AUDIO) {
            if (hasCameraAudioPermission()) startCamera();
            else Toast.makeText(this, "Camera and microphone permissions are required", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        if (recording != null) {
            recording.stop();
            recording = null;
        }
        if (player != null) { player.release(); player = null; }
        exportExecutor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
}

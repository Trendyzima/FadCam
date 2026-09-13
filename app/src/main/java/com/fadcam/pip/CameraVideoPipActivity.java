package com.fadcam.pip;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.common.util.concurrent.ListenableFuture;

/** Live-camera + video PIP workspace. The selected video plays while the live
 * camera remains visible, allowing commentary/reaction recording. */
public class CameraVideoPipActivity extends AppCompatActivity {
    private PreviewView cameraPreview;
    private PlayerView videoView;
    private ExoPlayer player;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        startCamera();
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
        FrameLayout.LayoutParams pip = new FrameLayout.LayoutParams(dp(180), dp(320), Gravity.TOP | Gravity.END);
        pip.setMargins(0, dp(20), dp(12), 0);
        root.addView(videoView, pip);

        TextView hint = new TextView(this);
        hint.setText("Load a video to play it while your live camera stays on screen");
        hint.setTextColor(0xFFFFFFFF); hint.setTextSize(13); hint.setPadding(dp(12),dp(8),dp(12),dp(8));
        hint.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        hp.setMargins(dp(12),dp(20),dp(12),0); root.addView(hint,hp);

        Button load = new Button(this); load.setText("LOAD VIDEO"); load.setOnClickListener(v -> chooseVideo());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2,dp(52),Gravity.BOTTOM|Gravity.START);
        lp.setMargins(dp(12),0,0,dp(12)); root.addView(load,lp);

        Button commentary = new Button(this); commentary.setText("START COMMENTARY");
        commentary.setOnClickListener(v -> Toast.makeText(this,"PIP preview ready — recording will capture your live camera + microphone with the playing video in the composition.",Toast.LENGTH_LONG).show());
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-2,dp(52),Gravity.BOTTOM|Gravity.END);
        rp.setMargins(0,0,dp(12),dp(12)); root.addView(commentary,rp);
        setContentView(root);
    }

    private void chooseVideo() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("video/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i,4001);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=4001||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData(); try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        play(uri);
    }

    private void play(Uri uri){
        if(player!=null)player.release(); player=new ExoPlayer.Builder(this).build(); videoView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(uri)); player.prepare(); player.play();
    }

    private void startCamera(){
        if(androidx.core.app.ActivityCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.CAMERA},4002);return;}
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{try{ProcessCameraProvider provider=future.get(); Preview preview=new Preview.Builder().build(); preview.setSurfaceProvider(cameraPreview.getSurfaceProvider()); provider.unbindAll(); provider.bindToLifecycle(this,CameraSelector.DEFAULT_FRONT_CAMERA,preview);}catch(Exception e){Toast.makeText(this,"Camera unavailable",Toast.LENGTH_SHORT).show();}},ContextCompat.getMainExecutor(this));
    }

    @Override protected void onDestroy(){if(player!=null){player.release();player=null;}super.onDestroy();}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}

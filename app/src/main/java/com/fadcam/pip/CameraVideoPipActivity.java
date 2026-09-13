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
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.FallbackStrategy;
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
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraVideoPipActivity extends AppCompatActivity {
    private static final int PICK_VIDEO = 4001, REQUEST_CAMERA_AUDIO = 4002;
    private static final int MIN_PIP_DP = 120, MAX_PIP_DP = 520;
    private PreviewView cameraPreview; private PlayerView videoView; private FrameLayout pipContainer;
    private TextView status, sourceVolumeLabel, micVolumeLabel; private ProgressBar exportProgress;
    private Button commentaryButton, pauseButton, flipButton; private SeekBar sourceVolume, micVolume;
    private ExoPlayer player; private File sourceFile, cameraFile; private Recording recording;
    private VideoCapture<Recorder> videoCapture; private CameraSelector currentCamera = CameraSelector.DEFAULT_FRONT_CAMERA;
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();
    private float sourceVolumeLevel=1f, micVolumeLevel=1f; private boolean recordingPaused, stopping;
    private float pipLeftFraction=.62f, pipTopFraction=.08f, pipWidthFraction=.32f;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState){super.onCreate(savedInstanceState);buildUi();if(hasCameraAudioPermission())startCamera();else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},REQUEST_CAMERA_AUDIO);}
    private boolean hasCameraAudioPermission(){return ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED&&ActivityCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
    private void buildUi(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xFF000000);
        cameraPreview=new PreviewView(this);cameraPreview.setScaleType(PreviewView.ScaleType.FILL_CENTER);root.addView(cameraPreview,new FrameLayout.LayoutParams(-1,-1));
        pipContainer=new FrameLayout(this);pipContainer.setBackgroundColor(0xCC000000);videoView=new PlayerView(this);videoView.setUseController(true);videoView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);videoView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);pipContainer.addView(videoView,new FrameLayout.LayoutParams(-1,-1));
        Button resize=new Button(this);resize.setText("↘");resize.setTextSize(16);resize.setOnTouchListener(new ResizeTouchListener());FrameLayout.LayoutParams rlp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM|Gravity.END);rlp.setMargins(0,0,dp(2),dp(2));pipContainer.addView(resize,rlp);
        root.addView(pipContainer,new FrameLayout.LayoutParams(dp(190),dp(320),Gravity.TOP|Gravity.START));pipContainer.post(this::syncPipGeometry);installPipDrag();
        status=new TextView(this);status.setText("Load a video, then start commentary");status.setTextColor(0xFFFFFFFF);status.setTextSize(13);status.setPadding(dp(12),dp(8),dp(12),dp(8));status.setBackgroundColor(0x99000000);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);sp.setMargins(dp(12),dp(12),dp(12),0);root.addView(status,sp);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(8),dp(6),dp(8),dp(6));controls.setBackgroundColor(0xCC101010);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(320),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);cp.setMargins(dp(8),0,dp(8),dp(8));
        LinearLayout buttons=new LinearLayout(this);Button load=new Button(this);load.setText("LOAD VIDEO");load.setOnClickListener(v->chooseVideo());flipButton=new Button(this);flipButton.setText("FLIP CAMERA");flipButton.setOnClickListener(v->flipCamera());buttons.addView(load,new LinearLayout.LayoutParams(0,dp(48),1));buttons.addView(flipButton,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(buttons);
        sourceVolumeLabel=new TextView(this);sourceVolumeLabel.setTextColor(0xFFFFFFFF);sourceVolumeLabel.setText("Video volume 100%");controls.addView(sourceVolumeLabel);sourceVolume=volumeBar(100,100,value->{sourceVolumeLevel=value/100f;if(player!=null)player.setVolume(sourceVolumeLevel);sourceVolumeLabel.setText("Video volume "+value+"%");});controls.addView(sourceVolume);
        micVolumeLabel=new TextView(this);micVolumeLabel.setTextColor(0xFFFFFFFF);micVolumeLabel.setText("Mic volume 100%");controls.addView(micVolumeLabel);micVolume=volumeBar(100,100,value->{micVolumeLevel=value/100f;micVolumeLabel.setText("Mic volume "+value+"%");});controls.addView(micVolume);
        LinearLayout actions=new LinearLayout(this);pauseButton=new Button(this);pauseButton.setText("PAUSE");pauseButton.setEnabled(false);pauseButton.setOnClickListener(v->togglePause());commentaryButton=new Button(this);commentaryButton.setText("START COMMENTARY");commentaryButton.setOnClickListener(v->toggleCommentary());actions.addView(pauseButton,new LinearLayout.LayoutParams(0,dp(50),1));actions.addView(commentaryButton,new LinearLayout.LayoutParams(0,dp(50),1));controls.addView(actions);root.addView(controls,cp);
        exportProgress=new ProgressBar(this);exportProgress.setIndeterminate(true);exportProgress.setVisibility(View.GONE);root.addView(exportProgress,new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.CENTER));setContentView(root);
    }
    private interface VolumeListener{void onChanged(int value);} private SeekBar volumeBar(int max,int progress,VolumeListener l){SeekBar b=new SeekBar(this);b.setMax(max);b.setProgress(progress);b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean u){if(u)l.onChanged(v);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});return b;}
    private void chooseVideo(){if(recording!=null||stopping)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_VIDEO);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=PICK_VIDEO||resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}try{cleanup(sourceFile);sourceFile=copySourceToCache(uri);play(uri);status.setText("Source loaded — drag PIP, use ↘ to resize, then start");}catch(Exception e){sourceFile=null;Toast.makeText(this,"Could not read selected video",Toast.LENGTH_LONG).show();}}
    private File copySourceToCache(Uri uri)throws Exception{File f=new File(getCacheDir(),"pip_source_"+System.currentTimeMillis()+".mp4");try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(f)){if(in==null)throw new IllegalStateException("No input stream");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return f;}
    private void play(Uri uri){if(player!=null)player.release();player=new ExoPlayer.Builder(this).build();videoView.setPlayer(player);player.setMediaItem(MediaItem.fromUri(uri));player.setVolume(sourceVolumeLevel);player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&recording!=null&&!stopping)stopCommentary();}});player.prepare();player.seekTo(0);player.setPlayWhenReady(false);}
    private void toggleCommentary(){if(recording!=null){stopCommentary();return;}if(stopping)return;if(sourceFile==null||!sourceFile.exists()){Toast.makeText(this,"Load a video first",Toast.LENGTH_SHORT).show();return;}if(!hasCameraAudioPermission()){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},REQUEST_CAMERA_AUDIO);return;}if(videoCapture==null){Toast.makeText(this,"Camera is still starting",Toast.LENGTH_SHORT).show();return;}syncPipGeometry();cameraFile=new File(getCacheDir(),"pip_camera_"+System.currentTimeMillis()+".mp4");try{FileOutputOptions output=new FileOutputOptions.Builder(cameraFile).build();recording=videoCapture.getOutput().prepareRecording(this,output).withAudioEnabled().start(ContextCompat.getMainExecutor(this),this::onVideoRecordEvent);}catch(Exception e){recording=null;Toast.makeText(this,"Could not start camera recording",Toast.LENGTH_LONG).show();return;}stopping=false;recordingPaused=false;player.seekTo(0);player.setVolume(sourceVolumeLevel);player.setPlayWhenReady(true);commentaryButton.setText("STOP & SAVE");pauseButton.setEnabled(true);flipButton.setEnabled(false);status.setText("● Recording — camera + microphone + source video");}
    private void togglePause(){if(recording==null||stopping)return;try{if(recordingPaused){recording.resume();player.setPlayWhenReady(true);recordingPaused=false;pauseButton.setText("PAUSE");status.setText("● Recording resumed");}else{recording.pause();player.setPlayWhenReady(false);recordingPaused=true;pauseButton.setText("RESUME");status.setText("Ⅱ Paused — synchronization preserved");}}catch(Exception e){Toast.makeText(this,"Pause/resume is unavailable on this device",Toast.LENGTH_SHORT).show();}}
    private void stopCommentary(){if(recording==null||stopping)return;stopping=true;try{if(recordingPaused)recording.resume();}catch(Exception ignored){}recordingPaused=false;if(player!=null)player.setPlayWhenReady(false);try{recording.stop();}catch(Exception e){resetAfterFailure("Could not finalize camera recording");return;}commentaryButton.setEnabled(false);pauseButton.setEnabled(false);flipButton.setEnabled(false);commentaryButton.setText("FINALIZING…");status.setText("Finalizing camera recording…");}
    private void onVideoRecordEvent(VideoRecordEvent event){if(!(event instanceof VideoRecordEvent.Finalize))return;VideoRecordEvent.Finalize f=(VideoRecordEvent.Finalize)event;if(f.hasError()){runOnUiThread(()->resetAfterFailure("Recording failed: "+f.getError()));return;}composeFinalVideo();}
    private void composeFinalVideo(){exportProgress.setVisibility(View.VISIBLE);status.setText("Exporting reaction video…");exportExecutor.execute(()->{File output=new File(getCacheDir(),"pip_final_"+System.currentTimeMillis()+".mp4");try{if(cameraFile==null||!cameraFile.exists())throw new IllegalStateException("Camera recording is missing");boolean sourceHasAudio=hasAudio(sourceFile);float width=clampFloat(pipWidthFraction,.16f,.55f),x=clampFloat(pipLeftFraction,0f,.84f),y=clampFloat(pipTopFraction,0f,.84f);String camera=quote(cameraFile.getAbsolutePath()),source=quote(sourceFile.getAbsolutePath()),out=quote(output.getAbsolutePath());String videoFilter="[0:v]scale="+fraction(width)+"*W:-2[pip];[1:v][pip]overlay=x="+fraction(x)+"*W:y="+fraction(y)+"*H:shortest=1[v]";String command;if(sourceHasAudio)command="-y -autorotate 1 -i "+source+" -i "+camera+" -filter_complex \"[0:a]volume="+volume(sourceVolumeLevel)+"[sa];[1:a]volume="+volume(micVolumeLevel)+"[ca];"+videoFilter+";[sa][ca]amix=inputs=2:duration=shortest:dropout_transition=2[a]\" -map \"[v]\" -map \"[a]\" -shortest -movflags +faststart "+out;else command="-y -autorotate 1 -i "+source+" -i "+camera+" -filter_complex \""+videoFilter+";[1:a]volume="+volume(micVolumeLevel)+"[a]\" -map \"[v]\" -map \"[a]\" -shortest -movflags +faststart "+out;if(!ReturnCode.isSuccess(FFmpegKit.execute(command).getReturnCode()))throw new IllegalStateException("Video composition failed");saveToMediaStore(output);cleanup(sourceFile,cameraFile,output);runOnUiThread(this::resetAfterSuccess);}catch(Exception e){runOnUiThread(()->resetAfterFailure("Export failed — source and camera recordings retained"));}});}
    private void resetAfterSuccess(){exportProgress.setVisibility(View.GONE);stopping=false;recording=null;cameraFile=null;commentaryButton.setEnabled(true);commentaryButton.setText("START COMMENTARY");pauseButton.setEnabled(false);flipButton.setEnabled(true);status.setText("Saved reaction video to Movies/FadCam");Toast.makeText(this,"Reaction video saved",Toast.LENGTH_LONG).show();}
    private void resetAfterFailure(String message){exportProgress.setVisibility(View.GONE);stopping=false;recording=null;commentaryButton.setEnabled(true);commentaryButton.setText("START COMMENTARY");pauseButton.setEnabled(false);flipButton.setEnabled(true);if(player!=null)player.pause();status.setText(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private boolean hasAudio(File file){MediaMetadataRetriever retriever=new MediaMetadataRetriever();try{retriever.setDataSource(file.getAbsolutePath());return "yes".equalsIgnoreCase(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));}catch(Exception ignored){return false;}finally{try{retriever.release();}catch(Exception ignored){}}}
    private void saveToMediaStore(File file)throws Exception{ContentResolver r=getContentResolver();ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.DISPLAY_NAME,"FadCam_Reaction_"+System.currentTimeMillis()+".mp4");v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");if(android.os.Build.VERSION.SDK_INT>=29){v.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/FadCam");v.put(MediaStore.Video.Media.IS_PENDING,1);}Uri u=r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IllegalStateException("Could not create MediaStore item");try(InputStream in=new java.io.FileInputStream(file);OutputStream out=r.openOutputStream(u)){if(out==null)throw new IllegalStateException("Could not open output");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);if(android.os.Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);r.update(u,done,null,null);}}catch(Exception e){r.delete(u,null,null);throw e;}}
    private void flipCamera(){if(recording!=null||videoCapture==null)return;currentCamera=currentCamera==CameraSelector.DEFAULT_FRONT_CAMERA?CameraSelector.DEFAULT_BACK_CAMERA:CameraSelector.DEFAULT_FRONT_CAMERA;flipButton.setText(currentCamera==CameraSelector.DEFAULT_FRONT_CAMERA?"FLIP CAMERA (FRONT)":"FLIP CAMERA (BACK)");bindCamera(currentCamera);}
    private void startCamera(){bindCamera(currentCamera);}
    private void bindCamera(CameraSelector selector){ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);future.addListener(()->{try{ProcessCameraProvider provider=future.get();Preview preview=new Preview.Builder().build();preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());Recorder recorder=new Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD,FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))).build();videoCapture=VideoCapture.withOutput(recorder);provider.unbindAll();provider.bindToLifecycle(this,selector,preview,videoCapture);}catch(Exception e){videoCapture=null;Toast.makeText(this,"Camera unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}},ContextCompat.getMainExecutor(this));}
    private void installPipDrag(){pipContainer.setOnTouchListener(new View.OnTouchListener(){float downX,downY,startX,startY;public boolean onTouch(View v,MotionEvent e){if(recording!=null||stopping)return true;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=v.getX();startY=v.getY();return true;case MotionEvent.ACTION_MOVE:View p=(View)v.getParent();float maxX=Math.max(0,p.getWidth()-v.getWidth()),maxY=Math.max(0,p.getHeight()-v.getHeight());v.setX(clampFloat(startX+e.getRawX()-downX,0,maxX));v.setY(clampFloat(startY+e.getRawY()-downY,0,maxY));syncPipGeometry();return true;case MotionEvent.ACTION_UP:syncPipGeometry();return true;default:return true;}}});}
    private final class ResizeTouchListener implements View.OnTouchListener{float downX;int startWidth;public boolean onTouch(View v,MotionEvent e){if(recording!=null||stopping)return true;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getRawX();startWidth=pipContainer.getWidth();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){int newWidth=(int)clampFloat(startWidth+e.getRawX()-downX,dp(MIN_PIP_DP),dp(MAX_PIP_DP));FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)pipContainer.getLayoutParams();lp.width=newWidth;lp.height=Math.max(dp(MIN_PIP_DP),(int)(newWidth*1.68f));pipContainer.setLayoutParams(lp);syncPipGeometry();}return true;}}
    private void syncPipGeometry(){if(pipContainer==null||pipContainer.getParent()==null)return;View p=(View)pipContainer.getParent();if(p.getWidth()<=0||p.getHeight()<=0)return;pipContainer.setX(clampFloat(pipContainer.getX(),0,Math.max(0,p.getWidth()-pipContainer.getWidth())));pipContainer.setY(clampFloat(pipContainer.getY(),0,Math.max(0,p.getHeight()-pipContainer.getHeight())));pipLeftFraction=clampFloat(pipContainer.getX()/p.getWidth(),0f,.84f);pipTopFraction=clampFloat(pipContainer.getY()/p.getHeight(),0f,.84f);pipWidthFraction=clampFloat((float)pipContainer.getWidth()/p.getWidth(),.16f,.55f);}
    private String fraction(float v){return String.format(Locale.US,"%.5f",v);}private String volume(float v){return String.format(Locale.US,"%.3f",Math.max(0f,Math.min(1f,v)));}private String quote(String p){return "\""+p.replace("\\","\\\\").replace("\"","\\\"")+"\"";}private float clampFloat(float v,float min,float max){return Math.max(min,Math.min(max,v));}private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}private void cleanup(File...fs){for(File f:fs)if(f!=null)f.delete();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQUEST_CAMERA_AUDIO){if(hasCameraAudioPermission())startCamera();else Toast.makeText(this,"Camera and microphone permissions are required",Toast.LENGTH_LONG).show();}}
    @Override protected void onDestroy(){if(recording!=null){try{recording.stop();}catch(Exception ignored){}recording=null;}if(player!=null){player.release();player=null;}exportExecutor.shutdownNow();super.onDestroy();}
}

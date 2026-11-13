package ch.jarvis.companion;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.*;
import java.io.File;

public class RecorderService extends Service {
    private MediaRecorder recorder;
    private File working;
    private PowerManager.WakeLock wake;
    private final Handler handler=new Handler(Looper.getMainLooper());
    public static volatile boolean recording=false;
    public static volatile String status="Bereit";
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"stop".equals(intent.getAction())){finish();return START_NOT_STICKY;}
        if(recording)return START_NOT_STICKY;
        try{
            NotificationManager manager=getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel("recording","Jarvis Aufnahme",NotificationManager.IMPORTANCE_LOW));
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RecorderService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(this,"recording").setSmallIcon(ch.jarvis.companion.R.drawable.ic_mic).setContentTitle("Jarvis hört zu").setContentText("Aufnahme läuft · maximal 2 Minuten").setOngoing(true).setContentIntent(open).addAction(new Notification.Action.Builder(null,"Stoppen",stop).build()).build();
            startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            File dir=new File(getFilesDir(),"recordings");dir.mkdirs();
            working=new File(dir,System.currentTimeMillis()+".pending");
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setOutputFile(working.getAbsolutePath());
            recorder.prepare();recorder.start();recording=true;status="Aufnahme läuft";
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Jarvis:Recording");
            wake.acquire(130000);
            handler.postDelayed(this::finish,120000);
        }catch(Exception ex){status="Aufnahme konnte nicht gestartet werden. Mikrofonfreigabe prüfen.";release();stopSelf();}
        return START_NOT_STICKY;
    }
    private void finish(){
        if(recorder!=null){
            try{recorder.stop();File target=new File(working.getParentFile(),working.getName().replace(".pending",".m4a"));if(!working.renameTo(target))throw new Exception();status="Aufnahme auf dem Handy gespeichert. Jetzt hochladen.";}
            catch(Exception ex){status="Aufnahme nicht abgeschlossen. Erneut versuchen.";if(working!=null)working.delete();}
        }
        release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    private void release(){handler.removeCallbacksAndMessages(null);recording=false;if(recorder!=null){recorder.release();recorder=null;}if(wake!=null&&wake.isHeld())wake.release();}
    @Override public void onDestroy(){release();super.onDestroy();}
}

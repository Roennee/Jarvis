package ch.jarvis.companion;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

/** Explicit foreground sessions only. Wake audio stays in RAM on the phone. */
public class ListeningService extends Service {
    public static volatile boolean active=false;
    public static volatile String status="Zuhören ausgeschaltet";
    private volatile boolean running=false,waiting=false;
    private volatile long followUntil=0;
    private volatile boolean directCaptureRequested=false;
    private android.speech.tts.TextToSpeech cueVoice;
    private volatile boolean cueReady=false;
    private volatile AudioRecord microphone;
    private volatile MediaPlayer player;
    private PowerManager.WakeLock wake;
    private Thread capture;
    private String mode;
    private final ThreadPoolExecutor uploads=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if("stop".equals(intent.getAction())){stopSession();return START_NOT_STICKY;}
        if("talk".equals(intent.getAction()))directCaptureRequested=true;
        if(active||(capture!=null&&capture.isAlive()))return START_NOT_STICKY;
        getSharedPreferences("voice",0).edit().putString("session_id",java.util.UUID.randomUUID().toString()).apply();
        mode="silent".equals(intent.getStringExtra("mode"))?"silent":"assistant";
        try{
            if(RecorderService.recording)throw new IOException("Zuerst laufende Aufnahme stoppen");
            VoiceClient.base(this);
            if("assistant".equals(mode)&&!WakeModel.ready(this)&&!directCaptureRequested)throw new IOException("Zuerst Hey-Jarvis-Modell installieren");
            NotificationManager nm=getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("listening","Jarvis Zuhörmodus",NotificationManager.IMPORTANCE_LOW));
            PendingIntent stop=PendingIntent.getService(this,31,new Intent(this,ListeningService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent open=PendingIntent.getActivity(this,32,new Intent(this,ListeningActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification=new Notification.Builder(this,"listening").setSmallIcon(R.drawable.ic_mic).setContentTitle("silent".equals(mode)?"Jarvis hört still zu":"Hey Jarvis ist aktiv")
                .setContentText("Mikrofon aktiv · Tippen zum Stoppen").setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(null,"Zuhören stoppen",stop).build()).build();
            startForeground(3,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            running=true;active=true;
            cueVoice=new android.speech.tts.TextToSpeech(this,result->{
                if(result==android.speech.tts.TextToSpeech.SUCCESS&&cueVoice!=null){
                    java.util.Set<android.speech.tts.Voice> voices=cueVoice.getVoices();
                    if(voices!=null)for(android.speech.tts.Voice v:voices)if("de".equals(v.getLocale().getLanguage())&&!v.isNetworkConnectionRequired()){cueVoice.setVoice(v);cueReady=true;break;}
                }
            });
            long minutes=Math.max(1,Math.min(480,intent.getIntExtra("minutes",30)));
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Jarvis:Listen");wake.acquire(minutes*60000+10000);
            capture=new Thread(()->listen(minutes*60000),"jarvis-audio");capture.start();
        }catch(Exception e){stopSession();status="Start fehlgeschlagen: "+e.getMessage();}
        return START_NOT_STICKY;
    }
    private void listen(long duration){
        Model model=null;Recognizer recognizer=null;AudioRecord audio=null;
        ByteArrayOutputStream utterance=new ByteArrayOutputStream();
        byte[] previous=new byte[0];boolean capturing=false;int voiced=0,silence=0;long triggered=0;
        final int sampleRate=16000;long deadline=SystemClock.elapsedRealtime()+duration;
        try{
            if("assistant".equals(mode)&&WakeModel.ready(this)){model=new Model(WakeModel.directory(this).getAbsolutePath());recognizer=new Recognizer(model,sampleRate,"[\"hey jarvis\", \"hi jarvis\", \"[unk]\"]");}
            int size=Math.max(6400,AudioRecord.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
            audio=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);
            microphone=audio;
            if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Mikrofon konnte nicht geöffnet werden");
            audio.startRecording();byte[] buffer=new byte[3200];
            status="silent".equals(mode)?"Still zuhören aktiv":"Bereit für Hey Jarvis";
            while(running&&SystemClock.elapsedRealtime()<deadline){
                int n=audio.read(buffer,0,buffer.length);if(n<0)throw new IOException("Mikrofon unterbrochen");if(n==0)continue;
                if(waiting&&"assistant".equals(mode)){if(recognizer!=null)recognizer.reset();continue;}
                if("assistant".equals(mode)&&!capturing&&(directCaptureRequested||SystemClock.elapsedRealtime()<followUntil)){directCaptureRequested=false;capturing=true;triggered=SystemClock.elapsedRealtime();voiced=0;silence=0;utterance.reset();status="Ich höre deinen Wunsch";}
                if(recognizer!=null&&!capturing){
                    boolean done=recognizer.acceptWaveForm(buffer,n);
                    JSONObject result=new JSONObject(done?recognizer.getResult():recognizer.getPartialResult());
                    String words=result.optString(done?"text":"partial");
                    if(words.contains("hey jarvis")||words.contains("hi jarvis")){
                        capturing=true;triggered=SystemClock.elapsedRealtime();voiced=0;silence=0;utterance.reset();recognizer.reset();status="Jarvis hört deinen Wunsch";
                        // Pause capture during the local acknowledgement to avoid self-recording.
                        if(cueReady){cueVoice.speak("Ja, ich höre.",android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"wake");Thread.sleep(250);long cueEnd=SystemClock.elapsedRealtime()+2500;while(running&&cueVoice.isSpeaking()&&SystemClock.elapsedRealtime()<cueEnd)Thread.sleep(50);}
                        else {ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_MUSIC,75);tone.startTone(ToneGenerator.TONE_PROP_ACK,180);Thread.sleep(220);tone.release();}
                        byte[] flush=new byte[size];audio.read(flush,0,flush.length,AudioRecord.READ_NON_BLOCKING);
                        triggered=SystemClock.elapsedRealtime();
                    }
                    continue;
                }
                long energy=0;for(int i=0;i+1<n;i+=2){short v=(short)((buffer[i]&255)|(buffer[i+1]<<8));energy+=(long)v*v;}
                double rms=Math.sqrt(energy/(double)(n/2));boolean speech=rms>550;
                int ms=n*1000/(sampleRate*2);
                if("silent".equals(mode)&&!capturing){if(!speech){previous=java.util.Arrays.copyOf(buffer,n);continue;}capturing=true;triggered=SystemClock.elapsedRealtime();utterance.reset();utterance.write(previous);voiced=0;silence=0;}
                if(capturing){
                    utterance.write(buffer,0,n);if(speech){voiced+=ms;silence=0;}else silence+=ms;
                    long elapsed=SystemClock.elapsedRealtime()-triggered;
                    if((voiced>=400&&silence>=1800)||elapsed>=45000||(voiced==0&&elapsed>=30000)){
                        if(voiced>=400)enqueue(utterance.toByteArray());
                        capturing=false;if(voiced==0)followUntil=0;utterance.reset();voiced=0;silence=0;if(recognizer!=null)recognizer.reset();
                    }
                }
            }
            if(capturing&&voiced>=400&&utterance.size()>0)enqueue(utterance.toByteArray());
        }catch(Exception e){if(running)status="Zuhören beendet: "+e.getMessage();}
        finally{
            running=false;active=false;
            if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();}microphone=null;
            if(recognizer!=null)recognizer.close();if(model!=null)model.close();
            if(wake!=null&&wake.isHeld())wake.release();
            if(!status.startsWith("Zuhören beendet"))status="Zuhörsitzung beendet";
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
    }
    private void enqueue(byte[] pcm)throws Exception{
        File dir=new File(getFilesDir(),"voice-pending");dir.mkdirs();
        long used=0;File[] stored=dir.listFiles();if(stored!=null)for(File f:stored)used+=f.length();
        if(used>100000000)throw new IOException("Lokaler Sprachspeicher voll. Dateien in der App prüfen/löschen.");
        File file=new File(dir,System.currentTimeMillis()+"-"+java.util.UUID.randomUUID()+".wav");
        try(OutputStream out=new FileOutputStream(file)){int len=pcm.length;ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(len+36).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(len);out.write(header.array());out.write(pcm);}
        VoiceClient.writeText(new File(file.getPath()+".mode").toPath(),mode);
        VoiceClient.writeText(new File(file.getPath()+".session").toPath(),getSharedPreferences("voice",0).getString("session_id",""));
        if("assistant".equals(mode))waiting=true;
        try{uploads.execute(()->process(file));}catch(RejectedExecutionException e){waiting=false;status="Warteschlange voll. Sprachdatei bleibt lokal; später erneut senden.";}
    }
    private void process(File file){
        try{
            String base=VoiceClient.base(this),rid="";
            String jobMode=VoiceClient.readText(new File(file.getPath()+".mode").toPath());
            // Retry a busy laptop without duplicating the saved recording.
            for(int i=0;i<60;i++){try{rid=VoiceClient.upload(this,file,jobMode);break;}catch(IOException e){if(!e.getMessage().contains("409")||i==59)throw e;Thread.sleep(5000);}}
            for(int i=0;i<180;i++){
                JSONObject result=VoiceClient.json(VoiceClient.request(base,"/api/voice/result?id="+rid,null,"",30000));
                String state=result.optString("status");
                if("done".equals(state)){
                    JSONObject decision=result.optJSONObject("result");
                    if(decision==null)throw new IOException("Transkript bleibt auf Laptop, Aktion muss geprüft werden");
                    VoiceClient.writeText(new File(file.getPath()+".done").toPath(),"ok");
                    status="Verarbeitet · "+decision.optString("action");
                    if("assistant".equals(jobMode)&&running){String reply=decision.optString("reply");if(!reply.isEmpty())speak(base,reply);followUntil=SystemClock.elapsedRealtime()+30000;status="Ich höre weiter · 30 Sekunden";}
                    if(!decision.optBoolean("keep_transcript")){file.delete();new File(file.getPath()+".session").delete();new File(file.getPath()+".receipt").delete();new File(file.getPath()+".mode").delete();new File(file.getPath()+".done").delete();}
                    return;
                }
                if("error".equals(state)||"interrupted".equals(state))throw new IOException("Verarbeitung auf Laptop prüfen");
                Thread.sleep(3000);
            }
            throw new IOException("Laptop braucht länger. Original bleibt lokal.");
        }catch(Exception e){status="Sprachdatei bleibt lokal: "+e.getMessage();}
        finally{waiting=false;}
    }
    private void speak(String base,String reply)throws Exception{
        JSONObject status=VoiceClient.json(VoiceClient.request(base,"/api/status",null,"",30000));
        byte[] audio=VoiceClient.request(base,"/api/voice",new JSONObject().put("text",reply),status.getString("csrf"),120000);
        File file=File.createTempFile("reply-",".wav",getCacheDir());Files.write(file.toPath(),audio);
        MediaPlayer p=new MediaPlayer();player=p;
        try{p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());p.setDataSource(file.getAbsolutePath());p.prepare();if(running){p.start();while(running&&p.isPlaying())Thread.sleep(100);}}
        finally{p.release();player=null;file.delete();}
    }
    private void stopSession(){running=false;active=false;if(player!=null){try{player.stop();}catch(Exception ignored){}}if(capture==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}status="Zuhören ausgeschaltet";}
    @Override public void onDestroy(){running=false;active=false;uploads.shutdown();if(cueVoice!=null){cueVoice.stop();cueVoice.shutdown();}if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}

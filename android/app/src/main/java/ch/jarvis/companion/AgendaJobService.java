package ch.jarvis.companion;

import android.app.job.*;
import android.content.SharedPreferences;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** System scheduled fetch: Android may defer work while idle. Cached items stay visible. */
public class AgendaJobService extends JobService {
    private final java.util.concurrent.ConcurrentHashMap<Integer,AtomicBoolean> active=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Object FETCH_LOCK=new Object();
    @Override public boolean onStartJob(JobParameters params){
        AtomicBoolean cancelled=new AtomicBoolean(false);active.put(params.getJobId(),cancelled);
        new Thread(()->{synchronized(FETCH_LOCK){try{
            if(cancelled.get()||!AgendaWidget.exists(this))return;
            String base=VoiceClient.base(this);String warning="";
            JSONObject status=VoiceClient.json(VoiceClient.request(base,"/api/status",null,"",15000));String csrf=status.getString("csrf");
            try{CalendarLink.syncSelected(this,base,csrf);}catch(Exception e){warning="Pixel-Kalender prüfen";}
            JSONObject accounts=status.optJSONObject("calendars");
            for(String provider:new String[]{"google","microsoft"})if(accounts!=null&&accounts.optBoolean(provider)&&!cancelled.get()){
                try{VoiceClient.request(base,"/api/calendar/sync",new JSONObject().put("provider",provider),csrf,30000);}catch(Exception e){warning="Kontoabruf prüfen";}
            }
            JSONObject data=VoiceClient.json(VoiceClient.request(base,"/api/widget",null,"",15000));
            // Verify the cache shape before replacing the last usable snapshot.
            data.getJSONArray("events");data.getJSONArray("tasks");java.time.Instant.parse(data.getString("generated_at"));
            if(!cancelled.get()&&AgendaWidget.exists(this)&&base.equals(VoiceClient.base(this)))getSharedPreferences("agenda",0).edit().putString("base",base).putString("data",data.toString()).putString("error",warning).apply();
        }catch(Exception e){if(!cancelled.get())getSharedPreferences("agenda",0).edit().putString("error","Offline · letzter Abruf").apply();}
        finally{if(!cancelled.get()){AgendaWidget.render(this);jobFinished(params,false);}active.remove(params.getJobId(),cancelled);}}},"jarvis-agenda").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){AtomicBoolean c=active.remove(params.getJobId());if(c!=null)c.set(true);return false;}
}

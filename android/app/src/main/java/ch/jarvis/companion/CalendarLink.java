package ch.jarvis.companion;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.provider.CalendarContract;
import android.net.Uri;
import android.widget.Toast;
import org.json.*;
import java.util.*;

/** Only calendars explicitly selected by the user are copied to the private laptop. */
final class CalendarLink {
    static void select(Activity activity) {
        ArrayList<Long> ids=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        try(Cursor c=activity.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                new String[]{"_id","calendar_displayName","account_name"},"visible=1",null,null)){
            while(c!=null&&c.moveToNext()){ids.add(c.getLong(0));labels.add(c.getString(1)+" · "+c.getString(2));}
        }catch(Exception e){message(activity,"Kalender konnten nicht gelesen werden.");return;}
        if(ids.isEmpty()){message(activity,"Keine synchronisierten Kalender gefunden. Google/Outlook in Android-Konten aktivieren.");return;}
        boolean[] chosen=new boolean[ids.size()];
        new AlertDialog.Builder(activity).setTitle("Kalender mit Jarvis verbinden")
            .setMultiChoiceItems(labels.toArray(new String[0]),chosen,(dialog,index,checked)->chosen[index]=checked)
            .setMessage(null)
            .setPositiveButton("Ausgewählte abrufen",(dialog,which)->{
                HashSet<Long> selected=new HashSet<>();for(int i=0;i<chosen.length;i++)if(chosen[i])selected.add(ids.get(i));
                if(selected.isEmpty()){message(activity,"Kein Kalender ausgewählt.");return;}
                sync(activity,selected);
            }).setNegativeButton("Abbrechen",null).show();
    }
    private static void sync(Activity activity,Set<Long> selected){
        message(activity,"Kalender werden abgerufen …");
        new Thread(()->{try{
            JSONArray events=new JSONArray();long day=86400000L,begin=System.currentTimeMillis()-30*day,end=System.currentTimeMillis()+370*day;
            Uri.Builder builder=CalendarContract.Instances.CONTENT_URI.buildUpon();ContentUris.appendId(builder,begin);ContentUris.appendId(builder,end);
            String selection="calendar_id IN ("+String.join(",",Collections.nCopies(selected.size(),"?"))+")";
            String[] arguments=selected.stream().map(String::valueOf).toArray(String[]::new);
            try(Cursor c=activity.getContentResolver().query(builder.build(),new String[]{"event_id","begin","end","title","description","calendar_id","allDay"},selection,arguments,"begin ASC")){
                while(c!=null&&c.moveToNext()){
                    if(events.length()>=3000)throw new Exception("Mehr als 3000 Termine. Weniger Kalender auswählen.");
                    long start=c.getLong(1),finish=c.getLong(2);
                    if(finish<=start)continue;
                    java.time.Instant a=java.time.Instant.ofEpochMilli(start),z=java.time.Instant.ofEpochMilli(finish);
                    // All-day timestamps are UTC dates in Android; preserve dates in Zurich.
                    if(c.getInt(6)!=0){java.time.ZoneId zone=java.time.ZoneId.of("Europe/Zurich");a=a.atZone(java.time.ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant();z=z.atZone(java.time.ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant();}
                    String title=c.isNull(3)?"Termin":c.getString(3),details=c.isNull(4)?"":c.getString(4);
                    events.put(new JSONObject().put("id",c.getLong(5)+":"+c.getLong(0)+":"+start).put("title",title.isEmpty()?"Termin":title.substring(0,Math.min(500,title.length()))).put("start",a.toString()).put("end",z.toString()).put("details",details.substring(0,Math.min(1000,details.length()))));
                }
            }
            String base=VoiceClient.base(activity);JSONObject status=VoiceClient.json(VoiceClient.request(base,"/api/status",null,"",30000));
            JSONObject result=VoiceClient.json(VoiceClient.request(base,"/api/calendar/device",new JSONObject().put("events",events),status.getString("csrf"),45000));
            message(activity,result.getInt("count")+" Termine mit Jarvis verbunden. Zum Aktualisieren erneut abrufen.");
        }catch(Exception e){message(activity,"Kalender nicht synchronisiert: "+e.getMessage());}}).start();
    }
    static void insert(Activity activity,String id){
        if(id==null||!id.matches("[0-9a-f]{32}")){message(activity,"Termin-ID ungültig");return;}
        new Thread(()->{try{
            JSONArray list=new JSONArray(new String(VoiceClient.request(VoiceClient.base(activity),"/api/events",null,"",30000),java.nio.charset.StandardCharsets.UTF_8));
            JSONObject event=null;for(int i=0;i<list.length();i++)if(id.equals(list.getJSONObject(i).getString("id")))event=list.getJSONObject(i);
            if(event==null)throw new Exception("Termin fehlt");
            Intent intent=new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE,event.getString("title")).putExtra(CalendarContract.Events.DESCRIPTION,event.optString("details"))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,java.time.OffsetDateTime.parse(event.getString("start")).toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,java.time.OffsetDateTime.parse(event.getString("end")).toInstant().toEpochMilli());
            activity.runOnUiThread(()->{try{activity.startActivity(intent);}catch(Exception e){message(activity,"Keine Kalender-App installiert. ICS-Export verwenden.");}});
        }catch(Exception e){message(activity,"Termin konnte nicht geöffnet werden.");}}).start();
    }
    private static void message(Activity activity,String text){activity.runOnUiThread(()->Toast.makeText(activity,text,Toast.LENGTH_LONG).show());}
}

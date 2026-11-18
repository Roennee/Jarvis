package ch.jarvis.companion;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A cached, private 3 + 3 agenda. No microphone or model calls. */
public class AgendaWidget extends AppWidgetProvider {
    static final String REFRESH="ch.jarvis.companion.AGENDA_REFRESH";
    private static final ZoneId ZONE=ZoneId.of("Europe/Zurich");
    private static final int[] EVENTS={R.id.agenda_event1,R.id.agenda_event2,R.id.agenda_event3};
    private static final int[] EVENT_TIMES={R.id.agenda_time1,R.id.agenda_time2,R.id.agenda_time3};
    private static final int[] TASKS={R.id.agenda_task1,R.id.agenda_task2,R.id.agenda_task3};
    private static final int[] DUES={R.id.agenda_due1,R.id.agenda_due2,R.id.agenda_due3};
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){render(c);AgendaJobs.schedule(c);AgendaJobs.refresh(c);}
    @Override public void onEnabled(Context c){AgendaJobs.schedule(c);}
    @Override public void onDisabled(Context c){AgendaJobs.cancel(c);c.getSharedPreferences("agenda",0).edit().clear().apply();}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){render(c);}
    @Override public void onReceive(Context c,Intent intent){super.onReceive(c,intent);if(REFRESH.equals(intent.getAction())){AgendaJobs.refresh(c);}}
    static boolean exists(Context c){return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,AgendaWidget.class)).length>0;}
    static PendingIntent open(Context c,String view,int request){
        Intent i=new Intent(c,MainActivity.class).putExtra("agenda_view",view).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static String text(String value,int max){return value.length()>max?value.substring(0,max-1)+"…":value;}
    static String eventTime(JSONObject event)throws JSONException{
        return AgendaTiming.eventTime(event.getString("start"),event.getString("end"),event.optBoolean("all_day"),Instant.now());
    }
    static void render(Context c){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.agenda_widget);
        v.setTextViewText(R.id.agenda_date,ZonedDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.GERMAN)));
        v.setOnClickPendingIntent(R.id.agenda_heading,open(c,"calendar",100));v.setOnClickPendingIntent(R.id.agenda_tasks_heading,open(c,"tasks",101));
        v.setOnClickPendingIntent(R.id.agenda_refresh,PendingIntent.getBroadcast(c,102,new Intent(c,AgendaWidget.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        for(int i=0;i<3;i++){v.setTextViewText(EVENTS[i],i==0?"Keine anstehenden Termine":"");v.setTextViewText(EVENT_TIMES[i],"");v.setTextViewText(TASKS[i],i==0?"Keine offenen Aufgaben":"");v.setTextViewText(DUES[i],"");v.setOnClickPendingIntent(EVENTS[i],open(c,"calendar",100));v.setOnClickPendingIntent(TASKS[i],open(c,"tasks",101));}
        String footer="Jarvis öffnen und verbinden";
        android.content.SharedPreferences prefs=c.getSharedPreferences("agenda",0);
        try{
            String base=VoiceClient.base(c);
            if(base.equals(prefs.getString("base",""))&&!prefs.getString("data","").isEmpty()){
                JSONObject data=new JSONObject(prefs.getString("data",""));JSONArray events=data.getJSONArray("events"),tasks=data.getJSONArray("tasks");int index=0;
                for(int n=0;n<events.length()&&index<3;n++){JSONObject event=events.getJSONObject(n);if(!Instant.parse(event.getString("end")).isAfter(Instant.now()))continue;v.setTextViewText(EVENTS[index],text(event.optString("title","Termin"),180));v.setTextViewText(EVENT_TIMES[index],eventTime(event));index++;}
                for(int i=0;i<Math.min(3,tasks.length());i++){JSONObject task=tasks.getJSONObject(i);v.setTextViewText(TASKS[i],text(task.getString("body"),180));String due=task.optString("due");v.setTextViewText(DUES[i],due.isEmpty()?"Ohne Datum":"Fällig "+LocalDate.parse(due).format(DateTimeFormatter.ofPattern("d.M.yyyy")));}
                ZonedDateTime stamp=Instant.parse(data.getString("generated_at")).atZone(ZONE);
                footer="Stand "+stamp.format(DateTimeFormatter.ofPattern("d.M. HH:mm"));
                if(!prefs.getString("error","").isEmpty())footer+=" · "+prefs.getString("error","");
                else footer+=" · ↻ aktualisieren";
            }else{v.setTextViewText(EVENTS[0],"Jarvis öffnen");v.setTextViewText(EVENT_TIMES[0],"Tailscale und Laptop verbinden");}
        }catch(Exception ignored){footer="Verbindung in Jarvis einrichten";}
        int[] widgetIds=AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,AgendaWidget.class));
        for(int id:widgetIds){
            boolean compact=AppWidgetManager.getInstance(c).getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,600)<520;
            v.setViewVisibility(R.id.agenda_date,compact?android.view.View.GONE:android.view.View.VISIBLE);
            int padding=(int)((compact?12:20)*c.getResources().getDisplayMetrics().density);
            v.setViewPadding(R.id.agenda_root,padding,padding,padding,padding);
            for(int target:EVENTS){v.setInt(target,"setMaxLines",compact?1:2);v.setTextViewTextSize(target,android.util.TypedValue.COMPLEX_UNIT_SP,compact?15:16);}
            for(int target:TASKS){v.setInt(target,"setMaxLines",compact?1:2);v.setTextViewTextSize(target,android.util.TypedValue.COMPLEX_UNIT_SP,compact?15:16);}
            v.setTextViewText(R.id.agenda_status,footer);
            AppWidgetManager.getInstance(c).updateAppWidget(id,v);
        }
    }
}

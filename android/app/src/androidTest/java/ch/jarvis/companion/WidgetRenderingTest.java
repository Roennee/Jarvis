package ch.jarvis.companion;

import android.appwidget.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.Instant;
import static org.junit.Assert.*;

/** Applies RemoteViews with the framework's launcher inflater, not just a widget binding. */
@RunWith(AndroidJUnit4.class)
public class WidgetRenderingTest {
    private Context context;
    @Before public void prepare(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("voice",0).edit().clear().commit();
        context.getSharedPreferences("agenda",0).edit().clear().commit();
    }
    @After public void clear(){context.getSharedPreferences("agenda",0).edit().clear().commit();}

    @Test public void unsupportedLegacyDividerIsRejected(){
        Context test=InstrumentationRegistry.getInstrumentation().getContext();
        int resource=test.getResources().getIdentifier("unsupported_widget_divider","layout",test.getPackageName());
        assertNotEquals(0,resource);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{
                new RemoteViews(test.getPackageName(),resource).apply(context,new FrameLayout(context));
                fail("A plain View must be rejected by RemoteViews");
            }catch(RuntimeException error){
                StringBuilder causes=new StringBuilder();
                for(Throwable cause=error;cause!=null;cause=cause.getCause())causes.append(cause.getMessage()).append(' ');
                assertTrue(causes.toString(),causes.toString().toLowerCase(java.util.Locale.ROOT).contains("not allowed"));
            }
        });
    }
    @Test public void disconnectedWidgetInflatesInBothSizes(){checkRendering(false);}
    @Test public void cachedAgendaInflatesAndResizesInLauncherHost()throws Exception{
        String base="https://jarvis-runtime-test.invalid.ts.net";
        context.getSharedPreferences("voice",0).edit().putString("url",base).commit();
        JSONArray events=new JSONArray(),tasks=new JSONArray();
        for(int i=1;i<=3;i++){
            Instant start=Instant.now().plusSeconds(3600L*i);
            events.put(new JSONObject().put("title","Termin "+i+" · "+"Langer Titel ".repeat(30)).put("start",start.toString()).put("end",start.plusSeconds(1800).toString()));
            tasks.put(new JSONObject().put("body","Aufgabe "+i+" · "+"Lange Aufgabe ".repeat(30)).put("due",""));
        }
        JSONObject data=new JSONObject().put("events",events).put("tasks",tasks).put("generated_at",Instant.now().toString());
        context.getSharedPreferences("agenda",0).edit().putString("base",base).putString("data",data.toString()).putString("error","Offline · letzter Abruf").commit();
        checkRendering(true);
    }
    private void checkRendering(boolean cached){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            AppWidgetProviderInfo provider=null;
            for(AppWidgetProviderInfo info:AppWidgetManager.getInstance(context).getInstalledProviders())
                if(info.provider.equals(new ComponentName(context,AgendaWidget.class)))provider=info;
            assertNotNull(provider);
            for(boolean compact:new boolean[]{true,false}){
                RemoteViews packet=AgendaWidget.views(context,compact);
                View applied=packet.apply(context,new FrameLayout(context));
                assertNotNull(applied.findViewById(R.id.agenda_heading));
                AgendaWidget.views(context,!compact).reapply(context,applied);
                AppWidgetHostView host=new AppWidgetHostView(context);
                host.setAppWidget(1,provider);host.updateAppWidget(packet);
                int width=(int)(360*context.getResources().getDisplayMetrics().density);
                int height=(int)((compact?420:640)*context.getResources().getDisplayMetrics().density);
                host.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                host.layout(0,0,width,height);
                TextView heading=host.findViewById(R.id.agenda_heading);
                assertNotNull("Launcher must show agenda rather than its error placeholder",heading);
                assertTrue(heading.getHeight()>0);
                TextView first=host.findViewById(R.id.agenda_event1);
                assertTrue(first.getText().toString(),first.getText().toString().startsWith(cached?"Termin 1":"Jarvis öffnen"));
                if(cached){
                    TextView task=host.findViewById(R.id.agenda_task3);
                    assertTrue(task.getText().toString().startsWith("Aufgabe 3"));
                }
            }
        });
    }
}

package ch.jarvis.companion;

import android.Manifest;
import android.app.job.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Executes on a real Android runtime, including JobScheduler's permission checks. */
@RunWith(AndroidJUnit4.class)
public class StartupTest {
    private Context context;
    @Before public void prepare(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.getSharedPreferences("voice",0).edit().clear().commit();}
    @After public void stopJobs(){AgendaJobs.cancel(context);}

    @Test public void networkJobsHaveRequiredPermissionAndCanBeScheduled(){
        assertEquals(PackageManager.PERMISSION_GRANTED,context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE));
        JobScheduler jobs=context.getSystemService(JobScheduler.class);
        assertEquals(JobScheduler.RESULT_SUCCESS,jobs.schedule(AgendaJobs.periodic(context)));
        assertNotNull(jobs.getPendingJob(AgendaJobs.PERIODIC));
        assertEquals(JobScheduler.RESULT_SUCCESS,jobs.schedule(AgendaJobs.once(context)));
    }
    @Test public void rejectedBackgroundWorkDoesNotCrashUi(){
        assertFalse(AgendaJobs.safely(context,()->{throw new SecurityException("simulated scheduler rejection");}));
        assertTrue(context.getSharedPreferences("agenda",0).getString("error","").contains("nicht verfügbar"));
        assertTrue(AgendaJobs.safely(context,()->{}));
        launchAndCheckVisibleContent();
    }
    @Test public void firstLaunchShowsConnectionScreen(){launchAndCheckVisibleContent();}
    @Test public void installedWidgetDoesNotCrashStartupOrResume(){
        AppWidgetHost host=new AppWidgetHost(context,9001);
        android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try{
            automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
            int id=host.allocateAppWidgetId();
            assertTrue(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id,new ComponentName(context,AgendaWidget.class)));
            automation.dropShellPermissionIdentity();
            assertTrue(AgendaWidget.exists(context));
            launchAndCheckVisibleContent();
            AgendaJobs.refresh(context);
        }finally{automation.dropShellPermissionIdentity();host.deleteHost();}
    }
    @Test public void savedConnectionOpensDashboardAndCanResume(){
        context.getSharedPreferences("voice",0).edit().putString("url","https://jarvis-runtime-test.invalid.ts.net").commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{assertFalse(activity.isFinishing());assertTrue(((ViewGroup)activity.findViewById(android.R.id.content)).getChildCount()>0);});
            scenario.recreate();
            scenario.onActivity(activity->assertFalse(activity.isFinishing()));
        }
    }
    private void launchAndCheckVisibleContent(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{assertFalse(activity.isFinishing());assertTrue(((ViewGroup)activity.findViewById(android.R.id.content)).getChildCount()>0);});
        }
    }
}

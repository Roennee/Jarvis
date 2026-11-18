package ch.jarvis.companion;

import android.app.job.*;
import android.content.*;
import android.util.Log;

final class AgendaJobs {
    static final int PERIODIC=301,ONCE=302;
    static JobInfo periodic(Context c){return new JobInfo.Builder(PERIODIC,new ComponentName(c,AgendaJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30*60*1000L).setPersisted(true).build();}
    static JobInfo once(Context c){return new JobInfo.Builder(ONCE,new ComponentName(c,AgendaJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build();}
    // A failed widget update must never terminate the dashboard or its receiver.
    static boolean safely(Context c,Runnable work){
        try{work.run();return true;}
        catch(RuntimeException error){
            Log.e("JarvisAgenda","Background agenda update unavailable",error);
            c.getSharedPreferences("agenda",0).edit().putString("error","Automatische Aktualisierung nicht verfügbar · Jarvis öffnen").apply();
            return false;
        }
    }
    static void schedule(Context c){
        safely(c,()->{
            if(!AgendaWidget.exists(c))return;
            JobScheduler jobs=c.getSystemService(JobScheduler.class);
            if(jobs.getPendingJob(PERIODIC)==null&&jobs.schedule(periodic(c))!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("Agenda schedule rejected");
        });
    }
    static void refresh(Context c){
        safely(c,()->{
            if(!AgendaWidget.exists(c))return;
            if(c.getSystemService(JobScheduler.class).schedule(once(c))!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("Agenda refresh rejected");
            AgendaWidget.render(c);
        });
    }
    static void cancel(Context c){safely(c,()->{JobScheduler jobs=c.getSystemService(JobScheduler.class);jobs.cancel(PERIODIC);jobs.cancel(ONCE);});}
}

package ch.jarvis.companion;

import android.app.job.*;
import android.content.*;

final class AgendaJobs {
    static final int PERIODIC=301,ONCE=302;
    static void schedule(Context c){
        if(!AgendaWidget.exists(c))return;
        JobScheduler jobs=c.getSystemService(JobScheduler.class);
        if(jobs.getPendingJob(PERIODIC)==null)jobs.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,AgendaJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30*60*1000L).setPersisted(true).build());
    }
    static void refresh(Context c){
        if(!AgendaWidget.exists(c))return;
        c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(ONCE,new ComponentName(c,AgendaJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
        AgendaWidget.render(c);
    }
    static void cancel(Context c){JobScheduler jobs=c.getSystemService(JobScheduler.class);jobs.cancel(PERIODIC);jobs.cancel(ONCE);}
}

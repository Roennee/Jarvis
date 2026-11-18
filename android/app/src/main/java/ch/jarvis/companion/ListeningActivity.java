package ch.jarvis.companion;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;

/** Lock-screen surface exposes controls only, never notes, calendars or account data. */
public class ListeningActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView state;
    private String error="";
    private final Runnable refresh=new Runnable(){public void run(){state.setText(ListeningService.active?"Mikrofon aktiv":error.isEmpty()?"Mikrofon aus":error);handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);setShowWhenLocked(true);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(40,70,40,40);setContentView(layout);
        TextView title=new TextView(this);title.setText("Jarvis · Still zuhören");title.setTextSize(26);layout.addView(title);
        TextView text=new TextView(this);text.setText("Sammelt Vorschläge für Notizen, Aufgaben und Termine. Keine Sprachausgabe. Sitzung endet nach 30 Minuten.");layout.addView(text);
        state=new TextView(this);layout.addView(state);
        Button start=new Button(this);start.setText("Still zuhören starten");start.setOnClickListener(v->begin());layout.addView(start);
        Button stop=new Button(this);stop.setText("Zuhören stoppen");stop.setOnClickListener(v->{stopService(new Intent(this,ListeningService.class));state.setText("Mikrofon aus");});layout.addView(stop);
        if("listen".equals(getIntent().getAction()))new Handler(Looper.getMainLooper()).postDelayed(this::begin,400);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if("listen".equals(intent.getAction()))handler.postDelayed(this::begin,400);}
    private void begin(){
        error="";
        if(!getSharedPreferences("voice",0).getBoolean("lockEnabled",false)){state.setText(error="Kachel zuerst in der entsperrten Jarvis-App freigeben.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){state.setText(error="Mikrofon zuerst in der entsperrten App freigeben.");return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){state.setText(error="Benachrichtigungen zuerst in der entsperrten App erlauben.");return;}
        try{startForegroundService(new Intent(this,ListeningService.class).putExtra("mode","silent").putExtra("minutes",30));}catch(Exception e){state.setText(error="Android verhindert den Start. App entsperrt öffnen.");}
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}

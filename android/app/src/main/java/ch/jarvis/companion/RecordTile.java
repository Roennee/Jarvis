package ch.jarvis.companion;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class RecordTile extends TileService {
    @Override public void onStartListening(){Tile tile=getQsTile();if(tile!=null){tile.setState(ListeningService.active?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);tile.setLabel("Jarvis still zuhören");tile.updateTile();}}
    @Override public void onClick(){
        super.onClick();
        if(ListeningService.active){stopService(new Intent(this,ListeningService.class));onStartListening();return;}
        Runnable open=()->{
            Intent intent=new Intent(this,ListeningActivity.class).setAction("listen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,7,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            else startActivityAndCollapse(intent);
        };
        if(getSharedPreferences("voice",0).getBoolean("lockEnabled",false))open.run();else unlockAndRun(open);
    }
}

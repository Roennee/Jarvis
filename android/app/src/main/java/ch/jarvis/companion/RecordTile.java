package ch.jarvis.companion;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class RecordTile extends TileService {
    @Override public void onStartListening() {
        Tile tile=getQsTile();
        if(tile!=null){tile.setState(Tile.STATE_INACTIVE);tile.setLabel("Jarvis aufnehmen");tile.updateTile();}
    }
    @Override public void onClick() {
        super.onClick();
        // Authentication is intentionally respected; recording starts from visible activity.
        unlockAndRun(() -> {
            Intent intent=new Intent(this,MainActivity.class).setAction("record").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if(Build.VERSION.SDK_INT>=34) startActivityAndCollapse(PendingIntent.getActivity(this,7,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            else startActivityAndCollapse(intent);
        });
    }
}

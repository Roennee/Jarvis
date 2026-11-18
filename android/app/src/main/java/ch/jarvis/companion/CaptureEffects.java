package ch.jarvis.companion;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.NoiseSuppressor;
import android.media.audiofx.AutomaticGainControl;
import android.util.Log;

/** Optional device processing, scoped to one microphone session. No software gain. */
final class CaptureEffects implements AutoCloseable {
    private NoiseSuppressor noise;
    private AutomaticGainControl gain;

    CaptureEffects(int session) {
        // Each effect is independent: a vendor failure must not stop recording.
        try {
            if (NoiseSuppressor.isAvailable()) {
                noise=NoiseSuppressor.create(session);
                if(noise!=null) {
                    if(!noise.getEnabled())noise.setEnabled(true);
                    Log.i("JarvisAudio","Noise suppression enabled: "+noise.getEnabled());
                }
            }
        } catch(RuntimeException e) { Log.w("JarvisAudio","Noise suppression unavailable",e); }
        try {
            if(AutomaticGainControl.isAvailable()) {
                gain=AutomaticGainControl.create(session);
                if(gain!=null) {
                    if(gain.getEnabled())gain.setEnabled(false);
                    Log.i("JarvisAudio","Automatic gain enabled: "+gain.getEnabled());
                }
            }
        } catch(RuntimeException e) { Log.w("JarvisAudio","Gain control unavailable",e); }
    }

    private static void release(AudioEffect effect) {
        if(effect!=null)try{effect.release();}catch(RuntimeException ignored){}
    }
    @Override public void close() {
        release(noise);noise=null;
        release(gain);gain=null;
    }
}

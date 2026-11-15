package ch.jarvis.companion;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.json.JSONObject;

public class VoiceSettingsActivity extends Activity {
    private LinearLayout layout,files;
    private EditText address;
    private TextView status;
    private boolean wantsRecord=false;
    private String listenMode="assistant";
    private int listenMinutes=30;
    private static final java.util.Set<String> uploading=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refreshTask=new Runnable(){public void run(){status.setText(ListeningService.active?ListeningService.status:RecorderService.status+"\n"+ListeningService.status);refreshFiles();handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(40,60,40,40);layout.setBackgroundColor(Color.rgb(11,17,23));scroll.addView(layout);setContentView(scroll);
        TextView title=new TextView(this);title.setText("Jarvis · Sprache einrichten");title.setTextSize(26);title.setTextColor(Color.rgb(154,229,198));layout.addView(title);
        TextView description=new TextView(this);description.setText("Sprachassistent und stilles Mitschreiben über deinen privaten Laptop. Einzelne manuelle Aufnahmen bleiben auf 2 Minuten begrenzt. Keine Cloud-Sprachverarbeitung.");layout.addView(description);
        address=new EditText(this);address.setHint("https://dein-laptop.dein-netz.ts.net");address.setSingleLine(true);address.setInputType(17);address.setText(getSharedPreferences("voice",0).getString("url",getSharedPreferences("ch.jarvis.companion.MainActivity",0).getString("url","")));layout.addView(address);
        addButton(layout,"Adresse speichern",()->{try{String url=baseUrl();getPreferences(0).edit().putString("url",url).apply();getSharedPreferences("voice",0).edit().putString("url",url).apply();message("Adresse gespeichert.");}catch(Exception e){message(e.getMessage());}});
        TextView guide=new TextView(this);guide.setText("Hey Jarvis: englisch aussprechen, kurz warten, dann Wunsch auf Deutsch sagen. Stillmodus: Notizen/Termine ohne Stimme. Mikrofon ist sichtbar aktiv. Kein Start nach Neustart. Akkuverbrauch noch nicht gemessen.");layout.addView(guide);
        addButton(layout,"Englisches Hey-Jarvis-Modell installieren (ca. 40 MB)",()->new Thread(()->{try{message("Modell wird direkt vom Vosk-Anbieter geladen.");WakeModel.install(this);message("Hey-Jarvis-Modell bereit.");}catch(Exception e){message("Modelldownload fehlgeschlagen: "+e.getMessage());}}).start());
        Spinner duration=new Spinner(this);duration.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"30 Minuten","60 Minuten","Bis 8 Stunden"}));duration.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,android.view.View v,int position,long id){listenMinutes=new int[]{30,60,480}[position];}public void onNothingSelected(AdapterView<?> p){}});layout.addView(duration);
        addButton(layout,"Hey Jarvis aktivieren",()->listen("assistant"));
        addButton(layout,"Still zuhören aktivieren",()->listen("silent"));
        addButton(layout,"Zuhören ausschalten",()->stopService(new Intent(this,ListeningService.class)));
        addButton(layout,"Ausstehende Sprachdateien erneut senden",this::retryVoice);
        addButton(layout,"Verarbeitete Sprachdateien vom Pixel löschen",()->new android.app.AlertDialog.Builder(this).setMessage("Löscht nur bereits verarbeitete Sprachdateien auf diesem Pixel. Notizen/Termine auf Laptop bleiben erhalten.").setPositiveButton("Löschen",(d,w)->{File dir=new File(getFilesDir(),"voice-pending");File[] entries=dir.listFiles((folder,name)->name.endsWith(".wav"));int count=0;if(entries!=null)for(File file:entries)if(new File(file.getPath()+".done").exists()){new File(file.getPath()+".mode").delete();new File(file.getPath()+".receipt").delete();new File(file.getPath()+".done").delete();if(file.delete())count++;}message(count+" verarbeitete Dateien gelöscht.");}).setNegativeButton("Abbrechen",null).show());
        Switch lock=new Switch(this);lock.setText("Stille Kachel auf Sperrbildschirm erlauben");lock.setChecked(getSharedPreferences("voice",0).getBoolean("lockEnabled",false));lock.setOnCheckedChangeListener((b,checked)->getSharedPreferences("voice",0).edit().putBoolean("lockEnabled",checked).apply());layout.addView(lock);
        addButton(layout,"● Aufnahme starten",this::record);
        addButton(layout,"■ Aufnahme stoppen",()->startService(new Intent(this,RecorderService.class).setAction("stop")));
        addButton(layout,"Zurück zu Jarvis",()->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});
        status=new TextView(this);layout.addView(status);files=new LinearLayout(this);files.setOrientation(LinearLayout.VERTICAL);layout.addView(files);
        wantsRecord="record".equals(getIntent().getAction());
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);wantsRecord="record".equals(intent.getAction());}
    @Override protected void onResume(){super.onResume();handler.post(refreshTask);String mode=getIntent().getStringExtra("listen_mode");if(mode!=null){getIntent().removeExtra("listen_mode");handler.postDelayed(()->listen(mode),400);}if(wantsRecord){wantsRecord=false;handler.postDelayed(this::record,350);}}
    @Override protected void onPause(){handler.removeCallbacks(refreshTask);super.onPause();}
    private void message(String value){runOnUiThread(()->Toast.makeText(this,value,Toast.LENGTH_LONG).show());}
    private void addButton(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());parent.addView(b);}
    private void record(){
        if(RecorderService.recording)return;
        if(ListeningService.active){message("Zuerst Zuhörmodus ausschalten.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);return;}
        try{startForegroundService(new Intent(this,RecorderService.class));}catch(Exception e){message("Aufnahme nur aus geöffneter App starten.");}
    }
    private void retryVoice(){
        if(ListeningService.active){message("Zuerst Zuhören stoppen, damit keine parallelen Uploads laufen.");return;}
        new Thread(()->{
            File dir=new File(getFilesDir(),"voice-pending");File[] pending=dir.listFiles((d,name)->name.endsWith(".wav"));
            if(pending==null){message("Keine ausstehenden Sprachdateien.");return;}
            int count=0;
            for(File file:pending){
                if(new File(file.getPath()+".done").exists()||!uploading.add(file.getPath()))continue;
                try{
                    String base=VoiceClient.base(this),mode=VoiceClient.readText(new File(file.getPath()+".mode").toPath());
                    String rid=VoiceClient.upload(this,file,mode);
                    boolean done=false;
                    for(int i=0;i<180;i++){
                        JSONObject result=VoiceClient.json(VoiceClient.request(base,"/api/voice/result?id="+rid,null,"",30000));
                        if("done".equals(result.optString("status"))){if(result.optJSONObject("result")==null)throw new IOException("Transkript auf Laptop prüfen");done=true;break;}
                        if("error".equals(result.optString("status")))throw new IOException("Verarbeitung auf Laptop prüfen");
                        Thread.sleep(3000);
                    }
                    if(!done)throw new IOException("Verarbeitung dauert länger");
                    VoiceClient.writeText(new File(file.getPath()+".done").toPath(),"ok");count++;
                }catch(Exception e){message("Noch nicht verarbeitet: "+e.getMessage());break;}
                finally{uploading.remove(file.getPath());}
            }
            message(count+" Sprachdateien verarbeitet. Keine Sprachausgabe beim Nachsenden.");
        }).start();
    }
    private void listen(String mode){
        listenMode=mode;
        try{String url=baseUrl();getSharedPreferences("voice",0).edit().putString("url",url).apply();}catch(Exception e){message(e.getMessage());return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},20);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},21);return;}
        try{startForegroundService(new Intent(this,ListeningService.class).putExtra("mode",mode).putExtra("minutes",listenMinutes));}catch(Exception e){message("Zuhören aus sichtbarer App starten.");}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==20||code==21){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)listen(listenMode);else message("Mikrofon und sichtbare Benachrichtigungen sind für Zuhören erforderlich.");return;}if(code==10&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)record();else if(code==11){try{startForegroundService(new Intent(this,RecorderService.class));}catch(Exception e){message("Aufnahme konnte nicht gestartet werden.");}}}
    private String baseUrl() throws Exception {
        String raw=address.getText().toString().trim().replaceAll("/+$","");URI uri=new URI(raw);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||!uri.getHost().endsWith(".ts.net")||uri.getPort()!=-1||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||!uri.getPath().isEmpty())throw new Exception("Nur deine vollständige HTTPS-Tailscale-Adresse ohne Pfad verwenden.");
        return raw;
    }
    private void refreshFiles(){
        File dir=new File(getFilesDir(),"recordings");File[] recordings=dir.listFiles((d,name)->name.endsWith(".m4a"));files.removeAllViews();if(recordings==null)return;Arrays.sort(recordings,(a,b)->b.getName().compareTo(a.getName()));
        for(File f:recordings){TextView label=new TextView(this);boolean uploaded=new File(f.getPath()+".uploaded").exists();label.setText(f.getName()+" · "+(uploaded?"auf Laptop gespeichert":"auf Handy gespeichert"));files.addView(label);if(!uploaded)addButton(files,"An Laptop senden",()->upload(f));}
    }
    private byte[] request(String endpoint,String base,JSONObject payload,String token) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(base+endpoint).openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(45000);connection.setInstanceFollowRedirects(false);
        try{
            if(payload!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Origin",base);connection.setRequestProperty("X-Jarvis-Token",token);try(OutputStream out=connection.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=connection.getResponseCode();InputStream in=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(in!=null){try(InputStream stream=in){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);}}
            if(code<200||code>=300)throw new Exception("Server meldet HTTP "+code+". Tailscale und Jarvis prüfen.");return bytes.toByteArray();
        }finally{connection.disconnect();}
    }
    private void upload(File file){
        final String base;try{base=baseUrl();}catch(Exception e){message(e.getMessage());return;}
        if(!uploading.add(file.getAbsolutePath())){message("Upload läuft bereits.");return;}
        new Thread(()->{
            try{
                if(file.length()>20000000)throw new Exception("Aufnahme grösser als 20 MB.");
                JSONObject s=new JSONObject(new String(request("/api/status",base,null,""),StandardCharsets.UTF_8));String token=s.getString("csrf");
                JSONObject body=new JSONObject().put("mime","audio/mp4").put("audio",Base64.encodeToString(Files.readAllBytes(file.toPath()),Base64.NO_WRAP));
                JSONObject result=new JSONObject(new String(request("/api/recordings",base,body,token),StandardCharsets.UTF_8));
                new File(file.getPath()+".uploaded").createNewFile();
                message("Aufnahme auf Laptop gespeichert.");
                if(s.optBoolean("transcription")){try{request("/api/transcribe",base,new JSONObject().put("id",result.getString("id")),token);}catch(Exception e){message("Audio gespeichert. Transkription später in Jarvis starten.");}}
            }catch(Exception e){message("Nicht hochgeladen. Original bleibt auf dem Handy. "+e.getMessage());}
            finally{uploading.remove(file.getAbsolutePath());runOnUiThread(this::refreshFiles);}
        }).start();
    }
}

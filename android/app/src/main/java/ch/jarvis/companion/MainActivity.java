package ch.jarvis.companion;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import android.view.View;
import android.view.WindowInsets;
import android.graphics.Insets;
import java.net.URI;
import org.json.JSONObject;

/** The established Jarvis dashboard is the app. Native speech is an implementation detail. */
public class MainActivity extends Activity {
    private WebView web;
    private String origin="";
    private PermissionRequest pendingMicrophone;
    private ValueCallback<Uri[]> pendingFile;
    private String requestedMode="assistant";
    private boolean recordTraining=false;
    private String recordMode="protocol";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        if(web!=null&&trusted(web.getUrl())){
            String status=ListeningService.active?ListeningService.status:"Zuhören aus";
            String recordState=RecorderService.recording?"Aufnahme läuft · "+((SystemClock.elapsedRealtime()-RecorderService.startedAt)/1000)+" s":RecorderService.status;
            web.evaluateJavascript("window.jarvisNative=true;document.querySelectorAll('[data-native-voice]').forEach(e=>e.hidden=false);var s=document.getElementById('nativeVoiceStatus');if(s)s.textContent="+JSONObject.quote(status)+";var r=document.getElementById('nativeRecord');if(r)r.textContent="+JSONObject.quote(RecorderService.recording?"■ Aufnahme beenden":ListeningService.active?"■ Zuhören beenden":"● Aufnahme starten")+";var b=document.getElementById('record');if(b)b.hidden=true;var t=document.getElementById('nativeRecordStatus');if(t)t.textContent="+JSONObject.quote(recordState)+";",null);
        }
        handler.postDelayed(this,1500);
    }};
    @Override public void onCreate(Bundle state){super.onCreate(state);openDashboard();}
    private boolean trusted(String url){
        if(url==null||origin.isEmpty())return false;
        try{URI u=new URI(url),base=new URI(origin);return "https".equals(u.getScheme())&&base.getHost().equals(u.getHost())&&u.getPort()==-1&&u.getRawUserInfo()==null;}catch(Exception e){return false;}
    }
    private void openDashboard(){
        try{
            String saved=getSharedPreferences("voice",0).getString("url",getPreferences(0).getString("url",""));
            if(!saved.isEmpty())getSharedPreferences("voice",0).edit().putString("url",saved).apply();
            origin=VoiceClient.base(this);
        }catch(Exception e){showSetup();return;}
        if(web!=null){web.destroy();web=null;}
        web=new WebView(this);web.setBackgroundColor(Color.rgb(11,18,24));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){if(trusted(url)){handler.removeCallbacks(refresh);handler.post(refresh);}}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                if("jarvis-native".equals(uri.getScheme())){
                    if(!request.isForMainFrame()||!request.hasGesture()||!trusted(view.getUrl()))return true;
                    String action=uri.getHost();
                    if("stop".equals(action))stopService(new Intent(MainActivity.this,ListeningService.class));
                    else if("record".equals(action)){recordTraining="true".equals(uri.getQueryParameter("training"));recordMode=uri.getQueryParameter("mode");record();}
                    else if("calendar".equals(action))calendar();
                    else if("calendar-add".equals(action))CalendarLink.insert(MainActivity.this,uri.getQueryParameter("id"));
                    else if("lockscreen".equals(action))setupTile();
                    else if("setup".equals(action))startActivity(new Intent(MainActivity.this,VoiceSettingsActivity.class));
                    else if("talk".equals(action)||"wake".equals(action)||"silent".equals(action))activate("talk".equals(action)?"talk":"wake".equals(action)?"assistant":"silent");
                    return true;
                }
                if(trusted(uri.toString()))return false;
                if(request.isForMainFrame()&&request.hasGesture()&&"https".equals(uri.getScheme())){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
                }
                return true;
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame()){Toast.makeText(MainActivity.this,"Jarvis nicht erreichbar. Tailscale und Laptop prüfen.",Toast.LENGTH_LONG).show();showSetup();}
            }
        });

        web.setDownloadListener((url,agent,disposition,mime,length)->{if(trusted(url)){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}}});
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.rgb(11,17,23));frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);frame.addView(progress,new FrameLayout.LayoutParams(-1,6));
        web.setWebChromeClient(createChromeClient(progress));
        applyInsets(frame);setContentView(frame);web.loadUrl(origin+"/");
    }
    private WebChromeClient createChromeClient(ProgressBar progress){
        return new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){progress.setProgress(value);progress.setVisibility(value==100?View.GONE:View.VISIBLE);}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(!trusted(view.getUrl()))return false;
                if(pendingFile!=null)pendingFile.onReceiveValue(null);pendingFile=callback;
                try{startActivityForResult(params.createIntent(),70);return true;}catch(Exception e){pendingFile=null;return false;}
            }
            @Override public void onPermissionRequest(PermissionRequest request){runOnUiThread(()->{
                if(!trusted(request.getOrigin().toString())||!trusted(web.getUrl())){request.deny();return;}
                boolean audio=false;for(String r:request.getResources())if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r))audio=true;
                if(!audio){request.deny();return;}
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                else {pendingMicrophone=request;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},60);}
            });}
            @Override public void onPermissionRequestCanceled(PermissionRequest request){if(pendingMicrophone==request)pendingMicrophone=null;}
        };
    }
    private void applyInsets(View root){
        final int l=root.getPaddingLeft(),t=root.getPaddingTop(),r=root.getPaddingRight(),b=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(l+bars.left,t+bars.top,r+bars.right,b+bars.bottom);}
            else view.setPadding(l+insets.getSystemWindowInsetLeft(),t+insets.getSystemWindowInsetTop(),r+insets.getSystemWindowInsetRight(),b+insets.getSystemWindowInsetBottom());
            return insets;
        });
    }
    private void activate(String mode){
        requestedMode=mode;
        if("assistant".equals(mode)&&!WakeModel.ready(this)){Toast.makeText(this,"Einmal das Hey-Jarvis-Modell unter Sprache einrichten installieren.",Toast.LENGTH_LONG).show();startActivity(new Intent(this,VoiceSettingsActivity.class));return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},61);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},62);return;}
        try{startForegroundService(new Intent(this,ListeningService.class).setAction("talk".equals(mode)?"talk":"start").putExtra("mode","talk".equals(mode)?"assistant":mode).putExtra("minutes",30));}catch(Exception e){Toast.makeText(this,"Zuhören konnte nicht gestartet werden.",Toast.LENGTH_LONG).show();}
    }
    private void record(){
        if(!recordTraining&&("assistant".equals(recordMode)||"silent".equals(recordMode))){if(ListeningService.active)stopService(new Intent(this,ListeningService.class));else activate("silent".equals(recordMode)?"silent":"talk");return;}
        if(RecorderService.recording){startService(new Intent(this,RecorderService.class).setAction("stop"));return;}
        if(ListeningService.active){Toast.makeText(this,"Zuerst Zuhören stoppen, dann Aufnahme starten.",Toast.LENGTH_LONG).show();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},63);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},64);return;}
        try{startForegroundService(new Intent(this,RecorderService.class).putExtra("upload",true).putExtra("mode",recordTraining?"training":"protocol"));}
        catch(Exception e){Toast.makeText(this,"Aufnahme konnte nicht gestartet werden. Mikrofonberechtigung prüfen.",Toast.LENGTH_LONG).show();}
    }
    private void calendar(){
        if(checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},65);return;}
        CalendarLink.select(this);
    }
    private void setupTile(){
        new android.app.AlertDialog.Builder(this).setTitle("Jarvis auf dem Sperrbildschirm")
            .setMessage("Erlaubt stilles Zuhören nach deinem Tippen auf die Jarvis-Kachel. Ziehe auf dem Sperrbildschirm von oben nach unten. Keine Sprachausgabe; Vorschläge prüfst du später in der App.")
            .setPositiveButton("Erlauben & Kachel hinzufügen",(d,w)->{
                getSharedPreferences("voice",0).edit().putBoolean("lockEnabled",true).apply();
                enableTile();
            }).setNeutralButton("Zugriff widerrufen",(d,w)->{getSharedPreferences("voice",0).edit().putBoolean("lockEnabled",false).apply();stopService(new Intent(this,ListeningService.class));}).setNegativeButton("Abbrechen",null).show();
    }
    private void enableTile(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},66);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},67);return;}
        if(Build.VERSION.SDK_INT>=33)getSystemService(android.app.StatusBarManager.class).requestAddTileService(
            new ComponentName(this,RecordTile.class),"Jarvis still zuhören",
            android.graphics.drawable.Icon.createWithResource(this,R.drawable.ic_mic),getMainExecutor(),
            result->Toast.makeText(this,result==2||result==1?"Jarvis-Kachel bereit. Vom oberen Bildschirmrand herunterziehen.":"Kachel nicht hinzugefügt. In den Schnelleinstellungen über Bearbeiten hinzufügen.",Toast.LENGTH_LONG).show());
        else Toast.makeText(this,"Schnelleinstellungen öffnen → Bearbeiten → Jarvis hinzufügen.",Toast.LENGTH_LONG).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==70&&pendingFile!=null){pendingFile.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));pendingFile=null;}}
    private void showSetup(){
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(35,70,35,35);layout.setBackgroundColor(Color.rgb(11,18,24));
        TextView title=new TextView(this);title.setText("Jarvis verbinden");title.setTextColor(Color.WHITE);title.setTextSize(24);layout.addView(title);
        TextView text=new TextView(this);text.setText("Einmal deine bisherige HTTPS-Tailscale-Adresse speichern. Danach öffnet sich direkt deine gewohnte Jarvis-Oberfläche.");text.setTextColor(Color.WHITE);layout.addView(text);
        EditText address=new EditText(this);address.setTextColor(Color.WHITE);address.setSingleLine(true);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setHint("laptop.tailnetz.ts.net");address.setText(getSharedPreferences("voice",0).getString("url",""));layout.addView(address);
        TextView error=new TextView(this);error.setTextColor(Color.rgb(255,180,160));layout.addView(error);
        Button save=new Button(this);save.setText("Jarvis öffnen");save.setOnClickListener(v->{try{String raw=JarvisAddress.normalize(address.getText().toString());getSharedPreferences("voice",0).edit().putString("url",raw).apply();openDashboard();}catch(Exception e){error.setText(e.getMessage());}});layout.addView(save);
        TextView help=new TextView(this);help.setText("Tailscale auf dem Pixel einschalten. Laptop und Jarvis-Server müssen laufen. https:// wird automatisch ergänzt.");help.setTextColor(Color.LTGRAY);layout.addView(help);applyInsets(layout);setContentView(layout);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==63||code==64){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)record();else Toast.makeText(this,"Aufnahme benötigt Mikrofon und sichtbare Benachrichtigungen. In Androids App-Einstellungen freigeben.",Toast.LENGTH_LONG).show();return;}if(code==65){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)calendar();return;}if(code==66||code==67){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)enableTile();else Toast.makeText(this,"Für die Zuhörkachel sind Mikrofon und sichtbare Benachrichtigungen erforderlich. Einrichtung kann später erneut geöffnet werden.",Toast.LENGTH_LONG).show();return;}if(code==61||code==62){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)activate(requestedMode);else Toast.makeText(this,"Zuhören benötigt Mikrofon und sichtbare Benachrichtigungen. In Androids App-Einstellungen freigeben.",Toast.LENGTH_LONG).show();return;}if(code==60&&pendingMicrophone!=null){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&web!=null&&trusted(web.getUrl()))pendingMicrophone.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else pendingMicrophone.deny();pendingMicrophone=null;}}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override public boolean onCreateOptionsMenu(android.view.Menu menu){menu.add("Verbindung & Sprache").setOnMenuItemClickListener(item->{startActivity(new Intent(this,VoiceSettingsActivity.class));return true;});return true;}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(pendingMicrophone!=null)pendingMicrophone.deny();if(pendingFile!=null)pendingFile.onReceiveValue(null);if(web!=null)web.destroy();super.onDestroy();}
}

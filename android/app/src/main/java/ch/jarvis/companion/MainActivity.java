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
import java.net.URI;
import org.json.JSONObject;

/** The established Jarvis dashboard is the app. Native speech is an implementation detail. */
public class MainActivity extends Activity {
    private WebView web;
    private String origin="";
    private PermissionRequest pendingMicrophone;
    private ValueCallback<Uri[]> pendingFile;
    private String requestedMode="assistant";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        if(web!=null&&trusted(web.getUrl())){
            String status=ListeningService.active?ListeningService.status:"Zuhören aus";
            web.evaluateJavascript("document.querySelectorAll('[data-native-voice]').forEach(e=>e.hidden=false);var s=document.getElementById('nativeVoiceStatus');if(s)s.textContent="+JSONObject.quote(status)+";",null);
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
                    else if("setup".equals(action))startActivity(new Intent(MainActivity.this,VoiceSettingsActivity.class));
                    else if("wake".equals(action)||"silent".equals(action))activate("wake".equals(action)?"assistant":"silent");
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
        web.setWebChromeClient(new WebChromeClient(){
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
        });
        web.setDownloadListener((url,agent,disposition,mime,length)->{if(trusted(url)){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}}});
        setContentView(web);web.loadUrl(origin+"/");
    }
    private void activate(String mode){
        requestedMode=mode;
        if("assistant".equals(mode)&&!WakeModel.ready(this)){Toast.makeText(this,"Einmal das Hey-Jarvis-Modell unter Sprache einrichten installieren.",Toast.LENGTH_LONG).show();startActivity(new Intent(this,VoiceSettingsActivity.class));return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},61);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},62);return;}
        try{startForegroundService(new Intent(this,ListeningService.class).putExtra("mode",mode).putExtra("minutes",30));}catch(Exception e){Toast.makeText(this,"Zuhören konnte nicht gestartet werden.",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==70&&pendingFile!=null){pendingFile.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));pendingFile=null;}}
    private void showSetup(){
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(35,70,35,35);layout.setBackgroundColor(Color.rgb(11,18,24));
        TextView title=new TextView(this);title.setText("Jarvis verbinden");title.setTextColor(Color.WHITE);title.setTextSize(24);layout.addView(title);
        TextView text=new TextView(this);text.setText("Einmal deine bisherige HTTPS-Tailscale-Adresse speichern. Danach öffnet sich direkt deine gewohnte Jarvis-Oberfläche.");text.setTextColor(Color.WHITE);layout.addView(text);
        EditText address=new EditText(this);address.setTextColor(Color.WHITE);address.setSingleLine(true);address.setHint("https://dein-laptop.dein-netz.ts.net");address.setText(getSharedPreferences("voice",0).getString("url",""));layout.addView(address);
        Button save=new Button(this);save.setText("Jarvis öffnen");save.setOnClickListener(v->{String raw=address.getText().toString().trim().replaceAll("/+$","");getSharedPreferences("voice",0).edit().putString("url",raw).apply();try{VoiceClient.base(this);openDashboard();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});layout.addView(save);setContentView(layout);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==61||code==62){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)activate(requestedMode);return;}if(code==60&&pendingMicrophone!=null){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&web!=null&&trusted(web.getUrl()))pendingMicrophone.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else pendingMicrophone.deny();pendingMicrophone=null;}}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override public boolean onCreateOptionsMenu(android.view.Menu menu){menu.add("Verbindung & Sprache").setOnMenuItemClickListener(item->{startActivity(new Intent(this,VoiceSettingsActivity.class));return true;});return true;}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(pendingMicrophone!=null)pendingMicrophone.deny();if(pendingFile!=null)pendingFile.onReceiveValue(null);if(web!=null)web.destroy();super.onDestroy();}
}

package ch.jarvis.companion;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.zip.*;

final class WakeModel {
    static final String NAME="vosk-model-small-en-us-0.15";
    static File directory(Context c){return new File(c.getFilesDir(),NAME);}
    static boolean ready(Context c){return new File(directory(c),"am/final.mdl").isFile()&&new File(directory(c),"conf/model.conf").isFile();}
    static synchronized void install(Context c)throws Exception {
        if(ready(c))return;
        File stage=new File(c.getCacheDir(),"wake-install");erase(stage);stage.mkdirs();
        HttpURLConnection connection=(HttpURLConnection)new URL("https://alphacephei.com/vosk/models/"+NAME+".zip").openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);
        try {
            if(connection.getResponseCode()!=200)throw new IOException("Modelldownload fehlgeschlagen");
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(connection.getInputStream()))){
                ZipEntry entry;long total=0;int count=0;byte[] buffer=new byte[8192];
                while((entry=zip.getNextEntry())!=null){
                    if(++count>2000)throw new IOException("Zu viele Modelldateien");
                    File target=new File(stage,entry.getName());
                    if(!target.getCanonicalPath().startsWith(stage.getCanonicalPath()+File.separator))throw new IOException("Ungültiger Modellpfad");
                    if(entry.isDirectory()){target.mkdirs();continue;}
                    target.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(target)){int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>200000000)throw new IOException("Modell zu gross");out.write(buffer,0,n);}}
                }
            }
            File extracted=new File(stage,NAME);
            if(!new File(extracted,"am/final.mdl").isFile())throw new IOException("Modell unvollständig");
            erase(directory(c));if(!extracted.renameTo(directory(c)))throw new IOException("Modell konnte nicht installiert werden");
        }finally{connection.disconnect();erase(stage);}
    }
    static void erase(File file){if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)erase(child);}file.delete();}
}

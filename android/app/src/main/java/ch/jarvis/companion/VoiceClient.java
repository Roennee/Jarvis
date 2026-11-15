package ch.jarvis.companion;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

final class VoiceClient {
    static String base(Context context) throws Exception {
        String value=context.getSharedPreferences("voice",0).getString("url","");
        URI u=new URI(value);
        if(!"https".equals(u.getScheme())||u.getHost()==null||!u.getHost().endsWith(".ts.net")||u.getPort()!=-1||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!u.getPath().isEmpty())throw new IOException("Tailscale-Adresse zuerst in der App speichern.");
        return value;
    }
    static byte[] request(String base,String endpoint,JSONObject body,String token,int timeout) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(base+endpoint).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(timeout);c.setInstanceFollowRedirects(false);
        try {
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Origin",base);c.setRequestProperty("X-Jarvis-Token",token);try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("HTTP "+status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>20000000)throw new IOException("Antwort zu gross");}return out.toByteArray();}
        }finally{c.disconnect();}
    }
    static JSONObject json(byte[] bytes)throws Exception{return new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
    static String upload(Context context,File file,String mode)throws Exception {
        String base=base(context);
        File receipt=new File(file.getPath()+".receipt");String rid;
        JSONObject status=json(request(base,"/api/status",null,"",30000));
        if(!status.optBoolean("transcription"))throw new IOException("Lokale Transkription fehlt auf Laptop");
        String token=status.getString("csrf");
        if(receipt.exists())rid=Files.readString(receipt.toPath());
        else {
            JSONObject data=new JSONObject().put("mime","audio/wav").put("mode",mode).put("captured_at",java.time.Instant.ofEpochMilli(Long.parseLong(file.getName().split("-")[0])).toString()).put("audio",Base64.encodeToString(Files.readAllBytes(file.toPath()),Base64.NO_WRAP));
            rid=json(request(base,"/api/recordings",data,token,45000)).getString("id");
            Files.writeString(receipt.toPath(),rid);
        }
        JSONObject current=json(request(base,"/api/voice/result?id="+rid,null,"",30000));
        if("saved".equals(current.optString("status"))||"interrupted".equals(current.optString("status")))
            request(base,"/api/transcribe",new JSONObject().put("id",rid),token,30000);
        return rid;
    }
}

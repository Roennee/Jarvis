package ch.jarvis.companion;

import java.net.URI;
import java.util.Locale;

/** One canonical HTTPS origin shared by the dashboard and the microphone client. */
final class JarvisAddress {
    static String normalize(String input)throws Exception{
        String value=input==null?"":input.trim();
        if(value.isEmpty())throw new IllegalArgumentException("Bitte die Tailscale-Adresse deines Laptops eingeben.");
        if(!value.contains("://"))value="https://"+value;
        URI uri=new URI(value);
        String scheme=uri.getScheme(),host=uri.getHost();
        if((!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))||host==null||!host.toLowerCase(Locale.ROOT).endsWith(".ts.net")||uri.getPort()!=-1||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||(!uri.getPath().isEmpty()&&!"/".equals(uri.getPath())))
            throw new IllegalArgumentException("Nur die HTTPS-Tailscale-Adresse ohne Pfad oder Port verwenden, z. B. laptop.tailnetz.ts.net.");
        return "https://"+host.toLowerCase(Locale.ROOT);
    }
}

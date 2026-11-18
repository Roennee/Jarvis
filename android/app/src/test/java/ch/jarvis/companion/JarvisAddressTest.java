package ch.jarvis.companion;
import org.junit.Test;
import static org.junit.Assert.*;
public class JarvisAddressTest {
    @Test public void acceptsHostnameAndAlwaysUsesHttps()throws Exception{
        assertEquals("https://laptop.tailtest.ts.net",JarvisAddress.normalize(" laptop.tailtest.ts.net "));
        assertEquals("https://laptop.tailtest.ts.net",JarvisAddress.normalize("https://LAPTOP.tailtest.ts.net/"));
        assertEquals("https://laptop.tailtest.ts.net",JarvisAddress.normalize("http://laptop.tailtest.ts.net"));
    }
    @Test public void refusesExternalOriginsAndEmbeddedCredentials(){
        String[] invalid={"","https://example.com","https://laptop.tailtest.ts.net.evil.com","https://user@laptop.tailtest.ts.net","https://laptop.tailtest.ts.net:1234","https://laptop.tailtest.ts.net/path","https://laptop.tailtest.ts.net?token=1","https://laptop.tailtest.ts.net#foo","javascript:alert(1)","ftp://laptop.tailtest.ts.net"};
        for(String input:invalid){try{JarvisAddress.normalize(input);fail("Accepted "+input);}catch(Exception expected){}}
    }
}

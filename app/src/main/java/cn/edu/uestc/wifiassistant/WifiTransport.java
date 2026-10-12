package cn.edu.uestc.wifiassistant;

import android.net.Network;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.net.ssl.HttpsURLConnection;

public final class WifiTransport implements SrunClient.Transport {
    private final Network network;
    private final BooleanSupplier active;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private volatile HttpURLConnection connection;
    public WifiTransport(Network network, BooleanSupplier active) { this.network=network; this.active=active; }
    private void check() throws IOException { if (!active.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new IOException("cancelled"); }
    public void cancel() { HttpURLConnection c=connection; if(c != null) c.disconnect(); }
    static boolean school(URL url) {
        return "http".equals(url.getProtocol()) && "10.253.0.235".equals(url.getHost())
                && (url.getPort() == -1 || url.getPort() == 80) && url.getUserInfo() == null;
    }
    private HttpURLConnection open(URL url, boolean campus) throws IOException {
        check();
        if (campus && !school(url)) throw new IOException("unexpected portal host");
        HttpURLConnection c = (HttpURLConnection) network.openConnection(url, Proxy.NO_PROXY);
        connection=c;
        c.setConnectTimeout(6500); c.setReadTimeout(6500);
        c.setUseCaches(false); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Cache-Control", "no-store");
        c.setRequestProperty("User-Agent", "UESTC-WiFi-Assistant/5.0 (Android)");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (campus) {
            c.setRequestProperty("Referer", SrunClient.BASE + "/srun_portal_pc?ac_id=3&theme=pro");
            c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            try {
                for (Map.Entry<String, List<String>> h : cookies.get(url.toURI(), Collections.emptyMap()).entrySet())
                    c.setRequestProperty(h.getKey(), String.join("; ", h.getValue()));
            } catch (Exception e) { c.disconnect(); throw new IOException("cookie processing failed"); }
        }
        check();
        return c;
    }
    @Override public String get(String path, Map<String, String> params) throws IOException {
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("..") || path.contains("?"))
            throw new IOException("invalid campus path");
        StringBuilder query=new StringBuilder();
        for (Map.Entry<String,String> p : params.entrySet()) {
            if (query.length() > 0) query.append('&');
            query.append(URLEncoder.encode(p.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(p.getValue(), "UTF-8"));
        }
        URL url=new URL(SrunClient.BASE + path + (query.length() > 0 ? "?" + query : ""));
        for (int redirect=0; redirect < 4; redirect++) {
            HttpURLConnection c=open(url, true);
            try {
                int status=c.getResponseCode();
                cookies.put(url.toURI(), c.getHeaderFields());
                if (status >= 300 && status < 400) {
                    String location=c.getHeaderField("Location");
                    if (location == null || path.startsWith("/cgi-bin/")) throw new IOException("unexpected API redirect");
                    URL next=new URL(url, location);
                    if (!school(next)) throw new IOException("unexpected redirect host");
                    url=next; continue;
                }
                if (status != 200) throw new IOException("campus HTTP status");
                try (InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[4096]; int count;
                    while ((count=in.read(buffer)) != -1) {
                        check();
                        if (out.size()+count > 1024*1024) throw new IOException("response too large");
                        out.write(buffer,0,count);
                    }
                    return out.toString(StandardCharsets.UTF_8.name());
                }
            } catch (java.net.URISyntaxException e) { throw new IOException("invalid response URI"); }
            finally { c.disconnect(); if(connection == c) connection=null; }
        }
        throw new IOException("too many redirects");
    }
    @Override public boolean internet() throws IOException {
        // No account, password, cookies, campus query or referrer is ever sent to this endpoint.
        HttpURLConnection c=open(new URL("https://www.baidu.com/"), false);
        try {
            if (!(c instanceof HttpsURLConnection)) return false;
            return c.getResponseCode() == 200;
        } finally { c.disconnect(); if(connection == c) connection=null; }
    }
    @Override public void pause(long millis) throws IOException {
        check();
        try { Thread.sleep(millis); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("cancelled"); }
        check();
    }
}

package cn.edu.uestc.wifiassistant;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class WifiTracker {
    public static final String TARGET = "Xiaomi_181A";
    public static final class Snapshot {
        public final Network network;
        public final String ssid;
        public final boolean validated, permission, location;
        Snapshot(Network network, String ssid, boolean validated, boolean permission, boolean location) {
            this.network=network; this.ssid=ssid; this.validated=validated; this.permission=permission; this.location=location;
        }
        public boolean target() { return network != null && TARGET.equals(ssid) && permission && location; }
    }
    private final Context context;
    private final ConnectivityManager cm;
    private final WifiManager wifi;
    private final Handler handler;
    private final Runnable changed;
    private final ConcurrentHashMap<Network, NetworkCapabilities> networks = new ConcurrentHashMap<>();
    private ConnectivityManager.NetworkCallback callback;
    public WifiTracker(Context context, Handler handler, Runnable changed) {
        this.context=context.getApplicationContext(); this.handler=handler; this.changed=changed;
        cm=context.getSystemService(ConnectivityManager.class);
        wifi=(WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        restart();
    }
    public boolean permission() { return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    public boolean location() {
        LocationManager manager=context.getSystemService(LocationManager.class);
        if(manager == null) return false;
        if(Build.VERSION.SDK_INT >= 28) return manager.isLocationEnabled();
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
    public void restart() {
        if (cm == null) return;
        if(callback != null) { try { cm.unregisterNetworkCallback(callback); } catch(IllegalArgumentException ignored) {} }
        networks.clear();
        if(Build.VERSION.SDK_INT >= 31) {
            callback=new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { accept(n,c); }
                @Override public void onLost(Network n) { lost(n); }
            };
        } else callback=legacyCallback();
        try { cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback, handler); }
        catch (SecurityException | IllegalArgumentException ignored) { callback=null; }
        handler.post(changed);
    }
    private void accept(Network network, NetworkCapabilities caps) { networks.put(network,caps); handler.post(changed); }
    private void lost(Network network) { networks.remove(network); handler.post(changed); }
    private ConnectivityManager.NetworkCallback legacyCallback() {
        return new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { accept(n,c); }
            @Override public void onLost(Network n) { lost(n); }
        };
    }

    private static String name(WifiInfo info) {
        if(info == null) return "";
        String s=info.getSSID();
        if(s == null || s.equals(WifiManager.UNKNOWN_SSID) || s.equals("<unknown ssid>")) return "";
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1,s.length()-1) : s;
    }
    @SuppressLint("MissingPermission") @SuppressWarnings("deprecation")
    public Snapshot snapshot() {
        boolean permission=permission(), location=location();
        if(cm == null) return new Snapshot(null,"",false,permission,location);
        List<Network> list=new ArrayList<>();
        for(Network n : cm.getAllNetworks()) {
            NetworkCapabilities c=cm.getNetworkCapabilities(n);
            if(c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) list.add(n);
        }
        Snapshot best=new Snapshot(null,"",false,permission,location);
        for(Network n : list) {
            NetworkCapabilities fresh=cm.getNetworkCapabilities(n);
            if(fresh == null) continue;
            NetworkCapabilities cached=networks.get(n);
            String ssid="";
            if(permission && location) {
                if(Build.VERSION.SDK_INT >= 29 && cached != null && cached.getTransportInfo() instanceof WifiInfo)
                    ssid=name((WifiInfo)cached.getTransportInfo());
                if(ssid.isEmpty() && Build.VERSION.SDK_INT >= 29 && fresh.getTransportInfo() instanceof WifiInfo)
                    ssid=name((WifiInfo)fresh.getTransportInfo());
                // Legacy API also provides the primary SSID on OEM devices. Never assign it to a second Wi-Fi network.
                if(ssid.isEmpty() && list.size() == 1 && wifi != null) {
                    try { ssid=name(wifi.getConnectionInfo()); } catch(SecurityException ignored) {}
                }
            }
            Snapshot s=new Snapshot(n,ssid,fresh.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),permission,location);
            if(s.target()) return s;
            if(best.network == null || !ssid.isEmpty()) best=s;
        }
        return best;
    }
    public boolean stillTarget(Network n) { Snapshot s=snapshot(); return s.target() && n.equals(s.network); }
}

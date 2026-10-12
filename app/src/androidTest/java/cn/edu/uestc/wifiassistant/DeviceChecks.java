package cn.edu.uestc.wifiassistant;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.security.NetworkSecurityPolicy;
import android.util.Base64;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** Installed only as a separate test APK. All test credentials are synthetic and isolated. */
public final class DeviceChecks extends Instrumentation {
    private final List<String> report=new ArrayList<>();
    private int failures;
    interface TestBody { void run() throws Exception; }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void assertTrue(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private void test(String name,TestBody body) {
        try { body.run(); report.add("PASS "+name); }
        catch(Throwable e) { failures++; report.add("FAIL "+name+" ("+e.getClass().getSimpleName()+(e instanceof AssertionError ? ": "+e.getMessage() : "")+")"); }
        Bundle update=new Bundle(); update.putString("stream",report.get(report.size()-1)+"\n"); sendStatus(0,update);
    }
    @Override public void onStart() {
        Context context=getTargetContext();
        SharedPreferences real=context.getSharedPreferences(CredentialVault.PREFS,Context.MODE_PRIVATE);
        Map<String,?> before=real.getAll();
        String testAlias=CredentialVault.ALIAS+"_device_tests";
        String testPrefs=CredentialVault.PREFS+"_device_tests";
        CredentialVault vault=new CredentialVault(context,testAlias,testPrefs);
        SharedPreferences prefs=context.getSharedPreferences(testPrefs,Context.MODE_PRIVATE);
        test("Owner consent required",() -> {
            try(Credentials c=new Credentials("202600000000","@dx","Synthetic!123".toCharArray())) {
                boolean rejected=false;
                try { vault.save(c,false); } catch(IllegalArgumentException expected) { rejected=true; }
                assertTrue(rejected && !vault.hasCredentials(),"unapproved credential saved");
            }
        });
        test("Android Keystore AES-GCM roundtrip, fresh IV, no plaintext and key non-exportability",() -> {
            try(Credentials c=new Credentials("202600000000","@dx","Synthetic!123汉字".toCharArray())) {
                vault.save(c,true);
                String iv=prefs.getString("iv","");
                String first=prefs.getString("ciphertext","");
                assertTrue(!prefs.getAll().toString().contains("Synthetic") && !prefs.getAll().toString().contains("202600000000"),"plaintext persisted");
                try(Credentials loaded=vault.load()) { assertTrue(loaded != null && Arrays.equals(c.password,loaded.password) && c.account.equals(loaded.account),"roundtrip"); }
                vault.save(c,true);
                assertTrue(!iv.equals(prefs.getString("iv","")) && !first.equals(prefs.getString("ciphertext","")),"IV reused");
                KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
                assertTrue(store.getKey(testAlias,null).getEncoded() == null,"key exportable");
            }
        });
        test("AES-GCM rejects tampering",() -> {
            String previous=prefs.getString("ciphertext","");
            byte[] data=Base64.decode(previous,Base64.NO_WRAP); data[0]^=1;
            prefs.edit().putString("ciphertext",Base64.encodeToString(data,Base64.NO_WRAP)).commit();
            boolean rejected=false;
            try { Credentials unexpected=vault.load(); if(unexpected != null) unexpected.close(); } catch(Exception expected) { rejected=true; }
            prefs.edit().putString("ciphertext",previous).commit();
            assertTrue(rejected,"tampered blob accepted");
        });
        test("Clear deletes both stored authorization and Keystore entry",() -> {
            vault.clear();
            KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
            assertTrue(!vault.hasCredentials() && prefs.getAll().isEmpty() && !store.containsAlias(testAlias),"revocation incomplete");
            assertTrue(vault.load() == null,"credential remains usable");
        });
        test("Cleartext traffic restricted to campus IP",() -> {
            assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("10.253.0.235"),"campus HTTP blocked");
            assertTrue(!NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.com"),"external HTTP allowed");
        });
        test("Main Activity starts and survives recreation",() -> {
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            MainActivity main=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            assertTrue(main.findViewById(R.id.main_button) != null && main.findViewById(R.id.wifi_name) != null,"home views missing");
            runOnMainSync(main::recreate); waitForIdleSync(); Thread.sleep(800); waitForIdleSync();
            Activity recreated=monitor.getLastActivity();
            assertTrue(recreated != null && recreated != main,"recreation missing");
            runOnMainSync(recreated::finish); removeMonitor(monitor);
        });
        test("Settings screen masks password, forbids autofill/state restoration, requires consent",() -> {
            SettingsActivity settings=(SettingsActivity)startActivitySync(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(() -> {
                EditText secret=settings.findViewById(R.id.password_input);
                CheckBox consent=settings.findViewById(R.id.owner_consent);
                assertTrue(!secret.isSaveEnabled(),"password state saved");
                assertTrue(!consent.isChecked(),"consent preselected");
                assertTrue((settings.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0,"screenshots not protected");
                settings.findViewById(R.id.save_button).performClick();
                settings.finish();
            });
        });
        test("Target SSID detected with user-granted permission",() -> {
            AppController controller=((AssistantApp)context.getApplicationContext()).controller;
            WifiTracker.Snapshot snapshot=controller.tracker.snapshot();
            assertTrue(snapshot.permission && snapshot.location && snapshot.target() && "Xiaomi_181A".equals(snapshot.ssid),"target SSID not available");
        });
        test("Official Android Wi-Fi selection opens and returns without disconnecting",() -> {
            ActivityMonitor monitor=addMonitor(new android.content.IntentFilter(android.provider.Settings.Panel.ACTION_WIFI),null,false);
            MainActivity main=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); Thread.sleep(700);
            runOnMainSync(() -> main.findViewById(R.id.wifi_button).performClick());
            Thread.sleep(800);
            assertTrue(monitor.getHits() > 0,"official Wi-Fi panel intent not issued");
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            assertTrue(root != null && !"cn.edu.uestc.wifiassistant".contentEquals(root.getPackageName()),"system selector not shown");
            getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            Thread.sleep(600); waitForIdleSync();
            AppController controller=((AssistantApp)context.getApplicationContext()).controller;
            assertTrue(controller.tracker.snapshot().target(),"current Wi-Fi unexpectedly changed");
            runOnMainSync(main::finish); removeMonitor(monitor);
        });
        test("Live campus check on Wi-Fi performs zero credential loads and no login",() -> {
            ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
            Network wifi=null;
            for(Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps=cm.getNetworkCapabilities(n);
                if(caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { wifi=n; break; }
            }
            assertTrue(wifi != null,"no Wi-Fi network");
            WifiTransport t=new WifiTransport(wifi,() -> true);
            SrunClient.Result result=new SrunClient(t,() -> true,() -> { throw new AssertionError("login slot requested"); }).run(false,"",() -> { throw new AssertionError("credential loaded"); });
            assertTrue(result.kind == SrunClient.Kind.ONLINE || result.kind == SrunClient.Kind.AUTHENTICATED_NO_INTERNET,"campus online check failed");
        });
        test("Real user authorization untouched by test suite",() -> assertTrue(before.equals(real.getAll()),"real user preferences changed"));
        try { vault.clear(); } catch(Exception ignored) {}
        Bundle results=new Bundle();
        results.putString("stream","\n"+String.join("\n",report)+"\nChecks: "+report.size()+", failures: "+failures+"\n");
        results.putInt("failures",failures); results.putInt("checks",report.size());
        finish(failures == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED,results);
    }
}

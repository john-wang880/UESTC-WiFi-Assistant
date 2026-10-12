package cn.edu.uestc.wifiassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class AppController {
    public interface Listener { void changed(State state); }
    public interface Done { void complete(boolean ok, String message); }
    public static final class State {
        public final WifiTracker.Snapshot wifi;
        public final String title, detail;
        public final boolean busy;
        public final SrunClient.Kind kind;
        State(WifiTracker.Snapshot wifi,String title,String detail,boolean busy,SrunClient.Kind kind) {
            this.wifi=wifi; this.title=title; this.detail=detail; this.busy=busy; this.kind=kind;
        }
    }
    private final Context context;
    public final Handler main=new Handler(Looper.getMainLooper());
    public final WifiTracker tracker;
    public final CredentialVault vault;
    public final SharedPreferences settings;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger epoch=new AtomicInteger();
    private final AtomicLong lastAttempt=new AtomicLong(-60000);
    private final Set<Listener> listeners=new LinkedHashSet<>();
    private volatile WifiTransport transport;
    private Network operationNetwork;
    private State state;
    private boolean busy, waitingWifi;
    public AppController(Context context) {
        this.context=context.getApplicationContext();
        vault=new CredentialVault(context);
        settings=context.getSharedPreferences("app_settings",Context.MODE_PRIVATE);
        tracker=new WifiTracker(context,main,this::networkChanged);
        state=idle();
    }
    public void attach(Listener listener) { listeners.add(listener); listener.changed(state); }
    public void detach(Listener listener) { listeners.remove(listener); }
    private void publish(State next) {
        state=next;
        for(Listener l : new LinkedHashSet<>(listeners)) l.changed(next);
    }
    private State idle() {
        WifiTracker.Snapshot w=tracker.snapshot();
        String title,detail;
        if(!w.permission) { title="准备连接宿舍网络"; detail="点击按钮允许读取 Wi-Fi 名称，随后自动检查联网状态。"; }
        else if(!w.location) { title="请开启系统定位"; detail="Android 要求开启系统定位才能读取 Wi-Fi 名称。开启后返回继续，App 不记录位置。"; }
        else if(w.network == null) { title="尚未连接 Wi-Fi"; detail="点击按钮，在系统窗口选择 Xiaomi_181A，返回后继续认证。"; }
        else if(!w.target()) { title=w.ssid.isEmpty() ? "正在读取 Wi-Fi 名称" : "请切换宿舍 Wi-Fi"; detail="目标网络为 Xiaomi_181A；只在确认目标 Wi-Fi 后使用授权凭据。"; }
        else if(w.validated) { title="网络已连接"; detail="Android 已验证当前 Wi-Fi 可联网，未提交登录请求。"; }
        else { title="等待检查校园网"; detail="已连接宿舍路由器，点击按钮检查认证与互联网状态。"; }
        return new State(w,title,detail,false,w.target() && w.validated ? SrunClient.Kind.ONLINE : null);
    }
    private void networkChanged() {
        if(tracker == null) return;
        WifiTracker.Snapshot w=tracker.snapshot();
        if(busy && operationNetwork != null && (!w.target() || !operationNetwork.equals(w.network))) {
            cancel();
            publish(new State(w,"连接已变化","认证已停止，请重新连接 Xiaomi_181A 后再试。",false,SrunClient.Kind.CANCELLED));
            return;
        }
        if(waitingWifi && w.target() && !busy) { waitingWifi=false; execute(true); return; }
        if(!busy && (!w.target() || state == null || state.kind == null)) publish(idle());
        else if(!busy && state != null) publish(new State(w,state.title,state.detail,false,state.kind));
    }
    public void detect() { if(!busy) execute(false); }
    public void checkOnEntry() { if(!busy) execute(vault.hasCredentials() && settings.getBoolean("auto_on_entry",true)); }
    public void waitForWifi() {
        waitingWifi=true;
        publish(new State(tracker.snapshot(),"请在系统中选择 Wi-Fi","选择 Xiaomi_181A 后返回，连接完成后会继续检测与认证。",false,null));
    }
    public void message(String title,String detail) { if(!busy) publish(new State(tracker.snapshot(),title,detail,false,null)); }
    public void cancel() {
        epoch.incrementAndGet(); waitingWifi=false;
        WifiTransport t=transport; if(t != null) t.cancel();
        transport=null; busy=false; operationNetwork=null;
    }
    private boolean takeSlot() {
        long now=SystemClock.elapsedRealtime();
        long previous=lastAttempt.get();
        return now-previous >= 30000 && lastAttempt.compareAndSet(previous,now);
    }
    public void execute(boolean allowLogin) {
        if(busy) return;
        WifiTracker.Snapshot w=tracker.snapshot();
        if(!w.target()) { publish(idle()); return; }

        busy=true; waitingWifi=false; operationNetwork=w.network;
        int token=epoch.incrementAndGet();
        WifiTransport t=new WifiTransport(w.network,() -> epoch.get() == token && tracker.stillTarget(w.network));
        transport=t;
        publish(new State(w,"正在检查并认证","正在通过宿舍 Wi-Fi 检查学校在线状态及外网连接…",true,null));
        String acid=settings.getString("ac_override","");
        worker.execute(() -> {
            SrunClient client=new SrunClient(t,() -> epoch.get() == token && tracker.stillTarget(w.network),this::takeSlot);
            SrunClient.Result result=client.run(allowLogin,acid,vault::load);
            main.post(() -> {
                if(epoch.get() != token) return;
                busy=false; transport=null; operationNetwork=null;
                if(result.success()) {
                    ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
                    if(cm != null) { try { cm.reportNetworkConnectivity(w.network,true); } catch(SecurityException ignored) {} }
                }
                publish(new State(tracker.snapshot(),result.title,result.detail,false,result.kind));
            });
        });
    }
    public void save(Credentials credentials, boolean confirmed, Done done) {
        cancel();
        worker.execute(() -> {
            boolean ok; String message;
            try { vault.save(credentials,confirmed); ok=true; message="授权信息已加密保存"; }
            catch(Exception e) { ok=false; message="加密保存失败，请检查本机安全设置后重试。"; }
            finally { credentials.close(); }
            boolean result=ok; String text=message;
            main.post(() -> { publish(idle()); done.complete(result,text); });
        });
    }
    public void clear(Done done) {
        cancel();
        worker.execute(() -> {
            boolean ok; String text;
            try { vault.clear(); ok=true; text="授权信息和本机密钥已删除"; }
            catch(Exception e) { ok=false; text="清除未完成，请在系统设置中清除本 App 数据。"; }
            boolean result=ok; String message=text;
            main.post(() -> { publish(idle()); done.complete(result,message); });
        });
    }
}

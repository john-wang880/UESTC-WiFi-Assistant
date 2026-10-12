package cn.edu.uestc.wifiassistant;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Ui implements AppController.Listener {
    private TextView wifiName,title,detail,pill;
    private Button connect;
    private WifiGlyph glyph;
    private boolean requested,permissionRequested,pickerPending,firstEntry=true;
    private SrunClient.Kind shownKind;
    private final Runnable resumed=() -> {
        if(isFinishing() || isDestroyed()) return;
        if(requested && !permissionRequested) connectFlow();
        else if(firstEntry) { firstEntry=false; controller.checkOnEntry(); }
        else controller.checkOnEntry();
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(state != null) { requested=state.getBoolean("requested"); pickerPending=state.getBoolean("picker"); firstEntry=false; }
        page();
        LinearLayout toolbar=row();
        TextView brand=text("UESTC  /  CAMPUS",12,MINT,true); brand.setLetterSpacing(0.10f);
        toolbar.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1)); brand.setGravity(Gravity.CENTER_VERTICAL);
        Button settings=button("设置",false); settings.setId(R.id.settings_button); settings.setContentDescription("打开账号与高级设置");
        toolbar.addView(settings,new LinearLayout.LayoutParams(dp(76),dp(48)));
        settings.setOnClickListener(v -> { requested=false; startActivity(new Intent(this,SettingsActivity.class)); });
        add(body,toolbar); gap(body,20);
        add(body,text("成电 Wi-Fi",30,WHITE,true)); gap(body,8);
        add(body,text("认证助手 · 清水河校区宿舍",14,MUTED,false)); gap(body,26);
        LinearLayout network=card();
        TextView label=text("当前 WI-FI",11,MUTED,true); label.setLetterSpacing(0.1f); add(network,label); gap(network,8);
        wifiName=text("等待检测",23,WHITE,true); wifiName.setId(R.id.wifi_name); wifiName.setSingleLine(true); wifiName.setEllipsize(TextUtils.TruncateAt.END);
        add(network,wifiName); gap(network,12);
        pill=text("●  目标 Xiaomi_181A",12,MINT,false); add(network,pill); add(body,network); gap(body,8);
        glyph=new WifiGlyph(this); body.addView(glyph,new LinearLayout.LayoutParams(-1,dp(138)));
        title=text("准备连接宿舍网络",24,WHITE,true); title.setId(R.id.status_title); title.setGravity(Gravity.CENTER); title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(body,title); gap(body,10);
        detail=text("点击按钮开始",14,MUTED,false); detail.setId(R.id.status_detail); detail.setGravity(Gravity.CENTER); add(body,detail); gap(body,26);
        connect=button("连接并认证",true); connect.setId(R.id.main_button);
        body.addView(connect,new LinearLayout.LayoutParams(-1,dp(64)));
        connect.setOnClickListener(v -> { requested=true; shownKind=null; connectFlow(); });
        gap(body,14);
        TextView tip=text("确认目标 Wi-Fi 后，仅向学校提交授权凭据",11,MUTED,false); tip.setGravity(Gravity.CENTER); add(body,tip); gap(body,22);
        LinearLayout actions=row();
        Button official=button("官方认证页",false); official.setId(R.id.official_button);
        Button wifi=button("选取 Wi-Fi",false); wifi.setId(R.id.wifi_button);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(50),1); left.setMargins(0,0,dp(10),0);
        actions.addView(official,left); actions.addView(wifi,new LinearLayout.LayoutParams(0,dp(50),1)); add(body,actions);
        official.setOnClickListener(v -> openOfficial());
        wifi.setOnClickListener(v -> { requested=true; controller.waitForWifi(); openWifi(); });
        gap(body,24);
        TextView foot=text("本机加密保存  ·  无广告与跟踪\n首次配置需账号持有人亲自授权",11,MUTED,false); foot.setGravity(Gravity.CENTER); add(body,foot);
    }
    @Override protected void onStart() { super.onStart(); controller.attach(this); }
    @Override protected void onResume() { super.onResume(); controller.main.postDelayed(resumed,350); }
    @Override protected void onStop() { controller.main.removeCallbacks(resumed); controller.detach(this); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("requested",requested); state.putBoolean("picker",pickerPending); super.onSaveInstanceState(state); }
    @Override public void changed(AppController.State state) {
        if(wifiName == null || isDestroyed()) return;
        WifiTracker.Snapshot w=state.wifi;
        wifiName.setText(!w.permission ? "读取名称需授权" : !w.location ? "请开启系统定位" : w.network == null ? "未连接 Wi-Fi" : w.ssid.isEmpty() ? "名称暂不可读取" : w.ssid);
        title.setText(state.title); detail.setText(state.detail);
        boolean success=state.kind == SrunClient.Kind.ONLINE || state.kind == SrunClient.Kind.AUTHENTICATED;
        boolean failure=state.kind == SrunClient.Kind.AUTH_FAILED || state.kind == SrunClient.Kind.NETWORK_FAILED || state.kind == SrunClient.Kind.KEY_FAILED || state.kind == SrunClient.Kind.PROTOCOL_CHANGED;
        int color=success ? MINT : failure ? RED : AMBER;
        glyph.color(color);
        pill.setText(getString(R.string.status_pill, state.busy ? "正在检查与认证" : success ? "宿舍网络可用" : w.target() ? "已连接宿舍路由器" : "目标 Xiaomi_181A")); pill.setTextColor(color);
        connect.setEnabled(!state.busy); connect.setAlpha(state.busy ? 0.55f : 1f);
        connect.setText(state.busy ? "正在检查并认证…" : "连接并认证");
        if(!state.busy && state.kind != null && requested) {
            requested=false; pickerPending=false;
            if(state.kind == SrunClient.Kind.NEED_CREDENTIALS && shownKind != state.kind && hasWindowFocus()) {
                shownKind=state.kind;
                show(new AlertDialog.Builder(this).setTitle("需要账号持有人授权")
                        .setMessage("请由账号持有人在设置页亲自输入账号、密码并确认授权。")
                        .setPositiveButton("打开设置",(d,which) -> startActivity(new Intent(this,SettingsActivity.class)))
                        .setNegativeButton("稍后",null));
            } else if((state.kind == SrunClient.Kind.AUTH_FAILED || state.kind == SrunClient.Kind.PROTOCOL_CHANGED) && shownKind != state.kind && hasWindowFocus()) {
                shownKind=state.kind;
                show(new AlertDialog.Builder(this).setTitle(state.title).setMessage(state.detail)
                        .setPositiveButton("打开官方认证页",(d,which) -> openOfficial())
                        .setNegativeButton("留在首页",null));
            }
        }
    }
    private void connectFlow() {
        if(permissionRequested) return;
        if(!controller.tracker.permission()) {
            permissionRequested=true;
            show(new AlertDialog.Builder(this).setTitle("读取当前 Wi-Fi 名称")
                    .setMessage("Android 需要精确位置权限才能读取 Wi-Fi 名称。App 只核对是否连接 Xiaomi_181A，不读取 GPS 坐标或记录位置。请在系统弹窗中选择「精确位置」及「使用应用时允许」。")
                    .setPositiveButton("继续",(d,w) -> {
                        if(!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) && controller.settings.getBoolean("permission_asked",false)) {
                            permissionRequested=false; openAppSettings();
                        } else {
                            controller.settings.edit().putBoolean("permission_asked",true).apply();
                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},11);
                        }
                    }).setNegativeButton("取消",(d,w) -> { permissionRequested=false; requested=false; })
                    .setOnCancelListener(d -> { permissionRequested=false; requested=false; }));
            return;
        }
        if(!controller.tracker.location()) {
            requested=false;
            show(new AlertDialog.Builder(this).setTitle("开启系统定位以读取 Wi-Fi")
                    .setMessage("系统定位关闭时，Android 会隐藏 Wi-Fi 名称。开启后返回 App 再点击按钮继续。")
                    .setPositiveButton("打开定位设置",(d,w) -> launch(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("取消",null)); return;
        }
        WifiTracker.Snapshot w=controller.tracker.snapshot();
        if(!w.target()) {
            if(pickerPending) { requested=false; controller.message("尚未连接目标 Wi-Fi","请在系统 Wi-Fi 设置选择 Xiaomi_181A，并保持连接，返回后再次点击按钮。"); return; }
            controller.waitForWifi(); openWifi(); return;
        }
        pickerPending=false;
        controller.execute(true);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode != 11) return;
        permissionRequested=false;
        if(controller.tracker.permission()) { controller.tracker.restart(); controller.main.postDelayed(resumed,450); }
        else { requested=false; controller.message("需要 Wi-Fi 名称读取权限","请在应用权限中允许精确位置，再点击「连接并认证」。未读取 GPS 位置，也未提交凭据。"); }
    }
    private void openWifi() {
        pickerPending=true;
        Intent intent=new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS);
        try { startActivity(intent); }
        catch(ActivityNotFoundException e) { launch(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
    }
    private void openAppSettings() { launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))); }
    private void launch(Intent intent) {
        try { startActivity(intent); }
        catch(ActivityNotFoundException e) { requested=false; controller.message("系统页面无法打开","请手动进入系统设置，完成 Wi-Fi 或权限设置后返回。"); }
    }
    private void openOfficial() {
        requested=false;
        WifiTracker.Snapshot w=controller.tracker.snapshot();
        if(!w.target()) controller.message("请先连接宿舍 Wi-Fi","官方认证页需要通过 Xiaomi_181A 访问。可点击「选取 Wi-Fi」完成连接。");
        launch(new Intent(Intent.ACTION_VIEW,Uri.parse(SrunClient.BASE+"/")));
    }
}

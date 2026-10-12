package cn.edu.uestc.wifiassistant;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Arrays;

public final class SettingsActivity extends Ui {
    private EditText account,password,acid;
    private Spinner operator;
    private CheckBox consent;
    private Button save,clear;
    private TextView saved;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        page();
        Button back=button("←  返回首页",false); back.setOnClickListener(v -> finish()); add(body,back); gap(body,25);
        add(body,text("账号与设置",28,WHITE,true)); gap(body,8);
        add(body,text("由账号持有人亲自配置授权",14,MUTED,false)); gap(body,24);
        LinearLayout credentials=card();
        add(credentials,text("账号授权",18,WHITE,true)); gap(credentials,8);
        saved=text(controller.vault.hasCredentials() ? "已保存本机授权 · 密码不会回显" : "尚未保存授权信息",13,MINT,false); add(credentials,saved); gap(credentials,18);
        add(credentials,text("运营商",12,MUTED,false)); gap(credentials,8);
        operator=new Spinner(this); operator.setId(R.id.operator_spinner); operator.setContentDescription("选择运营商");
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"电信  @dx","移动  @cmcc"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); operator.setAdapter(adapter);
        operator.setSelection(controller.settings.getString("carrier_hint","@dx").equals("@cmcc") ? 1 : 0);
        operator.setBackground(shape(BG,14,true)); operator.setPadding(dp(10),0,dp(10),0);
        credentials.addView(operator,new LinearLayout.LayoutParams(-1,dp(52))); gap(credentials,16);
        add(credentials,text("校园网账号",12,MUTED,false)); gap(credentials,8);
        account=input("学号或所选运营商的完整账号",false); account.setId(R.id.account_input); account.setContentDescription("校园网账号"); add(credentials,account); gap(credentials,16);
        add(credentials,text("密码",12,MUTED,false)); gap(credentials,8);
        password=input("由账号持有人输入",true); password.setId(R.id.password_input); password.setContentDescription("校园网密码"); add(credentials,password); gap(credentials,18);
        consent=new CheckBox(this); consent.setId(R.id.owner_consent); consent.setTextColor(WHITE); consent.setTextSize(13); consent.setSaveEnabled(false);
        consent.setText(R.string.owner_consent);
        add(credentials,consent); gap(credentials,14);
        save=button("确认授权并加密保存",true); save.setId(R.id.save_button); credentials.addView(save,new LinearLayout.LayoutParams(-1,dp(56)));
        save.setOnClickListener(v -> saveCredentials()); gap(credentials,12);
        clear=button("清除授权信息",false); clear.setId(R.id.clear_button); clear.setTextColor(RED);
        credentials.addView(clear,new LinearLayout.LayoutParams(-1,dp(50))); clear.setOnClickListener(v -> clearCredentials());
        add(body,credentials); gap(body,20);
        LinearLayout privacy=card(); add(privacy,text("安全与隐私",17,WHITE,true)); gap(privacy,12);
        add(privacy,text("账号和密码使用 Android Keystore + AES-GCM 加密保存，仅用于学校认证，不发送给第三方，不加入广告或跟踪。清除授权会删除密文及对应密钥。",13,MUTED,false)); gap(privacy,12);
        add(privacy,text("即使使用 Keystore，也不能保证手机持有人绝对无法使用已授权凭据。本 App 在授权后能够调用凭据；仅应授权给信任的手机。",13,AMBER,false)); gap(privacy,12);
        add(privacy,text("学校当前认证地址为 HTTP。SRun 的 HMAC、XEncode 和 Base64 是协议参数处理，并非 HTTPS 传输加密；同一网络的攻击者可能截获或篡改认证通信。",13,AMBER,false)); gap(privacy,12);
        add(privacy,text("连通性检测使用 Android 网络验证及一次不携带账号、密码或学校 Cookie 的 HTTPS 请求（www.baidu.com）。只在确认目标 Wi-Fi 后认证，网络失败不会自动循环登录。",12,MUTED,false));
        add(body,privacy); gap(body,20);
        LinearLayout advanced=card(); add(advanced,text("高级设置",17,WHITE,true)); gap(advanced,16);
        Switch automatic=new Switch(this); automatic.setId(R.id.auto_switch); automatic.setText("进入首页自动检查并认证"); automatic.setTextSize(14); automatic.setTextColor(WHITE);
        automatic.setChecked(controller.settings.getBoolean("auto_on_entry",true)); add(advanced,automatic); gap(advanced,10);
        add(advanced,text("已保存授权且连接目标 Wi-Fi 时生效。不会在后台定时登录，认证失效后可打开 App 或点击主按钮。",12,MUTED,false)); gap(advanced,16);
        add(advanced,text("接入编号（留空为自动）",12,MUTED,false)); gap(advanced,8);
        acid=input("自动读取学校页面，宿舍当前为 3",false); acid.setId(R.id.ac_input); acid.setInputType(InputType.TYPE_CLASS_NUMBER);
        acid.setText(controller.settings.getString("ac_override","")); add(advanced,acid); gap(advanced,10);
        add(advanced,text("仅在学校要求时手动覆盖，范围 1–4095。认证服务固定为 10.253.0.235，防止误将凭据发往其他服务器。",12,MUTED,false)); gap(advanced,16);
        Button options=button("保存高级设置",false); advanced.addView(options,new LinearLayout.LayoutParams(-1,dp(50)));
        options.setOnClickListener(v -> {
            String value=acid.getText().toString().trim();
            if(!value.isEmpty() && (!value.matches("[0-9]{1,4}") || Integer.parseInt(value) < 1 || Integer.parseInt(value) > 4095)) {
                acid.setError("请输入 1–4095，或留空自动检测"); return;
            }
            controller.cancel(); controller.settings.edit().putString("ac_override",value).putBoolean("auto_on_entry",automatic.isChecked()).apply();
            toast("设置已保存");
        });
        add(body,advanced); gap(body,24);
        TextView version=text("版本 5.0.0 · 标准 Android Gradle 构建\n个人工具，与学校官方客户端无隶属关系",11,MUTED,false); version.setGravity(Gravity.CENTER); add(body,version);
    }
    private void saveCredentials() {
        if(!consent.isChecked()) { toast("请由账号持有人亲自确认授权"); return; }
        String suffix=operator.getSelectedItemPosition() == 1 ? "@cmcc" : "@dx";
        String username=account.getText().toString().trim();
        try { SrunCrypto.username(username,suffix); } catch(IllegalArgumentException e) { account.setError(e.getMessage()); return; }
        int length=password.getText().length();
        if(length == 0 || length > 256) { password.setError("请输入 1–256 个字符的密码"); return; }
        char[] secret=new char[length]; password.getText().getChars(0,length,secret,0);
        Credentials credentials=new Credentials(username,suffix,secret); Arrays.fill(secret,'\0');
        password.getText().clear(); account.getText().clear(); consent.setChecked(false); save.setEnabled(false); clear.setEnabled(false);
        controller.save(credentials,true,(ok,message) -> {
            if(isFinishing() || isDestroyed()) return;
            save.setEnabled(true); clear.setEnabled(true); toast(message);
            if(ok) { controller.settings.edit().putString("carrier_hint",suffix).apply(); saved.setText("已保存本机授权 · 密码不会回显"); finish(); }
        });
    }
    private void clearCredentials() {
        show(new AlertDialog.Builder(this).setTitle("清除授权信息")
                .setMessage("删除本机加密凭据和密钥，并停止未完成认证。不会注销宿舍当前网络或影响室友的在线连接。")
                .setPositiveButton("清除",(dialog,which) -> {
                    password.getText().clear(); account.getText().clear(); consent.setChecked(false); save.setEnabled(false); clear.setEnabled(false);
                    controller.clear((ok,message) -> {
                        if(isFinishing() || isDestroyed()) return;
                        save.setEnabled(true); clear.setEnabled(true); saved.setText(controller.vault.hasCredentials() ? "授权信息仍需清除" : "尚未保存授权信息"); toast(message);
                    });
                }).setNegativeButton("取消",null));
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    @Override protected void onStop() { password.getText().clear(); super.onStop(); }
}

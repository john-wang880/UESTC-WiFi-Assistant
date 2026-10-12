package cn.edu.uestc.wifiassistant;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class Ui extends Activity {
    static final int BG=Color.rgb(12,17,26), CARD=Color.rgb(21,30,43), LINE=Color.rgb(39,54,73);
    static final int WHITE=Color.rgb(237,244,253), MUTED=Color.rgb(151,168,188), MINT=Color.rgb(119,228,208);
    static final int AMBER=Color.rgb(255,206,138), RED=Color.rgb(255,159,149);
    protected LinearLayout body;
    protected AppController controller;
    private final List<AlertDialog> dialogs=new ArrayList<>();
    static int dp(Context c,float v) { return Math.round(v*c.getResources().getDisplayMetrics().density); }
    int dp(float v) { return dp(this,v); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        controller=((AssistantApp)getApplication()).controller;
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    void show(AlertDialog.Builder builder) {
        AlertDialog dialog=builder.create(); dialogs.add(dialog);
        dialog.setOnDismissListener(d -> dialogs.remove(dialog)); dialog.show();
    }
    @Override protected void onDestroy() {
        for(AlertDialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
        dialogs.clear(); super.onDestroy();
    }
    void page() {
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            if(Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(i.left,i.top,i.right,i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        body=column(); body.setPadding(dp(24),dp(18),dp(24),dp(26));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        setContentView(scroll); scroll.requestApplyInsets();
    }
    LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(3),1);
        if(bold) t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }
    void gap(LinearLayout parent,int height) { parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    void add(LinearLayout parent,View view) { parent.addView(view,new LinearLayout.LayoutParams(-1,-2)); }
    GradientDrawable shape(int color,int radius,boolean border) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if(border) d.setStroke(dp(1),LINE); return d;
    }
    LinearLayout card() { LinearLayout c=column(); c.setPadding(dp(20),dp(20),dp(20),dp(20)); c.setBackground(shape(CARD,22,true)); return c; }
    Button button(String title,boolean primary) {
        Button b=new Button(this); b.setText(title); b.setAllCaps(false); b.setTextSize(primary ? 18 : 14);
        b.setTextColor(primary ? BG : WHITE); b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33FFFFFF : 0x2277E4D0),shape(primary ? MINT : CARD,primary ? 20 : 14,!primary),null));
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setStateListAnimator(null);
        b.setPadding(dp(12),0,dp(12),0); return b;
    }
    EditText input(String hint,boolean password) {
        EditText t=new EditText(this); t.setTextColor(WHITE); t.setHintTextColor(MUTED); t.setTextSize(16);
        t.setSingleLine(true); t.setHint(hint); t.setPadding(dp(15),dp(14),dp(15),dp(14));
        t.setBackground(shape(BG,14,true)); t.setSaveEnabled(false); t.setFreezesText(false);
        t.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        t.setInputType(password ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if(password) { t.setLongClickable(false); t.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance()); }
        return t;
    }
}

package cn.edu.uestc.wifiassistant;

import android.app.Application;

public final class AssistantApp extends Application {
    public AppController controller;
    @Override public void onCreate() { super.onCreate(); controller=new AppController(this); }
}

package local.qqslim.module;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity{
    @Override protected void onCreate(Bundle b){super.onCreate(b);setTitle("QQ瘦身独立模块");setContentView(content());}
    private View content(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(24),dp(30),dp(24),dp(20));x.setBackgroundColor(Color.rgb(246,248,252));
        x.addView(text("QQ瘦身独立模块",27,Color.rgb(20,40,70)));x.addView(text("v1.0-standalone · 69项原始目录规则",14,Color.DKGRAY));
        x.addView(text("此页面只负责状态与导航。\n\n功能入口：QQ → 设置 → QQ瘦身\n\n双开：在 LSPosed 作用域中同时勾选主实例和双开实例。模块在每个实例进程内根据 userId、UID、dataDir 和用户外部存储分别扫描，互不共用目录。",16,Color.rgb(25,55,90)));
        x.addView(button("打开 QQ",v->open("com.tencent.mobileqq")));x.addView(button("打开 LSPosed",v->open("org.lsposed.manager")));x.addView(button("应用详情",v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())))));return x;}
    private void open(String p){Intent i=getPackageManager().getLaunchIntentForPackage(p);if(i==null)Toast.makeText(this,"未检测到 "+p,Toast.LENGTH_SHORT).show();else startActivity(i);}
    private Button button(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(l);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(12);b.setLayoutParams(p);return b;}
    private TextView text(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setPadding(0,0,0,dp(18));return v;}private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}

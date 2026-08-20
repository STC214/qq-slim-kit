package local.qqslim.module;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SlimUi {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    static void show(Context c,String dataDir){
        final Palette p=Palette.from(c);
        final List<SlimCatalog.Item> items=SlimCatalog.resolve(c,dataDir);
        LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(p.page);
        LinearLayout bar=new LinearLayout(c);bar.setGravity(Gravity.CENTER);bar.setBackgroundColor(p.card);bar.setElevation(dp(c,4));
        TextView pageTitle=text(c,"QQ 瘦身",18,p.primary);pageTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);bar.addView(pageTitle,new LinearLayout.LayoutParams(-2,dp(c,40)));root.addView(bar,new LinearLayout.LayoutParams(-1,dp(c,40)));
        ListView list=new ListView(c);list.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,20));list.setClipToPadding(false);list.setDivider(new ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(2);list.setOverScrollMode(View.OVER_SCROLL_NEVER);list.setVerticalScrollBarEnabled(false);
        final SlimAdapter adapter=new SlimAdapter(c,items,dataDir,p);list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Dialog dialog=new Dialog(c,p.dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);dialog.setContentView(root);dialog.show();
        Window window=dialog.getWindow();if(window!=null){window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);window.setStatusBarColor(p.card);window.setNavigationBarColor(p.card);window.getDecorView().setSystemUiVisibility(p.dark?0:(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));}
        Log.i(SlimEntry.TAG,"slim page shown; theme="+(p.dark?"dark":"light")+"; user="+userId()+"; uid="+android.os.Process.myUid()+"; internal="+dataDir+"; externalAppRoot="+SlimCatalog.externalRoot(c)+"; rows="+items.size());
        Handler main=new Handler(Looper.getMainLooper());
        IO.execute(()->{
            long total=0;int found=0,nonZero=0;StringBuilder media=new StringBuilder();for(SlimCatalog.Item i:items){i.bytes=SlimCatalog.size(i.path);total+=i.bytes;if(i.path.exists())found++;if(i.bytes>0)nonZero++;if((i.template.contains("chatpic")||i.template.contains("diskcache")||i.template.contains("photo")||i.template.contains("shortvideo"))&&i.bytes>0)media.append(i.name).append('=').append(i.bytes).append(';');}final int foundCount=found,nonZeroCount=nonZero;final String mediaResult=media.toString();
            Collections.sort(items,(a,b)->Long.compare(b.bytes,a.bytes));final long sum=total;
            main.post(()->{adapter.notifyDataSetChanged();Log.i(SlimEntry.TAG,"scan complete; user="+userId()+"; bytes="+sum+"; rows="+items.size()+"; found="+foundCount+"; nonZero="+nonZeroCount+"; media="+mediaResult);});
        });
    }
    private static final class SlimAdapter extends BaseAdapter{
        final Context c;final List<SlimCatalog.Item> data;final File internal,external;final Palette palette;final Handler main=new Handler(Looper.getMainLooper());
        SlimAdapter(Context c,List<SlimCatalog.Item> d,String dir,Palette p){this.c=c;data=d;internal=new File(dir);external=SlimCatalog.externalRoot(c);palette=p;}
        public int getCount(){return data.size();}public Object getItem(int p){return data.get(p);}public long getItemId(int p){return p;}
        public View getView(int pos,View old,ViewGroup parent){
            SlimCatalog.Item item=data.get(pos);LinearLayout card=new LinearLayout(c);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,16));card.setBackground(roundRect(palette.card,dp(c,12)));
            LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout words=new LinearLayout(c);words.setOrientation(LinearLayout.VERTICAL);
            TextView title=text(c,item.name,16,palette.primary);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setSingleLine(true);
            TextView size=text(c,item.bytes<0?"正在计算…":format(item.bytes),13,palette.secondary);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(c,2);words.addView(title);words.addView(size,sp);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            TextView clean=text(c,"清理",14,Color.rgb(239,68,68));clean.setTypeface(Typeface.DEFAULT,Typeface.BOLD);clean.setGravity(Gravity.CENTER);clean.setPadding(dp(c,14),dp(c,6),dp(c,14),dp(c,6));clean.setEnabled(item.path.exists());clean.setOnClickListener(v->confirm(item));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.leftMargin=dp(c,12);row.addView(clean,cp);card.addView(row);return card;
        }
        void confirm(SlimCatalog.Item item){new AlertDialog.Builder(c).setTitle("清理“"+item.name+"”").setMessage(item.path.getAbsolutePath()+"\n\n将递归删除此缓存目录。")
            .setNegativeButton("取消",null).setPositiveButton("清理",(d,w)->IO.execute(()->{boolean ok=SlimCatalog.delete(item.path,internal,external);item.bytes=SlimCatalog.size(item.path);main.post(()->{notifyDataSetChanged();Toast.makeText(c,ok?"清理完成":"清理失败，请查看日志",Toast.LENGTH_SHORT).show();Log.i(SlimEntry.TAG,"clean result="+ok+"; user="+userId()+"; path="+item.path);});})).show();}
    }
    private static TextView text(Context c,String s,int z,int color){TextView v=new TextView(c);v.setText(s);v.setTextSize(z);v.setTextColor(color);return v;}
    private static GradientDrawable roundRect(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private static final class Palette{
        final boolean dark;final int page,card,primary,secondary;
        Palette(boolean d,int p,int c,int a,int b){dark=d;page=p;card=c;primary=a;secondary=b;}
        static Palette from(Context c){boolean d=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;return d?new Palette(true,Color.rgb(17,24,39),Color.rgb(31,41,55),Color.rgb(249,250,251),Color.rgb(156,163,175)):new Palette(false,Color.rgb(229,231,235),Color.WHITE,Color.rgb(31,41,55),Color.rgb(156,163,175));}
    }
    private static int dp(Context c,int x){return Math.round(x*c.getResources().getDisplayMetrics().density);}
    private static int userId(){return android.os.Process.myUid()/100000;}
    private static String format(long n){DecimalFormat f=new DecimalFormat("0.##");if(n>=1073741824)return f.format(n/1073741824d)+" GB";if(n>=1048576)return f.format(n/1048576d)+" MB";if(n>=1024)return f.format(n/1024d)+" KB";return n+" B";}
}

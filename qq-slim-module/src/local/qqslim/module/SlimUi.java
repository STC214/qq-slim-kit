package local.qqslim.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SlimUi {
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(32),r->{Thread t=new Thread(r,"QQSlim-IO");t.setDaemon(true);return t;});
    // Weak values avoid a value -> dialog -> activity -> key retention cycle.
    private static final Map<Activity,WeakReference<Dialog>> OPEN=new WeakHashMap<>();

    static void show(Context context,String dataDir){
        if(Looper.myLooper()!=Looper.getMainLooper()){
            new Handler(Looper.getMainLooper()).post(()->show(context,dataDir));return;
        }
        Context unwrapped=context;
        for(int i=0;i<16&&!(unwrapped instanceof Activity)&&unwrapped instanceof android.content.ContextWrapper;i++){
            Context next=((android.content.ContextWrapper)unwrapped).getBaseContext();
            if(next==null||next==unwrapped)break;unwrapped=next;
        }
        if(!(unwrapped instanceof Activity))return;
        Activity activity=(Activity)unwrapped;
        if(activity.isFinishing()||activity.isDestroyed())return;
        WeakReference<Dialog> prior=OPEN.get(activity);
        if(prior!=null&&prior.get()!=null&&prior.get().isShowing())return;
        try{new Session(activity,dataDir).show();}
        catch(RuntimeException e){Log.e(SlimEntry.TAG,"Page creation failed",e);Toast.makeText(activity,"页面打开失败",Toast.LENGTH_SHORT).show();}
    }

    private static final class Session implements Application.ActivityLifecycleCallbacks {
        final Activity activity;
        final Dialog dialog;
        final Palette palette;
        final File internal,external;
        final Handler main=new Handler(Looper.getMainLooper());
        final AtomicBoolean closed=new AtomicBoolean();
        final List<Future<?>> tasks=new ArrayList<>();
        final List<SlimCatalog.Item> items=new ArrayList<>();
        final Adapter adapter=new Adapter();
        TextView heading;
        AlertDialog confirmation;
        boolean registered;

        Session(Activity activity,String dataDir){
            this.activity=activity;palette=Palette.from(activity);
            internal=new File(dataDir);external=SlimCatalog.externalRoot(activity);
            dialog=new Dialog(activity,palette.dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        }

        boolean active(){return !closed.get()&&!activity.isFinishing()&&!activity.isDestroyed();}

        void show(){
            LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(palette.page);
            heading=text(activity,"QQ 瘦身 · 正在扫描",18,palette.primary);heading.setGravity(Gravity.CENTER);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.setBackgroundColor(palette.card);
            heading.setPadding(dp(activity,12),dp(activity,12),dp(activity,12),dp(activity,12));
            root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
            ListView list=new ListView(activity);list.setPadding(dp(activity,12),dp(activity,12),dp(activity,12),dp(activity,20));list.setClipToPadding(false);
            list.setDivider(new ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(2);list.setOverScrollMode(View.OVER_SCROLL_NEVER);list.setAdapter(adapter);
            root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
            dialog.setContentView(root);dialog.setOnDismissListener(d->close());
            try{
                dialog.show();
                Window window=dialog.getWindow();if(window!=null)window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
                WindowStyle.apply(window,root,palette.dark);
                OPEN.put(activity,new WeakReference<>(dialog));
                activity.getApplication().registerActivityLifecycleCallbacks(this);registered=true;
                submit(()->scan(activity.getApplicationContext()));
            }catch(RuntimeException e){dialog.dismiss();close();throw e;}
        }

        void submit(Runnable work){
            try{tasks.add(IO.submit(work));}
            catch(RejectedExecutionException busy){heading.setText("任务较多，请稍后重新打开");}
        }

        void scan(Context context){
            try{
                List<SlimCatalog.Item> result=SlimCatalog.resolve(context,internal.getPath());int failed=0;
                for(SlimCatalog.Item item:result){
                    if(closed.get()||Thread.currentThread().isInterrupted())return;
                    try{item.bytes=SlimCatalog.size(item.path,internal,external);}
                    catch(IOException|RuntimeException e){item.error="扫描失败";failed++;Log.w(SlimEntry.TAG,"Cache scan failed: "+item.template,e);}
                }
                result.sort((a,b)->Long.compare(b.bytes,a.bytes));final int errors=failed;
                main.post(()->{if(!active())return;items.addAll(result);adapter.notifyDataSetChanged();heading.setText(errors==0?"QQ 瘦身":"QQ 瘦身 · "+errors+" 项扫描失败");});
            }catch(IOException|RuntimeException e){Log.e(SlimEntry.TAG,"Catalogue scan failed",e);main.post(()->{if(active())heading.setText("扫描失败，请重新打开");});}
        }

        void confirm(SlimCatalog.Item item){
            if(!active()||!item.cleanable()||item.cleaning||item.error!=null||item.bytes<=0||(confirmation!=null&&confirmation.isShowing()))return;
            confirmation=new AlertDialog.Builder(activity).setTitle("清理“"+item.name+"”")
                    .setMessage(item.path.getAbsolutePath()+"\n\n将清空目录内的文件并保留目录。本地副本可能丢失；QQ 可能重新生成缓存，中断清理可能仅完成部分删除。")
                    .setNegativeButton("取消",null).setPositiveButton("清理",(d,w)->{
                        if(!active()||item.cleaning)return;
                        item.cleaning=true;adapter.notifyDataSetChanged();
                        try{
                            tasks.add(IO.submit(()->{
                                if(closed.get())return;
                                boolean ok=SlimCatalog.delete(item,internal,external);long bytes=-1;String error=null;
                                try{bytes=SlimCatalog.size(item.path,internal,external);}catch(IOException|RuntimeException e){error="扫描失败";}
                                final long remaining=bytes;final String problem=error;
                                main.post(()->{if(!active())return;item.cleaning=false;item.bytes=remaining;item.error=problem;items.sort((a,b)->Long.compare(b.bytes,a.bytes));adapter.notifyDataSetChanged();Toast.makeText(activity,ok?"清理完成":"清理未完成，请重新扫描",Toast.LENGTH_SHORT).show();});
                                Log.i(SlimEntry.TAG,"Clean result="+ok);
                            }));
                        }catch(RejectedExecutionException busy){item.cleaning=false;adapter.notifyDataSetChanged();Toast.makeText(activity,"任务较多，请稍后重试",Toast.LENGTH_SHORT).show();}
                    }).create();
            confirmation.show();
        }

        void close(){
            if(!closed.compareAndSet(false,true))return;
            for(Future<?> task:tasks)task.cancel(true);tasks.clear();IO.purge();
            main.removeCallbacksAndMessages(null);
            if(confirmation!=null)confirmation.dismiss();
            if(registered){activity.getApplication().unregisterActivityLifecycleCallbacks(this);registered=false;}
            WeakReference<Dialog> reference=OPEN.get(activity);if(reference!=null&&reference.get()==dialog)OPEN.remove(activity);
        }
        @Override public void onActivityDestroyed(Activity a){if(a==activity){dialog.dismiss();close();}}
        @Override public void onActivityCreated(Activity a,Bundle b){}
        @Override public void onActivityStarted(Activity a){}
        @Override public void onActivityResumed(Activity a){}
        @Override public void onActivityPaused(Activity a){}
        @Override public void onActivityStopped(Activity a){}
        @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}

        final class Adapter extends BaseAdapter {
            public int getCount(){return items.size();}
            public Object getItem(int position){return items.get(position);}
            public long getItemId(int position){return position;}
            public View getView(int position,View old,ViewGroup parent){
                SlimCatalog.Item item=items.get(position);
                LinearLayout card=new LinearLayout(activity);card.setPadding(dp(activity,16),dp(activity,16),dp(activity,16),dp(activity,16));card.setGravity(Gravity.CENTER_VERTICAL);
                GradientDrawable shape=new GradientDrawable();shape.setColor(palette.card);shape.setCornerRadius(dp(activity,12));card.setBackground(shape);
                LinearLayout words=new LinearLayout(activity);words.setOrientation(LinearLayout.VERTICAL);
                TextView title=text(activity,item.name,16,palette.primary);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);words.addView(title);
                String status=item.error!=null?item.error:(item.bytes<0?"正在计算…":format(item.bytes));
                TextView size=text(activity,status,13,palette.secondary);size.setPadding(0,dp(activity,2),0,0);words.addView(size);
                card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                boolean enabled=item.cleanable()&&item.error==null&&item.bytes>0&&!item.cleaning;
                TextView clean=text(activity,item.cleaning?"清理中":(item.cleanable()?"清理":"仅查看"),14,enabled?Color.rgb(239,68,68):palette.secondary);
                clean.setGravity(Gravity.CENTER);clean.setTypeface(Typeface.DEFAULT,Typeface.BOLD);clean.setMinHeight(dp(activity,48));clean.setMinWidth(dp(activity,48));clean.setPadding(dp(activity,12),dp(activity,6),dp(activity,12),dp(activity,6));clean.setEnabled(enabled);clean.setOnClickListener(v->confirm(item));
                LinearLayout.LayoutParams button=new LinearLayout.LayoutParams(-2,-2);button.leftMargin=dp(activity,12);card.addView(clean,button);return card;
            }
        }
    }

    private static TextView text(Context context,String value,int size,int color){TextView view=new TextView(context);view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;}
    private static final class Palette {
        final boolean dark;final int page,card,primary,secondary;
        Palette(boolean dark,int page,int card,int primary,int secondary){this.dark=dark;this.page=page;this.card=card;this.primary=primary;this.secondary=secondary;}
        static Palette from(Context context){
            boolean dark=(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
            return dark?new Palette(true,Color.rgb(17,24,39),Color.rgb(31,41,55),Color.rgb(249,250,251),Color.rgb(156,163,175))
                    :new Palette(false,Color.rgb(229,231,235),Color.WHITE,Color.rgb(31,41,55),Color.rgb(107,114,128));
        }
    }
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static String format(long bytes){DecimalFormat f=new DecimalFormat("0.##");if(bytes>=1073741824)return f.format(bytes/1073741824d)+" GB";if(bytes>=1048576)return f.format(bytes/1048576d)+" MB";if(bytes>=1024)return f.format(bytes/1024d)+" KB";return bytes+" B";}
}

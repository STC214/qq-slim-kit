package local.qqslim.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class UiInstrumentation extends Instrumentation {
    private Bundle arguments;
    private int checks;
    private StringBuilder transcript=new StringBuilder();
    @Override public void onCreate(Bundle args){arguments=args;start();}
    private void expect(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;transcript.append("PASS ").append(name).append('\n');}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new RuntimeException(e);}}
    private static void call(Object owner,String name){try{Method m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);}catch(Exception e){throw new RuntimeException(e);}}
    private void main(Runnable runnable){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{runnable.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());}
    private static View text(View view,String value){if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=text(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private static ListView list(View view){if(view instanceof ListView)return (ListView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ListView found=list(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}

    @Override public void onStart(){
        Bundle result=new Bundle();Activity activity=null;
        try{
            Intent intent=new Intent(getTargetContext(),UiHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);final Activity owner=activity;
            File fixture=new File(owner.getCacheDir(),"ui-fixture");Files.write(fixture.toPath(),new byte[41]);
            Class<?> type=Class.forName("local.qqslim.module.SlimUi$Session");Constructor<?> ctor=type.getDeclaredConstructor(Activity.class,String.class);ctor.setAccessible(true);
            AtomicReference<Object> reference=new AtomicReference<>();
            main(()->{try{reference.set(ctor.newInstance(owner,owner.getApplicationInfo().dataDir));call(reference.get(),"show");}catch(Exception e){throw new RuntimeException(e);}});
            Object session=reference.get();Dialog dialog=(Dialog)field(session,"dialog");
            for(int i=0;i<100;i++){
                AtomicReference<Integer> rows=new AtomicReference<>(0);main(()->rows.set(((List<?>)field(session,"items")).size()));
                if(rows.get()>0)break;Thread.sleep(100);
            }
            main(()->{
                expect(dialog.isShowing(),"page-shown");
                ListView list=list(dialog.getWindow().getDecorView());expect(list!=null&&list.getAdapter().getCount()>=68,"scan-published");
                @SuppressWarnings("unchecked") List<SlimCatalog.Item> items=(List<SlimCatalog.Item>)field(session,"items");
                int readonly=0,errors=0;for(int i=0;i<items.size();i++){View row=list.getAdapter().getView(i,null,list);if(text(row,"仅查看")!=null)readonly++;if(items.get(i).error!=null)errors++;}
                expect(readonly>=14,"important-data-readonly");expect(errors==0,"real-android-roots-scanned");
                expect(list.getDividerHeight()==2,"two-physical-pixel-gap");
                ViewGroup content=dialog.findViewById(android.R.id.content);View root=content.getChildAt(0);expect(root.getPaddingTop()>0&&root.getPaddingBottom()>0,"system-bar-insets");
                Object palette=field(session,"palette");expect(((Boolean)field(palette,"dark"))==Boolean.parseBoolean(arguments.getString("dark","false")),"requested-theme");
                SlimUi.show(new android.view.ContextThemeWrapper(owner,android.R.style.Theme_Material),owner.getApplicationInfo().dataDir);
                try{Field f=SlimUi.class.getDeclaredField("OPEN");f.setAccessible(true);expect(((Map<?,?>)f.get(null)).size()==1,"duplicate-open-suppressed");}catch(Exception e){throw new RuntimeException(e);}
                int index=-1;for(int i=0;i<items.size();i++)if("G/cache".equals(items.get(i).template))index=i;
                expect(index>=0,"cache-row-present");View row=list.getAdapter().getView(index,null,list);View clean=text(row,"清理");expect(clean!=null&&clean.isEnabled(),"clean-button-enabled");clean.performClick();
                AlertDialog confirmation=(AlertDialog)field(session,"confirmation");expect(confirmation!=null&&confirmation.isShowing(),"confirmation-required");
                confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            for(int i=0;i<100&&fixture.exists();i++)Thread.sleep(100);
            main(()->{expect(!fixture.exists()&&owner.getCacheDir().isDirectory(),"confirmed-clean-preserves-directory");owner.finish();});
            for(int i=0;i<100;i++){AtomicReference<Boolean> destroyed=new AtomicReference<>(false);main(()->destroyed.set(owner.isDestroyed()));if(destroyed.get())break;Thread.sleep(100);}
            main(()->{expect(!dialog.isShowing(),"activity-destroy-dismisses-page");expect(!((Boolean)field(session,"registered")),"lifecycle-listener-unregistered");});
            result.putString("stream",transcript+"RESULT ui_passed="+checks+" failed=0\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){if(activity!=null){final Activity owner=activity;runOnMainSync(owner::finish);}result.putString("stream",transcript+"FAIL "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}

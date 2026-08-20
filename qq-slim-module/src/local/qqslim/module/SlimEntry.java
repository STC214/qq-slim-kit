package local.qqslim.module;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Sole Xposed entry. Each Android user receives a distinct app dataDir and UID. */
public final class SlimEntry implements IXposedHookLoadPackage {
    static final String TAG="QQSlim";
    private static final Set<String> INSTALLED=ConcurrentHashMap.newKeySet();
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p){
        if(!"com.tencent.mobileqq".equals(p.packageName)||p.appInfo==null)return;
        String main=p.appInfo.processName==null?p.packageName:p.appInfo.processName;
        if(!main.equals(p.processName))return;
        String instance=p.appInfo.uid+"|"+p.appInfo.dataDir+"|"+p.processName;
        if(!INSTALLED.add(instance))return;
        try{
            SettingsInjector.install(p.classLoader,instance,p.appInfo.dataDir);
            Log.i(TAG,"standalone loaded; instance="+instance+"; user="+(android.os.Process.myUid()/100000)+"; presets=69");
        }catch(Throwable t){
            INSTALLED.remove(instance);
            Log.e(TAG,"standalone startup failed; instance="+instance,t);
        }
    }
}

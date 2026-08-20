package local.qqslim.module;

import android.content.Context;
import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

final class SettingsInjector {
    private static final int ENTRY_ID=0x51534c49;
    private static final Set<Object> DECORATED=Collections.newSetFromMap(new WeakHashMap<Object,Boolean>());
    static void install(final ClassLoader host,final String instance,final String dataDir){
        int count=0;
        for(String name:new String[]{"com.tencent.mobileqq.setting.main.MainSettingConfigProvider","com.tencent.mobileqq.setting.main.NewSettingConfigProvider","com.tencent.mobileqq.setting.main.b"}){
            try{
                Class<?> type=Class.forName(name,false,host);
                for(Method method:type.getDeclaredMethods()){
                    Class<?>[] a=method.getParameterTypes();
                    if(List.class.isAssignableFrom(method.getReturnType())&&a.length==1&&Context.class.isAssignableFrom(a[0])){
                        method.setAccessible(true);
                        XposedBridge.hookMethod(method,new XC_MethodHook(60){
                            @Override protected void afterHookedMethod(MethodHookParam p){
                                try{decorate((Context)p.args[0],p.getResult(),host,dataDir,name);}
                                catch(Throwable t){Log.e(SlimEntry.TAG,"settings insertion failed; instance="+instance,t);}
                            }
                        });
                        count++;
                    }
                }
            }catch(ClassNotFoundException ignored){}catch(Throwable t){Log.e(SlimEntry.TAG,"provider hook failed: "+name,t);}
        }
        Log.i(SlimEntry.TAG,"settings providers hooked="+count+"; instance="+instance);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void decorate(final Context context,Object result,ClassLoader host,String dataDir,String providerName)throws Throwable{
        if(!(result instanceof List))return;
        List sections=(List)result;
        if(sections.isEmpty())return;
        synchronized(DECORATED){if(!DECORATED.add(result))return;}
        Class<?> base=null;
        for(String n:new String[]{"com.tencent.mobileqq.setting.main.processor.AccountSecurityItemProcessor","com.tencent.mobileqq.setting.main.processor.AboutItemProcessor"}){
            try{base=Class.forName(n,false,host).getSuperclass();break;}catch(ClassNotFoundException ignored){}
        }
        if(base==null)throw new IllegalStateException("setting processor base missing");
        ArrayList<Class<?>> candidates=new ArrayList<>();
        for(String n:new String[]{"com.tencent.mobileqq.setting.processor.g","com.tencent.mobileqq.setting.processor.h","com.tencent.mobileqq.setting.processor.i","com.tencent.mobileqq.setting.processor.j","as3.i"}){
            try{Class<?> c=Class.forName(n,false,host);if(c.getSuperclass()==base)candidates.add(c);}catch(ClassNotFoundException ignored){}
        }
        if(candidates.size()!=1)throw new IllegalStateException("simple processor candidates="+candidates);
        Class<?> pc=candidates.get(0);
        ArrayList<Method> setters=new ArrayList<>();
        for(Method m:pc.getDeclaredMethods()){
            Class<?>[] p=m.getParameterTypes();
            if(m.getReturnType()==Void.TYPE&&p.length==1&&"kotlin.jvm.functions.Function0".equals(p[0].getName()))setters.add(m);
        }
        setters.sort(Comparator.comparing(Method::getName));
        if(setters.isEmpty()||setters.size()>2)throw new IllegalStateException("callback setters="+setters);
        Method setter=setters.get(0);setter.setAccessible(true);
        Constructor<?> ctor;boolean five;
        try{ctor=pc.getDeclaredConstructor(Context.class,int.class,CharSequence.class,int.class,String.class);five=true;}
        catch(NoSuchMethodException e){ctor=pc.getDeclaredConstructor(Context.class,int.class,CharSequence.class,int.class);five=false;}
        ctor.setAccessible(true);
        int icon=context.getResources().getIdentifier("qui_tuning","drawable",context.getPackageName());
        Object processor=five?ctor.newInstance(context,ENTRY_ID,"QQ瘦身",icon,null):ctor.newInstance(context,ENTRY_ID,"QQ瘦身",icon);
        final Class<?> function0=setter.getParameterTypes()[0];
        final Object unit=function0.getClassLoader().loadClass("kotlin.Unit").getField("INSTANCE").get(null);
        Object callback=Proxy.newProxyInstance(host,new Class[]{function0},(proxy,m,args)->{
            if("invoke".equals(m.getName())){SlimUi.show(context,dataDir);return unit;}
            if("toString".equals(m.getName()))return "QQSlimClick";
            if("hashCode".equals(m.getName()))return System.identityHashCode(proxy);
            if("equals".equals(m.getName()))return proxy==args[0];
            return null;
        });
        setter.invoke(processor,callback);
        Class<?> sc=sections.get(0).getClass();Constructor<?> sctor;boolean extended;
        try{sctor=sc.getDeclaredConstructor(List.class,CharSequence.class,CharSequence.class);extended=false;}
        catch(NoSuchMethodException e){sctor=sc.getDeclaredConstructor(List.class,CharSequence.class,CharSequence.class,int.class,host.loadClass("kotlin.jvm.internal.DefaultConstructorMarker"));extended=true;}
        sctor.setAccessible(true);ArrayList<Object> one=new ArrayList<>(1);one.add(processor);
        Object slimSection=extended?sctor.newInstance(one,"","",6,null):sctor.newInstance(one,"","");

        // QA's current SettingEntryHook inserts its section at 1 (old provider) or 2 (new provider).
        // Locate the real QAuxiliary section when present, otherwise use that exact reference slot + 1.
        int qa=-1;
        for(int i=0;i<sections.size();i++)if(deepContains(sections.get(i),"QAuxiliary",5,new java.util.IdentityHashMap<Object,Boolean>())){qa=i;break;}
        int qaReference=providerName.contains("NewSettingConfigProvider")?2:1;
        int insert=qa>=0?qa+1:Math.min(qaReference+1,sections.size());
        sections.add(insert,slimSection);
        Log.i(SlimEntry.TAG,"QQ瘦身 entry inserted; placement=section["+insert+"]; qaSection="+qa+"; provider="+providerName+"; uid="+android.os.Process.myUid()+"; user="+(android.os.Process.myUid()/100000)+"; dataDir="+dataDir);
    }

    private static boolean deepContains(Object value,String needle,int depth,java.util.IdentityHashMap<Object,Boolean> seen){
        if(value==null||depth<0||seen.put(value,Boolean.TRUE)!=null)return false;
        if(value instanceof CharSequence)return value.toString().contains(needle);
        if(value instanceof Iterable){for(Object x:(Iterable<?>)value)if(deepContains(x,needle,depth-1,seen))return true;return false;}
        Class<?> c=value.getClass();if(c.isPrimitive()||c.isEnum()||c.getName().startsWith("java.lang."))return false;
        for(Class<?> k=c;k!=null&&k!=Object.class;k=k.getSuperclass())for(Field f:k.getDeclaredFields()){
            if(Modifier.isStatic(f.getModifiers()))continue;
            try{f.setAccessible(true);if(deepContains(f.get(value),needle,depth-1,seen))return true;}catch(Throwable ignored){}
        }
        return false;
    }
}

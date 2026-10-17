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
import java.util.Comparator;
import java.util.List;

final class SettingsInjector {
    private static final int ENTRY_ID=0x51534c49;
    private static final List<java.lang.ref.WeakReference<Object>> DECORATED=new ArrayList<>();
    private static boolean decorated(Object value){
        synchronized(DECORATED){
            java.util.Iterator<java.lang.ref.WeakReference<Object>> it=DECORATED.iterator();
            while(it.hasNext()){Object x=it.next().get();if(x==null)it.remove();else if(x==value)return true;}
            return false;
        }
    }
    static int install(final ClassLoader host,final String instance,final String dataDir){
        int count=0;
        for(String name:new String[]{"com.tencent.mobileqq.setting.main.MainSettingConfigProvider","com.tencent.mobileqq.setting.main.NewSettingConfigProvider","com.tencent.mobileqq.setting.main.b"}){
            try{
                Class<?> type=Class.forName(name,false,host);
                for(Method method:type.getDeclaredMethods()){
                    Class<?>[] a=method.getParameterTypes();
                    // Returning ArrayList is valid for List, not for arbitrary concrete subtypes.
                    if(method.getReturnType()==List.class&&a.length==1&&Context.class.isAssignableFrom(a[0])){
                        method.setAccessible(true);
                        XposedBridge.hookMethod(method,new XC_MethodHook(60){
                            @Override protected void afterHookedMethod(MethodHookParam p){
                                try{if(!p.hasThrowable())p.setResult(decorate((Context)p.args[0],p.getResult(),host,dataDir,name));}
                                catch(Throwable t){Log.e(SlimEntry.TAG,"settings insertion failed; instance="+instance,t);}
                            }
                        });
                        count++;
                    }
                }
            }catch(ClassNotFoundException ignored){}catch(Throwable t){Log.e(SlimEntry.TAG,"provider hook failed: "+name,t);}
        }
        Log.i(SlimEntry.TAG,"settings providers hooked="+count+"; instance="+instance);
        return count;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static Object decorate(final Context context,Object result,ClassLoader host,String dataDir,String providerName)throws Throwable{
        if(!(result instanceof List))return result;
        if(decorated(result))return result;
        List sections=new ArrayList((List)result);
        if(sections.isEmpty())return result;
        for(Object section:sections)if(deepContains(section,Integer.valueOf(ENTRY_ID),5,new java.util.IdentityHashMap<Object,Boolean>()))return result;
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
        Object callback=Proxy.newProxyInstance(host,new Class<?>[]{function0},(proxy,m,args)->{
            if("invoke".equals(m.getName())){SlimUi.show(context,dataDir);return unit;}
            if("toString".equals(m.getName()))return "QQSlimClick";
            if("hashCode".equals(m.getName()))return System.identityHashCode(proxy);
            if("equals".equals(m.getName()))return args!=null&&args.length==1&&proxy==args[0];
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
        synchronized(DECORATED){DECORATED.add(new java.lang.ref.WeakReference<Object>(sections));}
        Log.i(SlimEntry.TAG,"QQ瘦身 entry inserted; placement=section["+insert+"]; qaSection="+qa+"; provider="+providerName+"; uid="+android.os.Process.myUid()+"; user="+(android.os.Process.myUid()/100000)+"; dataDir="+dataDir);
        return sections;
    }

    private static boolean deepContains(Object value,Object needle,int depth,java.util.IdentityHashMap<Object,Boolean> seen){
        if(value==null||depth<0||seen.size()>=512||seen.put(value,Boolean.TRUE)!=null)return false;
        if(needle instanceof Integer&&needle.equals(value))return true;
        if(value instanceof CharSequence)return needle instanceof String&&value.toString().contains((String)needle);
        if(value instanceof Iterable){int count=0;for(Object x:(Iterable<?>)value){if(++count>512)break;if(deepContains(x,needle,depth-1,seen))return true;}return false;}
        Class<?> c=value.getClass();if(c.isPrimitive()||c.isEnum()||c.getName().startsWith("java.lang."))return false;
        for(Class<?> k=c;k!=null&&k!=Object.class;k=k.getSuperclass())for(Field f:k.getDeclaredFields()){
            if(Modifier.isStatic(f.getModifiers()))continue;
            try{f.setAccessible(true);if(deepContains(f.get(value),needle,depth-1,seen))return true;}catch(Throwable ignored){}
        }
        return false;
    }
}

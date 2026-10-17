package local.qqslim.module;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Executed by app_process on Android; no QQ data or live application is accessed. */
public final class RegressionTests {
    private static int passed,failed;
    private static Path base,g,e,outside;
    interface Check {void run() throws Throwable;}
    private static void test(String name,Check check){
        try{check.run();passed++;System.out.println("PASS "+name);}
        catch(Throwable error){failed++;System.out.println("FAIL "+name+": "+error);}
    }
    private static void expect(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void write(Path path,int bytes) throws IOException {Files.createDirectories(path.getParent());Files.write(path,new byte[bytes]);}
    private static long size(Path path,Path internal,Path external) throws Throwable {
        try{
            Method method;
            try{method=SlimCatalog.class.getDeclaredMethod("size",File.class,File.class,File.class);return (Long)method.invoke(null,path.toFile(),internal.toFile(),external.toFile());}
            catch(NoSuchMethodException old){method=SlimCatalog.class.getDeclaredMethod("size",File.class);return (Long)method.invoke(null,path.toFile());}
        }catch(InvocationTargetException wrapped){throw wrapped.getCause();}
    }
    private static boolean clear(String template,Path path,Path internal,Path external) throws Throwable {
        try{
            Method method;
            try{method=SlimCatalog.class.getDeclaredMethod("delete",SlimCatalog.Item.class,File.class,File.class);return (Boolean)method.invoke(null,new SlimCatalog.Item("fixture",template,path.toFile()),internal.toFile(),external.toFile());}
            catch(NoSuchMethodException old){method=SlimCatalog.class.getDeclaredMethod("delete",File.class,File.class,File.class);return (Boolean)method.invoke(null,path.toFile(),internal.toFile(),external.toFile());}
        }catch(InvocationTargetException wrapped){throw wrapped.getCause();}
    }
    private static void blockedScan(Path path,Path internal,Path external) throws Throwable {
        try{size(path,internal,external);throw new AssertionError("Expected scan error");}
        catch(IOException expected){/* error, not a false zero */}
    }
    static class FixtureContext extends ContextWrapper {
        final File external;
        FixtureContext(Path external){super(null);this.external=external.toFile();}
        @Override public File getExternalCacheDir(){return new File(external,"cache");}
        @Override public File getExternalFilesDir(String type){return new File(external,"files");}
        @Override public String getPackageName(){return "com.tencent.mobileqq";}
        @Override public Resources getResources(){return Resources.getSystem();}
    }
    public static final class Section {
        final List<?> items;final CharSequence title,subtitle;
        public Section(List<?> items,CharSequence title,CharSequence subtitle){this.items=items;this.title=title;this.subtitle=subtitle;}
    }
    private static Object decorate(Object result,Context context) throws Throwable {
        Method method=SettingsInjector.class.getDeclaredMethod("decorate",Context.class,Object.class,ClassLoader.class,String.class,String.class);method.setAccessible(true);
        try{return method.invoke(null,context,result,RegressionTests.class.getClassLoader(),g.toString(),"MainSettingConfigProvider");}
        catch(InvocationTargetException wrapped){throw wrapped.getCause();}
    }
    private static List<SlimCatalog.Item> catalogue(Context context) throws IOException{return SlimCatalog.resolve(context,g.toString());}

    public static void main(String[] args) throws Throwable {
        base=Files.createTempDirectory(new File(args[0]).toPath(),"qqslim-regression-");g=Files.createDirectories(base.resolve("internal"));e=Files.createDirectories(base.resolve("external"));outside=Files.createDirectories(base.resolve("outside"));
        System.out.println("fixture="+base);
        FixtureContext context=new FixtureContext(e);
        test("size-and-empty",()->{write(g.resolve("cache/a"),3);write(g.resolve("cache/sub/b"),5);expect(size(g.resolve("cache"),g,e)==8,"sum");expect(size(g.resolve("missing"),g,e)==0,"missing");});
        test("clear-preserves-selected-root",()->{expect(clear("G/cache",g.resolve("cache"),g,e),"clear");expect(Files.isDirectory(g.resolve("cache")),"root removed");expect(size(g.resolve("cache"),g,e)==0,"not empty");});
        test("external-isolation",()->{write(e.resolve("cache/a"),7);write(g.resolve("cache/keep"),11);expect(clear("P/cache",e.resolve("cache"),g,e),"external clear");expect(Files.size(g.resolve("cache/keep"))==11,"internal changed");});
        test("root-delete-blocked",()->expect(!clear("G/cache",g,g,e)&&Files.exists(g),"root deleted"));
        test("outside-delete-blocked",()->{write(outside.resolve("keep"),13);expect(!clear("G/cache",outside,g,e),"outside accepted");expect(Files.size(outside.resolve("keep"))==13,"outside changed");});
        test("prefix-collision-blocked",()->{Path prefix=Files.createDirectories(base.resolve("internal-other/cache"));write(prefix.resolve("keep"),17);expect(!clear("G/cache",prefix,g,e),"prefix accepted");});
        test("template-path-mismatch-blocked",()->{Path other=g.resolve("files/not-cache");write(other.resolve("keep"),19);expect(!clear("G/cache",other,g,e),"template bypass");expect(Files.exists(other.resolve("keep")),"deleted mismatch");});
        test("unknown-template-blocked",()->{write(g.resolve("unknown/a"),2);expect(!clear("G/unknown",g.resolve("unknown"),g,e),"unknown cleared");});
        test("protected-mmkv-blocked",()->{write(g.resolve("files/mmkv/settings"),23);expect(!clear("G/files/mmkv",g.resolve("files/mmkv"),g,e),"mmkv cleared");expect(Files.exists(g.resolve("files/mmkv/settings")),"settings deleted");});
        test("protected-libraries-and-media",()->{for(String t:new String[]{"G/app_lib","G/app_libs","G/files/qqso","P/Tencent/QQfile_recv/nt_data","P/Tencent/QQ_Favorite","P/Tencent/QQ_Images","P/Tencent/MobileQQ/12345/ptt","P/Tencent/MobileQQ/troopphoto","P/files/QWallet","P/Tencent/audio","P/Tencent/MobileQQ/Scribble","P/Tencent/MobileQQ/photo"})expect(SlimCatalog.isProtected(t),"not protected: "+t);});
        test("alias-root-scan",()->{Path alias=base.resolve("alias");Files.createSymbolicLink(alias,g);write(g.resolve("cache/alias"),29);expect(size(alias.resolve("cache"),alias,e)==40,"legitimate root alias rejected");});
        test("alias-root-clear",()->{Path alias=base.resolve("alias");expect(clear("G/cache",alias.resolve("cache"),alias,e),"alias clear");expect(Files.isDirectory(g.resolve("cache")),"alias root removed");});
        test("selected-symlink-blocked",()->{Path link=g.resolve("linked");Files.createSymbolicLink(link,outside);blockedScan(link,g,e);expect(!clear("G/cache",link,g,e),"link accepted");});
        test("nested-symlink-preflight",()->{write(g.resolve("cache/keep"),31);Files.createSymbolicLink(g.resolve("cache/link"),outside);blockedScan(g.resolve("cache"),g,e);expect(!clear("G/cache",g.resolve("cache"),g,e),"nested link accepted");expect(Files.exists(g.resolve("cache/keep")),"partial removal before preflight");Files.delete(g.resolve("cache/link"));});
        test("dangling-symlink-blocked",()->{Path link=g.resolve("cache/dangling");Files.createSymbolicLink(link,base.resolve("absent"));blockedScan(g.resolve("cache"),g,e);expect(!clear("G/cache",g.resolve("cache"),g,e),"dangling accepted");Files.delete(link);});
        test("ancestor-symlink-blocked",()->{Path link=g.resolve("parentLink");Files.createSymbolicLink(link,outside);blockedScan(link.resolve("any"),g,e);});
        test("permission-error-not-zero",()->{Path locked=Files.createDirectories(g.resolve("locked"));write(locked.resolve("keep"),3);java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions=Files.getPosixFilePermissions(locked);Files.setPosixFilePermissions(locked,Collections.emptySet());try{blockedScan(locked,g,e);}finally{Files.setPosixFilePermissions(locked,permissions);}});
        test("permission-error-clear-fails",()->{Path locked=g.resolve("cache");java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions=Files.getPosixFilePermissions(locked);Files.setPosixFilePermissions(locked,Collections.emptySet());try{expect(!clear("G/cache",locked,g,e),"permission treated as success");}finally{Files.setPosixFilePermissions(locked,permissions);}});
        test("non-directory-selected-blocked",()->{write(g.resolve("file"),37);blockedScan(g.resolve("file"),g,e);expect(!clear("G/cache",g.resolve("file"),g,e),"file deleted");});
        test("cancelled-scan",()->{Thread.currentThread().interrupt();try{blockedScan(g.resolve("cache"),g,e);}finally{Thread.interrupted();}});
        test("cancelled-clear",()->{Thread.currentThread().interrupt();try{expect(!clear("G/cache",g.resolve("cache"),g,e),"cancel ignored");}finally{Thread.interrupted();}});
        test("depth-limit",()->{Path deep=g.resolve("cache");for(int i=0;i<132;i++)deep=Files.createDirectories(deep.resolve("d"));write(deep.resolve("end"),1);blockedScan(g.resolve("cache"),g,e);expect(!clear("G/cache",g.resolve("cache"),g,e),"depth ignored");Files.delete(deep.resolve("end"));for(int i=0;i<132;i++){Files.delete(deep);deep=deep.getParent();}});
        test("account-expansion-and-dedup",()->{Files.createDirectories(e.resolve("Tencent/MobileQQ/12345"));Files.createDirectories(e.resolve("Tencent/MobileQQ/987654"));List<SlimCatalog.Item> items=SlimCatalog.resolve(context,g.toString());expect(items.size()==69,"expected 68 unique + one expanded account: "+items.size());HashSet<String> unique=new HashSet<>();int voices=0;for(SlimCatalog.Item item:items){expect(unique.add(item.path.toString()),"duplicate path");if(item.template.endsWith("/ptt")){voices++;expect(!item.cleanable(),"voice deletable");}}expect(voices==2,"voices");});
        test("files-root-fallback",()->{Context fallback=new FixtureContext(e){@Override public File getExternalCacheDir(){return null;}};expect(SlimCatalog.externalRoot(fallback).equals(e.toFile()),"files fallback");});
        test("invalid-account-enumeration",()->{Path other=Files.createDirectories(base.resolve("bad-account-root"));write(other.resolve("Tencent/MobileQQ"),1);try{catalogue(new FixtureContext(other));throw new AssertionError("account enumeration silently ignored");}catch(IOException expected){}});
        test("symlink-replacement-race",()->{
            Path race=g.resolve("cache/race"),parked=g.resolve("race-park");Files.createDirectories(race);write(race.resolve("local"),2);
            java.util.concurrent.atomic.AtomicBoolean stop=new java.util.concurrent.atomic.AtomicBoolean();
            Thread swap=new Thread(()->{while(!stop.get())try{
                if(Files.isSymbolicLink(race))Files.delete(race);
                if(Files.exists(parked,LinkOption.NOFOLLOW_LINKS)){if(!Files.exists(race,LinkOption.NOFOLLOW_LINKS))Files.move(parked,race);}
                if(!Files.exists(race,LinkOption.NOFOLLOW_LINKS))Files.createDirectories(race);
                Files.move(race,parked);Files.createSymbolicLink(race,outside);
                Thread.yield();
            }catch(IOException ignored){}});
            swap.start();try{for(int i=0;i<100;i++){clear("G/cache",g.resolve("cache"),g,e);expect(Files.size(outside.resolve("keep"))==13,"outside sentinel changed");}}finally{stop.set(true);swap.join();if(Files.isSymbolicLink(race))Files.delete(race);}
        });
        test("settings-immutable-input",()->{List<Section> original=Collections.singletonList(new Section(Collections.singletonList("ordinary"),"",""));Object decorated=decorate(original,context);expect(((List<?>)decorated).size()==2,"entry missing");expect(original.size()==1,"original changed");});
        test("settings-same-list-idempotent",()->{Object decorated=decorate(Collections.singletonList(new Section(Collections.singletonList("ordinary"),"","")),context);expect(decorate(decorated,context)==decorated,"not idempotent");});
        test("settings-copied-list-idempotent",()->{Object decorated=decorate(Collections.singletonList(new Section(Collections.singletonList("ordinary"),"","")),context);List<?> copy=new ArrayList<>((List<?>)decorated);expect(((List<?>)decorate(copy,context)).size()==copy.size(),"copied list duplicated");});
        test("settings-qa-placement",()->{List<Section> sections=new ArrayList<>();sections.add(new Section(Collections.singletonList("QAuxiliary"),"",""));sections.add(new Section(Collections.singletonList("ordinary"),"",""));List<?> result=(List<?>)decorate(sections,context);expect(((Section)result.get(1)).items.get(0) instanceof com.tencent.mobileqq.setting.processor.g,"wrong placement");});
        test("settings-proxy-object-methods",()->{List<?> result=(List<?>)decorate(Collections.singletonList(new Section(Collections.singletonList("ordinary"),"","")),context);com.tencent.mobileqq.setting.processor.g p=(com.tencent.mobileqq.setting.processor.g)((Section)result.get(1)).items.get(0);expect(p.callback.equals(p.callback),"equals");expect(!p.callback.equals(null),"null equals");expect(p.callback.hashCode()==System.identityHashCode(p.callback),"hashCode");expect("QQSlimClick".equals(p.callback.toString()),"toString");expect(p.callback.invoke()==kotlin.Unit.INSTANCE,"invoke result");});
        test("settings-null-empty",()->{expect(decorate(null,context)==null,"null");List<?> empty=Collections.emptyList();expect(decorate(empty,context)==empty,"empty");});
        System.out.println("RESULT passed="+passed+" failed="+failed);
        if(failed!=0)throw new AssertionError("Regression failures="+failed);
    }
}

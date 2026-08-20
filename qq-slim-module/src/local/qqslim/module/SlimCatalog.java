package local.qqslim.module;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Directory catalogue copied from the original QQ瘦身 preset table. */
final class SlimCatalog {
    static final class Item {
        final String name,template; final File path; long bytes=-1;
        Item(String n,String t,File p){name=n;template=t;path=p;}
    }
    private static final String[] NAMES={
        "默认内部缓存","X5 WebView缓存","JSON卡片缓存","已下载的小程序","pddata缓存1","气泡/礼物/VIP素材",
        "默认外部缓存","AE素材缓存","TBS日志","TBS缓存","OneLog日志","OpenSDK临时文件","小世界缓存1","小世界缓存2","QQ秀下载","QQ钱包缓存","腾讯日志","地图缓存","视频编辑缓存","Zootopia下载","空间缓存",
        "音频缓存","小程序外部缓存","收藏缓存","接收文件缩略图","QQ图片","短视频广告","微信小程序缓存","Apollo素材","名片素材","表情素材","字体特效","字体信息","挂件素材","资料卡素材","群特效","戳一戳素材","当前帐号语音消息",
        "长截图缓存","聊天图片","图片磁盘缓存","头像缓存","热图缓存","pddata缓存2","照片缓存","涂鸦缓存","短视频缓存","缩略图缓存","群游戏卡片","群相册","Lottie素材","智图缓存","聊天图片（兼容项）","新名片缓存","QQ音乐缓存","群临时文件","频道临时文件","接收临时文件","NT接收数据",
        "MMKV缓存","TIM下载缓存","动态库缓存1","动态库缓存2","QQ表情资源","QQ动态库","Emoji缓存","模块图片缓存","模块视频缓存","模块崩溃日志"
    };
    private static final String[] PATHS={
        "G/cache","G/app_x5webview/Cache","G/files/ArkApp/","G/files/mini","G/files/pddata","G/files/files/vas_material_folder",
        "P/cache","P/files/ae","P/files/commonlog","P/files/tbs","P/files/onelog","P/files/opensdk_tmp","P/files/qcircle","P/qcircle","P/files/QQShowDownload","P/files/QWallet","P/files/tencent/","P/files/tencentmapsdk","P/files/video","P/files/zootopia_download","P/qzone",
        "P/Tencent/audio","P/Tencent/mini","P/Tencent/QQ_Favorite","P/files/QQfile_recv/.thumbnails","P/Tencent/QQ_Images","P/Tencent/QQ_Shortvideos","P/Tencent/wxminiapp","P/Tencent/MobileQQ/.apollo","P/Tencent/MobileQQ/.card","P/Tencent/MobileQQ/.emotionsm","P/Tencent/MobileQQ/.font_effect","P/Tencent/MobileQQ/.font_info","P/Tencent/MobileQQ/.pendant","P/Tencent/MobileQQ/.profilecard","P/Tencent/MobileQQ/.troop","P/Tencent/MobileQQ/.vaspoke","P/Tencent/MobileQQ/{{Q}}/ptt",
        "P/Tencent/MobileQQ/aio_long_shot","P/Tencent/MobileQQ/chatpic","P/Tencent/MobileQQ/diskcache","P/Tencent/MobileQQ/head","P/Tencent/MobileQQ/hotpic","P/Tencent/MobileQQ/pddata","P/Tencent/MobileQQ/photo","P/Tencent/MobileQQ/Scribble","P/Tencent/MobileQQ/shortvideo","P/Tencent/MobileQQ/thumb","P/Tencent/MobileQQ/troopgamecard","P/Tencent/MobileQQ/troopphoto","P/Tencent/MobileQQ/vas","P/Tencent/MobileQQ/zhitu","P/Tencent/MobileQQ/chatpic","P/Tencent/MobileQQ/newCard","P/Tencent/MobileQQ/qqmusic","P/Tencent/QQfile_recv/.trooptmp","P/Tencent/QQfile_recv/.guildtmp","P/Tencent/QQfile_recv/.tmp","P/Tencent/QQfile_recv/nt_data",
        "G/files/mmkv","G/files/tim","G/app_lib","G/app_libs","G/files/qq_emoticon_res","G/files/qqso","G/files/emoji","P/Serendipity/picCache","P/Serendipity/video","P/Serendipity/CrashReport"
    };
    static List<Item> resolve(Context context,String dataDir){
        File internal=new File(dataDir);
        // Environment is user-aware inside a user-0/user-999 host process.
        File external=externalRoot(context);
        ArrayList<Item> out=new ArrayList<>();
        for(int i=0;i<PATHS.length;i++){
            String t=PATHS[i];
            if(t.contains("{{Q}}")){
                File accounts=new File(external,"Tencent/MobileQQ");
                File[] children=accounts.listFiles();boolean found=false;
                if(children!=null)for(File f:children)if(f.isDirectory()&&f.getName().matches("[0-9]{5,12}")){
                    addSafe(out,NAMES[i]+" ("+f.getName()+")",t.replace("{{Q}}",f.getName()),internal,external);found=true;
                }
                if(!found)addSafe(out,NAMES[i]+"（未发现帐号目录）",t.replace("{{Q}}","ACCOUNT"),internal,external);
            }else addSafe(out,NAMES[i],t,internal,external);
        }
        return out;
    }
    /** Original HookEnv.getqqgen equivalent: QQ's per-user external app-data root. */
    static File externalRoot(Context context){
        File cache=context.getExternalCacheDir();
        if(cache!=null&&cache.getParentFile()!=null)return cache.getParentFile();
        return new File(Environment.getExternalStorageDirectory(),"Android/data/"+context.getPackageName());
    }
    private static void addSafe(List<Item> out,String n,String t,File internal,File external){
        File root=t.charAt(0)=='G'?internal:external;
        String rel=t.substring(1);while(rel.startsWith("/"))rel=rel.substring(1);
        out.add(new Item(n,t,new File(root,rel)));
    }
    static long size(File f){
        if(f==null||!f.exists())return 0;
        if(f.isFile())return f.length();long total=0;File[] a=f.listFiles();
        if(a!=null)for(File x:a)total+=size(x);return total;
    }
    static boolean delete(File f,File internal,File external){
        try{
            String p=f.getCanonicalPath(),g=internal.getCanonicalPath(),e=external.getCanonicalPath();
            if(!(p.startsWith(g+File.separator)||p.startsWith(e+File.separator)))return false;
            if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)if(!delete(x,internal,external))return false;}
            return !f.exists()||f.delete();
        }catch(Exception ignored){return false;}
    }
}

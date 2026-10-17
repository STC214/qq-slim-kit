package com.tencent.mobileqq.setting.processor;
import android.content.Context;
import com.tencent.mobileqq.setting.main.processor.Base;
import kotlin.jvm.functions.Function0;
public class g extends Base {
    public final int id;
    public final CharSequence label;
    public Function0 callback;
    public g(Context context,int id,CharSequence label,int icon){this.id=id;this.label=label;}
    public void a(Function0 callback){this.callback=callback;}
}

package local.qqslim.module;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public final class UiHarnessActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);TextView view=new TextView(this);view.setText("UI test fixture");setContentView(view);}
}

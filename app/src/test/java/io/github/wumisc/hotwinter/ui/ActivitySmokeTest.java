package io.github.wumisc.hotwinter.ui;

import android.view.View;

import androidx.test.core.app.ActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;

import io.github.wumisc.hotwinter.R;

import static org.junit.Assert.assertNotNull;

/** 启动后的页面切换、刷新和捐赠入口冒烟测试，防止空指针重新回到主路径。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ActivitySmokeTest {

    @Test
    public void tabsRenderAndDonateDoNotCrash() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.heroTemp));
                assertNotNull(activity.findViewById(R.id.scenery));
                assertNotNull(activity.findViewById(R.id.couple));

                click(activity, R.id.tabFeature);
                click(activity, R.id.tabAbout);
                click(activity, R.id.tabHome);

                try {
                    Method render = MainActivity.class.getDeclaredMethod("render");
                    render.setAccessible(true);
                    render.invoke(activity);
                } catch (Exception e) {
                    throw new AssertionError("render() 不应抛异常", e);
                }

                click(activity, R.id.chipDonate);
            });
        }
    }

    private void click(MainActivity activity, int id) {
        View view = activity.findViewById(id);
        if (view != null) view.performClick();
    }
}

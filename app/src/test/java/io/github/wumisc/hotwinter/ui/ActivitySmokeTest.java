package io.github.wumisc.hotwinter.ui;

import android.view.View;

import androidx.test.core.app.ActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Method;

import io.github.wumisc.hotwinter.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 启动后的页面切换、一级「我的」页和刷新冒烟测试，防止空指针重新回到主路径。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ActivitySmokeTest {

    @Test
    public void tabsRenderAndMinePageDoNotCrash() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.heroTemp));
                assertNotNull(activity.findViewById(R.id.scenery));
                assertNotNull(activity.findViewById(R.id.couple));

                click(activity, R.id.tabFeature);
                click(activity, R.id.tabMine);
                assertNotNull(activity.findViewById(R.id.cardProfile));
                assertNotNull(activity.findViewById(R.id.rowAbout));
                assertNotNull(activity.findViewById(R.id.tvProfileName));
                assertNotNull(activity.findViewById(R.id.chipCloudMiss));
                assertNotNull(activity.findViewById(R.id.chipCloudHug));
                assertNotNull(activity.findViewById(R.id.chipCloudKiss));
                assertNotNull(activity.findViewById(R.id.chipCloudNight));
                assertNotNull(activity.findViewById(R.id.rowCare));
                assertNotNull(activity.findViewById(R.id.tvTogetherDays));
                assertNotNull(activity.findViewById(R.id.tvInteractHint));
                assertNotNull(activity.findViewById(R.id.tvNudgeGroup));
                assertNotNull(activity.findViewById(R.id.tvAffectionGroup));
                assertNotNull(activity.findViewById(R.id.tvDmGroup));
                assertNotNull(activity.findViewById(R.id.chipDmChannelCloud));
                assertNotNull(activity.findViewById(R.id.chipDmChannelUdp));
                assertNotNull(activity.findViewById(R.id.chipDmChannelBt));
                assertNotNull(activity.findViewById(R.id.tvDmChannelHint));

                click(activity, R.id.chipDmChannelCloud);
                assertEquals("未登录点云端发送应提示登录", "先到「我的」登录云端，再选云端发送",
                        ShadowToast.getTextOfLatestToast());
                assertFalse("未登录时云端通道不应被选中",
                        activity.findViewById(R.id.chipDmChannelCloud).isSelected());
                click(activity, R.id.chipCloudMiss);
                assertEquals("未登录点动作应明确提示", "先登录并绑定伴侣",
                        ShadowToast.getTextOfLatestToast());
                click(activity, R.id.rowCare);
                assertTrue("关心提醒应能打开设置弹窗",
                        ShadowDialog.getLatestDialog() != null
                                && ShadowDialog.getLatestDialog().isShowing());
                if (ShadowDialog.getLatestDialog() != null) ShadowDialog.getLatestDialog().dismiss();
                click(activity, R.id.tabHome);

                try {
                    Method render = MainActivity.class.getDeclaredMethod("render");
                    render.setAccessible(true);
                    render.invoke(activity);
                } catch (Exception e) {
                    throw new AssertionError("render() 不应抛异常", e);
                }
            });
        }
    }

    private void click(MainActivity activity, int id) {
        View view = activity.findViewById(id);
        if (view != null) view.performClick();
    }
}

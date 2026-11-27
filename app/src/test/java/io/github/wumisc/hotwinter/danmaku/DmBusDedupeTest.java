package io.github.wumisc.hotwinter.danmaku;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 合并通道后的消息去重：同一 id 只触发一次界面/悬浮窗。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DmBusDedupeTest {

    @After
    public void cleanUp() {
        DmBus.clearForTest();
    }

    @Test
    public void sameIdFromMultipleChannelsIsPublishedOnce() {
        List<DmMessage> received = new ArrayList<>();
        DmBus.Listener listener = new DmBus.Listener() {
            @Override
            public void onDanmaku(DmMessage message) {
                received.add(message);
            }
        };
        DmBus.addListener(listener);
        try {
            assertTrue(DmBus.publish(new DmMessage("love", "阿冬", "想你了", "same-id")));
            assertFalse(DmBus.publish(new DmMessage("love", "阿冬", "想你了", "same-id")));
            assertTrue(DmBus.publish(new DmMessage("love", "阿冬", "想你了", "another-id")));
            ShadowLooper.idleMainLooper();
            assertEquals(2, received.size());
        } finally {
            DmBus.removeListener(listener);
        }
    }
}

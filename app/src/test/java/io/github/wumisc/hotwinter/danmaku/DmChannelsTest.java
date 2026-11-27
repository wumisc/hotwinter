package io.github.wumisc.hotwinter.danmaku;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 发送方式设置的默认值和持久化。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DmChannelsTest {

    @Test
    public void defaultsKeepLegacyLanAndBluetoothOn() {
        Context context = ApplicationProvider.getApplicationContext();
        DmSettings settings = new DmSettings(context);
        assertFalse(settings.cloudChannel());
        assertTrue(settings.udpChannel());
        assertTrue(settings.btChannel());
    }

    @Test
    public void selectedChannelsPersist() {
        Context context = ApplicationProvider.getApplicationContext();
        DmSettings settings = new DmSettings(context);
        settings.setCloudChannel(true);
        settings.setUdpChannel(false);
        settings.setBtChannel(false);

        DmSettings reread = new DmSettings(context);
        assertTrue(reread.cloudChannel());
        assertFalse(reread.udpChannel());
        assertFalse(reread.btChannel());
    }

    @Test
    public void channelNamesMatchBitmask() {
        assertEquals("本机", DanmakuSender.channelNames(0));
        assertEquals("云端", DanmakuSender.channelNames(DanmakuSender.VIA_CLOUD));
        assertEquals("局域网 / 蓝牙",
                DanmakuSender.channelNames(DanmakuSender.VIA_UDP | DanmakuSender.VIA_BT));
        assertEquals("云端 / 局域网 / 蓝牙", DanmakuSender.channelNames(
                DanmakuSender.VIA_CLOUD | DanmakuSender.VIA_UDP | DanmakuSender.VIA_BT));
    }
}

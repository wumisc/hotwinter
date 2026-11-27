package io.github.wumisc.hotwinter.cloud;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CloudSyncTest {

    @Test
    public void reconnectBackoffGrowsAndCaps() {
        assertEquals(5_000L, CloudSync.reconnectDelay(0));
        assertEquals(10_000L, CloudSync.reconnectDelay(1));
        assertEquals(20_000L, CloudSync.reconnectDelay(2));
        assertEquals(40_000L, CloudSync.reconnectDelay(3));
        assertEquals(60_000L, CloudSync.reconnectDelay(4));
        assertEquals(60_000L, CloudSync.reconnectDelay(99));
    }
}

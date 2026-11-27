package io.github.wumisc.hotwinter.cloud;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CloudPrefsTest {

    @Test
    public void lastEmailSurvivesLogout() {
        Context context = ApplicationProvider.getApplicationContext();
        CloudPrefs prefs = CloudPrefs.get(context);
        prefs.saveSession("", "last-login@example.com", "阿冬", "user-id");
        assertEquals("last-login@example.com", prefs.lastEmail());

        prefs.clearSession();
        assertFalse(prefs.hasToken());
        assertEquals("last-login@example.com", prefs.lastEmail());
    }

    @Test
    public void careFlagsPersist() {
        CloudPrefs prefs = CloudPrefs.get(ApplicationProvider.getApplicationContext());
        prefs.setCareBattery(true);
        prefs.setCareHeat(false);
        assertTrue(prefs.careBattery());
        assertFalse(prefs.careHeat());

        prefs.setCareBattery(false);
        prefs.setCareHeat(true);
        assertFalse(prefs.careBattery());
        assertTrue(prefs.careHeat());
    }
}

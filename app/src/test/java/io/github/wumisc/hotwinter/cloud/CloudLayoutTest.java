package io.github.wumisc.hotwinter.cloud;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.wumisc.hotwinter.R;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CloudLayoutTest {

    @Test
    public void cloudLayoutInflates() {
        Context context = new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.Theme_HotWinter);
        View root = LayoutInflater.from(context).inflate(R.layout.activity_cloud, null);
        assertNotNull(root);
        assertNotNull(root.findViewById(R.id.switchCloudShare));
        assertNotNull(root.findViewById(R.id.etCloudEmail));
        assertNotNull(root.findViewById(R.id.cardProfile));
        assertNotNull(root.findViewById(R.id.tvAvatarInitial));
        assertNotNull(root.findViewById(R.id.chipProfileAction));
        assertNotNull(root.findViewById(R.id.rowAccount));
        assertNotNull(root.findViewById(R.id.rowAbout));
        assertNotNull(root.findViewById(R.id.tvTogetherDays));
        assertNotNull(root.findViewById(R.id.tvInteractHint));
        assertNotNull(root.findViewById(R.id.tvNudgeGroup));
        assertNotNull(root.findViewById(R.id.tvAffectionGroup));
        assertNotNull(root.findViewById(R.id.tvDmGroup));
        for (int id : new int[]{R.id.chipCloudMiss, R.id.chipCloudHug,
                R.id.chipCloudKiss, R.id.chipCloudNight}) {
            assertNotNull(root.findViewById(id));
        }
    }

    @Test
    public void cloudActivityCreatesSignedOut() {
        try (ActivityScenario<CloudActivity> scenario = ActivityScenario.launch(CloudActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.cardLogin));
                assertNotNull(activity.findViewById(R.id.chipCloudLogin));
                assertNotNull(activity.findViewById(R.id.tvProfileName));
                assertNotNull(activity.findViewById(R.id.tvAccountMenuHint));
            });
        }
    }
}

package io.github.wumisc.hotwinter.ui;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.wumisc.hotwinter.R;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LayoutInflateTest {

    @Test
    public void mainLayoutInflatesWithoutThrowing() {
        Context context = new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.Theme_HotWinter);
        View root = LayoutInflater.from(context).inflate(R.layout.activity_main, null);
        assertNotNull(root);
        assertNotNull(root.findViewById(R.id.heroTemp));
    }
}

package io.github.wumisc.hotwinter.cloud;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import io.github.wumisc.hotwinter.R;

/**
 * 独立打开的云端页。界面逻辑由 {@link CloudPanel} 统一承载，
 * 一级「我的」页复用同一套控制器。
 */
public class CloudActivity extends AppCompatActivity {

    private CloudPanel panel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud);
        View root = findViewById(R.id.scrollCloud);
        panel = new CloudPanel(this, root, false);
    }

    @Override
    protected void onDestroy() {
        if (panel != null) panel.destroy();
        super.onDestroy();
    }
}

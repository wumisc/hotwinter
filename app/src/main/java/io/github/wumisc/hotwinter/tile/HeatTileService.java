package io.github.wumisc.hotwinter.tile;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import io.github.wumisc.hotwinter.core.HeatRuntime;
import io.github.wumisc.hotwinter.core.HeatStatus;
import io.github.wumisc.hotwinter.service.HeatingService;

/** 控制中心磁贴：点一下开始发热，再点一下停下。只读 HeatRuntime，不持有业务状态。 */
public class HeatTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        render();
    }

    @Override
    public void onClick() {
        super.onClick();
        Context context = getApplicationContext();
        if (HeatRuntime.isActive()) {
            HeatingService.stop(context);
        } else {
            HeatingService.start(context);
        }
        render();
        new Handler(Looper.getMainLooper()).postDelayed(this::render, 600);
    }

    private void render() {
        Tile tile = getQsTile();
        if (tile == null) return;

        HeatStatus status = HeatRuntime.get();
        tile.setState(status.running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(io.github.wumisc.hotwinter.R.string.tile_label));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(status.running
                    ? status.preset.label + " " + Math.round(status.batteryTempC) + "°C"
                    : "点一下开始发热");
        }
        tile.updateTile();
    }

    public static void refresh(Context context) {
        try {
            TileService.requestListeningState(
                    context,
                    new ComponentName(context, HeatTileService.class));
        } catch (RuntimeException ignored) {
            // 磁贴未添加到控制中心时无需刷新
        }
    }
}

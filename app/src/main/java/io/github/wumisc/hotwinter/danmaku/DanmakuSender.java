package io.github.wumisc.hotwinter.danmaku;

import android.content.Context;

import io.github.wumisc.hotwinter.couple.CoupleLink;

/** 发送一条弹幕：局域网和蓝牙能发就都发一份，同时在本地先放出来。 */
public final class DanmakuSender {

    public static final int VIA_UDP = 1;
    public static final int VIA_BT = 2;

    private DanmakuSender() {
    }

    /** 返回走了哪些通道：1=局域网，2=蓝牙，3=两者都发。 */
    public static int send(Context context, String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        DmSettings settings = new DmSettings(context);
        DmMessage message = new DmMessage(settings.room(), settings.nick(), text.trim());

        int channels = 0;
        if (UdpMesh.active() && UdpMesh.send(message)) {
            channels |= VIA_UDP;
        }
        if (CoupleLink.isConnected() && CoupleLink.send(message.encode())) {
            channels |= VIA_BT;
        }
        DmBus.publish(message);
        return channels;
    }
}

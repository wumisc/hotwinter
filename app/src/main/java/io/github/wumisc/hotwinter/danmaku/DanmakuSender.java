package io.github.wumisc.hotwinter.danmaku;

import android.content.Context;

import io.github.wumisc.hotwinter.cloud.CloudSync;
import io.github.wumisc.hotwinter.couple.CoupleLink;

/**
 * 一条弹幕按用户勾选的通道合并发送：云端、局域网、蓝牙可任意组合。
 * 三个通道使用同一个消息 id；接收端会按 id 去重，避免同一句显示三次。
 */
public final class DanmakuSender {

    public static final int VIA_UDP = 1;
    public static final int VIA_BT = 2;
    public static final int VIA_CLOUD = 4;

    private DanmakuSender() {
    }

    /** 返回实际走通的通道位：1=局域网，2=蓝牙，4=云端。 */
    public static int send(Context context, String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        DmSettings settings = new DmSettings(context);
        String value = text.trim();
        int channels = 0;

        // 先发云端拿消息 id，再让局域网/蓝牙复用同一个 id。
        String cloudId = "";
        if (settings.cloudChannel()) {
            CloudSync sync = CloudSync.get(context);
            if (sync.isConnected()) cloudId = sync.sendDanmaku(value);
            if (!cloudId.isEmpty()) channels |= VIA_CLOUD;
        }

        DmMessage message = new DmMessage(settings.room(), settings.nick(), value,
                cloudId.isEmpty() ? null : cloudId);
        if (settings.udpChannel() && UdpMesh.active() && UdpMesh.send(message)) {
            channels |= VIA_UDP;
        }
        if (settings.btChannel() && CoupleLink.isConnected() && CoupleLink.send(message.encode())) {
            channels |= VIA_BT;
        }
        // 本地只预演一次；云端回显、蓝牙和局域网回环都按消息 id 去重。
        DmBus.publish(message);
        return channels;
    }

    /** 把通道位转成用户能看懂的名字，用于发送后的回执。 */
    public static String channelNames(int channels) {
        StringBuilder names = new StringBuilder();
        if ((channels & VIA_CLOUD) != 0) names.append("云端");
        if ((channels & VIA_UDP) != 0) append(names, "局域网");
        if ((channels & VIA_BT) != 0) append(names, "蓝牙");
        return names.length() == 0 ? "本机" : names.toString();
    }

    private static void append(StringBuilder names, String name) {
        if (names.length() > 0) names.append(" / ");
        names.append(name);
    }
}

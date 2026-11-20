package io.github.wumisc.hotwinter.danmaku;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 一条弹幕。文本走 Base64，避免房间名/昵称/正文里的分隔符把协议拆坏。
 * 协议：DM\t房间\t昵称\t消息id\tBase64(正文)；旧四段包也兼容。
 */
public final class DmMessage {

    public static final String PREFIX = "DM";
    private static final char SEP = '\t';
    private static final int MAX_TEXT = 200;

    public final String room;
    public final String nick;
    public final String text;
    public final String id;
    public final long at;

    public DmMessage(String room, String nick, String text) {
        this(room, nick, text, newId());
    }

    private DmMessage(String room, String nick, String text, String id) {
        this.room = room == null ? "" : room;
        this.nick = nick == null ? "" : nick;
        String value = text == null ? "" : text;
        this.text = value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
        this.id = id == null || id.isEmpty() ? newId() : id;
        this.at = System.currentTimeMillis();
    }

    public String encode() {
        String body = java.util.Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        return PREFIX + SEP + room + SEP + nick + SEP + id + SEP + body;
    }

    /** 解析失败返回 null，调用方直接丢弃这一包。 */
    public static DmMessage decode(String raw) {
        if (raw == null) return null;
        String line = raw.trim();
        if (!line.startsWith(PREFIX + SEP)) return null;
        String[] parts = line.split("\t", 5);
        if (parts.length < 4) return null;
        try {
            String id = "";
            String body;
            if (parts.length == 5) {
                id = parts[3];
                body = parts[4];
            } else {
                body = parts[3];
            }
            String text = new String(java.util.Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
            if (text.isEmpty()) return null;
            return new DmMessage(parts[1], parts[2], text, id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

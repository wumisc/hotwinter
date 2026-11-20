package io.github.wumisc.hotwinter.danmaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DmMessageTest {

    @Test
    public void roundTripKeepsSeparatorsAndUnicode() {
        DmMessage original = new DmMessage("love", "老婆", "多喝热水\t记得回家 ❤");
        DmMessage decoded = DmMessage.decode(original.encode());
        assertNotNull(decoded);
        assertEquals("love", decoded.room);
        assertEquals("老婆", decoded.nick);
        assertEquals("多喝热水\t记得回家 ❤", decoded.text);
    }

    @Test
    public void rejectsForeignPackets() {
        assertNull(DmMessage.decode("HEAT_ON"));
        assertNull(DmMessage.decode("DM"));
        assertNull(DmMessage.decode(null));
    }

    @Test
    public void rejectsBrokenBase64() {
        assertNull(DmMessage.decode("DM\tlove\tnick\t***"));
    }

    @Test
    public void givesEachMessageItsOwnId() {
        DmMessage first = new DmMessage("love", "n", "在吗");
        DmMessage second = new DmMessage("love", "n", "在吗");
        assertTrue(!first.id.equals(second.id));
        assertTrue(first.encode().split("\t").length == 5);
    }

    @Test
    public void stillDecodesLegacyFourPartPacket() {
        String body = java.util.Base64.getEncoder()
                .encodeToString("旧版包".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DmMessage message = DmMessage.decode("DM\tlove\t旧昵称\t" + body);
        assertNotNull(message);
        assertEquals("旧版包", message.text);
    }

    @Test
    public void truncatesOversizedText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 400; i++) builder.append('冷');
        DmMessage message = new DmMessage("love", "n", builder.toString());
        assertTrue(message.text.length() <= 200);
        assertEquals(200, message.text.length());
    }
}

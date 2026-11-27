package io.github.wumisc.hotwinter.cloud;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 本地集成测试：先起 `npm run dev`，再跑这个测试。
 * 没有本地 Worker 时自动跳过，不影响常规 JVM 测试。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CloudApiTest {

    private static final String BASE = "http://127.0.0.1:8787";

    @Before
    public void requireLocalWorker() {
        try {
            JSONObject health = CloudApi.get(BASE, "/api/health", null);
            Assume.assumeTrue(health.optBoolean("ok", false));
        } catch (Throwable error) {
            Assume.assumeNoException(error);
        }
    }

    @Test
    public void registerLoginAndMe() throws Exception {
        String email = "jvm-" + UUID.randomUUID() + "@example.com";
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", "correct horse battery");
        body.put("displayName", "JVM");
        JSONObject registered = CloudApi.post(BASE, "/api/register", null, body);
        String token = registered.optString("token", "");
        assertTrue(token.length() > 30);
        assertNotNull(registered.optString("recoveryCode"));

        JSONObject loginBody = new JSONObject();
        loginBody.put("email", email);
        loginBody.put("password", "correct horse battery");
        JSONObject login = CloudApi.post(BASE, "/api/login", null, loginBody);
        assertEquals(registered.optJSONObject("user").optString("id"),
                login.optJSONObject("user").optString("id"));

        JSONObject me = CloudApi.get(BASE, "/api/me", login.optString("token"));
        assertEquals("JVM", me.optJSONObject("user").optString("displayName"));
        assertTrue(me.isNull("couple"));
    }
}

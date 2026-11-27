package io.github.wumisc.hotwinter.cloud;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CloudAuthErrorTest {

    @Test
    public void login401IsNotTreatedAsExpiredSession() {
        CloudApi.CloudException badCredentials =
                new CloudApi.CloudException("bad_credentials", "邮箱或密码不正确", 401);
        assertFalse(CloudApi.isSessionExpired(badCredentials, ""));
        assertFalse(CloudApi.isSessionExpired(badCredentials, null));
        assertFalse(CloudApi.isSessionExpired(
                new CloudApi.CloudException("bad_credentials", "邮箱或密码不正确", 403), "token"));
        assertTrue(CloudApi.isSessionExpired(badCredentials, "a-real-token"));
    }
}

package io.github.wumisc.hotwinter.cloud;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** 云端 REST 的最小封装：同步调用，调用方放在后台线程。 */
public final class CloudApi {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static volatile OkHttpClient client;

    private CloudApi() {
    }

    public static OkHttpClient http() {
        OkHttpClient local = client;
        if (local == null) {
            synchronized (CloudApi.class) {
                local = client;
                if (local == null) {
                    local = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .pingInterval(0, TimeUnit.MILLISECONDS)
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    public static JSONObject post(String baseUrl, String path, String token, JSONObject body)
            throws IOException {
        return call("POST", baseUrl, path, token, body);
    }

    public static JSONObject get(String baseUrl, String path, String token) throws IOException {
        return call("GET", baseUrl, path, token, null);
    }

    public static Request websocketRequest(String baseUrl, String token) {
        String ws = CloudPrefs.normalize(baseUrl)
                .replaceFirst("^http://", "ws://")
                .replaceFirst("^https://", "wss://") + "/api/ws";
        return new Request.Builder()
                .url(ws)
                .header("Authorization", "Bearer " + token)
                .build();
    }

    private static JSONObject call(String method, String baseUrl, String path,
                                   String token, JSONObject body) throws IOException {
        String base = CloudPrefs.normalize(baseUrl);
        if (base.isEmpty()) throw new CloudException("bad_base", "还没有填写云端地址", 0);
        String url = base + path;
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            builder.post(RequestBody.create(
                    body == null ? "{}" : body.toString(), JSON));
        } else {
            builder.get();
        }
        try (Response response = http().newCall(builder.build()).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            JSONObject json = parse(text);
            if (!response.isSuccessful()) {
                String error = json.optString("error", "http_" + response.code());
                String message = json.optString("message", "请求失败（" + response.code() + "）");
                throw new CloudException(error, message, response.code());
            }
            return json;
        }
    }

    private static JSONObject parse(String text) throws IOException {
        try {
            return new JSONObject(text == null || text.isEmpty() ? "{}" : text);
        } catch (JSONException error) {
            throw new CloudException("bad_response", "云端返回的不是合法 JSON", 0);
        }
    }

    /** 只有已经携带 token 的请求返回 401，才算会话失效；登录/注册的 401 是账号密码错误。 */
    public static boolean isSessionExpired(CloudException error, String token) {
        return error != null && error.status == 401
                && token != null && !token.trim().isEmpty();
    }

    public static final class CloudException extends IOException {
        public final String code;
        public final int status;

        public CloudException(String code, String message, int status) {
            super(message);
            this.code = code;
            this.status = status;
        }
    }
}

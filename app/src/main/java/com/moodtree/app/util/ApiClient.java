package com.moodtree.app.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** /api/v1/ 全部端点，HttpURLConnection 同步阻塞调用（调用方必须放后台线程）。
 *  认证用 Bearer Token；离线/网络层失败抛 ApiException(status=0)，HTTP 错误抛 ApiException(status=状态码)。 */
public class ApiClient {

    public static class ApiException extends Exception {
        public final int status;   // 0=网络层失败（离线）；>0=HTTP 状态码
        public ApiException(int status, String msg) { super(msg); this.status = status; }
    }

    private final Config config;
    private static final int CONNECT_MS = 6000;
    private static final int READ_MS = 15000;

    public ApiClient(Config config) { this.config = config; }

    // ---------- 底层 ----------

    /** 探活：成功返回 true，任何失败都返回 false（UI 用它判断在线/离线） */
    public boolean ping() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(config.serverBase() + "/api/v1/ping/").toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestMethod("GET");
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private JsonObject request(String method, String path, JsonObject body, boolean auth, int readMs)
            throws ApiException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(config.serverBase() + path).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(readMs);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            if (auth) {
                String t = config.token();
                if (t.isEmpty()) throw new ApiException(401, "未登录");
                c.setRequestProperty("Authorization", "Bearer " + t);
            }
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String text = readAll(is);
            if (code >= 200 && code < 300) {
                return parse(text);
            }
            // HTTP 错误：尽量从 {error:...} 取消息
            String msg;
            try {
                msg = parse(text).get("error").getAsString();
            } catch (Exception e) {
                msg = "请求失败（" + code + "）";
            }
            throw new ApiException(code, msg);
        } catch (ApiException ae) {
            throw ae;
        } catch (Exception e) {
            throw new ApiException(0, "网络连接失败：" + e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private JsonObject get(String path, Map<String, String> params, boolean auth) throws ApiException {
        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder(path);
            sb.append(path.contains("?") ? "&" : "?");
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (!first) sb.append("&");
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
                sb.append("=");
                // 关键：值也要 URL 编码，ISO8601 里的 +08:00 的 + 不能被当成空格
                sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
            path = sb.toString();
        }
        return request("GET", path, null, auth, READ_MS);
    }

    private JsonObject post(String path, JsonObject body, boolean auth) throws ApiException {
        return request("POST", path, body, auth, READ_MS);
    }

    private static JsonObject parse(String text) {
        JsonElement el = JsonParser.parseString(text == null ? "" : text);
        return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
    }

    private static String readAll(InputStream is) {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        } catch (Exception ignored) { }
        return sb.toString();
    }

    // ---------- 端点 ----------

    /** {username, password, device?} -> {token, username, streak} */
    public JsonObject login(String username, String password) throws ApiException {
        JsonObject b = new JsonObject();
        b.addProperty("username", username);
        b.addProperty("password", password);
        b.addProperty("device", config.device());
        return post("/api/v1/login/", b, false);
    }

    /** 注册成功直接返回 {token, username, streak}（注册即登录） */
    public JsonObject register(String username, String password) throws ApiException {
        JsonObject b = new JsonObject();
        b.addProperty("username", username);
        b.addProperty("password", password);
        b.addProperty("agree", true);   // 界面勾选框已确认，如实上报
        b.addProperty("device", config.device());
        return post("/api/v1/register/", b, false);
    }

    public void logout() throws ApiException {
        post("/api/v1/logout/", new JsonObject(), true);
    }

    /** 增量拉取。since 传 null 表示全量。返回 {server_time, entries[]} */
    public JsonObject pullEntries(String since) throws ApiException {
        return get("/api/v1/sync/pull/",
                since == null ? null : Map.of("since", since), true);
    }

    /** 批量上传。entriesJson 是 {"entries":[...]}。返回 {saved,updated,skipped,errors,server_time} */
    public JsonObject pushEntries(JsonObject entriesJson) throws ApiException {
        return post("/api/v1/sync/push/", entriesJson, true);
    }

    /** 推荐目录缓存：{songs,activities,tips,videos,moods} */
    public JsonObject catalog() throws ApiException {
        return get("/api/v1/catalog/", null, true);
    }

    /** 在线推荐：{mood,songs,activities,tips,video,info} */
    public JsonObject recommend(String mood) throws ApiException {
        return get("/api/v1/recommend/", Map.of("mood", mood), true);
    }

    /** AI 树洞聊天（60s 超时）：{reply,crisis,hotline}。危机硬拦截在服务端做 */
    public JsonObject chat(String message) throws ApiException {
        JsonObject b = new JsonObject();
        b.addProperty("message", message);
        return request("POST", "/api/v1/chat/", b, true, 60000);
    }

    /** {messages:[{role,content}]} */
    public JsonObject chatHistory() throws ApiException {
        return get("/api/v1/chat/history/", null, true);
    }

    public void chatClear() throws ApiException {
        post("/api/v1/chat/clear/", new JsonObject(), true);
    }

    /** {username,streak,total_entries,date_joined,badges:[{emoji,name,days}]} */
    public JsonObject profile() throws ApiException {
        return get("/api/v1/profile/", null, true);
    }
}

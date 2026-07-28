package com.moodtree.app.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moodtree.app.db.AppDatabase;
import com.moodtree.app.db.CatalogDao;
import com.moodtree.app.db.CatalogItem;
import com.moodtree.app.db.MoodDao;
import com.moodtree.app.db.MoodEntry;
import com.moodtree.app.model.MoodMeta;
import com.moodtree.app.util.ApiClient;
import com.moodtree.app.util.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 同步引擎：先推本地脏记录（sync/push），再拉服务器变化（sync/pull）。
 *  规则与服务端 + Windows 端一致：uuid 去重、updated_at 最新者赢、墓碑软删。
 *  失败不抛异常——返回 SyncResult，离线静默跳过。调用方放后台线程。 */
public class SyncEngine {

    public static class SyncResult {
        public int pushed, pulled, failed;
        public String error;          // null = 成功（或离线跳过）
        public boolean offline;       // 连不上服务器

        public interface Callback { void onResult(SyncResult r); }

        public static SyncResult error(String msg, boolean offline) {
            SyncResult r = new SyncResult();
            r.error = msg;
            r.offline = offline;
            return r;
        }

        public String summary() {
            if (error != null) return error;
            return "已同步（上传 " + pushed + " 条，下载 " + pulled + " 条）";
        }
    }

    private final Config config;
    private final ApiClient api;
    private final AppDatabase db;

    public SyncEngine(Config config, ApiClient api, AppDatabase db) {
        this.config = config;
        this.api = api;
        this.db = db;
    }

    /** 执行一轮完整同步：先推后拉。未登录直接跳过。 */
    public SyncResult sync() {
        if (config.token().isEmpty()) {
            return SyncResult.error("未登录", false);
        }
        try {
            return doSync();
        } catch (ApiClient.ApiException e) {
            if (e.status == 401) return SyncResult.error("登录已过期，请重新登录", false);
            return SyncResult.error(e.getMessage(), e.status == 0);
        } catch (Exception e) {
            return SyncResult.error("本地数据库出错：" + e.getMessage(), false);
        }
    }

    private SyncResult doSync() throws Exception {
        SyncResult r = new SyncResult();
        MoodDao dao = db.moodDao();

        // ---- 推：本地脏记录 → 服务器 ----
        List<MoodEntry> dirty = dao.listDirty();
        if (!dirty.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (MoodEntry e : dirty) arr.add(toJson(e));
            JsonObject payload = new JsonObject();
            payload.add("entries", arr);
            JsonObject resp = api.pushEntries(payload);

            // 服务端按 uuid 报错的条目不能去掉脏标记，其余全部标干净
            Set<String> bad = new HashSet<>();
            if (resp.has("errors")) {
                for (JsonElement el : resp.getAsJsonArray("errors")) {
                    JsonObject eo = el.getAsJsonObject();
                    if (eo.has("uuid")) bad.add(eo.get("uuid").getAsString());
                }
            }
            List<String> ok = new ArrayList<>();
            for (MoodEntry e : dirty) if (!bad.contains(e.uuid)) ok.add(e.uuid);
            if (!ok.isEmpty()) dao.markClean(ok);
            r.pushed = ok.size();
            r.failed = bad.size();
        }

        // ---- 拉：服务器变化 → 本地 ----
        String since = db.kvDao().get("last_sync");
        JsonObject resp = api.pullEntries(since);
        for (JsonElement el : resp.getAsJsonArray("entries")) {
            MoodEntry e = fromJson(el.getAsJsonObject());
            if (saveFromServer(dao, e)) r.pulled++;
        }
        // 保存服务端时间作为下次增量起点（服务端权威时钟，避免本机时间不准）
        if (resp.has("server_time")) {
            db.kvDao().set("last_sync", resp.get("server_time").getAsString());
        }
        return r;
    }

    /** 服务端拉下来的记录入库。本地有更新的（含未上传的脏数据）则跳过，保持本地优先 */
    private boolean saveFromServer(MoodDao dao, MoodEntry e) {
        MoodEntry local = dao.get(e.uuid);
        if (local != null && local.updatedAt != null && e.updatedAt != null
                && local.updatedAt.compareTo(e.updatedAt) >= 0) {
            return false;   // 本地更新或相同，不覆盖
        }
        e.dirty = false;
        dao.upsert(e);
        return true;
    }

    /** 刷新推荐目录缓存（登录后或用户手动刷新时调用；离线静默失败） */
    public boolean refreshCatalog() {
        try {
            JsonObject cat = api.catalog();
            CatalogDao cdao = db.catalogDao();
            for (String kind : new String[]{"songs", "activities", "tips", "videos"}) {
                if (!cat.has(kind)) continue;
                cdao.clear(kind);
                for (JsonElement el : cat.getAsJsonArray(kind)) {
                    JsonObject o = el.getAsJsonObject();
                    CatalogItem item = new CatalogItem();
                    item.kind = kind;
                    item.id = o.has("id") ? o.get("id").getAsInt() : o.hashCode();
                    item.payload = o.toString();
                    cdao.put(item);
                }
            }
            // 心情定义也缓存下来，离线兜底定义可被服务端覆盖
            if (cat.has("moods")) {
                db.kvDao().set("moods_cache", cat.get("moods").toString());
                MoodMeta.overrideFromCatalogJson(cat.get("moods").toString());
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- MoodEntry <-> JSON（与服务端字段名一一对应）----------

    public static JsonObject toJson(MoodEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", e.uuid);
        o.addProperty("date", e.date);
        if (e.at != null) o.addProperty("at", e.at);
        o.addProperty("mood", e.mood);
        o.addProperty("note", e.note == null ? "" : e.note);
        o.addProperty("deleted", e.deleted);
        o.addProperty("updated_at", e.updatedAt);
        o.addProperty("intensity_level", e.intensityLevel);
        o.addProperty("intensity_percent", e.intensityPercent);
        return o;
    }

    public static MoodEntry fromJson(JsonObject o) {
        MoodEntry e = new MoodEntry();
        e.uuid = o.get("uuid").getAsString();
        e.date = o.get("date").getAsString();
        e.at = o.has("at") && !o.get("at").isJsonNull() ? o.get("at").getAsString() : null;
        e.mood = o.get("mood").getAsString();
        e.note = o.has("note") && !o.get("note").isJsonNull() ? o.get("note").getAsString() : "";
        e.deleted = o.has("deleted") && o.get("deleted").getAsBoolean();
        e.updatedAt = o.get("updated_at").getAsString();
        e.dirty = false;
        e.intensityLevel = o.has("intensity_level") ? o.get("intensity_level").getAsInt() : 0;
        e.intensityPercent = o.has("intensity_percent") ? o.get("intensity_percent").getAsInt() : 0;
        return e;
    }
}

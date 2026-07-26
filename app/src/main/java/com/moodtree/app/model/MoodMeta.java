package com.moodtree.app.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 10 种心情定义（与服务端 MOODS 一致）。离线兜底是这里的硬编码；登录同步目录后会覆盖。 */
public class MoodMeta {

    public String key;
    public String label;
    public String emoji;
    public String color;

    private static List<MoodMeta> MOODS = defaultMoods();

    private static List<MoodMeta> defaultMoods() {
        return new ArrayList<>(Arrays.asList(
                m("happy",    "开心", "😄", "#FFD56B"),
                m("calm",     "平静", "🙂", "#9BD1C6"),
                m("excited",  "兴奋", "🤩", "#FF9F68"),
                m("grateful", "感恩", "🥰", "#F7A6C4"),
                m("tired",    "疲惫", "😪", "#A6A6C9"),
                m("anxious",  "焦虑", "😟", "#7FA6E8"),
                m("sad",      "难过", "😢", "#6D8FB8"),
                m("angry",    "愤怒", "😠", "#E8736B"),
                m("lonely",   "孤独", "🌧️", "#8E94B8"),
                m("numb",     "麻木", "😶", "#B0B0B0")
        ));
    }

    private static MoodMeta m(String key, String label, String emoji, String color) {
        MoodMeta x = new MoodMeta();
        x.key = key; x.label = label; x.emoji = emoji; x.color = color;
        return x;
    }

    public static List<MoodMeta> all() { return MOODS; }

    public static MoodMeta of(String key) {
        for (MoodMeta m : MOODS) if (m.key.equals(key)) return m;
        return m(key, key, "·", "#B0B0B0");
    }

    /** 用服务端目录里的心情定义覆盖本地默认值（格式：[{key,label,emoji,color}, ...]） */
    public static void overrideFromCatalogJson(String json) {
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            List<MoodMeta> list = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                MoodMeta x = new MoodMeta();
                x.key = o.get("key").getAsString();
                x.label = o.get("label").getAsString();
                x.emoji = o.has("emoji") ? o.get("emoji").getAsString() : "";
                x.color = o.has("color") ? o.get("color").getAsString() : "#B0B0B0";
                list.add(x);
            }
            if (!list.isEmpty()) MOODS = list;
        } catch (Exception ignored) { }
    }
}

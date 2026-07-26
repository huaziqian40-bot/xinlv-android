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
    public int valence;   // 1 正面 / 0 中性 / -1 负面（与服务端 MOODS 一致；决定推荐给小知识还是小练习）

    private static List<MoodMeta> MOODS = defaultMoods();

    private static List<MoodMeta> defaultMoods() {
        return new ArrayList<>(Arrays.asList(
                m("happy",    "开心", "😄", "#FFD56B",  1),
                m("calm",     "平静", "🙂", "#9BD1C6",  1),
                m("excited",  "兴奋", "🤩", "#FF9F68",  1),
                m("grateful", "感恩", "🥰", "#F7A6C4",  1),
                m("tired",    "疲惫", "😪", "#A6A6C9", -1),
                m("anxious",  "焦虑", "😟", "#7FA6E8", -1),
                m("sad",      "难过", "😢", "#6D8FB8", -1),
                m("angry",    "愤怒", "😠", "#E8736B", -1),
                m("lonely",   "孤独", "🌧️", "#8E94B8", -1),
                m("numb",     "麻木", "😶", "#B0B0B0",  0)
        ));
    }

    private static MoodMeta m(String key, String label, String emoji, String color, int valence) {
        MoodMeta x = new MoodMeta();
        x.key = key; x.label = label; x.emoji = emoji; x.color = color; x.valence = valence;
        return x;
    }

    /** 是否正面心情（正面才推心理小知识，负面/中性推即时小练习，规则同服务端 recommendations.build） */
    public static boolean isPositive(String key) { return of(key).valence > 0; }

    public static List<MoodMeta> all() { return MOODS; }

    public static MoodMeta of(String key) {
        for (MoodMeta m : MOODS) if (m.key.equals(key)) return m;
        return m(key, key, "·", "#B0B0B0", 0);
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
                // 目录没带 valence 时回退本地默认表里的值，避免正面心情判断失效
                x.valence = o.has("valence") ? o.get("valence").getAsInt() : defaultValence(x.key);
                list.add(x);
            }
            if (!list.isEmpty()) MOODS = list;
        } catch (Exception ignored) { }
    }

    private static int defaultValence(String key) {
        for (MoodMeta m : defaultMoods()) if (m.key.equals(key)) return m.valence;
        return 0;
    }
}

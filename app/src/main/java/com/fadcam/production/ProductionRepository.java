package com.fadcam.production;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Offline-first production workspace store. Keeps production planning independent
 * from the camera/recording database so production data cannot block FadCam startup. */
public final class ProductionRepository {
    private static final String PREFS = "fadcam_production_room_v1";
    private static final String PRODUCTIONS = "productions";
    private final SharedPreferences prefs;

    public ProductionRepository(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<Production> listProductions() {
        List<Production> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(PRODUCTIONS, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Production.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    public synchronized Production createProduction(String name, String type, String director) {
        Production p = new Production(UUID.randomUUID().toString(), name, type, director);
        List<Production> all = listProductions();
        all.add(0, p);
        save(all);
        return p;
    }

    public synchronized void update(Production changed) {
        List<Production> all = listProductions();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(changed.id)) { all.set(i, changed); break; }
        }
        save(all);
    }

    public synchronized void delete(String id) {
        List<Production> all = listProductions();
        all.removeIf(p -> p.id.equals(id));
        save(all);
    }

    private void save(List<Production> all) {
        JSONArray a = new JSONArray();
        for (Production p : all) a.put(p.toJson());
        prefs.edit().putString(PRODUCTIONS, a.toString()).apply();
    }

    public static final class Production {
        public final String id;
        public String name;
        public String type;
        public String director;
        public String status = "Planning";
        public long createdAt = System.currentTimeMillis();
        public int episodes;
        public int scenes;
        public int tasks;
        public int castCrew;
        public int locations;
        public int equipment;
        public int assets;
        public int callSheets;
        public int reports;
        public int scripts;
        public long budgetCents;
        public long expensesCents;
        public String notes = "";

        Production(String id, String name, String type, String director) {
            this.id = id; this.name = name; this.type = type; this.director = director;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id).put("name", name).put("type", type).put("director", director)
                 .put("status", status).put("createdAt", createdAt).put("episodes", episodes)
                 .put("scenes", scenes).put("tasks", tasks).put("castCrew", castCrew)
                 .put("locations", locations).put("equipment", equipment).put("assets", assets)
                 .put("callSheets", callSheets).put("reports", reports).put("scripts", scripts)
                 .put("budgetCents", budgetCents).put("expensesCents", expensesCents).put("notes", notes);
            } catch (Exception ignored) { }
            return o;
        }

        static Production fromJson(JSONObject o) {
            Production p = new Production(o.optString("id", UUID.randomUUID().toString()),
                    o.optString("name", "Untitled production"), o.optString("type", "Film"),
                    o.optString("director", ""));
            p.status = o.optString("status", "Planning"); p.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            p.episodes = o.optInt("episodes"); p.scenes = o.optInt("scenes"); p.tasks = o.optInt("tasks");
            p.castCrew = o.optInt("castCrew"); p.locations = o.optInt("locations"); p.equipment = o.optInt("equipment");
            p.assets = o.optInt("assets"); p.callSheets = o.optInt("callSheets"); p.reports = o.optInt("reports");
            p.scripts = o.optInt("scripts"); p.budgetCents = o.optLong("budgetCents"); p.expensesCents = o.optLong("expensesCents");
            p.notes = o.optString("notes", "");
            return p;
        }
    }
}

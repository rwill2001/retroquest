package io.cannonforge.retroquest.registry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.cannonforge.retroquest.model.Quest;

public class QuestRegistry {

    private static final List<Quest> quests = new ArrayList<>();
    private static final String QUESTS_FILE;

    /**
     * Set when quests.json exists but could not be read. Defaults are still populated so the
     * game runs, but saving is refused so the real (merely unreadable) file survives.
     */
    private static boolean loadFailed = false;

    static {
        QUESTS_FILE = System.getProperty("user.dir") + "/data/quests.json";

        loadQuests();
        if (quests.isEmpty()) {
            createDefaultQuests();
            if (!loadFailed) saveQuests();
        }
    }

    private static void loadQuests() {
        File file = new File(QUESTS_FILE);
        if (!file.exists()) return; // genuinely absent — safe to seed and write

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<Quest>>(){}.getType();
            List<Quest> loaded = new Gson().fromJson(reader, listType);
            if (loaded == null) {
                loadFailed = true;
                System.err.println("[QuestRegistry] *** quests.json is present but contains no quest list."
                        + " Running on built-in defaults; saving is DISABLED so the file is not overwritten.");
                return;
            }
            quests.addAll(loaded);
        } catch (Exception e) {
            loadFailed = true;
            quests.clear(); // discard a partially-read list
            System.err.println("[QuestRegistry] *** FAILED TO PARSE quests.json: " + e
                    + " — running on built-in defaults; saving is DISABLED so your quest file is not overwritten.");
            e.printStackTrace();
        }
    }

    /** Exclusion strategy that strips vestigial progress/status from quest templates. */
    private static final ExclusionStrategy TEMPLATE_EXCLUSION = new ExclusionStrategy() {
        @Override public boolean shouldSkipField(FieldAttributes f) {
            return "progress".equals(f.getName()) || "status".equals(f.getName());
        }
        @Override public boolean shouldSkipClass(Class<?> c) { return false; }
    };

    /**
     * Persists the registry to {@code data/quests.json}.
     *
     * <p>Written to a sibling {@code .tmp} file and then moved into place, so a failure
     * mid-write cannot truncate the existing file. Refused outright if the file failed to
     * parse at startup.
     *
     * @return {@code true} if the file was written, {@code false} if the save was refused or failed
     */
    public static boolean saveQuests() {
        if (loadFailed) {
            System.err.println("[QuestRegistry] REFUSING to save quests.json — it failed to load at"
                    + " startup and saving now would replace it with defaults. Repair or remove the file.");
            return false;
        }

        File target = new File(QUESTS_FILE);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        File tmp = new File(QUESTS_FILE + ".tmp");

        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting()
                        .addSerializationExclusionStrategy(TEMPLATE_EXCLUSION)
                        .create().toJson(quests, writer);
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            System.err.println("[QuestRegistry] FAILED to save quests.json: " + e);
            e.printStackTrace();
            tmp.delete();
            return false;
        }
    }

    private static void createDefaultQuests() {
        quests.add(new Quest("wolf_menace", "The Wolf Menace",
                "Wolves have been attacking the farms south of Moonhaven. Kill {amount} wolves.",
                Quest.Type.KILL, "Wolf", 5, 150, 400, null));
        quests.get(0).setRepeatable(true);
        quests.get(0).setMinAmount(4);
        quests.get(0).setMaxAmount(12);

        quests.add(new Quest("potion_delivery", "Potion Delivery",
                "Eldrin the Merchant asked you to deliver 3 Healing Potions to Mira the Innkeeper.",
                Quest.Type.DELIVER, "Mira the Innkeeper", 3, 100, 250, "healing_potion"));
    }

    public static List<Quest> getAllQuests() {
        return new ArrayList<>(quests); // defensive copy for safety
    }

    public static Quest getById(String id) {
        return quests.stream().filter(q -> q.getId().equals(id)).findFirst().orElse(null);
    }

    // NEW: Proper update method that modifies the real list
    public static void updateQuest(Quest updatedQuest) {
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).getId().equals(updatedQuest.getId())) {
                quests.set(i, updatedQuest);
                saveQuests();
                return;
            }
        }
        // If not found, add as new
        quests.add(updatedQuest);
        saveQuests();
    }

    public static void addQuest(Quest q) {
        quests.add(q);
        saveQuests();
    }

    public static void removeQuest(String id) {
        quests.removeIf(q -> q.getId().equals(id));
        saveQuests();
    }

}

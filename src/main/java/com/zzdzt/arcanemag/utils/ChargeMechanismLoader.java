package com.zzdzt.arcanemag.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zzdzt.arcanemag.ArcaneMag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ArcaneMag.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChargeMechanismLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "spell_charge_mechanisms";

    private static Map<String, AttachmentDataUtils.MechanismEntry> mechanisms = new HashMap<>();

    public ChargeMechanismLoader() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ChargeMechanismLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, com.google.gson.JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, AttachmentDataUtils.MechanismEntry> newMechanisms = new HashMap<>();

        for (var entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            String key = id.toString();
            if (key.startsWith("_")) continue;

            try {
                AttachmentDataUtils.MechanismEntry mechanism = GSON.fromJson(
                    entry.getValue(),
                    new TypeToken<AttachmentDataUtils.MechanismEntry>() {}.getType()
                );
                newMechanisms.put(key, mechanism);
            } catch (Exception e) {
                ArcaneMag.LOGGER.warn("[ArcaneMag] Failed to parse spell_charge_mechanisms entry {}: {}", key, e.getMessage());
            }
        }

        mechanisms = newMechanisms;
        ArcaneMag.LOGGER.info("[ArcaneMag] Loaded {} spell charge mechanism entries from data packs", mechanisms.size());

        loadFromConfigDir();
    }

    private static void loadFromConfigDir() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(ArcaneMag.MODID).resolve(DIRECTORY);
        if (!Files.exists(configDir)) {
            return;
        }

        int loaded = 0;
        try {
            File[] namespaceDirs = configDir.toFile().listFiles(File::isDirectory);
            if (namespaceDirs == null) return;

            for (File namespaceDir : namespaceDirs) {
                String namespace = namespaceDir.getName();
                if (namespace.equals("example")) continue;
                File[] jsonFiles = namespaceDir.listFiles((dir, name) -> name.endsWith(".json") && !name.startsWith("_"));
                if (jsonFiles == null) continue;

                for (File jsonFile : jsonFiles) {
                    String path = jsonFile.getName().replace(".json", "");
                    String spellKey = namespace + ":" + path;

                    try (FileReader reader = new FileReader(jsonFile)) {
                        AttachmentDataUtils.MechanismEntry mechanism = GSON.fromJson(
                            reader,
                            new TypeToken<AttachmentDataUtils.MechanismEntry>() {}.getType()
                        );
                        mechanisms.put(spellKey, mechanism);
                        loaded++;
                    } catch (IOException e) {
                        ArcaneMag.LOGGER.warn("[ArcaneMag] Failed to read config file {}: {}", spellKey, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            ArcaneMag.LOGGER.warn("[ArcaneMag] Failed to load spell charge mechanisms from config dir: {}", e.getMessage());
        }

        if (loaded > 0) {
            ArcaneMag.LOGGER.info("[ArcaneMag] Loaded {} spell charge mechanism entries from config dir", loaded);
        }
    }

    public static AttachmentDataUtils.MechanismEntry getMechanism(String spellKey) {
        return mechanisms.get(spellKey);
    }

    public static Map<String, AttachmentDataUtils.MechanismEntry> getAllMechanisms() {
        return Map.copyOf(mechanisms);
    }

    public static void reset() {
        mechanisms.clear();
    }
}

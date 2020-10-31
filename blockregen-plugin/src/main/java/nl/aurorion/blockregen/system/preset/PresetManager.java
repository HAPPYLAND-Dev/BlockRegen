package nl.aurorion.blockregen.system.preset;

import com.cryptomorin.xseries.XBlock;
import com.cryptomorin.xseries.XMaterial;
import com.google.common.base.Strings;
import nl.aurorion.blockregen.BlockRegen;
import nl.aurorion.blockregen.ConsoleOutput;
import nl.aurorion.blockregen.ParseUtil;
import nl.aurorion.blockregen.Utils;
import nl.aurorion.blockregen.system.preset.struct.Amount;
import nl.aurorion.blockregen.system.preset.struct.BlockPreset;
import nl.aurorion.blockregen.system.preset.struct.PresetConditions;
import nl.aurorion.blockregen.system.preset.struct.PresetRewards;
import nl.aurorion.blockregen.system.preset.struct.drop.ExperienceDrop;
import nl.aurorion.blockregen.system.preset.struct.drop.ItemDrop;
import nl.aurorion.blockregen.system.preset.struct.event.EventBossBar;
import nl.aurorion.blockregen.system.preset.struct.event.PresetEvent;
import nl.aurorion.blockregen.system.preset.struct.material.DynamicMaterial;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PresetManager {

    private final BlockRegen plugin;

    private final Map<String, BlockPreset> presets = new HashMap<>();

    public PresetManager(BlockRegen plugin) {
        this.plugin = plugin;
    }

    public Optional<BlockPreset> getPreset(String name) {
        return Optional.ofNullable(presets.getOrDefault(name, null));
    }

    public Optional<BlockPreset> getPresetByBlock(Block block) {
        return presets.values().stream()
                .filter(p -> XBlock.isSimilar(block, XMaterial.matchXMaterial(p.getMaterial())))
                .findAny();
    }

    public Map<String, BlockPreset> getPresets() {
        return Collections.unmodifiableMap(presets);
    }

    public void loadAll() {
        presets.clear();

        ConfigurationSection blocks = plugin.getFiles().getBlockList().getFileConfiguration().getConfigurationSection("Blocks");

        if (blocks == null) return;

        for (String key : blocks.getKeys(false)) {
            load(key);
        }

        plugin.getConsoleOutput().info("Loaded " + presets.size() + " block preset(s)...");

        cacheEvents();
    }

    public void load(String name) {
        FileConfiguration file = plugin.getFiles().getBlockList().getFileConfiguration();

        ConfigurationSection section = file.getConfigurationSection("Blocks." + name);

        if (section == null) return;

        BlockPreset preset = new BlockPreset(name);

        // Target material
        String targetMaterial = section.getString("target-material");

        if (Strings.isNullOrEmpty(targetMaterial))
            targetMaterial = name;

        Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(targetMaterial.toUpperCase());

        if (!xMaterial.isPresent()) {
            ConsoleOutput.getInstance().warn("Could not load preset " + name + ", invalid target material.");
            return;
        }

        plugin.getConsoleOutput().debug("Target material: " + xMaterial.get().name());

        preset.setMaterial(xMaterial.get().parseMaterial());

        // Replace material
        String replaceMaterial = section.getString("replace-block");

        if (Strings.isNullOrEmpty(replaceMaterial))
            return;

        try {
            preset.setReplaceMaterial(new DynamicMaterial(replaceMaterial));
        } catch (IllegalArgumentException e) {
            if (plugin.getConsoleOutput().isDebug())
                e.printStackTrace();
            plugin.getConsoleOutput().err("Dynamic material ( " + replaceMaterial + " ) in replace-block material for " + name + " is invalid: " + e.getMessage());
            return;
        }

        // Regenerate into
        String regenerateInto = section.getString("regenerate-into");

        if (Strings.isNullOrEmpty(regenerateInto))
            regenerateInto = targetMaterial;

        try {
            preset.setRegenMaterial(new DynamicMaterial(regenerateInto));
        } catch (IllegalArgumentException e) {
            if (plugin.getConsoleOutput().isDebug())
                e.printStackTrace();
            plugin.getConsoleOutput().err("Dynamic material ( " + regenerateInto + " ) in regenerate-into material for " + name + " is invalid: " + e.getMessage());
            return;
        }

        // Delay
        preset.setDelay(Amount.loadAmount(file, "Blocks." + name + ".regen-delay", 3));

        // Natural break
        preset.setNaturalBreak(section.getBoolean("natural-break", true));

        // Apply fortune
        preset.setApplyFortune(section.getBoolean("apply-fortune", true));

        // Drop naturally
        preset.setDropNaturally(section.getBoolean("drop-naturally", true));

        // Particle
        String particleName = section.getString("particles");

        if (!Strings.isNullOrEmpty(particleName))
            preset.setParticle(particleName);

        String regenParticle = section.getString("regeneration-particles");

        if (!Strings.isNullOrEmpty(regenParticle))
            preset.setRegenerationParticle(regenParticle);

        // Conditions
        PresetConditions conditions = new PresetConditions();

        // Tools
        String toolsRequired = section.getString("tool-required");
        if (!Strings.isNullOrEmpty(toolsRequired)) {
            conditions.setToolsRequired(toolsRequired);
        }

        // Enchants
        String enchantsRequired = section.getString("enchant-required");
        if (!Strings.isNullOrEmpty(enchantsRequired)) {
            conditions.setEnchantsRequired(enchantsRequired);
        }

        // Jobs
        if (plugin.getJobsProvider() != null) {
            String jobsRequired = section.getString("jobs-check");
            if (!Strings.isNullOrEmpty(jobsRequired)) {
                conditions.setJobsRequired(jobsRequired);
            }
        }

        preset.setConditions(conditions);
        plugin.getConsoleOutput().debug("Conditions loaded");

        // Rewards
        PresetRewards rewards = new PresetRewards();

        // Money
        rewards.setMoney(Amount.loadAmount(file, "Blocks." + name + ".money", 0));

        // Console commands
        rewards.setConsoleCommands(section.getStringList("console-commands"));

        // Player commands
        rewards.setPlayerCommands(section.getStringList("player-commands"));

        // Items Drops
        if (section.contains("drop-item")) {
            List<ItemDrop> drops = new ArrayList<>();

            // Single drop
            if (section.contains("drop-item.material")) {
                Material material = ParseUtil.parseMaterial(section.getString("drop-item.material"));

                if (material != null) {
                    ItemDrop drop = loadItemDrop("Blocks." + name + ".drop-item");

                    if (drop != null) drops.add(drop);
                } else
                    ConsoleOutput.getInstance().warn("Could not load material for " + name);
            } else {
                // Multiple drops
                for (String dropName : section.getConfigurationSection("drop-item").getKeys(false)) {
                    // TODO No need to implement more drops yet.
                }
            }

            rewards.setDrops(drops);
            plugin.getConsoleOutput().debug("Added " + rewards.getDrops().size() + " drop(s)");
        }

        preset.setRewards(rewards);

        ConfigurationSection eventSection = section.getConfigurationSection("event");

        if (eventSection != null) {
            PresetEvent event = new PresetEvent();

            String eventName = eventSection.getString("event-name");

            if (eventName != null) {
                event.setName(eventName);

                event.setDoubleDrops(eventSection.getBoolean("double-drops", false));
                event.setDoubleExperience(eventSection.getBoolean("double-exp", false));

                if (eventSection.contains("bossbar")) {
                    EventBossBar bossBar = new EventBossBar();

                    if (!eventSection.contains("bossbar.name"))
                        bossBar.setText("&fEvent " + eventName + " &fis active!");
                    else
                        bossBar.setText(eventSection.getString("bossbar.name"));

                    if (eventSection.contains("bossbar.color")) {
                        String barColor = eventSection.getString("bossbar.color");
                        if (barColor != null) {
                            BarColor color;
                            try {
                                color = BarColor.valueOf(barColor.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                color = BarColor.BLUE;
                                plugin.getConsoleOutput().err("Boss bar color " + barColor + " for preset " + name + " is invalid.");
                            }
                            bossBar.setColor(color);
                        }
                    }

                    event.setBossBar(bossBar);
                }

                if (eventSection.contains("custom-item")) {
                    ItemDrop drop = loadItemDrop("Blocks." + name + ".event.custom-item");

                    if (drop != null) event.setItem(drop);
                }

                if (eventSection.contains("custom-item.rarity")) {
                    Amount rarity = Amount.loadAmount(file, "Blocks." + name + ".event.custom-item.rarity", 1);
                    event.setItemRarity(rarity);
                } else event.setItemRarity(new Amount(1));

                plugin.getConsoleOutput().debug("Loaded event " + eventName);
                preset.setEvent(event);
            } else
                plugin.getConsoleOutput().err("Event name for block " + name + " has not been set, but the section is present.");
        }

        presets.put(name, preset);
    }

    private ItemDrop loadItemDrop(String path) {

        if (Strings.isNullOrEmpty(path))
            return null;

        ConfigurationSection section = plugin.getFiles().getBlockList().getFileConfiguration().getConfigurationSection(path);

        if (section == null)
            return null;

        Material material = ParseUtil.parseMaterial(section.getString("material"));

        if (material == null)
            return null;

        ItemDrop drop = new ItemDrop(material);

        drop.setAmount(Amount.loadAmount(plugin.getFiles().getBlockList().getFileConfiguration(), path + ".amount", 1));

        drop.setDisplayName(section.getString("name"));

        drop.setLore(section.getStringList("lores"));

        ExperienceDrop experienceDrop = new ExperienceDrop();

        experienceDrop.setAmount(Amount.loadAmount(plugin.getFiles().getBlockList().getFileConfiguration(), path + ".exp.amount", 0));

        experienceDrop.setDropNaturally(plugin.getFiles().getBlockList().getFileConfiguration().getBoolean(path + ".exp.drop-naturally", false));

        drop.setExperienceDrop(experienceDrop);
        return drop;
    }

    private void cacheEvents() {

        for (BlockPreset preset : getPresets().values()) {
            if (preset.getEvent() != null)
                Utils.events.put(preset.getEvent().getName(), false);
        }

        plugin.getConsoleOutput().info(Utils.events.isEmpty() ?
                "&cFound no events. Skip adding to the system." :
                "&aFound " + Utils.events.keySet().size() + " event(s)... added all to the system.");
    }
}
package com.bubblecraft;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

public class BubbleCommand extends JavaPlugin implements CommandExecutor, TabCompleter {

    private FileConfiguration config;
    private final Map<String, CustomCommand> customCommands = new HashMap<>();
    private final Set<String> registeredCommands = new HashSet<>();

    // Cooldown tracking: commandName -> (playerUUID -> expiry timestamp ms)
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();

    // Persistence file for cooldowns
    private File cooldownFile;
    private YamlConfiguration cooldownData;

    private boolean debugMode;
    private boolean papiEnabled;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        debugMode = config.getBoolean("debug", false);

        // Register main command
        var bubbleCmd = getCommand("bubblecommand");
        if (bubbleCmd == null) {
            getLogger().severe("Command 'bubblecommand' is missing from plugin.yml; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        bubbleCmd.setExecutor(this);
        bubbleCmd.setTabCompleter(this);

        papiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (papiEnabled) {
            debug("PlaceholderAPI found — placeholder expansion enabled.");
        }

        loadCooldownData();
        loadCustomCommands();

        getLogger().info("BubbleCommand v" + getDescription().getVersion() + " enabled! Loaded " + customCommands.size() + " custom commands.");
    }

    @Override
    public void onDisable() {
        saveCooldownDataSync();
        unregisterCustomCommands();
        getLogger().info("BubbleCommand has been disabled!");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Command loading
    // ─────────────────────────────────────────────────────────────────────────

    private void loadCustomCommands() {
        customCommands.clear();

        if (!config.isConfigurationSection("custom-commands")) {
            getLogger().warning("No custom-commands section found in config.yml");
            return;
        }

        ConfigurationSection commandsSection = config.getConfigurationSection("custom-commands");

        for (String commandName : commandsSection.getKeys(false)) {
            try {
                ConfigurationSection cmdSection = commandsSection.getConfigurationSection(commandName);
                if (cmdSection == null) continue;

                List<String> aliases   = cmdSection.getStringList("aliases");
                String permission      = cmdSection.getString("permission");
                String description     = cmdSection.getString("description", "Custom command");
                String noPermMsg       = cmdSection.getString("no-permission-message", "&cYou don't have permission to use this command!");
                String cooldownMsg     = cmdSection.getString("cooldown-message", "&cYou must wait &e%remaining%s &cbefore using this command again.");
                boolean playerOnly     = cmdSection.getBoolean("player-only", true);
                int cooldownSeconds    = cmdSection.getInt("cooldown", 0);
                String sound           = cmdSection.getString("sound", null);
                float soundVolume      = (float) cmdSection.getDouble("sound-volume", 1.0);
                float soundPitch       = (float) cmdSection.getDouble("sound-pitch", 1.0);

                // Support both single "action" and list "actions"
                List<String> actions;
                if (cmdSection.isList("actions")) {
                    actions = cmdSection.getStringList("actions");
                } else {
                    String single = cmdSection.getString("action");
                    if (single == null || single.isEmpty()) {
                        getLogger().warning("Command '" + commandName + "' has no action/actions defined, skipping.");
                        continue;
                    }
                    actions = List.of(single);
                }

                CustomCommand customCmd = new CustomCommand(
                        commandName, aliases, actions, permission, noPermMsg,
                        cooldownMsg, description, playerOnly, cooldownSeconds,
                        sound, soundVolume, soundPitch
                );

                customCommands.put(commandName.toLowerCase(), customCmd);
                for (String alias : aliases) {
                    customCommands.put(alias.toLowerCase(), customCmd);
                }

                registerCommand(commandName, aliases, description);
                debug("Loaded command: /" + commandName + " | actions: " + actions.size() + " | cooldown: " + cooldownSeconds + "s");

            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error loading command '" + commandName + "'", e);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Command registration / unregistration
    // ─────────────────────────────────────────────────────────────────────────

    private void registerCommand(String name, List<String> aliases, String description) {
        try {
            DynamicPluginCommand command = new DynamicPluginCommand(name, this);
            command.setDescription(description);
            command.setAliases(aliases);

            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            Object commandMap = commandMapField.get(Bukkit.getServer());

            commandMap.getClass().getMethod("register", String.class, Command.class)
                    .invoke(commandMap, getName().toLowerCase(), command);

            registeredCommands.add(name.toLowerCase());
            for (String alias : aliases) {
                registeredCommands.add(alias.toLowerCase());
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to register command: " + name, e);
        }
    }

    private void unregisterCustomCommands() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            Object commandMap = commandMapField.get(Bukkit.getServer());

            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            if (knownCommands != null) {
                for (String cmdName : registeredCommands) {
                    knownCommands.remove(cmdName);
                    knownCommands.remove(getName().toLowerCase() + ":" + cmdName);
                }
            }
            registeredCommands.clear();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to unregister custom commands", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(Object commandMap) {
        for (String fieldName : List.of("knownCommands", "commands")) {
            try {
                Field f = commandMap.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                return (Map<String, Command>) f.get(commandMap);
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Could not read command map field: " + fieldName, e);
            }
        }
        getLogger().info("Could not access command map fields — commands may persist until server restart.");
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // onCommand
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bubblecommand")) {
            return handleMainPluginCommand(sender, args);
        }

        CustomCommand customCmd = resolveCustomCommand(command);
        if (customCmd == null) {
            sender.sendMessage(ChatColor.RED + "Unknown command!");
            return true;
        }

        if (!validateSender(sender, customCmd)) return true;
        if (!checkCooldown(sender, customCmd)) return true;

        applyActions(sender, customCmd, args);
        playCommandSound(sender, customCmd);
        setCooldown(sender, customCmd);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main plugin command (/bubblecommand)
    // ─────────────────────────────────────────────────────────────────────────

    private boolean handleMainPluginCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GREEN + "BubbleCommand v" + getDescription().getVersion());
            sender.sendMessage(ChatColor.YELLOW + "Use /bubblecommand reload to reload the config.");
            sender.sendMessage(ChatColor.YELLOW + "Custom commands loaded: " + customCommands.size());
            sender.sendMessage(ChatColor.YELLOW + "Debug mode: " + (debugMode ? ChatColor.GREEN + "on" : ChatColor.RED + "off"));
            return true;
        }

        if (!sender.hasPermission("bubblecommand.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                unregisterCustomCommands();
                reloadConfig();
                config = getConfig();
                debugMode = config.getBoolean("debug", false);
                loadCustomCommands();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded! Loaded " + customCommands.size() + " custom commands.");
            }
            case "debug" -> {
                debugMode = !debugMode;
                sender.sendMessage(ChatColor.YELLOW + "Debug mode " + (debugMode ? ChatColor.GREEN + "enabled." : ChatColor.RED + "disabled."));
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand. Usage: /bubblecommand [reload|debug]");
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Action execution
    // ─────────────────────────────────────────────────────────────────────────

    private void applyActions(CommandSender sender, CustomCommand customCmd, String[] args) {
        for (String rawAction : customCmd.getActions()) {
            String action = replacePlaceholders(rawAction, sender, args);
            executeAction(sender, action);
        }
    }

    private void executeAction(CommandSender sender, String action) {
        debug("Executing action: " + action + " for " + sender.getName());

        if (action.startsWith("console:")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.substring(8).trim());
            return;
        }

        if (action.startsWith("player:")) {
            if (sender instanceof Player p) {
                p.performCommand(action.substring(7).trim());
            } else {
                sender.sendMessage(ChatColor.RED + "This action can only be executed by players!");
            }
            return;
        }

        if (action.startsWith("message:")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', action.substring(8).trim()));
            return;
        }

        if (action.startsWith("broadcast:")) {
            String msg = ChatColor.translateAlternateColorCodes('&', action.substring(10).trim());
            Bukkit.broadcastMessage(msg);
            return;
        }

        if (action.startsWith("title:")) {
            // Format: title:<title>|<subtitle>|<fadeIn>|<stay>|<fadeOut>
            // All parts after "title:" are optional except the first
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Title actions can only target players!");
                return;
            }
            String[] parts = action.substring(6).split("\\|", 5);
            String title    = ChatColor.translateAlternateColorCodes('&', parts[0].trim());
            String subtitle = parts.length > 1 ? ChatColor.translateAlternateColorCodes('&', parts[1].trim()) : "";
            int fadeIn  = parts.length > 2 ? parseIntSafe(parts[2].trim(), 10) : 10;
            int stay    = parts.length > 3 ? parseIntSafe(parts[3].trim(), 70) : 70;
            int fadeOut = parts.length > 4 ? parseIntSafe(parts[4].trim(), 20) : 20;
            p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            return;
        }

        if (action.startsWith("actionbar:")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Actionbar actions can only target players!");
                return;
            }
            String msg = ChatColor.translateAlternateColorCodes('&', action.substring(10).trim());
            p.sendActionBar(msg);
            return;
        }

        if (action.startsWith("sound:")) {
            // Inline sound action: sound:<SOUND_NAME>:<volume>:<pitch>
            if (!(sender instanceof Player p)) return;
            String[] parts = action.substring(6).split(":", 3);
            try {
                Sound s = Sound.valueOf(parts[0].trim().toUpperCase());
                float vol   = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1.0f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1.0f;
                p.playSound(p.getLocation(), s, vol, pitch);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid sound in action: " + action);
            }
            return;
        }

        // Default: run as player command
        if (sender instanceof Player p) {
            p.performCommand(action);
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cooldowns
    // ─────────────────────────────────────────────────────────────────────────

    private boolean checkCooldown(CommandSender sender, CustomCommand cmd) {
        if (!(sender instanceof Player p)) return true;
        if (cmd.getCooldownSeconds() <= 0) return true;
        if (p.hasPermission("bubblecommand.bypasscooldown")) return true;

        Map<UUID, Long> cmdCooldowns = cooldowns.getOrDefault(cmd.getName(), Collections.emptyMap());
        long expiry = cmdCooldowns.getOrDefault(p.getUniqueId(), 0L);
        long now = System.currentTimeMillis();

        if (now < expiry) {
            long remaining = (expiry - now + 999) / 1000;
            String msg = cmd.getCooldownMessage().replace("%remaining%", String.valueOf(remaining));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }
        return true;
    }

    private void setCooldown(CommandSender sender, CustomCommand cmd) {
        if (!(sender instanceof Player p)) return;
        if (cmd.getCooldownSeconds() <= 0) return;

        cooldowns.computeIfAbsent(cmd.getName(), k -> new HashMap<>())
                .put(p.getUniqueId(), System.currentTimeMillis() + cmd.getCooldownSeconds() * 1000L);

        saveCooldownDataAsync();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cooldown persistence
    // ─────────────────────────────────────────────────────────────────────────

    private void loadCooldownData() {
        cooldownFile = new File(getDataFolder(), "cooldowns.yml");
        if (!cooldownFile.exists()) {
            cooldowns.clear();
            return;
        }
        cooldownData = YamlConfiguration.loadConfiguration(cooldownFile);
        long now = System.currentTimeMillis();

        for (String cmdName : cooldownData.getKeys(false)) {
            ConfigurationSection section = cooldownData.getConfigurationSection(cmdName);
            if (section == null) continue;
            Map<UUID, Long> map = new HashMap<>();
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long expiry = section.getLong(uuidStr);
                    if (expiry > now) {
                        map.put(uuid, expiry);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
            if (!map.isEmpty()) {
                cooldowns.put(cmdName, map);
            }
        }
        debug("Loaded cooldown data for " + cooldowns.size() + " commands.");
    }

    private void saveCooldownDataAsync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveCooldownDataSync();
            }
        }.runTaskAsynchronously(this);
    }

    private synchronized void saveCooldownDataSync() {
        if (cooldownFile == null) return;
        YamlConfiguration data = new YamlConfiguration();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Map<UUID, Long>> cmdEntry : cooldowns.entrySet()) {
            for (Map.Entry<UUID, Long> playerEntry : cmdEntry.getValue().entrySet()) {
                if (playerEntry.getValue() > now) {
                    data.set(cmdEntry.getKey() + "." + playerEntry.getKey().toString(), playerEntry.getValue());
                }
            }
        }

        try {
            data.save(cooldownFile);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to save cooldown data", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private CustomCommand resolveCustomCommand(Command command) {
        String cmdName = command.getName().toLowerCase();
        CustomCommand customCmd = customCommands.get(cmdName);
        if (customCmd != null) return customCmd;

        for (CustomCommand cmd : customCommands.values()) {
            if (cmd.getName().equalsIgnoreCase(cmdName) || cmd.getAliases().contains(cmdName)) {
                return cmd;
            }
        }
        return null;
    }

    private boolean validateSender(CommandSender sender, CustomCommand customCmd) {
        if (customCmd.isPlayerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return false;
        }

        String permission = customCmd.getPermission();
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', customCmd.getNoPermissionMessage()));
            return false;
        }
        return true;
    }

    private void playCommandSound(CommandSender sender, CustomCommand customCmd) {
        if (!(sender instanceof Player p)) return;
        String soundName = customCmd.getSound();
        if (soundName == null || soundName.isEmpty()) return;

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            p.playSound(p.getLocation(), sound, customCmd.getSoundVolume(), customCmd.getSoundPitch());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sound '" + soundName + "' for command '" + customCmd.getName() + "'");
        }
    }

    private String replacePlaceholders(String text, CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            text = text.replace("%player%", player.getName());
            text = text.replace("%uuid%", player.getUniqueId().toString());
            text = text.replace("%world%", player.getWorld().getName());
            text = text.replace("%displayname%", player.getDisplayName());
            text = text.replace("%health%", String.valueOf((int) player.getHealth()));
            text = text.replace("%x%", String.valueOf(player.getLocation().getBlockX()));
            text = text.replace("%y%", String.valueOf(player.getLocation().getBlockY()));
            text = text.replace("%z%", String.valueOf(player.getLocation().getBlockZ()));

            if (papiEnabled) {
                text = PlaceholderAPI.setPlaceholders(player, text);
            }
        }

        text = text.replace("%sender%", sender.getName());

        for (int i = 0; i < args.length; i++) {
            text = text.replace("%arg" + i + "%", args[i]);
            text = text.replace("%arg" + (i + 1) + "%", args[i]);
        }
        text = text.replace("%args%", String.join(" ", args));

        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private void debug(String message) {
        if (debugMode) getLogger().info("[DEBUG] " + message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab completion
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("bubblecommand")) {
            if (args.length == 1 && sender.hasPermission("bubblecommand.admin")) {
                return List.of("reload", "debug").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .toList();
            }
        }
        return List.of();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner classes
    // ─────────────────────────────────────────────────────────────────────────

    private static class CustomCommand {
        private final String name;
        private final List<String> aliases;
        private final List<String> actions;
        private final String permission;
        private final String noPermissionMessage;
        private final String cooldownMessage;
        private final String description;
        private final boolean playerOnly;
        private final int cooldownSeconds;
        private final String sound;
        private final float soundVolume;
        private final float soundPitch;

        public CustomCommand(String name, List<String> aliases, List<String> actions,
                             String permission, String noPermissionMessage, String cooldownMessage,
                             String description, boolean playerOnly, int cooldownSeconds,
                             String sound, float soundVolume, float soundPitch) {
            this.name = name;
            this.aliases = aliases != null ? aliases : new ArrayList<>();
            this.actions = actions;
            this.permission = permission;
            this.noPermissionMessage = noPermissionMessage;
            this.cooldownMessage = cooldownMessage;
            this.description = description;
            this.playerOnly = playerOnly;
            this.cooldownSeconds = cooldownSeconds;
            this.sound = sound;
            this.soundVolume = soundVolume;
            this.soundPitch = soundPitch;
        }

        public String getName() { return name; }
        public List<String> getAliases() { return aliases; }
        public List<String> getActions() { return actions; }
        public String getPermission() { return permission; }
        public String getNoPermissionMessage() { return noPermissionMessage; }
        public String getCooldownMessage() { return cooldownMessage; }
        public String getDescription() { return description; }
        public boolean isPlayerOnly() { return playerOnly; }
        public int getCooldownSeconds() { return cooldownSeconds; }
        public String getSound() { return sound; }
        public float getSoundVolume() { return soundVolume; }
        public float getSoundPitch() { return soundPitch; }
    }

    private static class DynamicPluginCommand extends Command {
        private final JavaPlugin plugin;

        public DynamicPluginCommand(String name, JavaPlugin plugin) {
            super(name);
            this.plugin = plugin;
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return plugin.onCommand(sender, this, commandLabel, args);
        }
    }
}

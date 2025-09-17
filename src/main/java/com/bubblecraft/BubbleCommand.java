package com.bubblecraft;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

public class BubbleCommand extends JavaPlugin implements CommandExecutor, TabCompleter {
    
    private FileConfiguration config;
    private Map<String, CustomCommand> customCommands = new HashMap<>();
    private Set<String> registeredCommands = new HashSet<>();
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        
        // Register main command
        getCommand("bubblecommand").setExecutor(this);
        getCommand("bubblecommand").setTabCompleter(this);
        
        loadCustomCommands();
        
        getLogger().info("BubbleCommand has been enabled! Loaded " + customCommands.size() + " custom commands.");
    }
    
    @Override
    public void onDisable() {
        unregisterCustomCommands();
        getLogger().info("BubbleCommand has been disabled!");
    }
    
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
                
                List<String> aliases = cmdSection.getStringList("aliases");
                String action = cmdSection.getString("action");
                String permission = cmdSection.getString("permission");
                String description = cmdSection.getString("description", "Custom command");
                boolean playerOnly = cmdSection.getBoolean("player-only", true);
                
                if (action == null || action.isEmpty()) {
                    getLogger().warning("Command '" + commandName + "' has no action defined, skipping.");
                    continue;
                }
                
                CustomCommand customCmd = new CustomCommand(commandName, aliases, action, permission, description, playerOnly);
                customCommands.put(commandName.toLowerCase(), customCmd);
                
                // Register aliases too
                for (String alias : aliases) {
                    customCommands.put(alias.toLowerCase(), customCmd);
                }
                
                // Dynamically register the command
                registerCommand(commandName, aliases, description);
                
                getLogger().info("Loaded custom command: /" + commandName + " with aliases: " + aliases);
                
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error loading command '" + commandName + "'", e);
            }
        }
    }
    
    private void registerCommand(String name, List<String> aliases, String description) {
        try {
            // Create a new command and register it
            DynamicPluginCommand command = new DynamicPluginCommand(name, this);
            command.setDescription(description);
            command.setAliases(aliases);
            
            // Get the command map and register our command
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            Object commandMap = commandMapField.get(Bukkit.getServer());
            
            // Register the command
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
            
            // Try different field names for different server versions
            Field knownCommandsField = null;
            Map<String, Command> knownCommands = null;
            
            // Try "knownCommands" first (older versions)
            try {
                knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Command> commands = (Map<String, Command>) knownCommandsField.get(commandMap);
                knownCommands = commands;
            } catch (NoSuchFieldException e1) {
                // Try "commands" field (newer versions)
                try {
                    knownCommandsField = commandMap.getClass().getDeclaredField("commands");
                    knownCommandsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<String, Command> commands = (Map<String, Command>) knownCommandsField.get(commandMap);
                    knownCommands = commands;
                } catch (NoSuchFieldException e2) {
                    // If neither field exists, just clear our tracking and return
                    getLogger().info("Could not access command map fields - commands may persist until server restart");
                    registeredCommands.clear();
                    return;
                }
            }
            
            if (knownCommands != null) {
                // Remove our custom commands
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
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle main plugin command
        if (command.getName().equals("bubblecommand")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.GREEN + "BubbleCommand v" + getDescription().getVersion());
                sender.sendMessage(ChatColor.YELLOW + "Use /bubblecommand reload to reload the config");
                sender.sendMessage(ChatColor.YELLOW + "Custom commands loaded: " + customCommands.size());
                return true;
            }
            
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("bubblecommand.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to reload the config!");
                    return true;
                }
                
                // Unregister old commands
                unregisterCustomCommands();
                
                // Reload config and register new commands
                reloadConfig();
                config = getConfig();
                loadCustomCommands();
                
                sender.sendMessage(ChatColor.GREEN + "Config reloaded! Loaded " + customCommands.size() + " custom commands.");
                return true;
            }
            
            sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use: /bubblecommand reload");
            return true;
        }
        
        // Handle custom commands
        String cmdName = command.getName().toLowerCase();
        CustomCommand customCmd = customCommands.get(cmdName);
        
        if (customCmd == null) {
            // Check if it's an alias
            for (CustomCommand cmd : customCommands.values()) {
                if (cmd.getName().equalsIgnoreCase(cmdName) || cmd.getAliases().contains(cmdName)) {
                    customCmd = cmd;
                    break;
                }
            }
        }
        
        if (customCmd == null) {
            sender.sendMessage(ChatColor.RED + "Unknown command!");
            return true;
        }
        
        // Check if player only
        if (customCmd.isPlayerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        // Check permission
        if (customCmd.getPermission() != null && !customCmd.getPermission().isEmpty()) {
            if (!sender.hasPermission(customCmd.getPermission())) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }
        }
        
        // Execute the action
        String action = customCmd.getAction();
        action = replacePlaceholders(action, sender, args);
        
        // Execute as console command or player command
        if (action.startsWith("console:")) {
            String consoleCmd = action.substring(8).trim();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd);
        } else if (action.startsWith("player:")) {
            if (sender instanceof Player) {
                String playerCmd = action.substring(7).trim();
                ((Player) sender).performCommand(playerCmd);
            } else {
                sender.sendMessage(ChatColor.RED + "This action can only be executed by players!");
            }
        } else {
            // Default to player command
            if (sender instanceof Player) {
                ((Player) sender).performCommand(action);
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action);
            }
        }
        
        return true;
    }
    
    private String replacePlaceholders(String text, CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            text = text.replace("%player%", player.getName());
            text = text.replace("%uuid%", player.getUniqueId().toString());
            text = text.replace("%world%", player.getWorld().getName());
        }
        
        text = text.replace("%sender%", sender.getName());
        
        // Replace argument placeholders
        for (int i = 0; i < args.length; i++) {
            text = text.replace("%arg" + i + "%", args[i]);
            text = text.replace("%arg" + (i + 1) + "%", args[i]); // 1-indexed as well
        }
        
        // Replace %args% with all arguments joined
        text = text.replace("%args%", String.join(" ", args));
        
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equals("bubblecommand")) {
            if (args.length == 1) {
                return Arrays.asList("reload");
            }
        }
        return new ArrayList<>();
    }
    
    // Inner class for custom commands
    private static class CustomCommand {
        private final String name;
        private final List<String> aliases;
        private final String action;
        private final String permission;
        private final String description;
        private final boolean playerOnly;
        
        public CustomCommand(String name, List<String> aliases, String action, String permission, String description, boolean playerOnly) {
            this.name = name;
            this.aliases = aliases != null ? aliases : new ArrayList<>();
            this.action = action;
            this.permission = permission;
            this.description = description;
            this.playerOnly = playerOnly;
        }
        
        // Getters
        public String getName() { return name; }
        public List<String> getAliases() { return aliases; }
        public String getAction() { return action; }
        public String getPermission() { return permission; }
        public String getDescription() { return description; }
        public boolean isPlayerOnly() { return playerOnly; }
    }
    
    // Dynamic command class
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

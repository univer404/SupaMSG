package com.supernulled.supamsg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class SupaMSG extends JavaPlugin implements Listener, TabExecutor {
    private static final TextColor CYAN = TextColor.color(0x00A4FB);
    private static final TextColor GRAY = TextColor.color(0xA2A8B4);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, Set<UUID>> blockedPlayers = new HashMap<>();
    private final Set<UUID> messagesDisabled = new HashSet<>();
    private final Map<UUID, UUID> replyTargets = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();
        for (String name : List.of("msg", "tell", "dm", "w", "r", "block", "msgtoggle")) {
            Objects.requireNonNull(getCommand(name), "Missing command in plugin.yml: " + name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SupaMSG enabled.");
    }

    @Override
    public void onDisable() {
        saveData();
        replyTargets.clear();
    }

    private void loadData() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) getLogger().warning("Could not create plugin data directory.");
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : data.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                Set<UUID> ids = data.getStringList(key + ".blocked").stream()
                        .map(this::parseUuid).filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
                blockedPlayers.put(owner, ids);
                if (data.getBoolean(key + ".messages-disabled", false)) messagesDisabled.add(owner);
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Skipping invalid player UUID in data.yml: " + key);
            }
        }
    }

    private UUID parseUuid(String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private void saveData() {
        if (data == null) return;
        data = new YamlConfiguration();
        Set<UUID> owners = new HashSet<>(blockedPlayers.keySet());
        owners.addAll(messagesDisabled);
        for (UUID owner : owners) {
            data.set(owner + ".blocked", blockedPlayers.getOrDefault(owner, Set.of()).stream().map(UUID::toString).toList());
            data.set(owner + ".messages-disabled", messagesDisabled.contains(owner));
        }
        try { data.save(dataFile); }
        catch (IOException exception) { getLogger().severe("Could not save data.yml: " + exception.getMessage()); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("supamsg.use")) {
            error(player, "You don't have permission to use private messages.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "msg", "tell", "dm", "w" -> sendPrivateMessage(player, args);
            case "r" -> reply(player, args);
            case "block" -> toggleBlock(player, args);
            case "msgtoggle" -> toggleMessages(player);
            default -> { return false; }
        }
        return true;
    }

    private void sendPrivateMessage(Player sender, String[] args) {
        if (args.length < 2 || joinMessage(args, 1).isBlank()) { error(sender, "The message cannot be empty."); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) { error(sender, "User is not online."); return; }
        if (target.getUniqueId().equals(sender.getUniqueId())) { error(sender, "You can't message yourself."); return; }
        if (messagesDisabled.contains(target.getUniqueId())) { error(sender, "This user toggled private messages off."); return; }
        if (blockedPlayers.getOrDefault(sender.getUniqueId(), Set.of()).contains(target.getUniqueId())) {
            error(sender, "You have this player blocked."); return;
        }
        if (blockedPlayers.getOrDefault(target.getUniqueId(), Set.of()).contains(sender.getUniqueId())) {
            error(sender, "You can't message this player."); return;
        }
        deliver(sender, target, joinMessage(args, 1));
    }

    private void deliver(Player sender, Player target, String message) {
        replyTargets.put(sender.getUniqueId(), target.getUniqueId());
        replyTargets.put(target.getUniqueId(), sender.getUniqueId());
        sender.sendMessage(privateLine("YOU", target.getName(), message));
        target.sendMessage(privateLine(sender.getName(), "YOU", message));
    }

    private Component privateLine(String from, String to, String message) {
        return Component.text(from, CYAN).append(Component.text(" -> ", GRAY))
                .append(Component.text(to, CYAN)).append(Component.text(": ", GRAY))
                .append(LEGACY.deserialize(message).colorIfAbsent(NamedTextColor.WHITE));
    }

    private void reply(Player sender, String[] args) {
        if (args.length == 0 || joinMessage(args, 0).isBlank()) { error(sender, "The message cannot be empty."); return; }
        UUID targetId = replyTargets.get(sender.getUniqueId());
        if (targetId == null) { error(sender, "You have no one to talk to."); return; }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            replyTargets.remove(sender.getUniqueId());
            error(sender, "User is not online."); return;
        }
        if (messagesDisabled.contains(targetId)) { error(sender, "This user toggled private messages off."); return; }
        if (blockedPlayers.getOrDefault(sender.getUniqueId(), Set.of()).contains(targetId)) {
            error(sender, "You have this player blocked."); return;
        }
        if (blockedPlayers.getOrDefault(targetId, Set.of()).contains(sender.getUniqueId())) {
            error(sender, "You can't message this player."); return;
        }
        deliver(sender, target, joinMessage(args, 0));
    }

    private void toggleBlock(Player player, String[] args) {
        if (args.length == 0) { error(player, "Please use /block <name>"); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) { error(player, "That player has never joined this server."); return; }
        UUID targetId = target.getUniqueId();
        if (targetId.equals(player.getUniqueId())) { error(player, "You can't block yourself."); return; }
        Set<UUID> blocked = blockedPlayers.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        if (blocked.add(targetId)) notifyPlayer(player, Component.text("User was ignored successfully.", TextColor.color(0xA8A8A8)));
        else {
            blocked.remove(targetId);
            notifyPlayer(player, Component.text("The user is not ignored anymore.", TextColor.color(0xA8A8A8)));
        }
        saveData();
    }

    private void toggleMessages(Player player) {
        if (messagesDisabled.add(player.getUniqueId())) error(player, "You've turned off private messages.");
        else {
            Component message = LEGACY.deserialize("&aYou've turned on private messages.");
            player.sendMessage(message);
            player.sendActionBar(message);
        }
        saveData();
    }

    private void error(Player player, String message) {
        Component component = LEGACY.deserialize("&c" + message);
        player.sendMessage(component);
        player.sendActionBar(component);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void notifyPlayer(Player player, Component message) {
        player.sendMessage(message);
        player.sendActionBar(message);
    }

    private String joinMessage(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { replyTargets.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { replyTargets.remove(event.getPlayer().getUniqueId()); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player) || !(name.equals("block") || List.of("msg", "tell", "dm", "w").contains(name)) || args.length != 1)
            return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(playerName -> playerName.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
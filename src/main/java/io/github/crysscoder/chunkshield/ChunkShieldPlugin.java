package io.github.crysscoder.chunkshield;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ChunkShieldPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey itemKey;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "shield_item");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("chunkshield")).setExecutor(this);
        Objects.requireNonNull(getCommand("chunkshield")).setTabCompleter(this);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || !event.getAction().isRightClick()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isShield(item)) {
            return;
        }

        event.setCancelled(true);
        claim(player, event.getClickedBlock().getChunk(), true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            send(event.getPlayer(), "blocked");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            send(event.getPlayer(), "blocked");
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (blocked(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            send(event.getPlayer(), "blocked");
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (blocked(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            send(event.getPlayer(), "blocked");
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> owner(block.getChunk()) != null);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> owner(block.getChunk()) != null);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        if (owner(event.getBlock().getChunk()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (owner(event.getBlock().getChunk()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFlow(BlockFromToEvent event) {
        String fromOwner = owner(event.getBlock().getChunk());
        String toOwner = owner(event.getToBlock().getChunk());
        if (toOwner != null && !Objects.equals(fromOwner, toOwner)) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "only-player");
            return true;
        }

        String action = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);

        if (action.equals("reload")) {
            if (!player.hasPermission("chunkshield.admin")) {
                send(player, "no-permission");
                return true;
            }
            reloadConfig();
            send(player, "reloaded");
            return true;
        }

        if (!player.hasPermission("chunkshield.use")) {
            send(player, "no-permission");
            return true;
        }

        if (action.equals("give")) {
            if (!player.hasPermission("chunkshield.admin")) {
                send(player, "no-permission");
                return true;
            }
            player.getInventory().addItem(shieldItem());
            send(player, "given");
            return true;
        }

        if (action.equals("claim")) {
            claim(player, player.getChunk(), true);
            return true;
        }

        if (action.equals("unclaim")) {
            unclaim(player);
            return true;
        }

        info(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give", "claim", "unclaim", "info", "reload").stream().filter(item -> item.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private void claim(Player player, Chunk chunk, boolean consume) {
        if (owner(chunk) != null) {
            send(player, "already");
            return;
        }

        if (consume && !takeShield(player)) {
            send(player, "need-item");
            return;
        }

        getConfig().set(path(chunk) + ".owner", player.getUniqueId().toString());
        getConfig().set(path(chunk) + ".name", player.getName());
        saveConfig();
        send(player, "claimed");
    }

    private void unclaim(Player player) {
        Chunk chunk = player.getChunk();
        String owner = owner(chunk);

        if (owner == null) {
            send(player, "not-claimed");
            return;
        }

        if (!owner.equals(player.getUniqueId().toString()) && !player.hasPermission("chunkshield.admin")) {
            send(player, "not-owner");
            return;
        }

        getConfig().set(path(chunk), null);
        saveConfig();
        send(player, "unclaimed");
    }

    private void info(Player player) {
        Chunk chunk = player.getChunk();
        String owner = owner(chunk);

        if (owner == null) {
            send(player, "not-claimed");
            return;
        }

        send(player, "protected", Map.of("owner", getConfig().getString(path(chunk) + ".name", owner)));
    }

    private boolean blocked(Player player, Block block) {
        String owner = owner(block.getChunk());
        return owner != null && !owner.equals(player.getUniqueId().toString()) && !player.hasPermission("chunkshield.bypass");
    }

    private String owner(Chunk chunk) {
        return getConfig().getString(path(chunk) + ".owner");
    }

    private String path(Chunk chunk) {
        World world = chunk.getWorld();
        return "claims." + world.getUID() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    private ItemStack shieldItem() {
        Material material = Material.matchMaterial(getConfig().getString("item-material", "NETHER_STAR"));
        ItemStack item = new ItemStack(material == null ? Material.NETHER_STAR : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(legacy.deserialize(getConfig().getString("item-name", "&aChunk Shield")));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isShield(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    private boolean takeShield(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isShield(item)) {
            return false;
        }
        item.setAmount(item.getAmount() - 1);
        return true;
    }

    private void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    private void send(CommandSender sender, String key, Map<String, String> values) {
        String prefix = getConfig().getString("messages.prefix", "&7[&aChunkShield&7]");
        String result = getConfig().getString("messages." + key, "").replace("%prefix%", prefix);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        sender.sendMessage(legacy.deserialize(result));
    }
}

package com.greeklongbow.plugin.listeners;

import com.greeklongbow.plugin.GreekLongbowPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ContainerListener implements Listener {

    private final GreekLongbowPlugin plugin;

    public ContainerListener(GreekLongbowPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Block placing the Greek Longbow into any external inventory
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        boolean topIsExternal = isExternalInventory(top.getType());

        if (event.isShiftClick() && isProtected(event.getCurrentItem())) {
            if (topIsExternal) {
                event.setCancelled(true);
                notifyPlayer(player);
                return;
            }
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked != null && isExternalInventory(clicked.getType())) {
            if (isProtected(event.getCursor()) || isProtected(event.getCurrentItem())) {
                event.setCancelled(true);
                notifyPlayer(player);
                return;
            }
        }

        if (event.getClick() == ClickType.NUMBER_KEY && topIsExternal) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                if (isProtected(hotbarItem)) {
                    event.setCancelled(true);
                    notifyPlayer(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isProtected(event.getOldCursor())) return;

        Inventory top = event.getView().getTopInventory();
        if (!isExternalInventory(top.getType())) return;

        int topSize = top.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                notifyPlayer(player);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Block item frames and armor stands
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        var entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame) && !(entity instanceof ArmorStand)) return;

        if (player.isSneaking()) return;

        ItemStack hand = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();

        if (isProtected(hand)) {
            event.setCancelled(true);
            notifyPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        var entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame) && !(entity instanceof ArmorStand)) return;

        if (player.isSneaking()) return;

        ItemStack hand = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();

        if (isProtected(hand)) {
            event.setCancelled(true);
            notifyPlayer(player);
        }
    }

    // -------------------------------------------------------------------------
    // Block hoppers
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (isProtected(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Block non-owner entities from picking up the Greek Longbow
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (!isProtected(stack)) return;

        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        var storage = plugin.getOwnerStorage();
        if (storage.isClaimed() && !player.getUniqueId().equals(storage.getOwnerUuid())) {
            event.setCancelled(true);
            player.sendActionBar(
                Component.text("The Greek Longbow does not belong to you.")
                    .color(NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Protect the item entity from lava, fire, explosions, etc.
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item itemEntity)) return;
        if (!isProtected(itemEntity.getItemStack())) return;
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Soulbound on death
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        var drops    = event.getDrops();
        var iterator = drops.iterator();

        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (isProtected(item)) {
                iterator.remove();
                event.getItemsToKeep().add(item);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Prevent dropping
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!isProtected(stack)) return;

        event.setCancelled(true);
        event.getPlayer().sendActionBar(
            Component.text("The Greek Longbow cannot be discarded.")
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isExternalInventory(InventoryType type) {
        return switch (type) {
            case PLAYER, CRAFTING -> false;
            default -> true;
        };
    }

    private boolean isProtected(ItemStack item) {
        return plugin.getGreekLongbowItem().isGreekLongbow(item);
    }

    private void notifyPlayer(Player player) {
        player.sendActionBar(
            Component.text("The Greek Longbow cannot be contained.")
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
    }
}

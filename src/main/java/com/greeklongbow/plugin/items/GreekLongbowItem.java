package com.greeklongbow.plugin.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import com.greeklongbow.plugin.GreekLongbowPlugin;

import java.util.List;

public class GreekLongbowItem {

    public static final String GREEK_LONGBOW_KEY = "greeklongbow";

    private final NamespacedKey itemKey;

    public GreekLongbowItem(GreekLongbowPlugin plugin) {
        this.itemKey = new NamespacedKey(plugin, GREEK_LONGBOW_KEY);
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.BOW);

        item.editMeta(ItemMeta.class, meta -> {
            // Name
            meta.displayName(
                Component.text("Greek Longbow")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false)
            );

            // Lore
            meta.lore(List.of(
                Component.empty(),
                Component.text("Gift from Apollo to one of few mortals")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("who amused him.")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("Said to have homing powers.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("\u2600 Apollo's Draw")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("Offhand")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("Makes the next attack of the bow deal")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("more damage while having the homing effect.")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("One of Apollo's bows seems to have")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("alteration powers capable of even changing oneself.")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Cooldown: ")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(
                        Component.text("35s")
                            .color(NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false)
                    ),
                Component.empty(),
                Component.text("\"...the sun itself bent its gaze upon the arrow...\"")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true)
            ));

            // Enchantments
            meta.addEnchant(Enchantment.POWER, 5, true);
            meta.addEnchant(Enchantment.PUNCH, 3, true);

            // Unbreakable
            meta.setUnbreakable(true);

            // Hide extra tooltip lines
            meta.addItemFlags(
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ATTRIBUTES
            );

            // PDC tag
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        });

        return item;
    }

    public boolean isGreekLongbow(ItemStack item) {
        if (item == null || item.getType() != Material.BOW) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }
}

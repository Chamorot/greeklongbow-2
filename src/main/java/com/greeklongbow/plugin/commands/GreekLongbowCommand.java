package com.greeklongbow.plugin.commands;

import com.greeklongbow.plugin.GreekLongbowPlugin;
import com.greeklongbow.plugin.storage.OwnerStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GreekLongbowCommand implements CommandExecutor, TabCompleter {

    private static final String DISPLAY_NAME = "Greek Longbow";
    private static final String CMD_NAME     = "greeklongbow";
    private static final NamedTextColor COLOR = NamedTextColor.LIGHT_PURPLE;

    private final GreekLongbowPlugin plugin;

    public GreekLongbowCommand(GreekLongbowPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Route subcommands
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length > 0) {
            return switch (args[0].toLowerCase()) {
                case "owner"   -> handleOwner(sender);
                case "restore" -> handleRestore(sender);
                default        -> { sendUsage(sender); yield true; }
            };
        }

        // /greeklongbow — claim the weapon
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        OwnerStorage storage = plugin.getOwnerStorage();

        if (storage.isClaimed()) {
            player.sendMessage(
                Component.text(DISPLAY_NAME + " has already been claimed by ")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(
                        Component.text(storage.getOwnerName())
                            .color(COLOR)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                    .append(
                        Component.text(". It can never be claimed again.")
                            .color(NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)
                    )
            );
            return true;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(
                Component.text("Your inventory is full! Clear a slot and use /" + CMD_NAME + " again.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        storage.setClaimed(player.getUniqueId(), player.getName());
        player.getInventory().addItem(plugin.getGreekLongbowItem().create());

        // Server-wide broadcast
        plugin.getServer().broadcast(
            Component.text("\u26A1 ")
                .color(NamedTextColor.GOLD)
                .append(
                    Component.text(player.getName())
                        .color(COLOR)
                        .decoration(TextDecoration.BOLD, true)
                )
                .append(Component.text(" has claimed the ").color(NamedTextColor.GRAY))
                .append(
                    Component.text(DISPLAY_NAME)
                        .color(COLOR)
                        .decoration(TextDecoration.BOLD, true)
                )
                .append(Component.text(". May the gods watch over them.").color(NamedTextColor.GRAY))
        );

        player.sendMessage(
            Component.text(DISPLAY_NAME + " is now yours \u2014 and yours alone.")
                .color(COLOR)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
        player.sendMessage(
            Component.text("Offhand right-click to activate Apollo's Draw \u2014 look at your target first.")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        );

        return true;
    }

    // -------------------------------------------------------------------------
    // /greeklongbow owner
    // -------------------------------------------------------------------------

    private boolean handleOwner(CommandSender sender) {
        OwnerStorage storage = plugin.getOwnerStorage();

        if (!storage.isClaimed()) {
            sender.sendMessage(
                Component.text(DISPLAY_NAME + " has not yet been claimed by anyone.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        sender.sendMessage(
            Component.text(DISPLAY_NAME + " Owner: ")
                .color(COLOR)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                .append(
                    Component.text(storage.getOwnerName())
                        .color(COLOR)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false)
                )
        );
        return true;
    }

    // -------------------------------------------------------------------------
    // /greeklongbow restore  (admin only)
    // -------------------------------------------------------------------------

    private boolean handleRestore(CommandSender sender) {
        if (!sender.hasPermission("greeklongbow.admin")) {
            sender.sendMessage(
                Component.text("You do not have permission to use this command.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        OwnerStorage storage = plugin.getOwnerStorage();

        if (!storage.isClaimed()) {
            sender.sendMessage(
                Component.text(DISPLAY_NAME + " has not been claimed yet. Nothing to restore.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        Player owner = plugin.getServer().getPlayer(storage.getOwnerUuid());
        if (owner == null) {
            sender.sendMessage(
                Component.text("The owner (" + storage.getOwnerName() + ") is not online.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        if (owner.getInventory().firstEmpty() == -1) {
            sender.sendMessage(
                Component.text(owner.getName() + "'s inventory is full.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            owner.sendMessage(
                Component.text("An admin tried to restore " + DISPLAY_NAME + " but your inventory is full.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        owner.getInventory().addItem(plugin.getGreekLongbowItem().create());

        sender.sendMessage(
            Component.text(DISPLAY_NAME + " restored and given to " + owner.getName() + ".")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        );
        owner.sendMessage(
            Component.text(DISPLAY_NAME + " has been restored to you by an administrator.")
                .color(COLOR)
                .decoration(TextDecoration.ITALIC, false)
        );
        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(
            Component.text("Usage: /" + CMD_NAME + " | /" + CMD_NAME + " owner | /" + CMD_NAME + " restore")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        );
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("owner", "restore").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}

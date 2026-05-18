package com.greeklongbow.plugin;

import com.greeklongbow.plugin.commands.GreekLongbowCommand;
import com.greeklongbow.plugin.items.GreekLongbowItem;
import com.greeklongbow.plugin.listeners.ApolloDrawListener;
import com.greeklongbow.plugin.listeners.ContainerListener;
import com.greeklongbow.plugin.storage.OwnerStorage;
import org.bukkit.plugin.java.JavaPlugin;

public class GreekLongbowPlugin extends JavaPlugin {

    private static GreekLongbowPlugin instance;

    private GreekLongbowItem greekLongbowItem;
    private OwnerStorage ownerStorage;
    private ApolloDrawListener apolloDrawListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        greekLongbowItem = new GreekLongbowItem(this);
        ownerStorage     = new OwnerStorage(this, "greeklongbow");

        apolloDrawListener = new ApolloDrawListener(this);
        getServer().getPluginManager().registerEvents(apolloDrawListener, this);
        getServer().getPluginManager().registerEvents(new ContainerListener(this), this);

        var bowCmd = getCommand("greeklongbow");
        if (bowCmd != null) {
            var handler = new GreekLongbowCommand(this);
            bowCmd.setExecutor(handler);
            bowCmd.setTabCompleter(handler);
        }

        getLogger().info("The Greek Longbow is ready. Apollo watches.");
    }

    @Override
    public void onDisable() {
        if (apolloDrawListener != null) apolloDrawListener.cleanupAll();
        getLogger().info("The Greek Longbow has gone silent. Apollo's gaze turns away.");
    }

    // -------------------------------------------------------------------------
    // Static accessor
    // -------------------------------------------------------------------------

    public static GreekLongbowPlugin getInstance() { return instance; }

    // -------------------------------------------------------------------------
    // Item accessor
    // -------------------------------------------------------------------------

    public GreekLongbowItem getGreekLongbowItem() { return greekLongbowItem; }

    // -------------------------------------------------------------------------
    // Owner storage accessor
    // -------------------------------------------------------------------------

    public OwnerStorage getOwnerStorage() { return ownerStorage; }
}

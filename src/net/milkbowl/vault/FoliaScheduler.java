package net.milkbowl.vault;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Scheduler shim so Vault runs on both Bukkit/Spigot/Paper and Folia.
 * Folia's API is accessed reflectively so the plugin still compiles against the plain Bukkit API.
 */
final class FoliaScheduler {

    private static final boolean FOLIA = detectFolia();

    private FoliaScheduler() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static boolean isFolia() {
        return FOLIA;
    }

    /** Runs on the next tick (global region thread on Folia, main thread otherwise). */
    static void runGlobal(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method execute = scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
            execute.invoke(scheduler, plugin, task);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to use Folia global region scheduler", e);
        }
    }

    /** Repeating asynchronous task; delay and period are in ticks. */
    static void runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
            return;
        }
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Method runAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);
            Consumer<Object> consumer = scheduledTask -> task.run();
            runAtFixedRate.invoke(scheduler, plugin, consumer, Math.max(1L, delayTicks * 50L), Math.max(1L, periodTicks * 50L), TimeUnit.MILLISECONDS);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to use Folia async scheduler", e);
        }
    }

    static void cancelAll(Plugin plugin) {
        if (!FOLIA) {
            Bukkit.getScheduler().cancelTasks(plugin);
            return;
        }
        try {
            for (String getter : new String[] { "getGlobalRegionScheduler", "getAsyncScheduler" }) {
                Object scheduler = Bukkit.class.getMethod(getter).invoke(null);
                scheduler.getClass().getMethod("cancelTasks", Plugin.class).invoke(scheduler, plugin);
            }
        } catch (ReflectiveOperationException ignored) {
            // shutting down anyway
        }
    }
}

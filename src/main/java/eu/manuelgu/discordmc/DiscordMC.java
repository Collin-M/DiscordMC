package eu.manuelgu.discordmc;

import eu.manuelgu.discordmc.listener.BukkitEventListener;
import eu.manuelgu.discordmc.listener.ChatListener;
import eu.manuelgu.discordmc.listener.DiscordEventListener;
import eu.manuelgu.discordmc.update.Updater;
import gnu.trove.set.hash.THashSet;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.GuildCreateEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.modules.Configuration;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.RateLimitException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DiscordMC extends JavaPlugin {
    public static DiscordMC instance;

    /**
     * Get the client instance
     */
    @Getter
    public static IDiscordClient client;

    /**
     * Channels that send messages to Minecraft
     */
    @Getter
    private static List<IChannel> discordToMinecraft;

    /**
     * Channels that receive messages from Minecraft
     */
    @Getter
    private static List<IChannel> minecraftToDiscord;

    /**
     * Players that have the permissions to use the plugin
     */
    @Getter
    private static Set<UUID> cachedHasChatPermission;

    /**
     * Subscribed players that receive messages
     */
    @Getter
    private static Set<UUID> subscribedPlayers;

    /**
     * If token was valid or not
     */
    private static boolean validToken;

    /**
     * Get instance of DiscordMC main class
     *
     * @return instance of main class
     */
    public static DiscordMC get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Disable all modules
        Configuration.LOAD_EXTERNAL_MODULES = false;
        Configuration.AUTOMATICALLY_ENABLE_MODULES = false;

        // Initialize messaging API
        new MessageAPI(this);

        cachedHasChatPermission = new THashSet<>();
        subscribedPlayers = new THashSet<>();

        String token = getConfig().getString("settings.token");

        // Token not entered, probably initial start
        if (token.equalsIgnoreCase("TOKEN_HERE") || token.equalsIgnoreCase("")) {
            getLogger().warning("You haven't entered a valid token for your bot. See Spigot page for a tutorial.");
            validToken = false;
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            validToken = true;
        }

        // Client builder and login
        try {
            client = new ClientBuilder().withToken(token).build();
        } catch (DiscordException e) {
            e.printStackTrace();
            getLogger().severe("Failed to build client");
        }

        // Disable audio
        Discord4J.disableAudio();

        // Register listeners
        client.getDispatcher().registerListener(new DiscordEventListener(this));
        client.getDispatcher().registerListener(this);

        try {
            client.login();
        } catch (DiscordException e) {
            e.printStackTrace();
            getLogger().severe("Failed to login");
        } catch (RateLimitException e) {
            e.printStackTrace();
            getLogger().severe("Ratelimited while logging in");
        }

        // Register bukkit listeners and commands
        getServer().getPluginManager().registerEvents(new BukkitEventListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getCommand("discord").setExecutor(new DiscordCommand());

        // Check for plugin updates
        if (getConfig().getBoolean("settings.check_for_updates")) {
            Updater.sendUpdateMessage(this);
        }

    }

    @Override
    public void onDisable() {
        if (!validToken) {
            return;
        }
        try {
            client.logout();
        } catch (DiscordException ignored) {
            getLogger().warning("Could not logout");
        }
    }

    @EventSubscriber
    public void onGuildCreate(GuildCreateEvent event) {
        if (event.getGuild() == null) {
            return;
        }

        discordToMinecraft = new ArrayList<>();
        minecraftToDiscord = new ArrayList<>();

        List<String> dTm = getConfig().getStringList("settings.channels.discord_to_minecraft");
        List<String> mTd = getConfig().getStringList("settings.channels.minecraft_to_discord");

        for (IChannel channel : getClient().getChannels(false)) {
            if (dTm.contains(channel.getName())) {
                discordToMinecraft.add(channel);
            }
            if (mTd.contains(channel.getName())) {
                minecraftToDiscord.add(channel);
            }
        }

        getLogger().info("Successfully logged in with '" + event.getClient().getOurUser().getName() + "'");
    }
}

package com.example.velocityplugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Plugin(id = "keywordkick", name = "Keyword Kick Plugin", version = "1.0", authors = {"Longwise (because yes, I am very long)"})
public class KeywordKickPlugin {

    private final Logger logger;
    private final Path configPath;
    private final ProxyServer server; // Store ProxyServer instance
    private List<String> keywords;

    @Inject
    public KeywordKickPlugin(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.server = server; // Assign ProxyServer instance
        this.configPath = dataDirectory.resolve("config.yml");

        // Load configuration and register commands
        loadConfig();
        registerCommands();
    }

    private void loadConfig() {
        // Check if the config file exists; if not, create a default one
        if (!Files.exists(configPath)) {
            try {
                Files.createDirectories(configPath.getParent());
                Files.write(configPath, List.of(
                        "# List of keywords that trigger a proxy-wide kick",
                        "keywords:",
                        "  - banned",
                        "  - cheating",
                        "  - AFK'd"
                ));
                logger.info("Created default config.yml");
            } catch (IOException e) {
                logger.error("Failed to create default config.yml", e);
                return;
            }
        }

        // Load the keywords from the config file
        try {
            List<String> lines = Files.readAllLines(configPath);
            keywords = lines.stream()
                    .filter(line -> !line.startsWith("#") && line.startsWith("  - "))
                    .map(line -> line.substring(4)) // Remove the leading "  - "
                    .toList();

            logger.info("Loaded {} keywords from config.yml", keywords.size());
        } catch (IOException e) {
            logger.error("Failed to load config.yml", e);
        }
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();

        // Check if the player has the bypass permission
        if (player.hasPermission("keywordkick.bypass")) {
            logger.info("Player {} has keywordkick.bypass permission and will not be kicked.", player.getUsername());
            return; // Skip kicking this player
        }

        if (event.getServerKickReason().isPresent()) {
            String kickMessage = event.getServerKickReason().get().toString();

            // Check if the kick message contains any of the configured keywords
            for (String keyword : keywords) {
                if (kickMessage.toLowerCase().contains(keyword.toLowerCase())) {
                    // Forward the backend server's kick message to the player
                    player.disconnect(event.getServerKickReason().get());

                    logger.info("Player {} was kicked from the proxy due to keyword match: {}", player.getUsername(), keyword);
                    return; // Stop checking further keywords after a match
                }
            }
        }
    }

    private void registerCommands() {
        server.getCommandManager().register("keywordkick", new ReloadCommand(this));
    }

    public void reloadConfig(CommandSource source) {
        loadConfig();
        source.sendMessage(Component.text("KeywordKick configuration reloaded!"));
        logger.info("KeywordKick configuration reloaded!");
    }

    public static class ReloadCommand implements SimpleCommand {

        private final KeywordKickPlugin plugin;

        public ReloadCommand(KeywordKickPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();

            // Check if the sender has permission to reload
            if (!source.hasPermission("keywordkick.reload")) {
                source.sendMessage(Component.text("You do not have permission to execute this command."));
                return;
            }

            plugin.reloadConfig(source);
        }
    }
}

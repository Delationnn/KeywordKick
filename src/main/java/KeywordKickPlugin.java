package com.example.velocityplugin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Plugin(id = "keywordkick", name = "Keyword Kick Plugin", version = "1.1", authors = {"Longwise"})
public class KeywordKickPlugin {

    private final Logger logger;
    private final Path configPath;
    private final ProxyServer server;
    private List<String> keywords;
    private String redirectServer;
    private boolean redirectEnabled;

    @Inject
    public KeywordKickPlugin(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.server = server;
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
                        "# List of keywords that trigger an action",
                        "keywords:",
                        "  - banned",
                        "  - cheating",
                        "  - AFK'd",
                        "",
                        "# Set whether players should be redirected instead of being kicked",
                        "redirect-enabled: false",
                        "# The server to redirect players to when kicked",
                        "redirect-server: lobby"
                ));
                logger.info("Created default config.yml");
            } catch (Exception e) {
                logger.error("Failed to create default config.yml", e);
                return;
            }
        }

        // Load the config values
        try {
            List<String> lines = Files.readAllLines(configPath);
            keywords = lines.stream()
                    .filter(line -> line.startsWith("  - "))
                    .map(line -> line.substring(4)) // Remove the leading "  - "
                    .toList();

            redirectEnabled = lines.stream()
                    .anyMatch(line -> line.startsWith("redirect-enabled:") && line.endsWith("true"));

            redirectServer = lines.stream()
                    .filter(line -> line.startsWith("redirect-server:"))
                    .map(line -> line.split(":", 2)[1].trim())
                    .findFirst()
                    .orElse("lobby");

            logger.info("Configuration loaded: {} keywords, redirect-enabled={}, redirect-server={}", keywords.size(), redirectEnabled, redirectServer);
        } catch (Exception e) {
            logger.error("Failed to load config.yml", e);
        }
    }

@Subscribe
public void onKickedFromServer(KickedFromServerEvent event) {
    Player player = event.getPlayer();

    // Check if the player has the bypass permission
    if (player.hasPermission("keywordkick.bypass")) {
        logger.info("Player {} has bypass permission and will not be redirected or kicked.", player.getUsername());
        return;
    }

    event.getServerKickReason().ifPresent(reason -> {
        String kickMessage = reason.toString();

        // Check if the kick message contains any of the configured keywords
        for (String keyword : keywords) {
            if (kickMessage.toLowerCase().contains(keyword.toLowerCase())) {
                if (redirectEnabled) {
                    // Attempt to redirect the player to the configured server
                    Optional<RegisteredServer> targetServer = server.getServer(redirectServer);
                    if (targetServer.isPresent()) {
                        player.createConnectionRequest(targetServer.get()).connect()
                                .whenComplete((result, throwable) -> {
                                    if (throwable != null) {
                                        // Log error and disconnect player if redirection fails
                                        logger.error("Failed to redirect player {} to {}: {}", player.getUsername(), redirectServer, throwable.getMessage());
                                        player.disconnect(Component.text("You have been disconnected."));
                                    } else {
                                        logger.info("Player {} was successfully redirected to {} due to keyword match: {}", player.getUsername(), redirectServer, keyword);
                                    }
                                });
                    } else {
                        // Log and disconnect if the target server does not exist
                        logger.error("Configured redirect server '{}' not found. Disconnecting player {}.", redirectServer, player.getUsername());
                        player.disconnect(Component.text("You have been disconnected because the redirect server is unavailable."));
                    }
                } else {
                    // Disconnect the player if redirect is disabled
                    player.disconnect(reason);
                    logger.info("Player {} was disconnected due to keyword match: {}", player.getUsername(), keyword);
                }
                return; // Exit after handling the first keyword match
            }
        }
    });
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

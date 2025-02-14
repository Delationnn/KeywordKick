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

        loadConfig();
        registerCommands();
    }

    private void loadConfig() {
        try {
            if (!Files.exists(configPath)) {
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
            }

            List<String> lines = Files.readAllLines(configPath);
            keywords = lines.stream()
                    .filter(line -> line.startsWith("  - "))
                    .map(line -> line.substring(4))
                    .toList();

            redirectEnabled = lines.stream()
                    .anyMatch(line -> line.startsWith("redirect-enabled:") && line.endsWith("true"));

            redirectServer = lines.stream()
                    .filter(line -> line.startsWith("redirect-server:"))
                    .map(line -> line.split(":", 2)[1].trim())
                    .findFirst()
                    .orElse("lobby");

            logger.info("Configuration loaded: {} keywords, redirect-enabled={}, redirect-server={}",
                    keywords.size(), redirectEnabled, redirectServer);
        } catch (Exception e) {
            logger.error("Failed to load configuration from config.yml", e);
        }
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("keywordkick.bypass")) {
            logger.info("Player {} has bypass permission, skipping keyword checks.", player.getUsername());
            return;
        }

        if (event.getServerKickReason().isPresent()) {
            String kickMessage = event.getServerKickReason().get().toString();

            for (String keyword : keywords) {
                if (kickMessage.toLowerCase().contains(keyword.toLowerCase())) {
                    if (redirectEnabled) {
                        Optional<RegisteredServer> targetServer = server.getServer(redirectServer);
                        if (targetServer.isPresent()) {
                            player.createConnectionRequest(targetServer.get()).fireAndForget();
                            logger.info("Player {} redirected to {} due to keyword match: {}", player.getUsername(), redirectServer, keyword);
                        } else {
                            logger.error("Redirect server '{}' not found. Disconnecting player {}.", redirectServer, player.getUsername());
                            player.disconnect(Component.text("Server not found. Please contact an administrator."));
                        }
                    } else {
                        player.disconnect(event.getServerKickReason().get());
                        logger.info("Player {} disconnected due to keyword match: {}", player.getUsername(), keyword);
                    }
                    return;
                }
            }
        }
    }

    public void reloadConfig(CommandSource source) {
        try {
            loadConfig();
            source.sendMessage(Component.text("KeywordKick configuration reloaded!"));
            logger.info("Configuration reloaded successfully.");
        } catch (Exception e) {
            logger.error("Error reloading configuration.", e);
            source.sendMessage(Component.text("Failed to reload configuration. Check the console for details."));
        }
    }

    private void registerCommands() {
        server.getCommandManager().register("keywordkick", new ReloadCommand(this));
    }

    public static class ReloadCommand implements SimpleCommand {
        private final KeywordKickPlugin plugin;

        public ReloadCommand(KeywordKickPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();

            plugin.logger.info("Executing /keywordkick reload from {}", source);

            if (!source.hasPermission("keywordkick.reload")) {
                source.sendMessage(Component.text("You do not have permission to execute this command."));
                return;
            }

            plugin.reloadConfig(source);
        }
    }
}

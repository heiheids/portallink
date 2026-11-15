package com.heishan.portallink.client.command;

import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.client.gui.PortalLinkConfigScreen;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalSnapshot;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /portallink ...} client commands: list, rescan, toggle and open the settings.
 *
 * <p>The scan itself may need the server thread, so listing is asynchronous: the command asks for a
 * scan and prints the result from a callback that runs on a later client tick.</p>
 */
public final class PortalLinkCommand {

    private PortalLinkCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("portallink")
                        .executes(context -> list(context.getSource()))
                        .then(ClientCommands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(ClientCommands.literal("rescan")
                                .executes(context -> list(context.getSource())))
                        .then(ClientCommands.literal("toggle")
                                .executes(context -> toggle(context.getSource())))
                        .then(ClientCommands.literal("config")
                                .executes(context -> openConfig(context.getSource())))));
    }

    private static int list(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("portallink.command.scanning")
                .withStyle(ChatFormatting.GRAY));
        ClientPortalLinkState.requestScan(snapshot -> report(source, snapshot));
        return 1;
    }

    private static void report(FabricClientCommandSource source, PortalSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            source.sendFeedback(Component.translatable("portallink.command.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        source.sendFeedback(Component.translatable("portallink.command.header",
                snapshot.entries().size()).withStyle(ChatFormatting.AQUA));
        if (!snapshot.otherKnown()) {
            source.sendFeedback(Component.translatable("portallink.command.incomplete")
                    .withStyle(ChatFormatting.YELLOW));
        }
        for (PortalEntry entry : snapshot.entries()) {
            source.sendFeedback(describe(entry));
        }
    }

    private static Component describe(PortalEntry entry) {
        BlockPos self = entry.portal().ref().centerPos();
        ChatFormatting color = switch (entry.status()) {
            case LINKED -> ChatFormatting.GREEN;
            case CONFLICT -> ChatFormatting.RED;
            case WILL_CREATE -> ChatFormatting.GOLD;
            case UNKNOWN -> ChatFormatting.GRAY;
        };
        Component status = Component.translatable(
                "portallink.status." + entry.status().name().toLowerCase()).withStyle(color);

        if (entry.hasMatch()) {
            BlockPos partner = entry.matched().ref().centerPos();
            return Component.translatable("portallink.command.line_linked", status,
                    self.getX(), self.getY(), self.getZ(),
                    partner.getX(), partner.getY(), partner.getZ(),
                    Math.round(entry.matchedDistance()));
        }
        BlockPos target = entry.targetAnchor();
        return Component.translatable("portallink.command.line_open", status,
                self.getX(), self.getY(), self.getZ(),
                target.getX(), target.getY(), target.getZ());
    }

    private static int toggle(FabricClientCommandSource source) {
        PortalLinkConfig config = PortalLinkConfig.get();
        config.enabled = !config.enabled;
        config.save();
        ClientPortalLinkState.markDirty();
        source.sendFeedback(Component.translatable(config.enabled
                ? "portallink.command.enabled"
                : "portallink.command.disabled"));
        return config.enabled ? 1 : 0;
    }

    private static int openConfig(FabricClientCommandSource source) {
        var client = source.getClient();
        client.execute(() -> PortalLinkConfigScreen.open(null));
        return 1;
    }
}

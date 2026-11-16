package com.heishan.portallink.client.command;

import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.client.gui.PortalLinkConfigScreen;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalSnapshot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /portallink ...} client command: list, rescan, toggle and open the settings.
 *
 * <p>Registered through NeoForge's {@code RegisterClientCommandsEvent}, so the command exists in
 * singleplayer and on servers without the mod, and never reaches the server. The scan itself may
 * need the server thread, so listing is asynchronous: the command asks for a scan and prints the
 * result from a callback that runs on a later client tick.</p>
 */
public final class PortalLinkCommand {

    private PortalLinkCommand() {
    }

    /**
     * Build the whole tree.
     *
     * @param dispatcher client command dispatcher supplied by NeoForge
     */
    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("portallink")
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("rescan")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("toggle")
                        .executes(context -> toggle(context.getSource())))
                .then(Commands.literal("config")
                        .executes(context -> openConfig(context.getSource()))));
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("portallink.command.scanning")
                .withStyle(ChatFormatting.GRAY), false);
        ClientPortalLinkState.requestScan(snapshot -> report(source, snapshot));
        return 1;
    }

    private static void report(CommandSourceStack source, PortalSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("portallink.command.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        source.sendSuccess(() -> Component.translatable("portallink.command.header",
                snapshot.entries().size()).withStyle(ChatFormatting.AQUA), false);
        if (!snapshot.otherKnown()) {
            source.sendSuccess(() -> Component.translatable("portallink.command.incomplete")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        for (PortalEntry entry : snapshot.entries()) {
            Component line = describe(entry);
            source.sendSuccess(() -> line, false);
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

    private static int toggle(CommandSourceStack source) {
        PortalLinkConfig config = PortalLinkConfig.get();
        config.enabled = !config.enabled;
        config.save();
        ClientPortalLinkState.markDirty();
        source.sendSuccess(() -> Component.translatable(config.enabled
                ? "portallink.command.enabled"
                : "portallink.command.disabled"), false);
        return config.enabled ? 1 : 0;
    }

    private static int openConfig(CommandSourceStack source) {
        // Client commands already run on the client thread, so the screen can be opened directly.
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> PortalLinkConfigScreen.open(null));
        return 1;
    }
}

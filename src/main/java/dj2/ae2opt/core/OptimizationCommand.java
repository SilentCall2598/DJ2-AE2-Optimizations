package dj2.ae2opt.core;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.List;


public final class OptimizationCommand extends CommandBase {

    @Override
    public String getName() {
        return Constants.MOD_ID;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + Constants.MOD_ID + " status [full|mixins|counters]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 1 || !"status".equalsIgnoreCase(args[0]) || args.length > 2) {
            reply(sender, "Usage: " + getUsage(sender));
            return;
        }

        final String mode = args.length == 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        if (mode.isEmpty()) {
            for (ITextComponent line : StatusDashboard.compactLines()) {
                sender.sendMessage(line);
            }
            return;
        }

        if ("full".equals(mode)) {
            for (String line : MixinStatus.selectionLines()) {
                reply(sender, line);
            }
            List<String> status = MixinStatus.statusLines();
            reply(sender, "----");
            for (String line : status) {
                reply(sender, line);
            }
            reply(sender, "----");
            for (String line : Diagnostics.summaryLines()) {
                reply(sender, line);
            }
            return;
        }

        if ("mixins".equals(mode)) {
            for (String line : MixinStatus.statusLines()) {
                reply(sender, line);
            }
            return;
        }

        if ("counters".equals(mode)) {
            for (String line : Diagnostics.summaryLines()) {
                reply(sender, line);
            }
            return;
        }

        reply(sender, "Usage: " + getUsage(sender));
    }

    private static void reply(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(message));
    }
}

package dj2.ae2opt.core;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.List;


public final class OptimizationCommand extends CommandBase {

    @Override
    public String getName() {
        return Constants.MOD_ID;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + Constants.MOD_ID + " status";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length != 1 || !"status".equalsIgnoreCase(args[0])) {
            reply(sender, "Usage: " + getUsage(sender));
            return;
        }

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
    }

    private static void reply(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(message));
    }
}

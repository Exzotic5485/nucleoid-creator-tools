package xyz.nucleoid.creator_tools.command;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;

public final class MapWorkspaceArgument {
    public static final DynamicCommandExceptionType WORKSPACE_NOT_FOUND = new DynamicCommandExceptionType(arg ->
            new TranslatableText("text.nucleoid_creator_tools.map_workspace.workspace_not_found", arg)
    );

    public static RequiredArgumentBuilder<ServerCommandSource, Identifier> argument(String name) {
        return CommandManager.argument(name, IdentifierArgumentType.identifier())
                .suggests((context, builder) -> {
                    var source = context.getSource();
                    var workspaceManager = MapWorkspaceManager.get(source.getServer());

                    return CommandSource.suggestIdentifiers(
                            workspaceManager.getWorkspaceIds().stream(),
                            builder
                    );
                });
    }

    public static MapWorkspace get(CommandContext<ServerCommandSource> context, String name) throws CommandSyntaxException {
        var identifier = IdentifierArgumentType.getIdentifier(context, name);

        var source = context.getSource();
        var workspaceManager = MapWorkspaceManager.get(source.getServer());

        var workspace = workspaceManager.byId(identifier);
        if (workspace == null) {
            throw WORKSPACE_NOT_FOUND.create(identifier);
        }

        return workspace;
    }
}

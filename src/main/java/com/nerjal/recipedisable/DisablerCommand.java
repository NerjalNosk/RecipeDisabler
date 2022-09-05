package com.nerjal.recipedisable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.MutableText;
import org.apache.logging.log4j.Level;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.*;

public class DisablerCommand {
    private static final DynamicCommandExceptionType ERR = new DynamicCommandExceptionType(s->new LiteralMessage((String)s));
    private static final int listPageSize = 9;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> disable = literal("disable");
        disable.then(argument("target", StringArgumentType.greedyString())
                .suggests(SuggestionProviders.ALL_RECIPES).executes(DisablerCommand::freeDisable));

        LiteralArgumentBuilder<ServerCommandSource> enable = literal("enable");
        ConfigLoader.getConfig().disabled().forEach(s ->
            enable.then(literal(s).executes(DisablerCommand::strictEnable))
        );
        enable.then(argument("target", StringArgumentType.greedyString())
                .suggests(SuggestionProviders.ALL_RECIPES).executes(DisablerCommand::freeEnable));

        LiteralArgumentBuilder<ServerCommandSource> keep = literal("keep");
        keep.then(argument("target", StringArgumentType.greedyString())
                .suggests(SuggestionProviders.ALL_RECIPES).executes(DisablerCommand::freeKeep));

        LiteralArgumentBuilder<ServerCommandSource> unkeep = literal("unkeep");
        ConfigLoader.getConfig().kept().forEach(s ->
            unkeep.then(literal(s).executes(DisablerCommand::strictUnkeep))
        );
        unkeep.then(argument("target", StringArgumentType.greedyString())
                .suggests(SuggestionProviders.ALL_RECIPES).executes(DisablerCommand::freeUnkeep));

        LiteralArgumentBuilder<ServerCommandSource> listResources = literal("resources")
                .then(literal("disabled")
                        .executes(DisablerCommand::listResourcesDisabled)
                        .then(argument("page",IntegerArgumentType.integer(1))
                                .executes(DisablerCommand::listResourcesDisabled)))
                .then(literal("kept")
                        .executes(DisablerCommand::listResourcesKept)
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(DisablerCommand::listResourcesKept)));
        LiteralArgumentBuilder<ServerCommandSource> list = literal("list")
                .then(literal("disabled")
                        .executes(DisablerCommand::listDisabled)
                        .then(argument("page",IntegerArgumentType.integer(1))
                                .executes(DisablerCommand::listDisabled)))
                .then(literal("kept")
                        .executes(DisablerCommand::listKept)
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(DisablerCommand::listKept)))
                .then(listResources);
        dispatcher.register(literal("recipes")
                .requires(source -> source.hasPermissionLevel(source.getServer().getOpPermissionLevel()))
                .then(disable)
                .then(enable)
                .then(keep)
                .then(unkeep)
                .then(list)
                .then(literal("save").executes(DisablerCommand::save))
        );
    }

    private static int freeDisable(CommandContext<ServerCommandSource> context) {
        String target = StringArgumentType.getString(context, "target").toLowerCase(Locale.ENGLISH);
        if (ConfigLoader.getConfig().disable(target)) {
            context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                    String.format("Resource template %s disabled", target))), true);
            RecipeDisabler.disable(context.getSource().getServer(), target);
        } else
            context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                String.format("Unable to disable %s: already disabled", target))), false);
        return 0;
    }

    private static int strictEnable(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String[] command = context.getInput().split(" ");
        if (command.length != 3) throw ERR.create("Invalid input "+String.join(" ", command));
        String target = command[2].toLowerCase(Locale.ENGLISH);
        if (ConfigLoader.getConfig().enable(target))
            context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                    String.format("Resource %s enabled", target))), true);
        else context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                String.format("Unable to enable %s: not disabled", target))), false);
        return 0;
    }

    private static int freeEnable(CommandContext<ServerCommandSource> context) {
        String target = StringArgumentType.getString(context, "target").toLowerCase(Locale.ENGLISH);
        if (ConfigLoader.getConfig().enable(target))
            context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                    String.format("Resource %s enabled", target))), true);
        else context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                String.format("Unable to enable %s: not disabled", target))), false);
        return 0;
    }

    private static int freeKeep(CommandContext<ServerCommandSource> context) {
        String target = StringArgumentType.getString(context, "target").toLowerCase(Locale.ENGLISH);
        if (ConfigLoader.getConfig().keep(target))
            context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                String.format("Resource template %s registered as keep", target))), true);
        else
            context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                String.format("Unable to disable %s: already registered as keep", target))), false);
        return 0;
    }

    private static int strictUnkeep(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String[] command = context.getInput().split(" ");
        if (command.length != 3) throw ERR.create("Invalid input "+String.join(" ",
                Arrays.copyOfRange(command, 3, command.length)));
        String target = command[2].toLowerCase(Locale.ENGLISH);
        if (ConfigLoader.getConfig().unkeep(target))
            context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                    String.format("Resource %s unregistered from keep", target))), true);
        else context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                String.format("Unable to unregister %s: not registered as keep", target))), false);
        return 0;
    }

    private static int freeUnkeep(CommandContext<ServerCommandSource> context) {
        String target = StringArgumentType.getString(context, "target").toLowerCase(Locale.ENGLISH);
        if (ConfigLoader.getConfig().unkeep(target))
            context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                    String.format("Resource %s unregistered from keep", target))), true);
        else context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                String.format("Unable to unregister %s: not registered as keep", target))), false);
        return 0;
    }

    private static int save(CommandContext<ServerCommandSource> context) {
        if (ConfigLoader.getConfig().save() != Success.SUCCESS)
            context.getSource().sendFeedback(MutableText.of(
                    new LiteralTextContent("Something went wrong")), false);
        else context.getSource().sendFeedback(MutableText.of(new LiteralTextContent(
                String.format("Successfully saved %s/%s", RecipeDisabler.configFolder,
                        RecipeDisabler.configFile))), true);
        return 0;
    }

    private static int listDisabled(CommandContext<ServerCommandSource> context) {
        int i = 1;
        try {
            i = context.getArgument("page", int.class);
        } catch (IllegalArgumentException ignored) {}
        List<String> disabled = ConfigLoader.getConfig().disabled();
        if (disabled.size() == 0)
            context.getSource().sendFeedback(MutableText.of(
                    new LiteralTextContent("There are currently no disabled templates")), false);
        else
            context.getSource().sendFeedback(MutableText.of(
                    new LiteralTextContent(listView(disabled, "Disabled templates", i))),false);
        return 0;
    }

    private static int listKept(CommandContext<ServerCommandSource> context) {
        int i = 1;
        try {
            i = context.getArgument("page", int.class);
        } catch (IllegalArgumentException ignored) {}
        List<String> kept = ConfigLoader.getConfig().kept();
        if (kept.size() == 0)
            context.getSource().sendFeedback(MutableText.of(
                    new LiteralTextContent("There are currently no kept templates")), false);
        else
            context.getSource().sendFeedback(MutableText.of(
                    new LiteralTextContent(listView(kept, "Kept templates", i))),false);
        return 0;
    }

    private static int listResourcesDisabled(CommandContext<ServerCommandSource> context) {
        int i = 1;
        try {
            i = context.getArgument("page", int.class);
        } catch (IllegalArgumentException ignored) {}
        List<String> resources = ConfigLoader.getConfig().disabledResources();
        if (resources.size() == 0)
            context.getSource().sendFeedback(MutableText.of(
                    new LiteralTextContent("There are currently no disabled resources")), false);
        else
            context.getSource().sendFeedback(MutableText.of(
                    new LiteralTextContent(listView(resources, "Disabled resources", i))), false);
        return 0;
    }

    private static int listResourcesKept(CommandContext<ServerCommandSource> context) {
        int i = 1;
        try {
            i = context.getArgument("page", int.class);
        } catch (IllegalArgumentException ignored) {}
        List<String> resources = ConfigLoader.getConfig().keptResources();
        if (resources.size() == 0)
            context.getSource().sendFeedback(MutableText.of(
                    new LiteralTextContent("There are currently no kept resources")), false);
        else
            context.getSource().sendFeedback(MutableText.of(
                    new LiteralTextContent(listView(resources, "Kept resources", i))), false);
        return 0;
    }

    private static String listView(List<String> list, String data, int page) {
        try {
            list.sort(String::compareTo);
            int max = (int) Math.max(1, Math.ceil(list.size() / (float) listPageSize));
            StringBuilder feedback = new StringBuilder();
            if (page > max) {
                feedback.append(String.format("Page %d over maximum page %d, falling back to page 1%n", page, max));
                page = 1;
            }
            feedback.append(String.format("%s, page %d out of %d", data, page, max));
            list = list.subList((page - 1) * listPageSize, Math.min(page * listPageSize, list.size()));
            for (String s : list) {
                feedback.append("\n  - ").append(s);
            }
            return feedback.toString();
        } catch (Exception e) {
            RecipeDisabler.LOGGER.log(Level.ERROR,e);
            e.printStackTrace();
            throw e;
        }
    }
}

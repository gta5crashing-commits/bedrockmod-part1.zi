package com.bedrockmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class BedrockCommands {
    private BedrockCommands() {}

    private static int msg(CommandContext<CommandSourceStack> c, String m) {
        c.getSource().sendSuccess(() -> Component.literal(m), false);
        return 1;
    }

    private static Box need(CommandContext<CommandSourceStack> c) {
        if (BedrockMod.box == null) {
            c.getSource().sendFailure(Component.literal("Pehle /bedrock create chalao."));
            return null;
        }
        return BedrockMod.box;
    }

    private static int create(CommandSourceStack src, int size, int height) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (BedrockMod.box != null) { BedrockMod.box.clearFrame(); BedrockMod.box.clearInterior(); }
        BlockPos pos = p.blockPosition();
        Box b = new Box(src.getLevel(), pos.getX(), pos.getY() - 1, pos.getZ(), size, height);
        BedrockMod.box = b;
        b.buildFrame();
        src.sendSuccess(() -> Component.literal("Box ban gaya: " + size + "x" + size + ", height " + height), false);
        return 1;
    }

    private static void wallCmd(LiteralArgumentBuilder<CommandSourceStack> root, String name, BlockState st) {
        root.then(Commands.literal(name).executes(c -> {
            Box b = need(c); if (b == null) return 0;
            b.wall = st; b.buildFrame();
            return msg(c, "Walls/floor: " + name);
        }));
    }

    private static void toggle(LiteralArgumentBuilder<CommandSourceStack> root, String name, java.util.function.BooleanSupplier get,
                               java.util.function.Consumer<Boolean> set) {
        root.then(Commands.literal(name).executes(c -> {
            boolean v = !get.getAsBoolean(); set.accept(v);
            return msg(c, name + ": " + (v ? "ON" : "OFF"));
        }));
    }

    private static void colorCmd(LiteralArgumentBuilder<CommandSourceStack> root, String which) {
        root.then(Commands.literal("color").then(Commands.literal(which)
                .then(Commands.argument("color", StringArgumentType.word()).executes(c -> {
                    ChatFormatting f = ChatFormatting.getByName(StringArgumentType.getString(c, "color"));
                    if (f == null || !f.isColor()) {
                        c.getSource().sendFailure(Component.literal("Galat color. Jaise: red, green, gold, aqua, dark_purple"));
                        return 0;
                    }
                    if (which.equals("win")) BedrockMod.winColor = f; else BedrockMod.cancelColor = f;
                    return msg(c, which + " color set: " + f.getName());
                }))));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d, CommandBuildContext ctx) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bedrock");

        // ---- Box creation & editing ----
        root.then(Commands.literal("create")
                .executes(c -> create(c.getSource(), 15, 12))
                .then(Commands.argument("size", IntegerArgumentType.integer(3, 63))
                        .then(Commands.argument("height", IntegerArgumentType.integer(9, 21))
                                .executes(c -> create(c.getSource(),
                                        IntegerArgumentType.getInteger(c, "size"),
                                        IntegerArgumentType.getInteger(c, "height"))))));

        root.then(Commands.literal("delete").executes(c -> {
            Box b = need(c); if (b == null) return 0;
            b.clearInterior(); b.clearFrame(); BedrockMod.box = null;
            return msg(c, "Box delete ho gaya.");
        }));

        root.then(Commands.literal("clear").executes(c -> {
            Box b = need(c); if (b == null) return 0;
            b.clearInterior();
            return msg(c, "Box clear.");
        }));

        root.then(Commands.literal("fill")
                .executes(c -> {
                    Box b = need(c); if (b == null) return 0;
                    return msg(c, "Fill: " + b.fillRows(b.height, BedrockMod.autoReplace) + " blocks");
                })
                .then(Commands.argument("rows", IntegerArgumentType.integer(1, 21)).executes(c -> {
                    Box b = need(c); if (b == null) return 0;
                    return msg(c, "Fill: " + b.fillRows(IntegerArgumentType.getInteger(c, "rows"), BedrockMod.autoReplace) + " blocks");
                })));

        root.then(Commands.literal("fillblock")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 200000)).executes(c -> {
                    Box b = need(c); if (b == null) return 0;
                    return msg(c, "Placed: " + b.fillCount(IntegerArgumentType.getInteger(c, "count"), BedrockMod.autoReplace) + " blocks");
                })));

        wallCmd(root, "glass", Blocks.GLASS.defaultBlockState());
        wallCmd(root, "wood", Blocks.OAK_PLANKS.defaultBlockState());
        wallCmd(root, "rock", Blocks.STONE.defaultBlockState());

        root.then(Commands.literal("layer")
                .then(Commands.argument("count", IntegerArgumentType.integer(0, 21))
                        .then(Commands.argument("block", BlockStateArgument.block(ctx)).executes(c -> {
                            Box b = need(c); if (b == null) return 0;
                            b.layerCount = IntegerArgumentType.getInteger(c, "count");
                            b.layerBlock = BlockStateArgument.getBlock(c, "block").getState();
                            return msg(c, "Bottom " + b.layerCount + " layers set.");
                        }))));

        root.then(Commands.literal("heightup").then(Commands.argument("n", IntegerArgumentType.integer(1, 12)).executes(c -> {
            Box b = need(c); if (b == null) return 0;
            b.resize(b.size, b.height + IntegerArgumentType.getInteger(c, "n"));
            return msg(c, "Height: " + b.height);
        })));
        root.then(Commands.literal("heightdown").then(Commands.argument("n", IntegerArgumentType.integer(1, 12)).executes(c -> {
            Box b = need(c); if (b == null) return 0;
            b.resize(b.size, b.height - IntegerArgumentType.getInteger(c, "n"));
            return msg(c, "Height: " + b.height);
        })));
        root.then(Commands.literal("radiusup").then(Commands.argument("n", IntegerArgumentType.integer(1, 30)).executes(c -> {
            Box b = need(c); if (b == null) return 0;
            b.resize(b.size + 2 * IntegerArgumentType.getInteger(c, "n"), b.height);
            return msg(c, "Size: " + b.size);
        })));
        root.then(Commands.literal("radiusdown").then(Commands.argument("n", IntegerArgumentType.integer(1, 30)).executes(c -> {
            Box b = need(c); if (b == null) return 0;
            b.resize(b.size - 2 * IntegerArgumentType.getInteger(c, "n"), b.height);
            return msg(c, "Size: " + b.size);
        })));

        root.then(Commands.literal("tp").executes(c -> {
            Box b = need(c); if (b == null) return 0;
            ServerPlayer t = BedrockMod.mainUser != null
                    ? c.getSource().getServer().getPlayerList().getPlayerByName(BedrockMod.mainUser)
                    : c.getSource().getPlayerOrException();
            if (t == null) { c.getSource().sendFailure(Component.literal("Main user online nahi hai.")); return 0; }
            t.teleportTo(b.level, b.cx + 0.5, b.top() + 3, b.cz + 0.5, Set.of(), t.getYRot(), t.getXRot(), true);
            return msg(c, "Teleported.");
        }));

        // ---- TNT & effects ----
        root.then(Commands.literal("tnt")
                .executes(c -> {
                    if (need(c) == null) return 0;
                    BedrockMod.dropTnt(null, 4f, 60); return 1;
                })
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                    if (need(c) == null) return 0;
                    BedrockMod.dropTnt(StringArgumentType.getString(c, "name"), 4f, 60); return 1;
                })));

        root.then(Commands.literal("randomtnt").executes(c -> {
            if (need(c) == null) return 0;
            BedrockMod.dropTnt(null, 1f + (float) (Math.random() * 11), 60); return 1;
        }));

        root.then(Commands.literal("supertnt")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                        .then(Commands.argument("strength", FloatArgumentType.floatArg(0.5f, 30f)).executes(c -> {
                            if (need(c) == null) return 0;
                            int n = IntegerArgumentType.getInteger(c, "count");
                            float s = FloatArgumentType.getFloat(c, "strength");
                            for (int i = 0; i < n; i++) BedrockMod.dropTnt(null, s, 60);
                            return 1;
                        }))));

        root.then(Commands.literal("autotnt").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600)).executes(c -> {
            BedrockMod.autoSeconds = IntegerArgumentType.getInteger(c, "seconds");
            BedrockMod.autoCounter = 0;
            return msg(c, BedrockMod.autoSeconds == 0 ? "AutoTNT OFF" : "AutoTNT har " + BedrockMod.autoSeconds + "s");
        })));

        toggle(root, "fireworks", () -> BedrockMod.fireworks, v -> BedrockMod.fireworks = v);

        // ---- Timer & settings ----
        root.then(Commands.literal("timer").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 36000)).executes(c -> {
            if (need(c) == null) return 0;
            BedrockMod.timerLeft = IntegerArgumentType.getInteger(c, "seconds") * 20;
            return msg(c, "Timer start: " + (BedrockMod.timerLeft / 20) + "s");
        })));

        root.then(Commands.literal("stop").executes(c -> {
            if (BedrockMod.timerLeft > 0) {
                BedrockMod.timerLeft = -1;
                BedrockMod.title(c.getSource().getServer(), "CANCELLED", BedrockMod.cancelColor);
            }
            return msg(c, "Timer stopped.");
        }));

        root.then(Commands.literal("autoreset").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600)).executes(c -> {
            BedrockMod.autoReset = IntegerArgumentType.getInteger(c, "seconds");
            return msg(c, BedrockMod.autoReset == 0 ? "AutoReset OFF" : "Win ke " + BedrockMod.autoReset + "s baad box clear hoga");
        })));

        toggle(root, "edit", () -> BedrockMod.protect, v -> BedrockMod.protect = v);          // ON = wall/floor protected
        toggle(root, "autoreplace", () -> BedrockMod.autoReplace, v -> BedrockMod.autoReplace = v);
        toggle(root, "toplock", () -> BedrockMod.topLock, v -> BedrockMod.topLock = v);

        colorCmd(root, "cancel");
        colorCmd(root, "win");

        root.then(Commands.literal("set_main_user").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
            BedrockMod.mainUser = StringArgumentType.getString(c, "name");
            return msg(c, "Main user: " + BedrockMod.mainUser);
        })));
        root.then(Commands.literal("remove_main_user").executes(c -> {
            BedrockMod.mainUser = null;
            return msg(c, "Main user removed.");
        }));

        root.then(Commands.literal("help").executes(c -> msg(c,
                "create [size height], delete, clear, fill [rows], fillblock n, glass/wood/rock, layer n block, "
                + "heightup/down n, radiusup/down n, tp, tnt [name], randomtnt, supertnt n strength, autotnt s, fireworks, "
                + "timer s, stop, autoreset s, edit, autoreplace, toplock, color <cancel|win> <color>, set_main_user, remove_main_user")));

        d.register(root);
    }
}

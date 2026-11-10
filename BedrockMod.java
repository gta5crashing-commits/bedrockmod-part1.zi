package com.bedrockmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BedrockMod implements ModInitializer {
    public static Box box;
    public static boolean protect = true, autoReplace = false, topLock = false, fireworks = true;
    public static int timerLeft = -1, autoSeconds = 0, autoReset = 0, resetLeft = -1, autoCounter = 0;
    public static ChatFormatting winColor = ChatFormatting.GREEN, cancelColor = ChatFormatting.RED;
    public static String mainUser = null;

    private static final List<Pending> PENDING = new ArrayList<>();

    private static class Pending {
        final ServerLevel level; final PrimedTnt tnt; int ticks; final float power;
        Pending(ServerLevel l, PrimedTnt t, int ticks, float p) { level = l; tnt = t; this.ticks = ticks; power = p; }
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> BedrockCommands.register(dispatcher, ctx));
        ServerTickEvents.END_SERVER_TICK.register(BedrockMod::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { box = null; PENDING.clear(); timerLeft = -1; resetLeft = -1; });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) ->
                !(box != null && world == box.level && protect && box.isFrame(pos)));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerLevel) || box == null || world != box.level) return InteractionResult.PASS;
            if (!(player.getItemInHand(hand).getItem() instanceof BlockItem)) return InteractionResult.PASS;
            BlockPos target = hit.getBlockPos().relative(hit.getDirection());
            if ((topLock && box.aboveBox(target)) || (protect && box.isFrame(target))) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
    }

    /** TNT box ke upar se girata hai. power = explosion strength. */
    public static void dropTnt(String name, float power, int fuseTicks) {
        if (box == null) return;
        ServerLevel lv = box.level;
        RandomSource r = lv.getRandom();
        double x = box.x0() + r.nextDouble() * box.size;
        double z = box.z0() + r.nextDouble() * box.size;
        double y = box.top() + 2;
        PrimedTnt tnt = new PrimedTnt(lv, x, y, z, null);
        tnt.setFuse(fuseTicks + 40); // asli blast hum khud karte hain
        if (name != null && !name.isBlank()) {
            tnt.setCustomName(Component.literal(name));
            tnt.setCustomNameVisible(true);
        }
        lv.addFreshEntity(tnt);
        PENDING.add(new Pending(lv, tnt, fuseTicks, power));
    }

    public static void title(MinecraftServer s, String text, ChatFormatting color) {
        Component c = Component.literal(text).withStyle(color, ChatFormatting.BOLD);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(c));
        }
    }

    private static void actionBar(MinecraftServer s, String text) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) p.displayClientMessage(Component.literal(text), true);
    }

    private static void tick(MinecraftServer server) {
        boolean frameDirty = false;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.tnt.isRemoved()) { it.remove(); continue; }
            if (--p.ticks <= 0) {
                double x = p.tnt.getX(), y = p.tnt.getY(), z = p.tnt.getZ();
                p.tnt.discard();
                p.level.explode((Entity) null, x, y, z, p.power, Level.ExplosionInteraction.TNT);
                if (fireworks) p.level.sendParticles(ParticleTypes.FIREWORK, x, y + 1, z, 60, 1.0, 1.0, 1.0, 0.15);
                frameDirty = true;
                it.remove();
            }
        }
        if (frameDirty && protect && box != null) box.buildFrame();

        if (timerLeft > 0) {
            timerLeft--;
            if (timerLeft % 20 == 0) actionBar(server, "Time: " + (timerLeft / 20) + "s");
            if (timerLeft == 0) {
                timerLeft = -1;
                title(server, "WIN!", winColor);
                if (autoReset > 0) resetLeft = autoReset * 20;
            }
        }
        if (resetLeft > 0 && --resetLeft == 0) {
            resetLeft = -1;
            if (box != null) box.clearInterior();
        }
        if (autoSeconds > 0 && box != null && ++autoCounter >= autoSeconds * 20) {
            autoCounter = 0;
            dropTnt(null, 4f, 60);
        }
    }
}

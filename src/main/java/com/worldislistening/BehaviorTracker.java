package com.worldislistening;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.SaplingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.ActionResult;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.world.World;
import net.minecraft.world.LightType;
import java.util.HashMap;
import java.util.Map;

public final class BehaviorTracker {
    private BehaviorTracker() {}

    private static final Map<RegistryKey<World>, WorldCounters> COUNTERS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(BehaviorTracker::onWorldTick);

        ServerLivingEntityEvents.AFTER_DEATH.register(BehaviorTracker::onEntityDeath);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return ActionResult.PASS;
            }
            if (entity instanceof PassiveEntity || entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity) {
                WorldAwarenessState awareness = WorldAwarenessState.get(serverWorld);
                awareness.addAggression(WorldIsListeningConfig.AGGRESSION_PER_PASSIVE_HIT);
            }
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            WorldCounters counters = getCounters(serverWorld);
            counters.blocksBroken++;
            if (pos.getY() < 0) {
                counters.deepBlocksBroken++;
            }
            if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.SAPLINGS)) {
                counters.natureBlocksBroken++;
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return ActionResult.PASS;
            }
            if (!(player.getStackInHand(hand).getItem() instanceof BlockItem blockItem)) {
                return ActionResult.PASS;
            }

            WorldCounters counters = getCounters(serverWorld);
            counters.blocksPlaced++;

            WorldAwarenessState awareness = WorldAwarenessState.get(serverWorld);
            awareness.addDestruction(WorldIsListeningConfig.BUILDING_REDUCTION);
            awareness.addAggression(WorldIsListeningConfig.BUILDING_CALMING_AGGRESSION);

            if (blockItem.getBlock() instanceof SaplingBlock) {
                awareness.addDestruction(-WorldIsListeningConfig.TREE_PLANTING_REDUCTION);
            }

            return ActionResult.PASS;
        });
    }

    private static void onEntityDeath(Entity entity, DamageSource source) {
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof ServerPlayerEntity)) {
            return;
        }
        if (entity instanceof PassiveEntity || entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity) {
            WorldAwarenessState awareness = WorldAwarenessState.get(serverWorld);
            awareness.addAggression(WorldIsListeningConfig.AGGRESSION_PER_PASSIVE_KILL);
        }
    }

    private static void onWorldTick(ServerWorld world) {
        if (world.getServer().getTicks() % WorldIsListeningConfig.BEHAVIOR_TICK_INTERVAL != 0) {
            return;
        }

        WorldCounters counters = getCounters(world);
        WorldAwarenessState awareness = WorldAwarenessState.get(world);

        if (counters.blocksBroken > 0) {
            awareness.addDestruction(counters.blocksBroken * WorldIsListeningConfig.DESTRUCTION_PER_BLOCK_BROKEN);
        }
        if (counters.blocksPlaced > 0) {
            awareness.addDestruction(counters.blocksPlaced * WorldIsListeningConfig.DESTRUCTION_PER_BLOCK_PLACED);
        }
        if (counters.natureBlocksBroken > 0) {
            awareness.addDestruction(counters.natureBlocksBroken * WorldIsListeningConfig.DESTRUCTION_NATURE_BREAK_BONUS);
        }
        if (counters.blocksPlaced > counters.blocksBroken) {
            awareness.addDestruction(-WorldIsListeningConfig.DESTRUCTION_REPAIR_BONUS);
        }
        if (counters.deepBlocksBroken > 0) {
            awareness.addGreed(counters.deepBlocksBroken * WorldIsListeningConfig.GREED_PER_DEEP_BLOCK);
        }
        if (hasDeepPlayers(world)) {
            awareness.addGreed(WorldIsListeningConfig.GREED_PER_DEEP_PLAYER_INTERVAL);
        }
        if (hasDeepDarkPlayers(world)) {
            awareness.addGreed(WorldIsListeningConfig.GREED_DEEP_DARK_BONUS);
        }

        if (world.isNight() && hasAwakePlayers(world)) {
            awareness.addRestlessness(WorldIsListeningConfig.RESTLESSNESS_PER_NIGHT_INTERVAL);
        }
        if (world.isNight() && hasOutsidePlayers(world)) {
            awareness.addRestlessness(WorldIsListeningConfig.RESTLESSNESS_OUTSIDE_NIGHT_BONUS);
        }
        if (hasSleepingPlayers(world)) {
            awareness.addRestlessness(-WorldIsListeningConfig.RESTLESSNESS_SLEEP_REDUCTION);
        }

        awareness.decayAll(WorldIsListeningConfig.DECAY_PER_INTERVAL);

        counters.reset();
    }

    private static boolean hasAwakePlayers(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isSleeping()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSleepingPlayers(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSleeping()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDeepPlayers(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getBlockPos().getY() < 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDeepDarkPlayers(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getBlockPos().getY() < 0) {
                int light = world.getLightLevel(LightType.BLOCK, player.getBlockPos());
                if (light < 4) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasOutsidePlayers(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isSleeping() && world.isSkyVisible(player.getBlockPos())) {
                return true;
            }
        }
        return false;
    }

    private static WorldCounters getCounters(ServerWorld world) {
        return COUNTERS.computeIfAbsent(world.getRegistryKey(), key -> new WorldCounters());
    }

    private static final class WorldCounters {
        private int blocksBroken;
        private int blocksPlaced;
        private int deepBlocksBroken;
        private int natureBlocksBroken;

        private void reset() {
            blocksBroken = 0;
            blocksPlaced = 0;
            deepBlocksBroken = 0;
            natureBlocksBroken = 0;
        }
    }
}

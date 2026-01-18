package com.worldislistening;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Box;
import net.minecraft.registry.tag.BlockTags;

import java.util.List;
import java.util.UUID;

public final class WorldResponseHandler {
    private WorldResponseHandler() {}

    private static final UUID AGGRESSION_FOLLOW_RANGE_UUID = UUID.fromString("2b8c2c95-4e7d-4f7f-a34f-7e1f4f0e3cc3");
    private static final UUID AGGRESSION_SPEED_UUID = UUID.fromString("9f38bc29-7dd3-4a65-9e62-3f2d8d1b2a5b");

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(WorldResponseHandler::onWorldTick);
        ServerEntityEvents.ENTITY_LOAD.register(WorldResponseHandler::onEntityLoad);
    }

    private static void onEntityLoad(net.minecraft.entity.Entity entity, ServerWorld world) {
        if (!(entity instanceof HostileEntity hostile)) {
            return;
        }
        WorldAwarenessState awareness = WorldAwarenessState.get(world);
        float multiplier = getAggressionFollowRangeMultiplier(awareness.getAggressionScore());
        float speedMultiplier = getAggressionSpeedMultiplier(awareness.getAggressionScore());
        EntityAttributeInstance attribute = hostile.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(AGGRESSION_FOLLOW_RANGE_UUID);
        attribute.addPersistentModifier(new EntityAttributeModifier(
            AGGRESSION_FOLLOW_RANGE_UUID,
            "world_is_listening_aggression",
            multiplier - 1.0,
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        ));

        EntityAttributeInstance speedAttribute = hostile.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.removeModifier(AGGRESSION_SPEED_UUID);
            speedAttribute.addPersistentModifier(new EntityAttributeModifier(
                AGGRESSION_SPEED_UUID,
                "world_is_listening_aggression_speed",
                speedMultiplier - 1.0,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    private static void onWorldTick(ServerWorld world) {
        if (world.getServer().getTicks() % WorldIsListeningConfig.RESPONSE_TICK_INTERVAL != 0) {
            return;
        }
        WorldAwarenessState awareness = WorldAwarenessState.get(world);

        applyAggression(world, awareness);
        applyGreed(world, awareness);
        applyRestlessness(world, awareness);
        applyDestruction(world, awareness);
    }

    private static void applyAggression(ServerWorld world, WorldAwarenessState awareness) {
        float aggression = awareness.getAggressionScore();
        if (aggression <= 0.0f) {
            return;
        }
        Random random = world.getRandom();
        float chance = WorldIsListeningConfig.AMBIENT_CAVE_SOUND_BASE_CHANCE * (aggression / 100.0f);
        if (random.nextFloat() < chance) {
            List<ServerPlayerEntity> players = world.getPlayers();
            if (!players.isEmpty()) {
                ServerPlayerEntity player = players.get(random.nextInt(players.size()));
                world.playSound(null, player.getBlockPos(), SoundEvents.AMBIENT_CAVE.value(), SoundCategory.AMBIENT, 0.25f, 1.0f);
            }
        }

        refreshHostileFollowRange(world, aggression, random);
    }

    private static void applyGreed(ServerWorld world, WorldAwarenessState awareness) {
        float greed = awareness.getGreedScore();
        if (greed <= 0.0f) {
            return;
        }
        Random random = world.getRandom();
        float oreChance = WorldIsListeningConfig.ORE_DOWNGRADE_BASE_CHANCE * (greed / 100.0f);
        float caveDecayChance = WorldIsListeningConfig.CAVE_DECAY_BASE_CHANCE * (greed / 100.0f);

        for (int i = 0; i < WorldIsListeningConfig.RESPONSE_SAMPLE_ATTEMPTS; i++) {
            BlockPos pos = getRandomLoadedPosNearPlayer(world, random);
            if (pos == null) {
                return;
            }
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            if (random.nextFloat() < oreChance && isOreBlock(state)) {
                BlockState replacement = isDeepslateOre(block)
                    ? Blocks.DEEPSLATE.getDefaultState()
                    : Blocks.STONE.getDefaultState();
                world.setBlockState(pos, replacement, Block.NOTIFY_LISTENERS);
                continue;
            }

            if (random.nextFloat() < caveDecayChance && (block == Blocks.GLOW_LICHEN || block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static void applyRestlessness(ServerWorld world, WorldAwarenessState awareness) {
        float restlessness = awareness.getRestlessnessScore();
        if (restlessness <= 0.0f) {
            return;
        }
        if (world.isNight()) {
            long time = world.getTimeOfDay();
            long backtrack = MathHelper.clamp(Math.round(WorldIsListeningConfig.RESTLESSNESS_NIGHT_TIME_BACKTRACK * (restlessness / 100.0f)), 0, 4);
            if (backtrack > 0) {
                world.setTimeOfDay(time - backtrack);
            }
        }

        int extraRestTicks = MathHelper.floor(WorldIsListeningConfig.RESTLESSNESS_PHANTOM_EXTRA_TICKS * (restlessness / 100.0f));
        if (extraRestTicks <= 0) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isSleeping()) {
                player.getStatHandler().increaseStat(player, Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST), extraRestTicks);
            }
        }
    }

    private static void applyDestruction(ServerWorld world, WorldAwarenessState awareness) {
        float destruction = awareness.getDestructionScore();
        if (destruction <= 0.0f) {
            return;
        }
        Random random = world.getRandom();
        float grassChance = WorldIsListeningConfig.GRASS_DECAY_BASE_CHANCE * (destruction / 100.0f);
        float leafChance = WorldIsListeningConfig.LEAF_DECAY_BASE_CHANCE * (destruction / 100.0f);
        float cropChance = WorldIsListeningConfig.CROP_SLOW_BASE_CHANCE * (destruction / 100.0f);
        float farmlandChance = WorldIsListeningConfig.FARMLAND_DECAY_BASE_CHANCE * (destruction / 100.0f);
        float mossChance = WorldIsListeningConfig.MOSS_DECAY_BASE_CHANCE * (destruction / 100.0f);
        float plantChance = WorldIsListeningConfig.PLANT_WILT_BASE_CHANCE * (destruction / 100.0f);
        float coarseChance = WorldIsListeningConfig.COARSE_DIRT_BASE_CHANCE * (destruction / 100.0f);

        for (int i = 0; i < WorldIsListeningConfig.RESPONSE_SAMPLE_ATTEMPTS; i++) {
            BlockPos pos = getRandomLoadedPosNearPlayer(world, random);
            if (pos == null) {
                return;
            }
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            if (block == Blocks.GRASS_BLOCK && random.nextFloat() < grassChance) {
                world.setBlockState(pos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
                continue;
            }

            if (block == Blocks.FARMLAND && random.nextFloat() < farmlandChance) {
                world.setBlockState(pos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
                continue;
            }

            if (block == Blocks.MOSS_BLOCK && random.nextFloat() < mossChance) {
                world.setBlockState(pos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
                continue;
            }

            if (block == Blocks.DIRT && random.nextFloat() < coarseChance) {
                world.setBlockState(pos, Blocks.COARSE_DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
                continue;
            }

            if ((state.isIn(BlockTags.FLOWERS) || block == Blocks.GRASS || block == Blocks.FERN || block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN)
                && random.nextFloat() < plantChance) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                continue;
            }

            if (block instanceof LeavesBlock && random.nextFloat() < leafChance) {
                if (state.contains(LeavesBlock.PERSISTENT) && !state.get(LeavesBlock.PERSISTENT)) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    continue;
                }
            }

            if (block instanceof CropBlock && random.nextFloat() < cropChance) {
                int age = state.get(CropBlock.AGE);
                if (age > 0) {
                    world.setBlockState(pos, state.with(CropBlock.AGE, age - 1), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private static void refreshHostileFollowRange(ServerWorld world, float aggression, Random random) {
        float multiplier = getAggressionFollowRangeMultiplier(aggression);
        float speedMultiplier = getAggressionSpeedMultiplier(aggression);
        List<ServerPlayerEntity> players = world.getPlayers();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayerEntity player = players.get(random.nextInt(players.size()));
        Box box = new Box(player.getBlockPos()).expand(64.0);
        List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, box, entity -> true);
        if (hostiles.isEmpty()) {
            return;
        }
        int samples = Math.min(WorldIsListeningConfig.AGGRESSION_FOLLOW_RANGE_REFRESH_SAMPLES, hostiles.size());
        int speedSamples = Math.min(WorldIsListeningConfig.AGGRESSION_SPEED_REFRESH_SAMPLES, hostiles.size());
        for (int i = 0; i < samples; i++) {
            HostileEntity hostile = hostiles.get(random.nextInt(hostiles.size()));
            EntityAttributeInstance attribute = hostile.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            if (attribute == null) {
                continue;
            }
            attribute.removeModifier(AGGRESSION_FOLLOW_RANGE_UUID);
            attribute.addPersistentModifier(new EntityAttributeModifier(
                AGGRESSION_FOLLOW_RANGE_UUID,
                "world_is_listening_aggression",
                multiplier - 1.0,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }

        for (int i = 0; i < speedSamples; i++) {
            HostileEntity hostile = hostiles.get(random.nextInt(hostiles.size()));
            EntityAttributeInstance speedAttribute = hostile.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speedAttribute == null) {
                continue;
            }
            speedAttribute.removeModifier(AGGRESSION_SPEED_UUID);
            speedAttribute.addPersistentModifier(new EntityAttributeModifier(
                AGGRESSION_SPEED_UUID,
                "world_is_listening_aggression_speed",
                speedMultiplier - 1.0,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    private static BlockPos getRandomLoadedPosNearPlayer(ServerWorld world, Random random) {
        List<ServerPlayerEntity> players = world.getPlayers();
        if (players.isEmpty()) {
            return null;
        }
        ServerPlayerEntity player = players.get(random.nextInt(players.size()));
        BlockPos base = player.getBlockPos();
        int dx = random.nextInt(64) - 32;
        int dy = random.nextInt(40) - 20;
        int dz = random.nextInt(64) - 32;
        BlockPos pos = base.add(dx, dy, dz);
        if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return null;
        }
        return pos;
    }

    private static boolean isOreBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.COAL_ORE
            || block == Blocks.IRON_ORE
            || block == Blocks.COPPER_ORE
            || block == Blocks.GOLD_ORE
            || block == Blocks.REDSTONE_ORE
            || block == Blocks.LAPIS_ORE
            || block == Blocks.DIAMOND_ORE
            || block == Blocks.EMERALD_ORE
            || block == Blocks.DEEPSLATE_COAL_ORE
            || block == Blocks.DEEPSLATE_IRON_ORE
            || block == Blocks.DEEPSLATE_COPPER_ORE
            || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.DEEPSLATE_REDSTONE_ORE
            || block == Blocks.DEEPSLATE_LAPIS_ORE
            || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.DEEPSLATE_EMERALD_ORE
            || block == Blocks.NETHER_GOLD_ORE
            || block == Blocks.NETHER_QUARTZ_ORE;
    }

    private static boolean isDeepslateOre(Block block) {
        return block == Blocks.DEEPSLATE_COAL_ORE
            || block == Blocks.DEEPSLATE_IRON_ORE
            || block == Blocks.DEEPSLATE_COPPER_ORE
            || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.DEEPSLATE_REDSTONE_ORE
            || block == Blocks.DEEPSLATE_LAPIS_ORE
            || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.DEEPSLATE_EMERALD_ORE;
    }

    private static float getAggressionFollowRangeMultiplier(float aggressionScore) {
        float t = MathHelper.clamp(aggressionScore / 100.0f, 0.0f, 1.0f);
        return MathHelper.lerp(t, 1.0f, WorldIsListeningConfig.MAX_FOLLOW_RANGE_MULTIPLIER);
    }

    private static float getAggressionSpeedMultiplier(float aggressionScore) {
        float t = MathHelper.clamp(aggressionScore / 100.0f, 0.0f, 1.0f);
        return MathHelper.lerp(t, 1.0f, WorldIsListeningConfig.MAX_HOSTILE_SPEED_MULTIPLIER);
    }
}
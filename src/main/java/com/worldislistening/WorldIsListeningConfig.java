package com.worldislistening;

public final class WorldIsListeningConfig {
    private WorldIsListeningConfig() {}

    public static final int BEHAVIOR_TICK_INTERVAL = 200; // 10s
    public static final int RESPONSE_TICK_INTERVAL = 200; // 10s

    public static final float DECAY_PER_INTERVAL = 0.02f;

    public static final float AGGRESSION_PER_PASSIVE_KILL = 0.8f;
    public static final float AGGRESSION_PER_PASSIVE_HIT = 0.05f;
    public static final float DESTRUCTION_PER_BLOCK_BROKEN = 0.01f;
    public static final float DESTRUCTION_PER_BLOCK_PLACED = -0.005f;
    public static final float DESTRUCTION_NATURE_BREAK_BONUS = 0.02f;
    public static final float GREED_PER_DEEP_BLOCK = 0.02f;
    public static final float GREED_PER_DEEP_PLAYER_INTERVAL = 0.15f;
    public static final float GREED_DEEP_DARK_BONUS = 0.2f;
    public static final float RESTLESSNESS_PER_NIGHT_INTERVAL = 0.08f;
    public static final float RESTLESSNESS_OUTSIDE_NIGHT_BONUS = 0.1f;

    public static final float RESTLESSNESS_SLEEP_REDUCTION = 0.4f;
    public static final float TREE_PLANTING_REDUCTION = 0.6f;
    public static final float BUILDING_REDUCTION = 0.02f;
    public static final float BUILDING_CALMING_AGGRESSION = 0.01f;
    public static final float DESTRUCTION_REPAIR_BONUS = 0.06f;

    public static final float MAX_FOLLOW_RANGE_MULTIPLIER = 1.05f;
    public static final float MAX_HOSTILE_SPEED_MULTIPLIER = 1.03f;
    public static final float AMBIENT_CAVE_SOUND_BASE_CHANCE = 0.005f;
    public static final int AGGRESSION_FOLLOW_RANGE_REFRESH_SAMPLES = 8;
    public static final int AGGRESSION_SPEED_REFRESH_SAMPLES = 6;

    public static final int RESPONSE_SAMPLE_ATTEMPTS = 8;

    public static final float ORE_DOWNGRADE_BASE_CHANCE = 0.01f;
    public static final float CAVE_DECAY_BASE_CHANCE = 0.01f;
    public static final float GRASS_DECAY_BASE_CHANCE = 0.01f;
    public static final float LEAF_DECAY_BASE_CHANCE = 0.02f;
    public static final float CROP_SLOW_BASE_CHANCE = 0.02f;
    public static final float FARMLAND_DECAY_BASE_CHANCE = 0.01f;
    public static final float MOSS_DECAY_BASE_CHANCE = 0.01f;
    public static final float PLANT_WILT_BASE_CHANCE = 0.015f;
    public static final float COARSE_DIRT_BASE_CHANCE = 0.01f;

    public static final int RESTLESSNESS_NIGHT_TIME_BACKTRACK = 2;
    public static final int RESTLESSNESS_PHANTOM_EXTRA_TICKS = 60;
}

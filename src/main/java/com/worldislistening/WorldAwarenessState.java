package com.worldislistening;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public class WorldAwarenessState extends PersistentState {
    private static final String STATE_KEY = WorldIsListeningMod.MOD_ID + "_awareness";

    private float aggressionScore;
    private float greedScore;
    private float restlessnessScore;
    private float destructionScore;

    public WorldAwarenessState() {
    }

    public static WorldAwarenessState get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(WorldAwarenessState::fromNbt, WorldAwarenessState::new, STATE_KEY);
    }

    public static WorldAwarenessState fromNbt(NbtCompound nbt) {
        WorldAwarenessState state = new WorldAwarenessState();
        state.aggressionScore = nbt.getFloat("aggressionScore");
        state.greedScore = nbt.getFloat("greedScore");
        state.restlessnessScore = nbt.getFloat("restlessnessScore");
        state.destructionScore = nbt.getFloat("destructionScore");
        state.clampAll();
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putFloat("aggressionScore", aggressionScore);
        nbt.putFloat("greedScore", greedScore);
        nbt.putFloat("restlessnessScore", restlessnessScore);
        nbt.putFloat("destructionScore", destructionScore);
        return nbt;
    }

    public float getAggressionScore() {
        return aggressionScore;
    }

    public float getGreedScore() {
        return greedScore;
    }

    public float getRestlessnessScore() {
        return restlessnessScore;
    }

    public float getDestructionScore() {
        return destructionScore;
    }

    public void addAggression(float amount) {
        aggressionScore = clamp(aggressionScore + amount);
        markDirty();
    }

    public void addGreed(float amount) {
        greedScore = clamp(greedScore + amount);
        markDirty();
    }

    public void addRestlessness(float amount) {
        restlessnessScore = clamp(restlessnessScore + amount);
        markDirty();
    }

    public void addDestruction(float amount) {
        destructionScore = clamp(destructionScore + amount);
        markDirty();
    }

    public void decayAll(float amount) {
        aggressionScore = clamp(aggressionScore - amount);
        greedScore = clamp(greedScore - amount);
        restlessnessScore = clamp(restlessnessScore - amount);
        destructionScore = clamp(destructionScore - amount);
        markDirty();
    }

    private void clampAll() {
        aggressionScore = clamp(aggressionScore);
        greedScore = clamp(greedScore);
        restlessnessScore = clamp(restlessnessScore);
        destructionScore = clamp(destructionScore);
    }

    private static float clamp(float value) {
        return MathHelper.clamp(value, 0.0f, 100.0f);
    }
}

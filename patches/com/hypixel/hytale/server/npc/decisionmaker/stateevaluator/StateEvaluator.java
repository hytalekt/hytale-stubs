package com.hypixel.hytale.server.npc.decisionmaker.stateevaluator;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.StateMappingHelper;
import com.hypixel.hytale.server.npc.decisionmaker.core.EvaluationContext;
import com.hypixel.hytale.server.npc.decisionmaker.core.Evaluator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import javax.annotation.Nonnull;
import io.github.hytalekt.stubs.GeneratedStubException;

public class StateEvaluator extends Evaluator<StateOption> implements Component<EntityStore> {

    public static final BuilderCodec<StateEvaluator> CODEC = null;

    protected StateOption[] rawOptions = null;

    protected double executeFrequency = 0.0;

    protected double stateChangeCooldown = 0.0;

    protected double minimumUtility = 0.0;

    private double timeUntilNextExecute = 0.0;

    private boolean active = false;

    private final EvaluationContext evaluationContext = null;

    public static ComponentType<EntityStore, StateEvaluator> getComponentType() {
        throw new GeneratedStubException();
    }

    protected StateEvaluator() {
        super();
        throw new GeneratedStubException();
    }

    public boolean isActive() {
        throw new GeneratedStubException();
    }

    public void setActive(boolean active) {
        throw new GeneratedStubException();
    }

    @Nonnull
    public EvaluationContext getEvaluationContext() {
        throw new GeneratedStubException();
    }

    public void prepareOptions(@Nonnull StateMappingHelper stateHelper) {
        throw new GeneratedStubException();
    }

    public boolean shouldExecute(double interval) {
        throw new GeneratedStubException();
    }

    public void prepareEvaluationContext(@Nonnull EvaluationContext context) {
        throw new GeneratedStubException();
    }

    public void onStateSwitched() {
        throw new GeneratedStubException();
    }

    @Nonnull
    @Override
    public String toString() {
        throw new GeneratedStubException();
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        throw new GeneratedStubException();
    }

    public class SelfOptionHolder extends Evaluator<StateOption>.OptionHolder {

        public SelfOptionHolder(final StateEvaluator param1, StateOption param2) {
            super((StateOption) null);
            throw new GeneratedStubException();
        }

        @Override
        public double calculateUtility(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer, @Nonnull EvaluationContext context) {
            throw new GeneratedStubException();
        }
    }
}

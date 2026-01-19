package com.hypixel.hytale.builtin.npccombatactionevaluator.evaluator;

import com.hypixel.hytale.builtin.npccombatactionevaluator.CombatActionEvaluatorSystems;
import com.hypixel.hytale.builtin.npccombatactionevaluator.NPCCombatActionEvaluatorPlugin;
import com.hypixel.hytale.builtin.npccombatactionevaluator.evaluator.combatactions.CombatActionOption;
import com.hypixel.hytale.builtin.npccombatactionevaluator.memory.TargetMemory;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.random.RandomExtra;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.StateMappingHelper;
import com.hypixel.hytale.server.npc.decisionmaker.core.EvaluationContext;
import com.hypixel.hytale.server.npc.decisionmaker.core.Evaluator;
import com.hypixel.hytale.server.npc.decisionmaker.core.Option;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.valuestore.ValueStore;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import io.github.hytalekt.stubs.GeneratedStubException;

public class CombatActionEvaluator extends Evaluator<CombatActionOption> implements Component<EntityStore> {

    protected static final float NO_TIMEOUT = 0.0f;

    protected CombatActionEvaluator.RunOption runOption = null;

    protected double minRunUtility = 0.0;

    protected long lastRunNanos = 0L;

    protected int runInState = 0;

    protected float predictability = 0.0f;

    protected double minActionUtility = 0.0;

    protected final Int2ObjectMap<List<Evaluator<CombatActionOption>.OptionHolder>> optionsBySubState = null;

    protected final Int2ObjectMap<CombatActionEvaluatorConfig.BasicAttacks> basicAttacksBySubState = null;

    protected int currentBasicAttackSubState = 0;

    protected CombatActionEvaluatorConfig.BasicAttacks currentBasicAttackSet = null;

    @Nullable
    protected String currentBasicAttack = null;

    protected Function<InteractionContext, Map<String, String>> currentBasicAttacksInteractionVarsGetter = null;

    protected boolean currentBasicAttackDamageFriendlies = false;

    protected int nextBasicAttackIndex = 0;

    protected double basicAttackCooldown = 0.0;

    @Nullable
    protected Ref<EntityStore> basicAttackTarget = null;

    protected double basicAttackTimeout = 0.0;

    @Nullable
    protected Ref<EntityStore> primaryTarget = null;

    @Nullable
    protected Ref<EntityStore> previousTarget = null;

    @Nullable
    protected CombatActionEvaluator.CombatOptionHolder currentAction = null;

    @Nullable
    protected double[] postExecutionDistanceRange = null;

    protected int markedTargetSlot = 0;

    protected int minRangeSlot = 0;

    protected int maxRangeSlot = 0;

    protected int positioningAngleSlot = 0;

    @Nullable
    protected String currentInteraction = null;

    protected Function<InteractionContext, Map<String, String>> currentInteractionVarsGetter = null;

    protected InteractionType currentInteractionType = null;

    protected float chargeFor = 0.0f;

    protected boolean currentDamageFriendlies = false;

    protected boolean requireAiming = false;

    protected boolean positionFirst = false;

    protected double chargeDistance = 0.0;

    protected float timeout = 0.0f;

    protected final EvaluationContext evaluationContext = null;

    public static ComponentType<EntityStore, CombatActionEvaluator> getComponentType() {
        throw new GeneratedStubException();
    }

    public CombatActionEvaluator(@Nonnull Role role, @Nonnull CombatActionEvaluatorConfig config, @Nonnull CombatActionEvaluatorSystems.CombatConstructionData data) {
        super();
        throw new GeneratedStubException();
    }

    protected CombatActionEvaluator() {
        super();
        throw new GeneratedStubException();
    }

    public CombatActionEvaluator.RunOption getRunOption() {
        throw new GeneratedStubException();
    }

    public double getMinRunUtility() {
        throw new GeneratedStubException();
    }

    @Nonnull
    public EvaluationContext getEvaluationContext() {
        throw new GeneratedStubException();
    }

    public long getLastRunNanos() {
        throw new GeneratedStubException();
    }

    public void setLastRunNanos(long lastRunNanos) {
        throw new GeneratedStubException();
    }

    public int getRunInState() {
        throw new GeneratedStubException();
    }

    @Nonnull
    public Int2ObjectMap<List<Evaluator<CombatActionOption>.OptionHolder>> getOptionsBySubState() {
        throw new GeneratedStubException();
    }

    public CombatActionEvaluatorConfig.BasicAttacks getBasicAttacks(int subState) {
        throw new GeneratedStubException();
    }

    public void setCurrentBasicAttackSet(int subState, CombatActionEvaluatorConfig.BasicAttacks attacks) {
        throw new GeneratedStubException();
    }

    @Nullable
    public String getCurrentBasicAttack() {
        throw new GeneratedStubException();
    }

    public CombatActionEvaluatorConfig.BasicAttacks getCurrentBasicAttackSet() {
        throw new GeneratedStubException();
    }

    public void setCurrentBasicAttack(String attack, boolean damageFriendlies, Function<InteractionContext, Map<String, String>> interactionVarsGetter) {
        throw new GeneratedStubException();
    }

    public int getNextBasicAttackIndex() {
        throw new GeneratedStubException();
    }

    public void setNextBasicAttackIndex(int next) {
        throw new GeneratedStubException();
    }

    public boolean canUseBasicAttack(int selfIndex, ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) {
        throw new GeneratedStubException();
    }

    public void tickBasicAttackCoolDown(float dt) {
        throw new GeneratedStubException();
    }

    @Nullable
    public Ref<EntityStore> getBasicAttackTarget() {
        throw new GeneratedStubException();
    }

    public void setBasicAttackTarget(Ref<EntityStore> target) {
        throw new GeneratedStubException();
    }

    public boolean tickBasicAttackTimeout(float dt) {
        throw new GeneratedStubException();
    }

    public void setBasicAttackTimeout(double timeout) {
        throw new GeneratedStubException();
    }

    @Nullable
    public Ref<EntityStore> getPrimaryTarget() {
        throw new GeneratedStubException();
    }

    public void clearPrimaryTarget() {
        throw new GeneratedStubException();
    }

    public void setActiveOptions(List<Evaluator<CombatActionOption>.OptionHolder> options) {
        throw new GeneratedStubException();
    }

    public int getMarkedTargetSlot() {
        throw new GeneratedStubException();
    }

    public int getMaxRangeSlot() {
        throw new GeneratedStubException();
    }

    public int getMinRangeSlot() {
        throw new GeneratedStubException();
    }

    public int getPositioningAngleSlot() {
        throw new GeneratedStubException();
    }

    @Nullable
    public String getCurrentAttack() {
        throw new GeneratedStubException();
    }

    public float getChargeFor() {
        throw new GeneratedStubException();
    }

    public InteractionType getCurrentInteractionType() {
        throw new GeneratedStubException();
    }

    public Function<InteractionContext, Map<String, String>> getCurrentInteractionVarsGetter() {
        throw new GeneratedStubException();
    }

    public boolean shouldDamageFriendlies() {
        throw new GeneratedStubException();
    }

    public boolean requiresAiming() {
        throw new GeneratedStubException();
    }

    public boolean shouldPositionFirst() {
        throw new GeneratedStubException();
    }

    public double getChargeDistance() {
        throw new GeneratedStubException();
    }

    public void setCurrentInteraction(String currentInteraction, InteractionType interactionType, float chargeFor, boolean damageFriendlies, boolean requireAiming, boolean positionFirst, double chargeDistance, Function<InteractionContext, Map<String, String>> interactionVarsGetter) {
        throw new GeneratedStubException();
    }

    @Nullable
    public CombatActionEvaluator.CombatOptionHolder getCurrentAction() {
        throw new GeneratedStubException();
    }

    public double[] consumePostExecutionDistanceRange() {
        throw new GeneratedStubException();
    }

    public void setTimeout(float timeout) {
        throw new GeneratedStubException();
    }

    public void clearTimeout() {
        throw new GeneratedStubException();
    }

    public boolean hasTimedOut(float dt) {
        throw new GeneratedStubException();
    }

    public void selectNextCombatAction(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer, @Nonnull Role role, ValueStore valueStore) {
        throw new GeneratedStubException();
    }

    public void completeCurrentAction(boolean forceClearAbility, boolean clearBasicAttack) {
        throw new GeneratedStubException();
    }

    public void terminateCurrentAction() {
        throw new GeneratedStubException();
    }

    public void clearCurrentBasicAttack() {
        throw new GeneratedStubException();
    }

    @Override
    public void setupNPC(Role role) {
        throw new GeneratedStubException();
    }

    @Override
    public void setupNPC(Holder<EntityStore> holder) {
        throw new GeneratedStubException();
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        throw new GeneratedStubException();
    }

    public abstract class CombatOptionHolder extends Evaluator<CombatActionOption>.OptionHolder {

        protected long lastUsedNanos = 0L;

        protected CombatOptionHolder(final CombatActionEvaluator param1, CombatActionOption param2) {
            super((CombatActionOption) null);
            throw new GeneratedStubException();
        }

        public void setLastUsedNanos(long lastUsedNanos) {
            throw new GeneratedStubException();
        }

        @Nullable
        public Ref<EntityStore> getOptionTarget() {
            throw new GeneratedStubException();
        }
    }

    public class MultipleTargetCombatOptionHolder extends CombatActionEvaluator.CombatOptionHolder {

        protected List<Ref<EntityStore>> targets = null;

        protected final DoubleList targetUtilities = null;

        @Nullable
        protected Ref<EntityStore> pickedTarget = null;

        protected MultipleTargetCombatOptionHolder(CombatActionOption option) {
            super((CombatActionEvaluator) null, (CombatActionOption) null);
            throw new GeneratedStubException();
        }

        @Override
        public double calculateUtility(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer, @Nonnull EvaluationContext context) {
            throw new GeneratedStubException();
        }

        @Override
        public double getTotalUtility(double threshold) {
            throw new GeneratedStubException();
        }

        @Override
        public double tryPick(double currentWeight, double threshold) {
            throw new GeneratedStubException();
        }

        @Override
        public Ref<EntityStore> getOptionTarget() {
            throw new GeneratedStubException();
        }
    }

    public static class RunOption extends Option {

        protected RunOption(String[] conditions) {
            super();
            throw new GeneratedStubException();
        }
    }

    public class SelfCombatOptionHolder extends CombatActionEvaluator.CombatOptionHolder {

        protected SelfCombatOptionHolder(CombatActionOption option) {
            super((CombatActionEvaluator) null, (CombatActionOption) null);
            throw new GeneratedStubException();
        }

        @Override
        public double calculateUtility(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer, @Nonnull EvaluationContext context) {
            throw new GeneratedStubException();
        }
    }
}

package net.pedito.mod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Front-guard formation: a fixed number of "slots" that stand between the
 * owner and whatever looks dangerous nearby, screening it defensively
 * through positioning alone — no projectile redirection, no mixins, just
 * movement.
 *
 * Each tick, both slots check for the nearest hostile (anything
 * implementing Enemy) within THREAT_SCAN_RADIUS of the owner:
 *   - Threat found: the two guards stand on the owner-to-threat line, offset
 *     to either side of it, facing the threat. This reads as "stepping up
 *     to block" without any actual collision/interception logic.
 *   - No threat: guards fall back to a calm flank stance, offset laterally
 *     from the direction the owner is looking, so they aren't just glued
 *     to one spot when nothing is happening.
 *
 * A slot is NOT tied to a specific entity. Every tick, whichever eligible
 * pedito is "best" (see isBestCandidateForSlot) claims any open slot. If
 * the current occupant dies, is unsummoned, changes owner, etc., its slot
 * opens up immediately and the next-best candidate from the rest of the
 * swarm (i.e. the rear semicircle handled by PeditoFormationGoal) takes
 * over on the next tick.
 */
public class PeditoFrontGuardGoal extends Goal {

    private static final int GUARD_SLOT_COUNT = 2;
    private static final double GUARD_ANGLE_OFFSET_DEG = 35.0;
    private static final double GUARD_RADIUS = 2.5;
    private static final double MAX_DISTANCE_SQR = 225.0D;
    private static final double THREAT_SCAN_RADIUS = 16.0D;

    // owner UUID -> fixed-size slot array. null entry = empty/open slot.
    private static final Map<UUID, PeditoEntity[]> GUARD_SLOTS = new ConcurrentHashMap<>();

    private final PeditoEntity pedito;
    private int mySlot = -1;

    private double hoverX;
    private double hoverY;
    private double hoverZ;

    public PeditoFrontGuardGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * Used by PeditoFormationGoal (and anything else) to check whether a
     * given pedito currently holds a front-guard slot, so it can be
     * excluded from the rear semicircle math.
     */
    public static boolean isFrontGuard(PeditoEntity pedito) {
        Player owner = pedito.getOwnerCustom();
        if (owner == null) return false;
        PeditoEntity[] slots = GUARD_SLOTS.get(owner.getUUID());
        if (slots == null) return false;
        for (PeditoEntity occupant : slots) {
            if (occupant == pedito) return true;
        }
        return false;
    }

    private static PeditoEntity[] slotsFor(Player owner) {
        return GUARD_SLOTS.computeIfAbsent(owner.getUUID(), id -> new PeditoEntity[GUARD_SLOT_COUNT]);
    }

    @Override
    public boolean canUse() {
        if (!this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()) return false;
        if (this.pedito.isOwnerInDanger()) return false;

        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return false;
        if (this.pedito.distanceToSqr(owner) > MAX_DISTANCE_SQR) return false;

        return this.holdOrClaimSlot(owner);
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.pedito.isTamedByOwner() || this.pedito.isSittingCustom() || !this.pedito.isAlive()) return false;
        if (this.pedito.isOwnerInDanger()) return false;

        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return false;
        if (this.pedito.distanceToSqr(owner) > MAX_DISTANCE_SQR) return false;

        PeditoEntity[] slots = slotsFor(owner);
        return this.mySlot != -1 && slots[this.mySlot] == this.pedito;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        Player owner = this.pedito.getOwnerCustom();
        if (owner != null && this.mySlot != -1) {
            PeditoEntity[] slots = GUARD_SLOTS.get(owner.getUUID());
            if (slots != null && slots[this.mySlot] == this.pedito) {
                slots[this.mySlot] = null; // free the slot for the next candidate
            }
        }
        this.mySlot = -1;
    }

    /**
     * Keeps the slot this pedito already holds (if any), otherwise tries to
     * claim any slot that is empty or whose current occupant is no longer
     * eligible (dead, resummoned, changed owner, etc.).
     */
    private boolean holdOrClaimSlot(Player owner) {
        PeditoEntity[] slots = slotsFor(owner);

        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == this.pedito) {
                this.mySlot = i;
                return true;
            }
        }

        for (int i = 0; i < slots.length; i++) {
            PeditoEntity occupant = slots[i];
            boolean slotOpen = occupant == null
                    || !occupant.isAlive()
                    || !occupant.isTamedByOwner()
                    || occupant.getOwnerCustom() != owner;

            if (slotOpen && this.isBestCandidateForSlot(owner, slots)) {
                slots[i] = this.pedito;
                this.mySlot = i;
                return true;
            }
        }

        this.mySlot = -1;
        return false;
    }

    /**
     * Among all currently eligible peditos that aren't already guarding a
     * slot, only the "best" one may claim an open slot this tick — this is
     * what makes substitution automatic: when a guard dies, its slot opens,
     * and whichever remaining pedito ranks highest steps up next tick.
     *
     * Ranking mirrors the same score PeditoEntity.updateSquadRole() already
     * uses for Vanguardia/Táctico/Artillería (tier * 1000 + health * 10),
     * so a Pedito's odds of becoming a front guard are consistent with its
     * odds of being Vanguardia elsewhere in the swarm — higher tier first,
     * health as tiebreaker, entity id as final tiebreaker for determinism.
     */
    private boolean isBestCandidateForSlot(Player owner, PeditoEntity[] slots) {
        List<PeditoEntity> candidates = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                owner.getBoundingBox().inflate(64.0D),
                e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner && !isAlreadyGuard(e, slots)
        );

        candidates.sort((p1, p2) -> {
            int score1 = p1.getTier() * 1000 + (int) (p1.getHealth() * 10);
            int score2 = p2.getTier() * 1000 + (int) (p2.getHealth() * 10);
            if (score1 != score2) return Integer.compare(score2, score1);
            return Integer.compare(p1.getId(), p2.getId());
        });

        return !candidates.isEmpty() && candidates.get(0) == this.pedito;
    }

    private static boolean isAlreadyGuard(PeditoEntity pedito, PeditoEntity[] slots) {
        for (PeditoEntity occupant : slots) {
            if (occupant == pedito) return true;
        }
        return false;
    }

    @Override
    public void tick() {
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null || this.mySlot == -1) return;

        double time = this.pedito.tickCount * 0.05;
        double sideSign = (this.mySlot == 0) ? -1.0 : 1.0;

        LivingEntity threat = this.findNearestThreat(owner);
        double baseAngle;
        double lookAtX;
        double lookAtZ;

        if (threat != null) {
            // Stand on the owner-to-threat line, offset to either side of
            // it, so the pair "brackets" whatever is approaching.
            baseAngle = Math.atan2(threat.getX() - owner.getX(), -(threat.getZ() - owner.getZ()));
            lookAtX = threat.getX();
            lookAtZ = threat.getZ();
        } else {
            // Nothing nearby: calm flank stance relative to where the
            // owner is looking, so guards aren't just glued in place.
            baseAngle = Math.toRadians(owner.getYRot());
            lookAtX = owner.getX() + Math.sin(baseAngle) * 10.0;
            lookAtZ = owner.getZ() - Math.cos(baseAngle) * 10.0;
        }

        double guardAngle = baseAngle + Math.toRadians(GUARD_ANGLE_OFFSET_DEG * sideSign);

        double offsetX = Math.sin(guardAngle) * GUARD_RADIUS;
        double offsetZ = -Math.cos(guardAngle) * GUARD_RADIUS;
        double offsetY = 1.0 + Math.sin(time * 1.5 + this.mySlot) * 0.2;

        this.hoverX = owner.getX() + offsetX;
        this.hoverY = owner.getY() + offsetY;
        this.hoverZ = owner.getZ() + offsetZ;

        this.pedito.getLookControl().setLookAt(lookAtX, owner.getY(), lookAtZ, 10.0F, 10.0F);

        BlockPos targetPos = BlockPos.containing(this.hoverX, this.hoverY, this.hoverZ);
        int adjustmentCount = 0;
        while (this.pedito.isSolidBlock(targetPos) && adjustmentCount < 5) {
            this.hoverY += 1.0;
            targetPos = BlockPos.containing(this.hoverX, this.hoverY, this.hoverZ);
            adjustmentCount++;
        }

        if (this.pedito.distanceToSqr(this.hoverX, this.hoverY, this.hoverZ) > 0.25) {
            // Slightly faster than the rear escort (1.2 vs 1.0) so a
            // newly-promoted guard snaps into position quickly, and so it
            // can keep up when repositioning to track a moving threat.
            this.pedito.getMoveControl().setWantedPosition(this.hoverX, this.hoverY, this.hoverZ, 1.2D);
        }
    }

    /**
     * Nearest hostile (anything implementing Enemy) near the owner, or null
     * if the area is clear. Pure positioning input — no combat/interception
     * logic reads or writes anything based on this beyond "which way do the
     * guards face and stand".
     */
    private LivingEntity findNearestThreat(Player owner) {
        List<LivingEntity> threats = this.pedito.level().getEntitiesOfClass(
                LivingEntity.class,
                owner.getBoundingBox().inflate(THREAT_SCAN_RADIUS),
                e -> e instanceof Enemy && e.isAlive()
        );

        LivingEntity nearest = null;
        double nearestDistSqr = Double.MAX_VALUE;
        for (LivingEntity candidate : threats) {
            double distSqr = candidate.distanceToSqr(owner);
            if (distSqr < nearestDistSqr) {
                nearestDistSqr = distSqr;
                nearest = candidate;
            }
        }
        return nearest;
    }
}

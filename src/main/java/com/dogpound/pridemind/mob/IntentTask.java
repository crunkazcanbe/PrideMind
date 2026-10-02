package com.dogpound.pridemind.mob;

import java.util.List;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;

/**
 * Carries out the brain's decision with vanilla movement. Only runs while a moving decision (stalk, flee, group up) is
 * fresh; the rest of the time the mob's own AI runs untouched. ATTACK / CALL_HELP / IGNORE just set or clear the
 * attack target once and let the mob's own attack AI do the fighting.
 */
final class IntentTask extends EntityAIBase {
    private final EntityCreature mob;
    private MobBrains.Thought applied;
    private int repath;

    IntentTask(EntityCreature mob) { this.mob = mob; setMutexBits(1); }

    @Override
    public boolean shouldExecute() {
        MobBrains.Thought t = MobBrains.current(mob);
        if (t == null) return false;
        if (t != applied) { applied = t; firstApply(t); }
        return t.intent.moves() && t.target(mob.world) != null;
    }

    @Override public boolean shouldContinueExecuting() { return shouldExecute(); }

    @Override public void startExecuting() { repath = 0; }

    @Override public void resetTask() { mob.getNavigator().clearPath(); }

    private void firstApply(MobBrains.Thought t) {
        EntityPlayer p = t.target(mob.world);
        switch (t.intent) {
            case ATTACK: if (p != null && !p.isCreative() && !p.isSpectator()) mob.setAttackTarget(p); break;
            case CALL_HELP:
                if (p == null || p.isCreative() || p.isSpectator()) break;
                mob.setAttackTarget(p);
                for (EntityCreature o : same(24)) if (o.getAttackTarget() == null) o.setAttackTarget(p);
                break;
            case STALK: case FLEE: case IGNORE:
                if (mob.getAttackTarget() instanceof EntityPlayer) mob.setAttackTarget(null);
                break;
            default:
        }
    }

    @Override
    public void updateTask() {
        MobBrains.Thought t = applied;
        EntityPlayer p = t == null ? null : t.target(mob.world);
        if (p == null || --repath > 0) return;
        repath = 10;
        Vec3d away = new Vec3d(p.posX, p.posY, p.posZ);
        switch (t.intent) {
            case FLEE: {
                Vec3d v = RandomPositionGenerator.findRandomTargetBlockAwayFrom(mob, 16, 7, away);
                if (v != null) mob.getNavigator().tryMoveToXYZ(v.x, v.y, v.z, 1.3);
                break;
            }
            case STALK: {
                double d = mob.getDistance(p);
                mob.getLookHelper().setLookPositionWithEntity(p, 30f, 30f);
                if (d < 8) {
                    Vec3d v = RandomPositionGenerator.findRandomTargetBlockAwayFrom(mob, 8, 4, away);
                    if (v != null) mob.getNavigator().tryMoveToXYZ(v.x, v.y, v.z, 1.0);
                } else if (d > 14) mob.getNavigator().tryMoveToEntityLiving(p, 0.9);
                else mob.getNavigator().clearPath();                      // keep watching from a distance
                break;
            }
            case GROUP_UP: {
                List<EntityCreature> g = same(24);
                if (g.isEmpty()) break;
                double x = 0, y = 0, z = 0;
                for (EntityLivingBase o : g) { x += o.posX; y += o.posY; z += o.posZ; }
                mob.getNavigator().tryMoveToXYZ(x / g.size(), y / g.size(), z / g.size(), 1.1);
                break;
            }
            default:
        }
    }

    private List<EntityCreature> same(double r) {
        return mob.world.getEntitiesWithinAABB(EntityCreature.class, mob.getEntityBoundingBox().grow(r),
                o -> o != mob && o.getClass() == mob.getClass() && o.isEntityAlive());
    }
}

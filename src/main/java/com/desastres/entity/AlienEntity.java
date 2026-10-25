package com.desastres.entity;

import com.desastres.config.DisasterConfig;
import com.desastres.disaster.LaserBeams;
import com.desastres.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Alienigena hostil. Dispara rayos laser magenta a distancia, ataca a los jugadores y responde con
 * represalia a cualquier criatura (tambien hostil) que lo dane. Los alienigenas no se atacan entre si.
 */
public class AlienEntity extends Monster implements RangedAttackMob {

    public AlienEntity(EntityType<? extends AlienEntity> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0D, 30, 18.0F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // Se defiende de quien lo dane (incluidas otras criaturas hostiles) y avisa a otros alienigenas
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, AlienEntity.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(this.getCommandSenderWorld() instanceof ServerLevel server)) {
            return;
        }
        Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(0.5D));
        Vec3 aim = target.getBoundingBox().getCenter();
        Vec3 dir = aim.subtract(from).normalize();
        float damage = DisasterConfig.ALIEN_DAMAGE.get().floatValue();
        LaserBeams.fire(server, this, this, from, dir, 32.0D, damage, LaserBeams.MAGENTA, false,
                server.damageSources().mobAttack(this));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMITE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMITE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMITE_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (this.random.nextInt(3) == 0) {
            this.spawnAtLocation(new ItemStack(ModItems.ENERGY_CELL.get()));
        }
        if (this.random.nextInt(25) == 0) {
            this.spawnAtLocation(new ItemStack(ModItems.LASER_PISTOL.get()));
        }
    }
}

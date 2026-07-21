package net.pedito.mod.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.pedito.mod.Pedito;
import net.pedito.mod.registry.ModItems;
import net.pedito.mod.registry.ModSounds;
import org.jetbrains.annotations.Nullable;

/**
 * Entidad Pedito - version Java (Mojang mappings, Minecraft 26.2). Extiende Animal (NO
 * TamableAnimal) y programa la domesticacion/sentado/dueno a mano, para evitar depender de la
 * interfaz de "tameable" que cambia de forma inestable entre versiones.
 *
 * Notas de la ultima ronda de correcciones (26.2):
 * - HurtByTargetGoal (Mojang Mappings; antes RevengeGoal en Yarn) vive en el subpaquete
 *   ...ai.goal.target y se sigue usando tal cual.
 * - NearestAttackableTargetGoal se saco por completo: no se pudo confirmar con certeza la
 *   firma de su constructor con predicado en 26.2 (el tipo del lambda no compilaba ni como
 *   LivingEntity ni como Player). Se reemplazo por PeditoNearestPlayerTargetGoal, un goal
 *   propio muy simple basado en Level#getNearestPlayer, que no depende de esa firma.
 * - FlyGoal se elimino de esta clase porque no se pudo confirmar con certeza su paquete/nombre
 *   exacto en 26.2; se sustituyo por un goal propio muy simple (RandomFlyWanderGoal, al final
 *   del archivo) que hace lo mismo (deambular volando) sin depender de esa clase interna.
 * - El guardado/carga de datos (addAdditionalSaveData/readAdditionalSaveData) ahora usa
 *   ValueOutput/ValueInput en vez de CompoundTag directo, que es el sistema nuevo desde 1.21.5.
 * - El "dueno" (owner) dejo de sincronizarse via SynchedEntityData (EntityDataSerializers.OPTIONAL_UUID
 *   ya no existe tal cual, ahora hay un sistema de EntityReference mas complejo) y pasa a ser un
 *   campo simple del lado servidor, que es todo lo que esta clase necesitaba.
 */
public class PeditoEntity extends Animal {

  /** 0 = normal (dia, verde), 1 = noche (azul), 2 = arcoiris (rara). Antes era un booleano
   * IS_NIGHT; se cambio a un entero para poder agregar la variante arcoiris sin necesitar un
   * segundo campo aparte, y para que cada color sea un estado propio y excluyente (nunca "un
   * poco de cada uno"). */
  public static final int VARIANT_NORMAL = 0;
  public static final int VARIANT_NIGHT = 1;
  public static final int VARIANT_RAINBOW = 2;
  public static final int VARIANT_ALPHA = 3;
  public static final int VARIANT_GOLDEN = 4;

  private static final EntityDataAccessor<Integer> VARIANT =
    SynchedEntityData.defineId(PeditoEntity.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> SURPRISE_TICKS =
    SynchedEntityData.defineId(PeditoEntity.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> TALKING_TICKS =
    SynchedEntityData.defineId(PeditoEntity.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Boolean> TAMED =
    SynchedEntityData.defineId(
      PeditoEntity.class,
      EntityDataSerializers.BOOLEAN
    );
  private static final EntityDataAccessor<Boolean> SITTING =
    SynchedEntityData.defineId(
      PeditoEntity.class,
      EntityDataSerializers.BOOLEAN
    );
  private static final EntityDataAccessor<Integer> TIER =
    SynchedEntityData.defineId(PeditoEntity.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Boolean> IS_SPINNING =
    SynchedEntityData.defineId(
      PeditoEntity.class,
      EntityDataSerializers.BOOLEAN
    );
  private static final EntityDataAccessor<Integer> SPINNING_TICKS =
    SynchedEntityData.defineId(PeditoEntity.class, EntityDataSerializers.INT);

  /** Dueno del Pedito. Solo se necesita del lado servidor, asi que no se sincroniza. */
  @Nullable
  private UUID ownerUuid;

  private int threatAssessmentTimer;
  private boolean ownerInDanger;

  public int wildAlphaBreedCooldown = 0;
  public long lastSoundTime = 0L;

  public boolean canPlaySound(long cooldownTicks) {
    long time = this.level().getGameTime();
    if (time - lastSoundTime < cooldownTicks) return false;
    lastSoundTime = time;
    return true;
  }

  private static final double[][] TELEPORT_OFFSETS = {
    { 0.6, -0.7, -5.2 },
    { -5.1, -0.4, -3.5 },
    { 2.1, 0.4, -0.9 },
    { -2.2, -0.4, 1.0 },
    { -0.5, 0.6, -2.4 },
    { 3.4, -0.4, 2.3 },
    { -3.0, 0.6, 0.9 },
    { 0.3, -1.0, 4.4 },
    { 2.7, -0.7, -2.5 },
    { 5.6, -0.9, -4.5 },
    { -0.9, -0.5, 3.0 },
    { -4.0, -0.8, -0.1 },
    { -5.3, 0.3, 2.0 },
    { 3.1, 0.9, 0.9 },
    { 4.4, 0.4, -2.2 },
    { 2.3, 0.7, 1.1 },
    { 0.9, 0.8, -0.5 },
    { 3.9, -1.2, 5.2 },
    { -0.3, 0.4, 1.9 },
    { -5.1, 0.8, 2.4 },
  };
  private static final int FART_ONLY_WEIGHT = 47;
  private static final int TOTAL_WEIGHT =
    FART_ONLY_WEIGHT + TELEPORT_OFFSETS.length;

  private int fartTimer;

  public PeditoEntity(
    EntityType<? extends PeditoEntity> entityType,
    Level world
  ) {
    super(entityType, world);
    this.moveControl = new FlyingMoveControl(this, 20, true);
    this.setNoGravity(true);
    resetFartTimer();
  }

  public static AttributeSupplier.Builder createPeditoAttributes() {
    return Animal.createMobAttributes()
      .add(Attributes.MAX_HEALTH, 8.0D)
      .add(Attributes.MOVEMENT_SPEED, 0.4D)
      .add(Attributes.FLYING_SPEED, 0.4D)
      .add(Attributes.FOLLOW_RANGE, 24.0D)
      .add(Attributes.TEMPT_RANGE, 10.0D)
      .add(Attributes.ARMOR, 0.0D)
      .add(Attributes.ATTACK_DAMAGE, 1.0D);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(VARIANT, VARIANT_NORMAL);
    builder.define(SURPRISE_TICKS, 0);
    builder.define(TALKING_TICKS, 0);
    builder.define(TAMED, false);
    builder.define(SITTING, false);
    builder.define(TIER, 0);
    builder.define(IS_SPINNING, false);
    builder.define(SPINNING_TICKS, 0);
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    super.addAdditionalSaveData(output);
    output.putBoolean("PeditoTamed", this.entityData.get(TAMED));
    output.putBoolean("PeditoSitting", this.entityData.get(SITTING));
    output.putInt("PeditoVariant", this.entityData.get(VARIANT));
    output.putInt("PeditoTier", this.entityData.get(TIER));
    output.storeNullable("PeditoOwner", UUIDUtil.CODEC, this.ownerUuid);
    output.putInt("WildAlphaBreedCooldown", this.wildAlphaBreedCooldown);
  }

  @Override
  protected void readAdditionalSaveData(ValueInput input) {
    super.readAdditionalSaveData(input);
    this.entityData.set(TAMED, input.getBooleanOr("PeditoTamed", false));
    this.entityData.set(SITTING, input.getBooleanOr("PeditoSitting", false));
    // Compatibilidad con guardados viejos que solo tenian "PeditoNight" (booleano).
    int legacyDefault = input.getBooleanOr("PeditoNight", false)
      ? VARIANT_NIGHT
      : VARIANT_NORMAL;
    this.entityData.set(
      VARIANT,
      input.getIntOr("PeditoVariant", legacyDefault)
    );
    this.entityData.set(TIER, input.getIntOr("PeditoTier", 0));
    this.ownerUuid = input.read("PeditoOwner", UUIDUtil.CODEC).orElse(null);
    this.wildAlphaBreedCooldown = input.getIntOr("WildAlphaBreedCooldown", 0);
    this.updateAttributesForTier();
  }

  public boolean wantsToAttack(
    net.minecraft.world.entity.LivingEntity target,
    net.minecraft.world.entity.LivingEntity owner
  ) {
    if (
      target instanceof net.minecraft.world.entity.monster.Creeper ||
      target instanceof net.minecraft.world.entity.monster.Ghast
    ) {
      return false;
    } else if (target instanceof PeditoEntity) {
      return false;
    } else if (
      target instanceof net.minecraft.world.entity.player.Player &&
      owner instanceof net.minecraft.world.entity.player.Player &&
      !((net.minecraft.world.entity.player.Player) owner).canHarmPlayer(
        (net.minecraft.world.entity.player.Player) target
      )
    ) {
      return false;
    } else if (
      target instanceof net.minecraft.world.entity.TamableAnimal tamable &&
      tamable.isTame() &&
      tamable.getOwner() == owner
    ) {
      return false;
    } else {
      return true;
    }
  }

  @Override
  protected boolean considersEntityAsAlly(Entity other) {
    if (super.considersEntityAsAlly(other)) {
      return true;
    }
    if (this.isTamedByOwner() && other instanceof PeditoEntity otherPedito) {
      return (
        otherPedito.isTamedByOwner() &&
        otherPedito.getOwnerCustom() == this.getOwnerCustom()
      );
    }
    return false;
  }

  public int getVariant() {
    return this.entityData.get(VARIANT);
  }

  public void setVariant(int variant) {
    this.entityData.set(VARIANT, variant);
    this.updateAttributesForTier();
  }

  public int getTier() {
    return this.entityData.get(TIER);
  }

  public void setTier(int tier) {
    this.entityData.set(TIER, tier);
    this.updateAttributesForTier();
  }

  public boolean isSpinning() {
    return this.entityData.get(IS_SPINNING);
  }

  public void setSpinning(boolean spinning) {
    this.entityData.set(IS_SPINNING, spinning);
  }

  public int getSpinningTicks() {
    return this.entityData.get(SPINNING_TICKS);
  }

  public void setSpinningTicks(int ticks) {
    this.entityData.set(SPINNING_TICKS, ticks);
  }

  private void updateAttributesForTier() {
    int currentTier = this.getTier();
    double health = 8.0D;
    double armor = 0.0D;
    double attack = 1.0D;
    double speed = 0.4D;
    double flyingSpeed = 0.4D;

    if (this.getVariant() == VARIANT_GOLDEN) {
      health = 80.0D;
      armor = 20.0D;
      attack = 10.0D;
      speed = 0.6D;
      flyingSpeed = 0.6D;
    } else if (this.getVariant() == VARIANT_ALPHA) {
      health = 50.0D; // High health for leader (25 hearts)
      armor = 16.0D; // High leader armor
      attack = 7.0D; // High leader attack damage
      speed = 0.55D;
      flyingSpeed = 0.55D;
    } else if (currentTier == 1) {
      // Copper
      health = 12.0D;
      armor = 2.0D;
      attack = 1.5D;
    } else if (currentTier == 2) {
      // Iron
      health = 16.0D;
      armor = 4.0D;
      attack = 2.0D;
    } else if (currentTier == 3) {
      // Gold
      health = 20.0D;
      armor = 6.0D;
      attack = 2.5D;
    } else if (currentTier == 4) {
      // Diamond
      health = 30.0D;
      armor = 10.0D;
      attack = 4.0D;
    } else if (currentTier == 5) {
      // Netherite
      health = 40.0D;
      armor = 14.0D;
      attack = 6.0D;
    }

    // Ventajas de criatura nocturna:
    if (this.getVariant() == VARIANT_NIGHT) {
      health += 6.0D; // +3 corazones
      armor += 3.0D; // Armadura natural extra
      attack += 1.5D; // Daño base extra

      // Frenesí Nocturno (Night Frenzy) durante la noche
      if (this.level().isDarkOutside()) {
        speed = 0.6D;
        flyingSpeed = 0.6D;
        attack += 1.0D; // Daño adicional de noche
        armor += 2.0D; // Defensa adicional de noche
      }
    }

    if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
      this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
    }
    if (this.getAttribute(Attributes.ARMOR) != null) {
      this.getAttribute(Attributes.ARMOR).setBaseValue(armor);
    }
    if (this.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
      this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
    }
    if (this.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
      this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
    }
    if (this.getAttribute(Attributes.FLYING_SPEED) != null) {
      this.getAttribute(Attributes.FLYING_SPEED).setBaseValue(flyingSpeed);
    }
  }

  public boolean isNightVariant() {
    return this.entityData.get(VARIANT) == VARIANT_NIGHT;
  }

  public int getSurpriseTicks() {
    return this.entityData.get(SURPRISE_TICKS);
  }

  public int getTalkingTicks() {
    return this.entityData.get(TALKING_TICKS);
  }

  // --- Domesticacion/sentado/dueno propios (sin depender de TamableAnimal) ---
  public boolean isTamedByOwner() {
    return this.entityData.get(TAMED);
  }

  public boolean isOwnerInDanger() {
    return this.ownerInDanger;
  }

  public void setOwnerInDanger(boolean danger) {
    this.ownerInDanger = danger;
    if (danger) {
      this.threatAssessmentTimer = 40; // Retrasar el siguiente chequeo si ya fue alertado
    }
  }

  private boolean isJukeboxDancing;

  @Override
  public void setRecordPlayingNearby(
    net.minecraft.core.BlockPos pos,
    boolean isPartying
  ) {
    this.isJukeboxDancing = isPartying;
  }

  public boolean isJukeboxDancing() {
    return this.isJukeboxDancing;
  }

  private void updateThreatAssessment() {
    Player owner = this.getOwnerCustom();
    if (owner == null || !owner.isAlive()) {
      this.ownerInDanger = false;
      return;
    }

    if (--this.threatAssessmentTimer <= 0) {
      this.threatAssessmentTimer = 20;

      boolean danger = false;

      // Check if owner is attacking someone
      if (
        owner.getLastHurtMob() != null &&
        owner.getLastHurtMob().isAlive() &&
        owner.getLastHurtMob().distanceToSqr(owner) < 400
      ) {
        danger = true;
      }
      // Check if owner was attacked by someone
      if (
        owner.getLastHurtByMob() != null &&
        owner.getLastHurtByMob().isAlive() &&
        owner.getLastHurtByMob().distanceToSqr(owner) < 400
      ) {
        danger = true;
      }

      // Scan 10 blocks around the owner for Hostile entities (Enemy) or mobs targeting the owner
      if (!danger) {
        List<LivingEntity> nearHostiles = this.level().getEntitiesOfClass(
          LivingEntity.class,
          owner.getBoundingBox().inflate(10.0D),
          entity ->
            entity.isAlive() &&
            (entity instanceof Enemy ||
              (entity instanceof net.minecraft.world.entity.Mob mob &&
                mob.getTarget() == owner))
        );
        if (!nearHostiles.isEmpty()) {
          danger = true;
        }
      }

      this.ownerInDanger = danger;

      // Caché de Enemigos: Emitir alerta a otros Peditos del mismo dueño
      if (danger) {
        List<PeditoEntity> allies = this.level().getEntitiesOfClass(
          PeditoEntity.class,
          this.getBoundingBox().inflate(24.0D),
          p -> p != this && p.isTamedByOwner() && p.getOwnerCustom() == owner
        );
        for (PeditoEntity ally : allies) {
          ally.setOwnerInDanger(true);
        }
      }
    }
  }

  public boolean isSittingCustom() {
    return this.entityData.get(SITTING);
  }

  public void setSittingCustom(boolean sitting) {
    boolean was = this.entityData.get(SITTING);
    this.entityData.set(SITTING, sitting);
    if (sitting && !was && this.level() instanceof ServerLevel serverLevel) {
      this.getNavigation().stop();
      serverLevel.sendParticles(
        ParticleTypes.CLOUD,
        this.getX(),
        this.getY() + 0.3,
        this.getZ(),
        3,
        0.15,
        0.05,
        0.15,
        0.01
      );
    }
  }

  public void setOwnerCustom(Player player) {
    this.ownerUuid = player.getUUID();
    this.entityData.set(TAMED, true);
  }

  public boolean isOwnerCustom(Player player) {
    if (player.level().isClientSide()) {
      return true;
    }
    return this.ownerUuid != null && this.ownerUuid.equals(player.getUUID());
  }

  @Nullable
  public Player getOwnerCustom() {
    return this.ownerUuid != null
      ? this.level().getPlayerByUUID(this.ownerUuid)
      : null;
  }

  public boolean isWhistleActive() {
    if (!this.isTamedByOwner()) return false;
    Player owner = this.getOwnerCustom();
    if (owner == null) return false;
    return (
      owner.getMainHandItem().is(ModItems.PEDITO_WHISTLE) ||
      owner.getOffhandItem().is(ModItems.PEDITO_WHISTLE)
    );
  }

  @Override
  protected PathNavigation createNavigation(Level world) {
    FlyingPathNavigation navigation = new FlyingPathNavigation(this, world);
    navigation.setCanOpenDoors(true);
    navigation.setCanFloat(false);
    return navigation;
  }

  @Override
  public boolean isPushedByFluid() {
    return false;
  }

  @Override
  public boolean canBreatheUnderwater() {
    return true;
  }

  @Override
  public boolean isPushable() {
    return true; // Permite empuje físico para evitar agrupaciones infinitas
  }

  @Override
  protected void doPush(Entity entity) {
    super.doPush(entity);
  }

  public boolean causeFallDamage(
    float fallDistance,
    float multiplier,
    DamageSource source
  ) {
    return false; // Al flotar, nunca recibe daño de caída
  }

  @Override
  protected void checkFallDamage(
    double y,
    boolean onGround,
    net.minecraft.world.level.block.state.BlockState state,
    net.minecraft.core.BlockPos pos
  ) {
    // Evita que calcule daño de caída internamente
  }

  public int getAllyCount() {
    return this.level()
      .getEntitiesOfClass(
        PeditoEntity.class,
        this.getBoundingBox().inflate(16.0D)
      )
      .size();
  }

  @Override
  protected void registerGoals() {
    this.goalSelector.addGoal(0, new FloatGoal(this));
    this.goalSelector.addGoal(1, new CustomSitGoal(this));

    // Ultimates in strict priority order (v2)
    this.goalSelector.addGoal(2, new BiogasProtectionAuraGoal(this));
    this.goalSelector.addGoal(3, new PeditoGoldenBurstGoal(this));
    this.goalSelector.addGoal(3, new NocturnalShadowNovaGoal(this));
    this.goalSelector.addGoal(4, new GigaPeditoHologramBeamGoal(this));
    this.goalSelector.addGoal(5, new CosmicGasCataclysmGoal(this));

    // Standard abilities
    this.goalSelector.addGoal(6, new ColorFusionAttackGoal(this));
    this.goalSelector.addGoal(6, new PixelLazerAttackGoal(this));
    this.goalSelector.addGoal(6, new RainbowDashAttackGoal(this));
    this.goalSelector.addGoal(6, new PeditoSonicBoomGoal(this));
    this.goalSelector.addGoal(6, new CubicVortexAttackGoal(this));
    this.goalSelector.addGoal(6, new PeditoGolemSummonGoal(this));
    this.goalSelector.addGoal(6, new PeditoAirstrikeGoal(this));
    this.goalSelector.addGoal(6, new PeditoAlphaConcentrationGoal(this));

    this.goalSelector.addGoal(7, new PeditoAttackGoal(this));

    this.goalSelector.addGoal(8, new PeditoHoverOwnerGoal(this));

    this.goalSelector.addGoal(9, new PeditoDanceGoal(this));
    this.goalSelector.addGoal(10, new PeditoPlayWithPeersGoal(this));
    this.goalSelector.addGoal(11, new PeditoWanderNearOwnerGoal(this));
    this.goalSelector.addGoal(12, new PeditoFormationGoal(this));

    this.goalSelector.addGoal(13, new ApproachTargetGoal(this, 1.8D, 2.0D));
    this.goalSelector.addGoal(14, new RandomFlyWanderGoal(this));
    this.goalSelector.addGoal(
      15,
      new LookAtPlayerGoal(this, Player.class, 8.0F)
    );
    this.goalSelector.addGoal(16, new RandomLookAroundGoal(this));
    this.goalSelector.addGoal(17, new PanicGoal(this, 1.3D));
    this.goalSelector.addGoal(18, new BreedGoal(this, 1.0D));
    this.goalSelector.addGoal(18, new WildAlphaBreedingGoal(this));
    this.goalSelector.addGoal(
      19,
      new TemptGoal(this, 1.0D, Ingredient.of(Items.EGG), false)
    );

    this.targetSelector.addGoal(0, new PeditoAlphaDefendVulnerableGoal(this));
    this.targetSelector.addGoal(1, new PeditoFollowAlphaTargetGoal(this));
    this.targetSelector.addGoal(2, new PeditoOwnerHurtByTargetGoal(this));
    this.targetSelector.addGoal(3, new PeditoOwnerHurtTargetGoal(this));
    this.targetSelector.addGoal(4, new HurtByTargetGoal(this).setAlertOthers());
    this.targetSelector.addGoal(5, new PeditoNearestPlayerTargetGoal(this, 16.0D));
  }

  /**
   * Reemplazo casero de NearestAttackableTargetGoal: busca al jugador vivo mas cercano
   * (que no este en modo espectador/creativo) y lo ataca, salvo que el Pedito ya este
   * domesticado. Usa solo APIs viejas y estables (Level#getNearestPlayer, Mob#setTarget)
   * para no depender de adivinar la firma exacta del constructor con predicado en 26.2.
   */
  static class PeditoNearestPlayerTargetGoal extends Goal {

    private final PeditoEntity pedito;
    private final double range;

    PeditoNearestPlayerTargetGoal(PeditoEntity pedito, double range) {
      this.pedito = pedito;
      this.range = range;
      this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
      if (this.pedito.isTamedByOwner()) {
        return false;
      }
      int variant = this.pedito.getVariant();
      if (variant != VARIANT_NIGHT && variant != VARIANT_ALPHA) {
        return false;
      }
      Player nearest = this.pedito
        .level()
        .getNearestPlayer(this.pedito, this.range);
      if (
        nearest == null ||
        !nearest.isAlive() ||
        nearest.isSpectator() ||
        nearest.isCreative()
      ) {
        return false;
      }
      this.pedito.setTarget(nearest);
      return true;
    }

    @Override
    public boolean canContinueToUse() {
      LivingEntity target = this.pedito.getTarget();
      return (
        !this.pedito.isTamedByOwner() && target != null && target.isAlive()
      );
    }

    @Override
    public void stop() {
      this.pedito.setTarget(null);
    }
  }

  @Override
  public void setTarget(@Nullable LivingEntity target) {
    if (
      target != null && this.isTamedByOwner() && this.getOwnerCustom() != null
    ) {
      if (target == this.getOwnerCustom()) {
        super.setTarget(null);
        return;
      }
      if (!this.wantsToAttack(target, this.getOwnerCustom())) {
        super.setTarget(null);
        return;
      }
    }
    super.setTarget(target);
  }

  @Override
  public void tick() {
    super.tick();
    if (
      !this.level().isClientSide() &&
      this.isTamedByOwner() &&
      !this.isSittingCustom()
    ) {
      Player owner = this.getOwnerCustom();
      if (owner != null && !this.isLeashed() && !this.isPassenger()) {
        if (this.distanceToSqr(owner) > 1600.0D) {
          // 40 blocks
          this.teleportToOwner(owner);
        }
      }
    }

    if (!this.level().isClientSide() && this.isTamedByOwner()) {
      this.updateThreatAssessment();
      this.updateSquadRole();
      this.updateAlphaStatus();
    }

    int surprise = this.getSurpriseTicks();
    if (surprise > 0) {
      this.entityData.set(SURPRISE_TICKS, surprise - 1);
    }

    int talking = this.getTalkingTicks();
    if (talking > 0) {
      this.entityData.set(TALKING_TICKS, talking - 1);
    }

    if (!this.level().isClientSide()) {
      // Los peditos nocturnos salvajes se queman de día si les da el sol (como los zombies)
      if (
        this.isAlive() &&
        !this.isTamedByOwner() &&
        this.getVariant() == VARIANT_NIGHT
      ) {
        net.minecraft.core.BlockPos pos =
          net.minecraft.core.BlockPos.containing(
            this.getX(),
            this.getEyeY(),
            this.getZ()
          );
        if (
          !this.level().isDarkOutside() &&
          !this.isInWater() &&
          !this.level().isRainingAt(pos)
        ) {
          if (this.level().canSeeSky(pos)) {
            this.igniteForSeconds(8);
          }
        }
      }

      // Actualización de atributos dinámica para el Frenesí Nocturno
      if (this.getVariant() == VARIANT_NIGHT && this.tickCount % 40 == 0) {
        this.updateAttributesForTier();
      }

      if (this.wildAlphaBreedCooldown > 0) {
        this.wildAlphaBreedCooldown--;
      }

      if (this.getTarget() != null) {
        LivingEntity t = this.getTarget();
        if (
          !t.isAlive() ||
          (this.isTamedByOwner() &&
            this.getOwnerCustom() != null &&
            !this.wantsToAttack(t, this.getOwnerCustom()))
        ) {
          this.setTarget(null);
        } else if (
          this.isTamedByOwner() &&
          this.getOwnerCustom() != null &&
          t instanceof net.minecraft.world.entity.player.Player p &&
          p == this.getOwnerCustom()
        ) {
          this.setTarget(null); // Never attack owner
        }
      }

      if (--this.fartTimer <= 0) {
        doTimerEvent();
        resetFartTimer();
      }

      // Safe Block Check: Prevent being trapped/suffocating in solid blocks
      if (this.isAlive() && this.tickCount % 5 == 0) {
        net.minecraft.core.BlockPos myPos = this.blockPosition();
        if (isSolidBlock(myPos)) {
          net.minecraft.core.BlockPos safePos = findSafePositionNearby(myPos);
          if (safePos != null) {
            this.teleportTo(
              safePos.getX() + 0.5,
              safePos.getY(),
              safePos.getZ() + 0.5
            );
          }
        }
      }
    }
  }

  private void resetFartTimer() {
    RandomSource random = this.getRandom();
    if (this.isNightVariant()) {
      this.fartTimer = 400 + random.nextInt(600);
    } else if (this.isTamedByOwner()) {
      this.fartTimer = 600 + random.nextInt(600);
    } else {
      this.fartTimer = 800 + random.nextInt(800);
    }
  }

  private void doTimerEvent() {
    if (!(this.level() instanceof ServerLevel serverLevel)) return;

    // 30% chance to say a voice line, 70% chance to fart
    if (this.random.nextFloat() < 0.30F) {
      playRandomVoice(serverLevel);
    } else {
      int roll = this.random.nextInt(TOTAL_WEIGHT);
      float pitch = getFartPitch();

      if (roll < FART_ONLY_WEIGHT) {
        playFart(
          serverLevel,
          this.getX(),
          this.getY(),
          this.getZ(),
          1.0F,
          pitch
        );
      } else {
        double[] offset = TELEPORT_OFFSETS[roll - FART_ONLY_WEIGHT];
        double targetX = this.getX() + offset[0];
        double targetY = this.getY() + offset[1];
        double targetZ = this.getZ() + offset[2];
        this.teleportTo(targetX, targetY, targetZ);
        serverLevel.sendParticles(
          ParticleTypes.PORTAL,
          targetX,
          targetY + 0.3,
          targetZ,
          12,
          0.3,
          0.4,
          0.3,
          0.02
        );
        playFart(serverLevel, targetX, targetY, targetZ, 1.0F, pitch);
      }
      triggerTalkAnimation(); // Sorpresa cara (O) para pedos
    }
  }

  @Override
  public float getVoicePitch() {
    if (this.getVariant() == VARIANT_ALPHA) {
      return 0.42F + this.random.nextFloat() * 0.06F; // Alpha: muy grave (0.42F a 0.48F)
    } else if (this.isBaby()) {
      return 1.50F + this.random.nextFloat() * 0.15F; // Bebé: más fino (1.50F a 1.65F)
    }
    return 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F; // Demás: pitch natural (0.9F a 1.1F)
  }

  private void playRandomVoice(ServerLevel world) {
    net.minecraft.sounds.SoundEvent[] voices = {
      ModSounds.PEDITO_VOICE_PEDI,
      ModSounds.PEDITO_VOICE_PEDITO,
      ModSounds.PEDITO_VOICE_PUPU,
      ModSounds.PEDITO_VOICE_PUPULLITO,
      ModSounds.PEDITO_VOICE_SWAN,
    };
    net.minecraft.sounds.SoundEvent chosenVoice = voices[
      this.random.nextInt(voices.length)
    ];

    float voicePitch = this.getVoicePitch();

    // SoundCategory.VOICE para permitir controlar el volumen en el menu de opciones (con volumen subido)
    world.playSound(
      null,
      this.getX(),
      this.getY(),
      this.getZ(),
      chosenVoice,
      SoundSource.NEUTRAL,
      1.8F,
      voicePitch
    );

    // Activar la animacion de la boca (aprox 30 ticks = 1.5 segundos)
    this.entityData.set(TALKING_TICKS, 30);
  }

  private float getFartPitch() {
    float basePitch = 0.9F;
    if (this.getVariant() == VARIANT_ALPHA) basePitch = 0.65F;
    // Grave fart!
    else if (this.isBaby()) basePitch = 1.6F;
    else if (this.isNightVariant()) basePitch = 0.55F;
    else if (this.isTamedByOwner()) basePitch = 1.3F;

    // Modulación dinámica de tono (Gestión Inteligente de Sonido)
    float finalPitch =
      basePitch +
      (this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 0.15F;
    return finalPitch < 0.55F ? 0.55F : finalPitch;
  }

  private void playFart(
    ServerLevel world,
    double x,
    double y,
    double z,
    float volume,
    float pitch
  ) {
    if (this.getVariant() == VARIANT_ALPHA) {
      world.sendParticles(
        ParticleTypes.CLOUD,
        x,
        y + 0.5,
        z,
        45,
        0.6,
        0.3,
        0.6,
        0.08
      );
      // 3 main colors: Gold (0xFFD700), Green (0x32CD32), Diamond (0x00FFFF), and a bit of Black (0x050505)
      world.sendParticles(
        new DustParticleOptions(0xFFD700, 1.2F),
        x,
        y + 0.5,
        z,
        25,
        0.5,
        0.3,
        0.5,
        0.1
      );
      world.sendParticles(
        new DustParticleOptions(0x32CD32, 1.2F),
        x,
        y + 0.5,
        z,
        25,
        0.5,
        0.3,
        0.5,
        0.1
      );
      world.sendParticles(
        new DustParticleOptions(0x00FFFF, 1.2F),
        x,
        y + 0.5,
        z,
        25,
        0.5,
        0.3,
        0.5,
        0.1
      );
      world.sendParticles(
        new DustParticleOptions(0x050505, 1.2F),
        x,
        y + 0.5,
        z,
        12,
        0.5,
        0.3,
        0.5,
        0.15
      );
    } else {
      world.sendParticles(
        ParticleTypes.CLOUD,
        x,
        y + 0.3,
        z,
        20,
        0.4,
        0.2,
        0.4,
        0.05
      );

      // Colored dust particles matching variant/state
      int color;
      if (this.getVariant() == VARIANT_RAINBOW) {
        // Rainbow/multicolor
        int r = (int) (this.random.nextFloat() * 255.0F);
        int g = (int) (this.random.nextFloat() * 255.0F);
        int b = (int) (this.random.nextFloat() * 255.0F);
        color = (r << 16) | (g << 8) | b;
      } else if (this.getVariant() == VARIANT_NIGHT) {
        // Indigo/dark blue
        color = 0x1A237E;
      } else {
        // VARIANT_NORMAL (Green)
        if (this.isBaby()) {
          // Soft baby yellow
          color = 0xFFEE58;
        } else {
          // Vibrant green
          color = 0x32CD32;
        }
      }
      world.sendParticles(
        new DustParticleOptions(color, 1.0F),
        x,
        y + 0.3,
        z,
        30,
        0.3,
        0.2,
        0.3,
        0.1
      );
    }

    world.playSound(
      null,
      x,
      y,
      z,
      ModSounds.PEDITO_FART,
      SoundSource.NEUTRAL,
      volume * 0.6F,
      pitch
    );
  }

  public void triggerTalkAnimation() {
    this.entityData.set(SURPRISE_TICKS, 6);
  }

  public void playAttackVoice() {
    playAttackVoice(1.0F);
  }

  public void playAttackVoice(float pitchModifier) {
    if (this.level() instanceof ServerLevel serverLevel) {
      float basePitch = this.getVoicePitch();
      float voicePitch = basePitch * pitchModifier;
      if (voicePitch < 0.35F) {
        voicePitch = 0.35F;
      }
      // Volumen del grito de combate subido a 2.0F para que sea perfectamente perceptible
      serverLevel.playSound(
        null,
        this.getX(),
        this.getY(),
        this.getZ(),
        ModSounds.PEDITO_VOICE_ATTACK,
        SoundSource.NEUTRAL,
        2.0F,
        voicePitch
      );
      this.entityData.set(TALKING_TICKS, 25);
    }
  }

  public boolean hasNetheriteLeader() {
    if (!this.isTamedByOwner()) return false;
    net.minecraft.world.entity.player.Player owner = this.getOwnerCustom();
    if (owner == null) return false;
    java.util.List<PeditoEntity> allies = this.level().getEntitiesOfClass(
      PeditoEntity.class,
      this.getBoundingBox().inflate(40.0D),
      e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
    );
    if (allies.size() < 8) return false;
    for (PeditoEntity ally : allies) {
      if (ally.getTier() == 5) return true;
    }
    return false;
  }

  public boolean hasCopperSynergy() {
    if (!this.isTamedByOwner()) return false;
    net.minecraft.world.entity.player.Player owner = this.getOwnerCustom();
    if (owner == null) return false;
    java.util.List<PeditoEntity> allies = this.level().getEntitiesOfClass(
      PeditoEntity.class,
      this.getBoundingBox().inflate(40.0D),
      e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
    );
    int copperCount = 0;
    int higherTierCount = 0;
    for (PeditoEntity ally : allies) {
      if (ally.getTier() == 1) copperCount++;
      else if (ally.getTier() > 1) higherTierCount++;
    }
    return copperCount >= 3 && higherTierCount >= 2;
  }

  public boolean hasIronGoldSynergy() {
    if (!this.isTamedByOwner()) return false;
    net.minecraft.world.entity.player.Player owner = this.getOwnerCustom();
    if (owner == null) return false;
    java.util.List<PeditoEntity> allies = this.level().getEntitiesOfClass(
      PeditoEntity.class,
      this.getBoundingBox().inflate(40.0D),
      e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
    );
    int ironCount = 0;
    int goldCount = 0;
    for (PeditoEntity ally : allies) {
      if (ally.getTier() == 2) ironCount++;
      else if (ally.getTier() == 3) goldCount++;
    }
    return ironCount >= 3 && goldCount >= 2;
  }

  public boolean hurtServer(
    ServerLevel level,
    DamageSource source,
    float amount
  ) {
    // Ignorar daño por asfixia dentro de bloques (normalmente daño de 1.0F continuo que ignora armadura o tipo IN_WALL)
    if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
      return false;
    }
    if (
      source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) &&
      amount <= 1.0F &&
      !source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
    ) {
      return false;
    }

    // No hacer daño entre peditos domesticados
    if (this.isTamedByOwner()) {
      Entity attacker = source.getEntity();
      if (
        attacker instanceof PeditoEntity otherPedito &&
        otherPedito.isTamedByOwner()
      ) {
        return false;
      }

      // +10% Resistencia Mágica de Líder de Netherite
      if (
        source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO) ||
        source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
      ) {
        if (this.hasNetheriteLeader()) {
          amount *= 0.9F;
        }
      }
    }

    boolean result = super.hurtServer(level, source, amount);
    if (result) {
      float volume = this.isNightVariant() ? 1.2F : 1.0F;
      playFart(
        level,
        this.getX(),
        this.getY(),
        this.getZ(),
        volume,
        getFartPitch()
      );
      triggerTalkAnimation();
    }
    return result;
  }

  public static class PeditoGroupSpawnData
    extends AgeableMob.AgeableMobGroupData
  {

    public int count = 0;
    public boolean spawnAlpha = false;
    public boolean alphaSpawned = false;

    public PeditoGroupSpawnData(boolean baby) {
      super(baby);
    }
  }

  public static boolean canSpawn(
    EntityType<PeditoEntity> type,
    ServerLevelAccessor world,
    EntitySpawnReason spawnReason,
    net.minecraft.core.BlockPos pos,
    RandomSource random
  ) {
    boolean isDark = world.getRawBrightness(pos, world.getLevel().getSkyDarken()) <= 8;
    if (isDark) {
      // Comportamiento de noche/oscuridad (como zombies): permite spawnear en cualquier bloque solido normal
      return world
        .getBlockState(pos.below())
        .isValidSpawn(world, pos.below(), type);
    } else {
      // Comportamiento de día (luz): solo sobre pasto/bloques para animales
      return world
        .getBlockState(pos.below())
        .is(net.minecraft.tags.BlockTags.ANIMALS_SPAWNABLE_ON);
    }
  }

  @Nullable
  @Override
  public SpawnGroupData finalizeSpawn(
    ServerLevelAccessor world,
    DifficultyInstance difficulty,
    EntitySpawnReason spawnReason,
    @Nullable SpawnGroupData entityData
  ) {
    SpawnGroupData data = super.finalizeSpawn(
      world,
      difficulty,
      spawnReason,
      entityData
    );

    if (spawnReason == EntitySpawnReason.BREEDING) {
      this.setBaby(true);
      this.entityData.set(VARIANT, VARIANT_NORMAL);
      this.setTier(0);
    } else if (
      spawnReason == EntitySpawnReason.NATURAL ||
      spawnReason == EntitySpawnReason.CHUNK_GENERATION
    ) {
      PeditoGroupSpawnData groupData;
      if (entityData instanceof PeditoGroupSpawnData) {
        groupData = (PeditoGroupSpawnData) entityData;
      } else {
        groupData = new PeditoGroupSpawnData(true);
        groupData.spawnAlpha = this.random.nextInt(100) < 15;
        data = groupData;
      }
      groupData.count++;

      boolean isDark = world.getRawBrightness(this.blockPosition(), world.getLevel().getSkyDarken()) <= 8;
      
      if (groupData.spawnAlpha && !groupData.alphaSpawned) {
        this.setBaby(false);
        this.setVariant(VARIANT_ALPHA);
        this.setTier(0);
        groupData.alphaSpawned = true;
      } else {
        int roll = this.random.nextInt(100);
        if (roll < 1) {
          this.setBaby(false);
          this.entityData.set(VARIANT, VARIANT_GOLDEN);
        } else if (roll < 15) {
          this.setBaby(true);
          this.entityData.set(VARIANT, isDark ? VARIANT_NIGHT : VARIANT_NORMAL);
        } else {
          this.setBaby(false);
          this.entityData.set(VARIANT, isDark ? VARIANT_NIGHT : VARIANT_NORMAL);
        }
        this.setTier(0);
      }
    } else {
      // Spawn egg, command, etc.
      boolean isDark = world.getRawBrightness(this.blockPosition(), world.getLevel().getSkyDarken()) <= 8;
      int roll = this.random.nextInt(100);
      if (roll < 1) {
        this.setBaby(false);
        this.entityData.set(VARIANT, VARIANT_GOLDEN);
      } else if (roll < 10) {
        this.setBaby(true);
        this.entityData.set(VARIANT, isDark ? VARIANT_NIGHT : VARIANT_NORMAL);
      } else {
        this.setBaby(false);
        this.entityData.set(VARIANT, isDark ? VARIANT_NIGHT : VARIANT_NORMAL);
      }
      this.setTier(0);
    }

    if (this.level() instanceof ServerLevel serverLevel) {
      serverLevel.playSound(
        null,
        this.getX(),
        this.getY(),
        this.getZ(),
        ModSounds.PEDITO_FART_SPAWN,
        SoundSource.NEUTRAL,
        1.5F,
        1.0F
      );
      serverLevel.sendParticles(
        ParticleTypes.CLOUD,
        this.getX(),
        this.getY() + 0.5,
        this.getZ(),
        8,
        0.2,
        0.1,
        0.2,
        0.01
      );
    }
    return data;
  }

  @Override
  public boolean isFood(ItemStack stack) {
    return stack.is(Items.EGG);
  }

  @Nullable
  @Override
  public void playAmbientSound() {
    net.minecraft.sounds.SoundEvent[] voices = {
      ModSounds.PEDITO_VOICE_PEDI,
      ModSounds.PEDITO_VOICE_PEDITO,
      ModSounds.PEDITO_VOICE_PUPU,
      ModSounds.PEDITO_VOICE_PUPULLITO,
      ModSounds.PEDITO_VOICE_SWAN,
    };

    if (this.level() instanceof ServerLevel serverLevel) {
      playRandomVoice(serverLevel);
    }
  }

  @Nullable
  @Override
  protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
    return ModSounds.PEDITO_VOICE_PEDI;
  }

  @Nullable
  @Override
  protected net.minecraft.sounds.SoundEvent getDeathSound() {
    return ModSounds.PEDITO_FART_SPAWN;
  }

  @Nullable
  @Override
  public PeditoEntity getBreedOffspring(ServerLevel world, AgeableMob mate) {
    return new PeditoEntity(Pedito.PEDITO_ENTITY, world);
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);

    if (stack.is(net.minecraft.world.item.Items.AMETHYST_SHARD)) {
      if (this.getVariant() != VARIANT_RAINBOW) {
        if (!this.level().isClientSide()) {
          this.setVariant(VARIANT_RAINBOW);
          if (!player.getAbilities().instabuild) stack.shrink(1);
          this.playSound(
            net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
            1.0F,
            1.0F
          );
        }
        return InteractionResult.SUCCESS;
      }
    }
    // Tier Upgrade System
    if (this.isTamedByOwner() && !this.isBaby()) {
      int currentTier = this.getTier();
      int newTier = -1;
      if (stack.is(ModItems.GAS_CAN_COPPER) && currentTier < 1) newTier = 1;
      else if (stack.is(ModItems.GAS_CAN_IRON) && currentTier < 2) newTier = 2;
      else if (stack.is(ModItems.GAS_CAN_GOLD) && currentTier < 3) newTier = 3;
      else if (stack.is(ModItems.GAS_CAN_DIAMOND) && currentTier < 4) newTier =
        4;
      else if (
        stack.is(ModItems.GAS_CAN_NETHERITE) && currentTier < 5
      ) newTier = 5;

      if (newTier != -1) {
        if (!this.level().isClientSide()) {
          this.setTier(newTier);
          if (!player.getAbilities().instabuild) stack.shrink(1);
          this.playSound(
            net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE,
            1.0F,
            1.0F
          );
          if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
              ParticleTypes.HAPPY_VILLAGER,
              this.getX(),
              this.getY() + 0.5,
              this.getZ(),
              15,
              0.3,
              0.3,
              0.3,
              0.05
            );
          }
          // Heal fully on upgrade
          this.setHealth(this.getMaxHealth());
        }
        return InteractionResult.SUCCESS;
      }
    }

    if (!this.isTamedByOwner() && !this.isBaby() && stack.is(ModItems.GEMA_DE_LUZ)) {
      if (this.getVariant() == VARIANT_NIGHT) {
        if (!this.level().isClientSide()) {
          this.setOwnerCustom(player);
          if (!player.getAbilities().instabuild) {
            stack.shrink(1);
          }
          this.level().broadcastEntityEvent(this, (byte) 7);
          if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
              ParticleTypes.HAPPY_VILLAGER,
              this.getX(),
              this.getY() + 0.5,
              this.getZ(),
              20,
              0.3,
              0.4,
              0.3,
              0.05
            );
            serverLevel.playSound(
              null,
              this.getX(),
              this.getY(),
              this.getZ(),
              net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
              SoundSource.NEUTRAL,
              1.0F,
              1.2F
            );
          }
        }
        return InteractionResult.SUCCESS;
      } else {
        if (this.level() instanceof ServerLevel serverLevel) {
          serverLevel.sendParticles(
            ParticleTypes.SMOKE,
            this.getX(),
            this.getY() + 0.5,
            this.getZ(),
            6,
            0.2,
            0.1,
            0.2,
            0.01
          );
        }
        return InteractionResult.FAIL;
      }
    }

    if (
      !this.isTamedByOwner() && !this.isBaby() && stack.is(ModItems.GAS_CAN)
    ) {
      if (
        this.getVariant() == VARIANT_NIGHT || this.getVariant() == VARIANT_ALPHA
      ) {
        if (this.level() instanceof ServerLevel serverLevel) {
          serverLevel.sendParticles(
            ParticleTypes.SMOKE,
            this.getX(),
            this.getY() + 0.5,
            this.getZ(),
            6,
            0.2,
            0.1,
            0.2,
            0.01
          );
        }
        return InteractionResult.FAIL;
      }

      if (!this.level().isClientSide()) {
        if (this.level() instanceof ServerLevel serverLevel) {
          serverLevel.sendParticles(
            ParticleTypes.CLOUD,
            this.getX(),
            this.getY() + 0.5,
            this.getZ(),
            6,
            0.2,
            0.1,
            0.2,
            0.01
          );
          serverLevel.playSound(
            null,
            this.getX(),
            this.getY(),
            this.getZ(),
            ModSounds.PEDITO_FART,
            SoundSource.NEUTRAL,
            1.0F,
            0.7F
          );
        }
        if (!player.getAbilities().instabuild) {
          stack.shrink(1);
        }

        if (this.random.nextFloat() < 0.35F) {
          this.setOwnerCustom(player);
          this.level().broadcastEntityEvent(this, (byte) 7);
          if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
              ParticleTypes.PORTAL,
              this.getX(),
              this.getY() + 0.5,
              this.getZ(),
              10,
              0.3,
              0.4,
              0.3,
              0.02
            );
            serverLevel.playSound(
              null,
              this.getX(),
              this.getY(),
              this.getZ(),
              ModSounds.PEDITO_FART,
              SoundSource.NEUTRAL,
              1.0F,
              1.2F
            );
          }
        } else {
          this.level().broadcastEntityEvent(this, (byte) 6);
        }
      }
      return InteractionResult.SUCCESS;
    }

    if (this.isTamedByOwner() && this.isOwnerCustom(player) && !this.isBaby()) {
      int targetTier = -1;
      if (stack.is(ModItems.GAS_CAN_COPPER)) targetTier = 1;
      else if (stack.is(ModItems.GAS_CAN_IRON)) targetTier = 2;
      else if (stack.is(ModItems.GAS_CAN_GOLD)) targetTier = 3;
      else if (stack.is(ModItems.GAS_CAN_DIAMOND)) targetTier = 4;
      else if (stack.is(ModItems.GAS_CAN_NETHERITE)) targetTier = 5;

      if (targetTier != -1 && targetTier != this.getTier()) {
        if (!this.level().isClientSide()) {
          if (!player.getAbilities().instabuild) stack.shrink(1);
          this.setTier(targetTier);
          this.heal(this.getMaxHealth());
          if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
              ParticleTypes.HAPPY_VILLAGER,
              this.getX(),
              this.getY() + 0.5,
              this.getZ(),
              15,
              0.3,
              0.4,
              0.3,
              0.05
            );
            serverLevel.playSound(
              null,
              this.getX(),
              this.getY(),
              this.getZ(),
              ModSounds.PEDITO_SPRAY,
              SoundSource.NEUTRAL,
              1.0F,
              1.0F
            );
            serverLevel.playSound(
              null,
              this.getX(),
              this.getY(),
              this.getZ(),
              net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
              SoundSource.NEUTRAL,
              0.5F,
              1.0F
            );
          }
        }
        return InteractionResult.SUCCESS;
      }
    }

    if (
      this.isTamedByOwner() &&
      this.isOwnerCustom(player) &&
      !this.isBaby() &&
      stack.isEmpty()
    ) {
      if (!this.level().isClientSide()) {
        this.setSittingCustom(!this.isSittingCustom());
      }
      return InteractionResult.SUCCESS;
    }

    return super.mobInteract(player, hand);
  }

  @Override
  protected int getBaseExperienceReward(ServerLevel level) {
    if (this.isBaby()) return 0;
    return 1 + this.random.nextInt(2);
  }

  /**
   * Equivalente a minecraft:behavior.move_towards_target: cuando el Pedito no esta domesticado y
   * tiene un objetivo (jugador cercano), se acerca sin atacar, hasta quedar a distancia corta.
   */
  static class ApproachTargetGoal extends Goal {

    private final PeditoEntity pedito;
    private final double speed;
    private final double withinRadius;
    private int timeToRecalcPath;

    ApproachTargetGoal(PeditoEntity pedito, double speed, double withinRadius) {
      this.pedito = pedito;
      this.speed = speed;
      this.withinRadius = withinRadius;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
      LivingEntity target = this.pedito.getTarget();
      return (
        !this.pedito.isTamedByOwner() &&
        target != null &&
        target.isAlive() &&
        this.pedito.distanceToSqr(target) >
          this.withinRadius * this.withinRadius
      );
    }

    @Override
    public boolean canContinueToUse() {
      return this.canUse();
    }

    @Override
    public void start() {
      this.timeToRecalcPath = 0;
    }

    @Override
    public void tick() {
      LivingEntity target = this.pedito.getTarget();
      if (target != null) {
        this.pedito
          .getLookControl()
          .setLookAt(target, 10.0F, (float) this.pedito.getMaxHeadXRot());
        if (--this.timeToRecalcPath <= 0) {
          this.timeToRecalcPath = 10;
          this.pedito
            .getMoveControl()
            .setWantedPosition(
              target.getX(),
              target.getY() + target.getBbHeight() / 2.0,
              target.getZ(),
              this.speed
            );
        }
      }
    }

    @Override
    public void stop() {
      this.pedito.getNavigation().stop();
    }
  }

  /** Equivalente a stay_while_sitting: cuando esta sentado, no se mueve. */
  static class CustomSitGoal extends Goal {

    private final PeditoEntity pedito;

    CustomSitGoal(PeditoEntity pedito) {
      this.pedito = pedito;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
      return this.pedito.isTamedByOwner() && this.pedito.isSittingCustom();
    }

    @Override
    public boolean canContinueToUse() {
      return this.canUse();
    }

    @Override
    public void start() {
      this.pedito.getNavigation().stop();
    }
  }

  /**
   * Reemplazo casero de FlyGoal: cuando el Pedito no tiene nada mas que hacer, de vez en
   * cuando elige un punto cercano al azar y vuela hacia el. Evita depender de una clase
   * interna del juego cuyo nombre/paquete exacto en 26.2 no se pudo confirmar con certeza.
   */
  /** Equivalente a behavior.follow_owner. */
  static class CustomFollowOwnerGoal extends Goal {

    private final PeditoEntity pedito;
    private final double speed;
    private final float minDistance;
    private final float maxDistance;
    private int timeToRecalcPath;

    CustomFollowOwnerGoal(
      PeditoEntity pedito,
      double speed,
      float minDistance,
      float maxDistance
    ) {
      this.pedito = pedito;
      this.speed = speed;
      this.minDistance = minDistance;
      this.maxDistance = maxDistance;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
      if (
        !this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()
      ) return false;
      Player owner = this.pedito.getOwnerCustom();
      return (
        owner != null &&
        this.pedito.distanceToSqr(owner) >
          (double) (this.maxDistance * this.maxDistance)
      );
    }

    @Override
    public boolean canContinueToUse() {
      if (this.pedito.isSittingCustom()) return false;
      Player owner = this.pedito.getOwnerCustom();
      return (
        owner != null &&
        this.pedito.distanceToSqr(owner) >
          (double) (this.minDistance * this.minDistance)
      );
    }

    @Override
    public void start() {
      this.timeToRecalcPath = 0;
    }

    @Override
    public void tick() {
      Player owner = this.pedito.getOwnerCustom();
      if (owner != null) {
        this.pedito
          .getLookControl()
          .setLookAt(owner, 10.0F, (float) this.pedito.getMaxHeadXRot());
        if (this.pedito.distanceToSqr(owner) > 1600.0D) {
          this.pedito.teleportTo(owner.getX(), owner.getY(), owner.getZ());
          this.pedito.getNavigation().stop();
        } else if (--this.timeToRecalcPath <= 0) {
          this.timeToRecalcPath = 10;
          this.pedito.getNavigation().moveTo(owner, this.speed);
        }
      }
    }

    @Override
    public void stop() {
      this.pedito.getNavigation().stop();
    }
  }

  private void teleportToOwner(Player owner) {
    net.minecraft.core.BlockPos targetPos = owner.blockPosition();
    for (int i = 0; i < 10; i++) {
      int dx = this.random.nextInt(7) - 3;
      int dy = this.random.nextInt(3) - 1;
      int dz = this.random.nextInt(7) - 3;
      net.minecraft.core.BlockPos pos = targetPos.offset(dx, dy, dz);
      if (!this.isSolidBlock(pos)) {
        this.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (this.getNavigation() != null) this.getNavigation().stop();
        return;
      }
    }
    this.teleportTo(owner.getX(), owner.getY(), owner.getZ());
  }

  public boolean isSolidBlock(net.minecraft.core.BlockPos pos) {
    net.minecraft.world.level.block.state.BlockState state =
      this.level().getBlockState(pos);
    return (
      !state.isAir() && !state.getCollisionShape(this.level(), pos).isEmpty()
    );
  }

  private net.minecraft.core.BlockPos findSafePositionNearby(
    net.minecraft.core.BlockPos startPos
  ) {
    net.minecraft.core.BlockPos[] offsets = {
      startPos.above(),
      startPos.below(),
      startPos.north(),
      startPos.south(),
      startPos.east(),
      startPos.west(),
    };
    for (net.minecraft.core.BlockPos pos : offsets) {
      if (!isSolidBlock(pos)) {
        return pos;
      }
    }
    net.minecraft.core.BlockPos up2 = startPos.above(2);
    if (!isSolidBlock(up2)) {
      return up2;
    }
    return null;
  }

  static class RandomFlyWanderGoal extends Goal {

    private final PeditoEntity pedito;
    private double targetX;
    private double targetY;
    private double targetZ;
    private int idleTime;
    private int moveTime;

    RandomFlyWanderGoal(PeditoEntity pedito) {
      this.pedito = pedito;
      this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
      if (this.pedito.isWhistleActive()) return false;
      if (this.pedito.isSittingCustom()) return false;
      if (this.pedito.getTarget() != null) return false;
      if (
        this.pedito.isTamedByOwner() && this.pedito.getOwnerCustom() != null
      ) {
        if (this.pedito.distanceToSqr(this.pedito.getOwnerCustom()) < 1600.0D) {
          return false;
        }
      }
      if (this.idleTime > 0) {
        this.idleTime--;
        return false;
      }
      // Juegan y exploran más si son bebés o salvajes
      int chance = this.pedito.isBaby()
        ? 10
        : !this.pedito.isTamedByOwner()
          ? 20
          : 60;
      if (this.pedito.getRandom().nextInt(chance) != 0) {
        return false;
      }

      RandomSource rand = this.pedito.getRandom();
      double rx = this.pedito.getX() + (rand.nextDouble() - 0.5) * 16.0;
      double rz = this.pedito.getZ() + (rand.nextDouble() - 0.5) * 16.0;

      int groundY = (int) this.pedito.getY();
      while (
        groundY > this.pedito.level().getMinY() &&
        this.pedito
          .level()
          .getBlockState(
            new net.minecraft.core.BlockPos((int) rx, groundY, (int) rz)
          )
          .isAir()
      ) {
        groundY--;
      }
      if (groundY <= this.pedito.level().getMinY()) {
        groundY = (int) this.pedito.getY() - 2 - rand.nextInt(3);
      }

      double ry = groundY + 1.0 + rand.nextDouble() * 2.0;

      if (ry > this.pedito.level().getMaxY() - 10) {
        ry = this.pedito.level().getMaxY() - 20;
      }
      if (ry < this.pedito.level().getMinY() + 5) {
        ry = this.pedito.level().getMinY() + 10;
      }

      net.minecraft.core.BlockPos targetPos = new net.minecraft.core.BlockPos(
        (int) rx,
        (int) ry,
        (int) rz
      );
      if (this.pedito.isSolidBlock(targetPos)) {
        return false;
      }

      this.targetX = rx;
      this.targetY = ry;
      this.targetZ = rz;
      return true;
    }

    @Override
    public boolean canContinueToUse() {
      if (this.pedito.isWhistleActive()) return false;
      if (
        this.pedito.isSittingCustom() || this.pedito.getTarget() != null
      ) return false;
      if (
        this.pedito.isTamedByOwner() &&
        this.pedito.getOwnerCustom() != null &&
        this.pedito.distanceToSqr(this.pedito.getOwnerCustom()) < 1600.0D
      ) {
        return false;
      }
      return (
        this.moveTime > 0 &&
        this.pedito.distanceToSqr(this.targetX, this.targetY, this.targetZ) >
          2.0D
      );
    }

    @Override
    public void start() {
      this.moveTime = 100; // max 5 seconds of moving
      this.pedito
        .getMoveControl()
        .setWantedPosition(this.targetX, this.targetY, this.targetZ, 1.0D);
    }

    @Override
    public void tick() {
      this.moveTime--;
    }

    @Override
    public void stop() {
      // Stop move control by setting wanted pos to current
      this.pedito
        .getMoveControl()
        .setWantedPosition(
          this.pedito.getX(),
          this.pedito.getY(),
          this.pedito.getZ(),
          0.0D
        );
      this.idleTime = this.pedito.isBaby()
        ? 20 + this.pedito.getRandom().nextInt(20)
        : !this.pedito.isTamedByOwner()
          ? 40 + this.pedito.getRandom().nextInt(40)
          : 60 + this.pedito.getRandom().nextInt(100);
    }
  }

  public enum SquadRole {
    VANGUARD,
    ARTILLERY,
    TACTICAL,
    ROYAL_GUARD,
    SOLO,
  }

  private SquadRole currentRole = SquadRole.SOLO;

  public SquadRole getSquadRole() {
    return this.currentRole;
  }

  private void updateSquadRole() {
    if (
      this.level().isClientSide() ||
      this.tickCount % 20 != 0 ||
      !this.isTamedByOwner()
    ) {
      return;
    }

    net.minecraft.world.entity.player.Player owner = this.getOwnerCustom();
    if (owner == null) return;

    java.util.List<PeditoEntity> allies = this.level().getEntitiesOfClass(
      PeditoEntity.class,
      owner.getBoundingBox().inflate(64.0D),
      e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
    );

    int total = allies.size();
    if (total <= 3) {
      int v = this.getVariant();
      if (v == VARIANT_ALPHA) this.currentRole = SquadRole.VANGUARD;
      else if (v == VARIANT_GOLDEN) this.currentRole = SquadRole.TACTICAL;
      else this.currentRole = SquadRole.SOLO;
      return;
    }

    PeditoEntity alpha = null;
    PeditoEntity golden = null;
    java.util.List<PeditoEntity> pool = new java.util.ArrayList<>();
    for (PeditoEntity ally : allies) {
      if (ally.getVariant() == VARIANT_ALPHA && alpha == null) {
        alpha = ally;
      } else if (ally.getVariant() == VARIANT_GOLDEN && golden == null) {
        golden = ally;
      } else {
        pool.add(ally);
      }
    }

    pool.sort((p1, p2) -> {
      int score1 = p1.getTier() * 1000 + (int) (p1.getHealth() * 10);
      int score2 = p2.getTier() * 1000 + (int) (p2.getHealth() * 10);
      
      // Nocturnal priority boost during nighttime for offensive roles
      if (p1.level().isDarkOutside()) {
        if (p1.getVariant() == VARIANT_NIGHT) score1 += 50000;
        if (p2.getVariant() == VARIANT_NIGHT) score2 += 50000;
      }
      
      if (score1 != score2) return Integer.compare(score2, score1);
      return Integer.compare(p1.getId(), p2.getId());
    });

    int poolTotal = pool.size();
    boolean needGuard = owner.getHealth() < owner.getMaxHealth() * 0.4f;
    int guardCount = needGuard ? (int) (poolTotal * 0.2) : 0;
    int vanguardCount = (int) (poolTotal * 0.35);
    int tacticalCount = (int) (poolTotal * 0.35);

    if (this == alpha) {
      this.currentRole = SquadRole.VANGUARD;
    } else if (this == golden) {
      this.currentRole = SquadRole.TACTICAL;
    } else {
      int index = pool.indexOf(this);
      if (index == -1) return;

      if (index < vanguardCount) {
        this.currentRole = SquadRole.VANGUARD;
      } else if (index < vanguardCount + tacticalCount) {
        this.currentRole = SquadRole.TACTICAL;
      } else if (index < poolTotal - guardCount) {
        this.currentRole = SquadRole.ARTILLERY;
      } else {
        this.currentRole = SquadRole.ROYAL_GUARD;
      }
    }
  }

  private void updateAlphaStatus() {
    if (
      this.level().isClientSide() ||
      this.tickCount % 20 != 0 ||
      !this.isTamedByOwner()
    ) {
      return;
    }

    Player owner = this.getOwnerCustom();
    if (owner == null) return;

    // Get all allies
    java.util.List<PeditoEntity> allies = this.level().getEntitiesOfClass(
      PeditoEntity.class,
      owner.getBoundingBox().inflate(64.0D),
      e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
    );

    // To prevent race conditions, only the ally with the lowest entity ID performs the master check for the group
    if (
      allies.isEmpty() ||
      allies.stream().mapToInt(Entity::getId).min().orElse(this.getId()) !=
        this.getId()
    ) {
      return;
    }

    // Count Gold (Tier 3) and Diamond (Tier 4) allies
    boolean hasGold = false;
    boolean hasDiamond = false;
    PeditoEntity currentAlpha = null;
    java.util.List<PeditoEntity> candidatePeditos = new java.util.ArrayList<>();

    for (PeditoEntity ally : allies) {
      if (ally.getVariant() == VARIANT_ALPHA) {
        if (currentAlpha == null) {
          currentAlpha = ally;
        } else {
          // Demote duplicate alphas to prevent multiple alphas
          ally.setVariant(VARIANT_NORMAL);
          ally.setTier(0);
        }
      } else {
        if (ally.getTier() == 3) {
          hasGold = true;
        } else if (ally.getTier() == 4) {
          hasDiamond = true;
        }
        // Candidates are adult, tamed, not sitting, not currently tier 3/4/5
        if (!ally.isBaby() && ally.getTier() < 3) {
          candidatePeditos.add(ally);
        }
      }
    }

    // Check if an Alpha owned by this player already exists globally in the loaded level,
    // to prevent promoting a new Alpha when the existing one is temporarily out of the local 64-block range.
    if (
      currentAlpha == null && this.level() instanceof ServerLevel serverLevel
    ) {
      for (Entity entity : serverLevel.getAllEntities()) {
        if (
          entity instanceof PeditoEntity pedito &&
          pedito.isAlive() &&
          pedito.isTamedByOwner() &&
          pedito.getOwnerCustom() == owner &&
          pedito.getVariant() == VARIANT_ALPHA
        ) {
          currentAlpha = pedito;
          break;
        }
      }
    }

    boolean shouldHaveAlpha = hasGold && hasDiamond;

    if (shouldHaveAlpha) {
      if (currentAlpha == null) {
        // We need to promote an alpha.
        // If we have candidates, promote one of them.
        // If no low-tier candidates, we can promote any non-baby ally.
        if (candidatePeditos.isEmpty()) {
          for (PeditoEntity ally : allies) {
            if (!ally.isBaby()) {
              candidatePeditos.add(ally);
            }
          }
        }

        if (!candidatePeditos.isEmpty()) {
          PeditoEntity chosen = candidatePeditos.get(
            this.random.nextInt(candidatePeditos.size())
          );
          chosen.setVariant(VARIANT_ALPHA);
          chosen.setTier(0); // Alpha doesn't use standard tier armor textures
          // Heal fully on promotion
          chosen.setHealth(chosen.getMaxHealth());

          // Play spawn sound / alert particles
          if (chosen.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(
              null,
              chosen.getX(),
              chosen.getY(),
              chosen.getZ(),
              ModSounds.PEDITO_SUMMON,
              SoundSource.NEUTRAL,
              2.0F,
              0.6F
            ); // Deep voice!
            serverLevel.sendParticles(
              ParticleTypes.HAPPY_VILLAGER,
              chosen.getX(),
              chosen.getY() + 0.5,
              chosen.getZ(),
              20,
              0.5,
              0.5,
              0.5,
              0.05
            );
          }
        }
      }
    } else {
      // Once promoted, the domestic Alpha retains its form even if the Gold/Diamond allies are temporarily out of range,
      // to avoid repeated promotion/demotion cycles and unnecessary loud sounds when the owner moves away.
      // "el debe de aparecer una vez y sio no muere el pedito que se convirtio debe guardar laforma"
    }
  }
}

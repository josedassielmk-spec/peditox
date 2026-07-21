package net.pedito.mod.entity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

public class PeditoFormationGoal extends Goal {

  public enum FormationType {
    DEFAULT(0, "Estándar", "Standard Semicircle"),
    COLUMN(1, "Columna de Desfile", "Parade Column"),
    LINE(2, "Muro de Honor / Línea", "Front Guard Line"),
    V_SHAPE(3, "Cuña Táctica / V", "Tactical V-Wedge"),
    BOX(4, "Cuadrado Batallón", "Battalion Square"),
    CIRCLE(5, "Aura Ceremonial / Círculo", "Ceremonial Circle"),
    DIAGONAL(6, "Flanco Militar Escalón", "Diagonal Echelon");

    private final int id;
    private final String nameEs;
    private final String nameEn;

    FormationType(int id, String nameEs, String nameEn) {
      this.id = id;
      this.nameEs = nameEs;
      this.nameEn = nameEn;
    }

    public int getId() {
      return id;
    }

    public String getNameEs() {
      return nameEs;
    }

    public String getNameEn() {
      return nameEn;
    }

    public static FormationType byId(int id) {
      for (FormationType t : values()) {
        if (t.id == id) return t;
      }
      return DEFAULT;
    }
  }

  private static final java.util.Map<
    java.util.UUID,
    Integer
  > PLAYER_FORMATIONS = new java.util.concurrent.ConcurrentHashMap<>();

  public static int getPlayerFormation(java.util.UUID uuid) {
    return PLAYER_FORMATIONS.getOrDefault(uuid, 0);
  }

  public static void setPlayerFormation(java.util.UUID uuid, int formation) {
    PLAYER_FORMATIONS.put(uuid, formation);
  }

  private final PeditoEntity pedito;
  private double hoverX;
  private double hoverY;
  private double hoverZ;

  public PeditoFormationGoal(PeditoEntity pedito) {
    this.pedito = pedito;
    this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
  }

  @Override
  public boolean canUse() {
    if (
      !this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()
    ) return false;

    // La formación solo se activa cuando el jugador los llama de forma activa con el silbato
    if (!this.pedito.isWhistleActive()) {
      return false;
    }

    // Peditos currently holding a front-guard slot are handled entirely by
    // PeditoFrontGuardGoal; this goal must not also try to place them.
    if (PeditoFrontGuardGoal.isFrontGuard(this.pedito)) return false;

    Player owner = this.pedito.getOwnerCustom();
    if (owner == null) return false;

    return this.pedito.distanceToSqr(owner) <= 225.0D;
  }

  @Override
  public boolean canContinueToUse() {
    if (
      !this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()
    ) return false;

    if (!this.pedito.isWhistleActive()) {
      return false;
    }

    if (PeditoFrontGuardGoal.isFrontGuard(this.pedito)) return false;

    Player owner = this.pedito.getOwnerCustom();
    if (owner == null) return false;

    return this.pedito.distanceToSqr(owner) <= 225.0D;
  }

  @Override
  public void start() {}

  @Override
  public void tick() {
    Player owner = this.pedito.getOwnerCustom();
    if (owner == null) return;

    // Front-guard peditos are excluded here too: they occupy their own
    // fixed flank slots and must not be counted when spacing out the
    // rear semicircle, or the arc math would leave a gap where they
    // "should" be.
    List<PeditoEntity> allies = this.pedito
      .level()
      .getEntitiesOfClass(
        PeditoEntity.class,
        owner.getBoundingBox().inflate(64.0D),
        e ->
          e.isAlive() &&
          e.isTamedByOwner() &&
          e.getOwnerCustom() == owner &&
          !PeditoFrontGuardGoal.isFrontGuard(e)
      );
    allies.sort(
      Comparator.comparingInt(net.minecraft.world.entity.Entity::getId)
    );

    int total = allies.size();
    int myIndex = allies.indexOf(this.pedito);
    if (myIndex == -1) myIndex = 0;

    double time = this.pedito.tickCount * 0.05;
    boolean isPlayerMoving = owner.getDeltaMovement().lengthSqr() > 0.01;

    // Base angle points directly behind the player
    double baseAngle = Math.toRadians(owner.getYRot());

    // Dirección hacia atrás y hacia los flancos
    double backX = Math.sin(baseAngle);
    double backZ = -Math.cos(baseAngle);
    double rightX = Math.cos(baseAngle);
    double rightZ = Math.sin(baseAngle);

    int currentFormId = getPlayerFormation(owner.getUUID());
    FormationType formation = FormationType.byId(currentFormId);

    if (formation == FormationType.DEFAULT) {
      if (isPlayerMoving) {
        double spread = 1.5;
        double row = Math.floor(myIndex / 2.0) + 1;
        double side = myIndex % 2 == 0 ? 1 : -1;

        if (myIndex == 0) {
          row = 0;
          side = 0;
        }
        double offsetX = backX * (row * 1.5) + rightX * (side * spread * row);
        double offsetZ = backZ * (row * 1.5) + rightZ * (side * spread * row);

        double offsetY = 1.5 + Math.sin(time + myIndex) * 0.3;

        this.hoverX = owner.getX() + offsetX;
        this.hoverY = owner.getY() + offsetY;
        this.hoverZ = owner.getZ() + offsetZ;
      } else {
        // Semicircle behind the player
        double arcSpan = Math.PI; // 180 degrees
        if (total <= 6) {
          double fraction = total == 1 ? 0.5 : (double) myIndex / (total - 1);
          double currentAngle = baseAngle - arcSpan / 2.0 + fraction * arcSpan;
          currentAngle += Math.sin(time * 0.5) * 0.1;

          double radius = 3.5;
          double offsetX = Math.sin(currentAngle) * radius;
          double offsetZ = -Math.cos(currentAngle) * radius;
          double offsetY = 1.0 + Math.sin(time * 1.5 + myIndex) * 0.4;

          this.hoverX = owner.getX() + offsetX;
          this.hoverY = owner.getY() + offsetY;
          this.hoverZ = owner.getZ() + offsetZ;
        } else {
          // Multiple semicircles behind the player
          int ringIndex = myIndex % 3;
          double radius = 3.5 + ringIndex * 1.5;
          int countInRing = (int) Math.ceil(total / 3.0);
          int posInRing = myIndex / 3;

          double fraction =
            countInRing == 1 ? 0.5 : (double) posInRing / (countInRing - 1);
          double currentAngle = baseAngle - arcSpan / 2.0 + fraction * arcSpan;
          currentAngle += Math.sin(time * 0.5 + ringIndex) * 0.1;

          double offsetX = Math.sin(currentAngle) * radius;
          double offsetZ = -Math.cos(currentAngle) * radius;
          double offsetY =
            1.0 + ringIndex * 0.8 + Math.sin(time + myIndex) * 0.3;

          this.hoverX = owner.getX() + offsetX;
          this.hoverY = owner.getY() + offsetY;
          this.hoverZ = owner.getZ() + offsetZ;
        }
      }
      // En formación estándar, miran al dueño
      this.pedito.getLookControl().setLookAt(owner, 10.0F, 10.0F);
    } else {
      // Formaciones militares / desfile ceremonial
      switch (formation) {
        case COLUMN: {
          // Columna en fila única india detrás del jugador
          double dist = 2.0 + myIndex * 1.4;
          double offsetX = backX * dist;
          double offsetZ = backZ * dist;
          // Ola sinbólica sincronizada verticalmente para el desfile
          double offsetY = 1.2 + Math.sin(time * 2.0 + myIndex * 0.4) * 0.15;

          this.hoverX = owner.getX() + offsetX;
          this.hoverY = owner.getY() + offsetY;
          this.hoverZ = owner.getZ() + offsetZ;
          break;
        }
        case LINE: {
          // Línea horizontal (muro de honor) perpendicular a la dirección del jugador
          double side = myIndex % 2 == 0 ? 1.0 : -1.0;
          double col = Math.floor((myIndex + 1) / 2.0);
          double offsetX = backX * 2.5 + rightX * (side * col * 1.3);
          double offsetZ = backZ * 2.5 + rightZ * (side * col * 1.3);
          double offsetY = 1.2 + Math.sin(time * 1.5 + myIndex * 0.3) * 0.15;

          this.hoverX = owner.getX() + offsetX;
          this.hoverY = owner.getY() + offsetY;
          this.hoverZ = owner.getZ() + offsetZ;
          break;
        }
        case V_SHAPE: {
          // Majestuosa formación de cuña simétrica en V
          double side = myIndex % 2 == 0 ? 1.0 : -1.0;
          double row = Math.floor((myIndex + 1) / 2.0);
          double offsetX =
            backX * (row * 1.3 + 2.0) + rightX * (side * row * 1.3);
          double offsetZ =
            backZ * (row * 1.3 + 2.0) + rightZ * (side * row * 1.3);
          double offsetY = 1.2 + Math.sin(time * 1.5 + myIndex * 0.3) * 0.15;

          this.hoverX = owner.getX() + offsetX;
          this.hoverY = owner.getY() + offsetY;
          this.hoverZ = owner.getZ() + offsetZ;
          break;
        }
        case BOX: {
          // Grid compacto de 3 columnas
          int col = (myIndex % 3) - 1; // -1, 0, 1
          int row = myIndex / 3 + 1; // 1, 2, 3...
          double offsetX = backX * (row * 1.6 + 1.5) + rightX * (col * 1.6);
          double offsetZ = backZ * (row * 1.6 + 1.5) + rightZ * (col * 1.6);
          double offsetY = 1.2 + Math.sin(time * 1.5 + row * 0.5) * 0.15;

          this.hoverX = owner.getX() + offsetX;
          this.hoverY = owner.getY() + offsetY;
          this.hoverZ = owner.getZ() + offsetZ;
          break;
        }
        case CIRCLE: {
          // Círculo perfecto rotatorio ceremonial alrededor del jugador
          double radius = 3.5 + (total > 8 ? 1.0 : 0.0);
          double angle =
            baseAngle + myIndex * ((2 * Math.PI) / total) + time * 0.3; // Rotación lenta
          double offsetX = Math.sin(angle) * radius;
          double offsetZ = -Math.cos(angle) * radius;
          double offsetY = 1.0 + Math.sin(time * 2.0 + myIndex * 1.2) * 0.2;

          this.hoverX = owner.getX() + offsetX;
          this.hoverY = owner.getY() + offsetY;
          this.hoverZ = owner.getZ() + offsetZ;
          break;
        }
        case DIAGONAL: {
          // Escalón diagonal (staggered flank) hacia la derecha
          double dist = 2.0 + myIndex * 1.3;
          double side = myIndex * 1.1;
          double offsetX = backX * dist + rightX * side;
          double offsetZ = backZ * dist + rightZ * side;
          double offsetY = 1.2 + Math.sin(time * 1.5 + myIndex * 0.4) * 0.15;

          this.hoverX = owner.getX() + offsetX;
          this.hoverY = owner.getY() + offsetY;
          this.hoverZ = owner.getZ() + offsetZ;
          break;
        }
      }

      // En desfile militar marchan mirando EXACTAMENTE HACIA ADELANTE (como el jugador)
      double lookX = this.pedito.getX() - backX * 10.0;
      double lookZ = this.pedito.getZ() - backZ * 10.0;
      this.pedito
        .getLookControl()
        .setLookAt(lookX, this.pedito.getEyeY(), lookZ, 10.0F, 10.0F);
    }

    net.minecraft.core.BlockPos targetPos =
      net.minecraft.core.BlockPos.containing(
        this.hoverX,
        this.hoverY,
        this.hoverZ
      );
    int adjustmentCount = 0;
    while (this.pedito.isSolidBlock(targetPos) && adjustmentCount < 5) {
      this.hoverY += 1.0;
      targetPos = net.minecraft.core.BlockPos.containing(
        this.hoverX,
        this.hoverY,
        this.hoverZ
      );
      adjustmentCount++;
    }

    double distSq = this.pedito.distanceToSqr(
      this.hoverX,
      this.hoverY,
      this.hoverZ
    );
    if (this.pedito.isWhistleActive()) {
      if (distSq > 4.0D) {
        this.pedito
          .getMoveControl()
          .setWantedPosition(this.hoverX, this.hoverY, this.hoverZ, 1.5D);
      } else {
        // If the whistle is active and they are close, glide them directly to the target position
        // to lock them into the grid perfectly with zero horizontal drift!
        double lerpFactor = 0.20D;
        double newX = net.minecraft.util.Mth.lerp(
          lerpFactor,
          this.pedito.getX(),
          this.hoverX
        );
        double newY = net.minecraft.util.Mth.lerp(
          lerpFactor,
          this.pedito.getY(),
          this.hoverY
        );
        double newZ = net.minecraft.util.Mth.lerp(
          lerpFactor,
          this.pedito.getZ(),
          this.hoverZ
        );
        this.pedito.setPos(newX, newY, newZ);

        // Dampen the velocity so they float stably without sliding
        this.pedito.setDeltaMovement(
          this.pedito.getDeltaMovement().scale(0.5D)
        );
      }
    } else {
      if (distSq > 0.25) {
        this.pedito
          .getMoveControl()
          .setWantedPosition(this.hoverX, this.hoverY, this.hoverZ, 1.0D);
      }
    }
  }
}

package cope.nebula.client.feature.module.combat;

import cope.nebula.asm.duck.IEntityPlayer;
import cope.nebula.client.events.EntityAddedEvent;
import cope.nebula.client.events.EntityRemoveEvent;
import cope.nebula.client.events.PacketEvent;
import cope.nebula.client.events.PacketEvent.Direction;
import cope.nebula.client.feature.module.Module;
import cope.nebula.client.feature.module.ModuleCategory;
import cope.nebula.client.value.Value;
import cope.nebula.util.internal.timing.Stopwatch;
import cope.nebula.util.internal.timing.TimeFormat;
import cope.nebula.util.renderer.FontUtil;
import cope.nebula.util.renderer.RenderUtil;
import cope.nebula.util.world.BlockUtil;
import cope.nebula.util.world.RaycastUtil;
import cope.nebula.util.world.damage.ExplosionUtil;
import cope.nebula.util.world.entity.CrystalUtil;
import cope.nebula.util.world.entity.player.inventory.InventorySpace;
import cope.nebula.util.world.entity.player.inventory.InventoryUtil;
import cope.nebula.util.world.entity.player.inventory.SwapType;
import cope.nebula.util.world.entity.player.rotation.AngleUtil;
import cope.nebula.util.world.entity.player.rotation.Bone;
import cope.nebula.util.world.entity.player.rotation.Rotation;
import cope.nebula.util.world.entity.player.rotation.RotationType;
import io.netty.util.internal.ConcurrentSet;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.ClickType;
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.network.play.server.SPacketSpawnObject;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AutoCrystal extends Module {
    public static AutoCrystal INSTANCE;

    /**
     * If we should pause AutoCrystal because other modules require rotations/block placements/attacks
     */
    private static boolean paused = false;

    /**
     * Pauses AutoCrystal
     */
    public static boolean pause() {
        return paused = true;
    }

    /**
     * Resumes AutoCrystal
     * @return
     */
    public static boolean resume() {
        return paused = false;
    }

    /**
     * Checks if we are paused
     * @return
     */
    public static boolean isPaused() {
        if (INSTANCE.isOff()) {
            return true;
        }

        return paused;
    }

    public AutoCrystal() {
        super("AutoCrystal", ModuleCategory.COMBAT, "Automatically places and explodes crystals");
        INSTANCE = this;
    }

    // placements
    public static final Value<Boolean> place = new Value<>("Place", true);
    public static final Value<Float> placeSpeed = new Value<>(place, "PlaceSpeed", 19.0f, 1.0f, 20.0f);
    public static final Value<Double> placeRange = new Value<>(place, "PlaceRange", 4.5, 1.0, 6.0);
    public static final Value<Double> placeWallRange = new Value<>(place, "PlaceWallRange", 3.0, 1.0, 6.0);
    public static final Value<Interact> interact = new Value<>(place, "Interact", Interact.NORMAL);
    public static final Value<Protocol> protocol = new Value<>(place, "Protocol", Protocol.NATIVE);
    public static final Value<Boolean> ignoreTerrain = new Value<>(place, "IgnoreTerrain", true);
    public static final Value<Boolean> facePlace = new Value<>(place, "Faceplace", false);
    public static final Value<Integer> armorPercent = new Value<>(facePlace, "ArmorPercent", 20, 1, 50);
    public static final Value<Float> faceHealth = new Value<>(facePlace, "FaceHealth", 10.0f, 1.0f, 20.0f);

    // exploding
    public static final Value<Boolean> explode = new Value<>("Explode", true);
    public static final Value<Float> explodeSpeed = new Value<>(explode, "ExplodeSpeed", 20.0f, 1.0f, 20.0f);
    public static final Value<Double> explodeRange = new Value<>(explode, "ExplodeRange", 4.5, 1.0, 6.0);
    public static final Value<Double> explodeWallRange = new Value<>(explode, "ExplodeWallRange", 3.0, 1.0, 6.0);
    public static final Value<Boolean> inhibit = new Value<>(explode, "Inhibit", true);
    public static final Value<Integer> ticksExisted = new Value<>(explode, "TicksExisted", 1, 0, 10);
    public static final Value<SwapType> antiWeakness = new Value<>(explode, "AntiWeakness", SwapType.SERVER);

    // swapping
    public static final Value<Swapping> swapping = new Value<>("Swapping", Swapping.CLIENT);
    public static final Value<Integer> swapDelay = new Value<>(swapping, "SwapDelay", 2, 0, 10);

    // rotations
    public static final Value<Rotate> rotate = new Value<>("Rotate", Rotate.SERVER);
    public static final Value<YawLimit> yawLimit = new Value<>(rotate, "YawLimit", YawLimit.SEMI);
    public static final Value<Float> maxYawRate = new Value<>(yawLimit, "MaxYawRate", 55.0f, 10.0f, 100.0f);

    // damages
    public static final Value<Float> minDamage = new Value<>("MinDamage", 4.0f, 0.0f, 10.0f);
    public static final Value<Float> maxLocal = new Value<>("MaxLocal", 12.0f, 1.0f, 20.0f);
    public static final Value<Boolean> safety = new Value<>("Safety", true);

    // misc
    public static final Value<Double> targetRange = new Value<>("TargetRange", 12.0, 6.0, 20.0);
    public static final Value<Boolean> swing = new Value<>("Swing", true);
    public static final Value<Merge> merge = new Value<>("Merge", Merge.CONFIRM);

    // targets
    private EntityPlayer target = null;
    private BlockPos placePos = null;
    private EntityEnderCrystal attackCrystal = null;

    private float damage = 0.5f;
    private int entityIdSpawn = -1;

    // rotations
    private Rotation nextRotation = Rotation.INVALID_ROTATION;

    // swapping
    private int oldSlot = -1;
    private EnumHand hand = EnumHand.MAIN_HAND;
    private int windowOld, windowCrystal;

    // crystals per second calculations
    private int crystalCount = 0;

    // timing
    private final Stopwatch placeTimer = new Stopwatch();
    private final Stopwatch explodeTimer = new Stopwatch();
    private final Stopwatch swapTimer = new Stopwatch();
    private final Stopwatch crystalCountTimer = new Stopwatch();

    private final Set<Integer> placedCrystals = new ConcurrentSet<>();
    private final Map<Integer, Stopwatch> inhibitCrystals = new ConcurrentHashMap<>();

    @Override
    public String getDisplayInfo() {
        long time = crystalCountTimer.getTime(TimeFormat.SECONDS);
        if (time == 0L) {
            return String.valueOf(crystalCount);
        }

        return String.valueOf(crystalCount / time);
    }

    @Override
    protected void onActivated() {
        crystalCountTimer.resetTime();
        paused = false;
    }

    @Override
    protected void onDeactivated() {
        target = null;
        placePos = null;
        attackCrystal = null;

        nextRotation = Rotation.INVALID_ROTATION;

        if (oldSlot != -1) {
            swapBack();
        }

        hand = EnumHand.MAIN_HAND;

        crystalCount = 0;

        placedCrystals.clear();
        inhibitCrystals.clear();

        paused = false;
    }

    @Override
    public void onRender3d() {
        if (placePos != null) {
            AxisAlignedBB box = new AxisAlignedBB(placePos)
                    .offset(-mc.getRenderManager().viewerPosX, -mc.getRenderManager().viewerPosY, -mc.getRenderManager().viewerPosZ);
            int color = new Color(122, 49, 183, 120).getRGB();

            // box render

            RenderUtil.renderFilledBox(box, color);
            RenderUtil.renderOutlinedBox(box, 3.5f, color);

            // text render

            String text = String.format("%.2f", damage);

            GlStateManager.pushMatrix();
            GlStateManager.disableDepth();

            RenderManager renderManager = mc.getRenderManager();

            GlStateManager.translate((placePos.getX() + 0.5f) - renderManager.viewerPosX, (placePos.getY() + 0.5f) - renderManager.viewerPosY, (placePos.getZ() + 0.5f) - renderManager.viewerPosZ);
            GlStateManager.glNormal3f(0.0f, 1.0f, 0.0f);
            GlStateManager.rotate(-renderManager.playerViewY, 0.0f, 1.0f, 0.0f);
            GlStateManager.rotate(renderManager.playerViewX, mc.gameSettings.thirdPersonView == 2 ? -1.0f : 1.0f, 0.0f, 0.0f);
            GlStateManager.scale(-0.02666667, -0.02666667, 0.02666667);

            GlStateManager.translate(-(FontUtil.getWidth(text) / 2.0), 0.0, 0.0);

            FontUtil.drawString(text, 0.0f, 0.0f, -1);

            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void onTick() {
        // find place position and best end crystal to attack
        findBestPlacePosition();
        findBestEndCrystal();

        // do not do anything while paused
        if (paused) {
            return;
        }

        if (explode.getValue() && attackCrystal != null) {
            // get the entity id for inhibit
            int entityId = attackCrystal.getEntityId();
            if (shouldInhibit(entityId)) {
                return;
            }

            // check if we have passed the required amount of ticks to explode a crystal
            long elapsed = explodeTimer.getTime(TimeFormat.TICKS);
            if (elapsed >= 20.0f - explodeSpeed.getValue()) {

                // reset time
                explodeTimer.resetTime();

                if (mc.player.isPotionActive(MobEffects.WEAKNESS) &&
                        !mc.player.isPotionActive(MobEffects.STRENGTH) &&
                        !antiWeakness.getValue().equals(SwapType.NONE)) {

                    // TODO: dont just swap to swords, allow axe and pick swapping as well
                }

                // send rotations and check if we should limit to the next tick
                if (!rotate.getValue().equals(Rotate.NONE) && sendRotations(AngleUtil.toEntity(attackCrystal, Bone.HEAD), yawLimit.getValue().equals(YawLimit.FULL))) {

                    // TODO
                }

                // attack the crystal
                CrystalUtil.explode(entityId, hand, swing.getValue());

                // add crystal to inhibited crystal map
                if (inhibit.getValue()) {
                    inhibitCrystals.put(entityId, new Stopwatch().resetTime());
                } else {
                    // reset crystal
                    attackCrystal = null;
                }
            }
        }

        if (place.getValue() && placePos != null) {

            // check if we have passed the required amount of ticks to place a crystal
            long elapsed = placeTimer.getTime(TimeFormat.TICKS);
            if (elapsed >= 20.0f - placeSpeed.getValue()) {

                // check if there are any crystals in our hotbar.
                // if not, we cannot continue as theres no crystals...
                if (!findCrystals()) {
                    return;
                }

                // if we cannot place because a crystal is in the way, we'll target this crystal to allow us to place
                for (Entity entity : new ArrayList<>(mc.world.loadedEntityList)) {

                    // ignore entities that are too far away (6 blocks)
                    if (entity.getDistanceSq(entity) > 36.0) {
                        continue;
                    }

                    if (entity.getPosition().equals(placePos.up()) && entity instanceof EntityEnderCrystal) {
                        attackCrystal = (EntityEnderCrystal) entity;
                        return;
                    }
                }

                // check if we can place a crystal here
                // where we pass inhibit.getValue(), it will check if theres a crystal already there.
                // if we cannot place, it'll return. as with above, this prevents unnecessary place packets
                if (!CrystalUtil.canPlaceAt(placePos, inhibit.getValue(), protocol.getValue().equals(Protocol.UPDATED))) {
                    return;
                }


                // send rotations and check if we should limit to the next tick
                if (!rotate.getValue().equals(Rotate.NONE) && sendRotations(AngleUtil.toVec(new Vec3d(placePos)), yawLimit.getValue().equals(YawLimit.SEMI))) {

                    // TODO
                }
                // reset time
                placeTimer.resetTime();

                // place crystal
                CrystalUtil.placeAt(placePos, hand, interact.getValue().equals(Interact.STRICT), swing.getValue(), rotate.getValue().rotationType);

                // if silent swap is active, we'll swap back
                if (swapping.getValue().equals(SwapType.SERVER)) {
                    swapBack();
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityAdded(EntityAddedEvent event) {
        if (event.getEntity() instanceof EntityEnderCrystal) {
            EntityEnderCrystal crystal = (EntityEnderCrystal) event.getEntity();
            if (placePos == null) {
                return;
            }

            if (placePos.equals(crystal.getPosition().down()) || crystal.getEntityId() == entityIdSpawn) {
                attackCrystal = crystal;
                placedCrystals.add(crystal.getEntityId());
            }
        }
    }

    @SubscribeEvent
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof EntityEnderCrystal) {
            if (attackCrystal == null) {
                return;
            }

            if (event.getEntity().equals(attackCrystal)) {
                placedCrystals.remove(attackCrystal.getEntityId());
                inhibitCrystals.remove(attackCrystal.getEntityId());

                attackCrystal.setDead();
                attackCrystal = null;
            }
        }
    }

    @SubscribeEvent
    public void onPacket(PacketEvent event) {
        if (event.getDirection().equals(Direction.INCOMING)) {
            if (event.getPacket() instanceof SPacketDestroyEntities) {
                SPacketDestroyEntities packet = event.getPacket();

                mc.addScheduledTask(() -> {
                    for (int entityId : packet.getEntityIDs()) {
                        if (attackCrystal != null && attackCrystal.getEntityId() == entityId) {
                            attackCrystal.setDead();
                            mc.world.removeEntity(attackCrystal);

                            if (merge.getValue().equals(Merge.CONFIRM)) {
                                mc.world.removeEntityDangerously(attackCrystal);
                            }
                        } else {
                            Entity entity = mc.world.getEntityByID(entityId);
                            if (entity == null || !(entity instanceof EntityEnderCrystal) || entity.isDead) {
                                return;
                            }

                            // we can just remove it as the server wants it removed anyway
                            entity.setDead();
                            mc.world.removeEntity(attackCrystal);
                            mc.world.removeEntityDangerously(attackCrystal);
                        }
                    }
                });
            } else if (event.getPacket() instanceof SPacketExplosion) {
                SPacketExplosion packet = event.getPacket();
                double power = packet.getStrength() * packet.getStrength();

                mc.addScheduledTask(() -> {
                    for (Entity entity : new ArrayList<>(mc.world.loadedEntityList)) {
                        if (!(entity instanceof EntityEnderCrystal) || entity.isDead) {
                            continue;
                        }

                        if (entity.getDistanceSq(packet.getX(), packet.getY(), packet.getZ()) < power) {
                            if (attackCrystal != null && attackCrystal.getEntityId() == entity.getEntityId()) {
                                attackCrystal = null;
                            }

                            entity.setDead();
                            mc.world.removeEntity(entity);

                            if (merge.getValue().equals(Merge.CONFIRM)) {
                                mc.world.removeEntityDangerously(entity);
                            }
                        }
                    }
                });
            } else if (event.getPacket() instanceof SPacketSoundEffect) {
                SPacketSoundEffect packet = event.getPacket();

                if (packet.getSound().equals(SoundEvents.ENTITY_GENERIC_EXPLODE)) {
                    mc.addScheduledTask(() -> {
                        for (Entity entity : new ArrayList<>(mc.world.loadedEntityList)) {
                            if (!(entity instanceof EntityEnderCrystal) || entity.isDead) {
                                continue;
                            }

                            if (entity.getDistanceSq(packet.getX(), packet.getY(), packet.getZ()) < 144.0) {
                                if (attackCrystal != null && attackCrystal.getEntityId() == entity.getEntityId()) {
                                    attackCrystal = null;
                                }

                                entity.setDead();
                                mc.world.removeEntity(entity);

                                if (merge.getValue().equals(Merge.CONFIRM)) {
                                    mc.world.removeEntityDangerously(entity);
                                }
                            }
                        }
                    });
                }
            } else if (event.getPacket() instanceof SPacketSpawnObject) {
                SPacketSpawnObject packet = event.getPacket();
                if (packet.getType() == 51) {
                    BlockPos pos = new BlockPos(packet.getX(), packet.getY(), packet.getZ());
                    if (placePos != null && placePos.equals(pos.down())) {
                        ++crystalCount;

                        entityIdSpawn = packet.getEntityID();
                        placedCrystals.add(packet.getEntityID());

                        if (explodeSpeed.getValue() == 20.0) {
                            if (!rotate.getValue().equals(Rotate.NONE)) {
                                Rotation rotation = AngleUtil.toVec(new Vec3d(pos).add(0.5, 0.5, 0.5));
                                if (rotation.isValid()) {
                                    getNebula().getRotationManager().setRotation(rotation);
                                }
                            }

                            CrystalUtil.explode(entityIdSpawn, hand, swing.getValue());
                            inhibitCrystals.put(entityIdSpawn, new Stopwatch().resetTime());
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds the best place positions
     */
    private void findBestPlacePosition() {
        BlockPos position = null;
        float currDamage = 1.0f;

        BlockPos origin = new BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);
        for (BlockPos pos : BlockUtil.sphere(origin, placeRange.getValue().intValue())) {
            // if we cannot see the position we want to place in and it exceeds our wall range bounds, we cant use this position
            if (!RaycastUtil.isBlockVisible(pos) && !BlockUtil.isInRange(pos, placeWallRange.getValue())) {
                continue;
            }

            // if we cannot place a crystal here
            if (!CrystalUtil.canPlaceAt(pos, false, protocol.getValue().equals(Protocol.UPDATED))) {
                continue;
            }

            Vec3d vec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);

            float localDamage = ExplosionUtil.calculateCrystalDamage(mc.player, vec, ignoreTerrain.getValue()) + 0.5f;
            if (localDamage > maxLocal.getValue()) {
                continue;
            }

            // if we want to be safe (safety is on)
            if (safety.getValue() && localDamage >= mc.player.getHealth() + 0.5f) {
                continue;
            }

            double targetRangeSq = targetRange.getValue() * targetRange.getValue();

            float targetDamage = currDamage;
            if (target != null) {
                if (target.isDead || target.getHealth() <= 0.0f || target.getDistanceSq(mc.player) > targetRangeSq) {
                    target = null;
                } else {
                    targetDamage = ExplosionUtil.calculateCrystalDamage(target, vec, ignoreTerrain.getValue());
                    if (targetDamage < minDamage.getValue() || localDamage > targetDamage) {
                        continue;
                    }
                }
            }

            for (EntityPlayer player : new ArrayList<>(mc.world.playerEntities)) {

                // make sure we can attack this entity
                if (player == null || player.isEntityInvulnerable(DamageSource.GENERIC) || player.equals(mc.player) || ((IEntityPlayer) player).isFriend()) {
                    continue;
                }

                // if they are out of range
                if (mc.player.getDistanceSq(player) > targetRangeSq) {
                    continue;
                }

                float playerDamage = ExplosionUtil.calculateCrystalDamage(player, vec, ignoreTerrain.getValue());
                if (playerDamage > targetDamage && playerDamage > localDamage) {
                    targetDamage = playerDamage;
                    target = player;
                }
            }

            if (targetDamage > currDamage) {
                position = pos;
                currDamage = targetDamage;
            }
        }

        placePos = position;
        damage = currDamage;
    }

    /**
     * Finds the best end crystal to attack
     *
     * I should make this sort by damage done to player, but in the end it doesn't really matter
     */
    private void findBestEndCrystal() {
        EntityEnderCrystal crystal = attackCrystal;

        double dist = 0.0;
        if (crystal != null) {
            dist = mc.player.getDistanceSq(crystal);
        }

        for (int entityId : new ArrayList<>(placedCrystals)) {
            Entity entity = mc.world.getEntityByID(entityId);
            if (entity == null || entity.isDead || entity.ticksExisted < ticksExisted.getValue()) {
                continue;
            }

            double distance = mc.player.getDistanceSq(entity);

            double range = explodeRange.getValue() * explodeRange.getValue();
            if (!mc.player.canEntityBeSeen(entity)) {
                range = explodeWallRange.getValue() * explodeWallRange.getValue();
            }

            if (distance > range) {
                continue;
            }

            if (crystal == null || distance < dist) {
                dist = distance;
                crystal = (EntityEnderCrystal) entity;
            }
        }

        if (crystal != null && shouldInhibit(crystal.getEntityId())) {
            crystal = null;
        }

        attackCrystal = crystal;
    }

    /**
     * Checks if we should limit our attacks based off of inhibit
     * @param entityId the crystal entity id
     * @return the entity id
     */
    private boolean shouldInhibit(int entityId) {
        Stopwatch stopwatch = inhibitCrystals.getOrDefault(entityId, null);
        if (!inhibit.getValue() || stopwatch == null) {
            return false;
        }

        int ping = 40 + getNebula().getServerManager().getLocalLatency();
        if (stopwatch.passedMs(ping)) {
            inhibitCrystals.remove(entityId);
            return false;
        }

        return true;
    }

    /**
     * Finds end crystals to of course, place
     * @return if we could find crystals or not
     */
    private boolean findCrystals() {
        // find the slot the crystals are in
        int slot = InventoryUtil.getSlot(InventorySpace.HOTBAR,
                (stack) -> !stack.isEmpty() && stack.getItem().equals(Items.END_CRYSTAL));

        // no crystals in the hotbar/offhand
        if (slot == -1) {
            return false;
        }

        if (slot == InventoryUtil.OFFHAND_SLOT) {
            hand = EnumHand.OFF_HAND;
        } else {
            hand = EnumHand.MAIN_HAND;

            // if we have a swapping option, we'll do that now
            if (!swapping.getValue().equals(Swapping.NONE)) {

                // if we have not swapped to anything before, we'll swap
                if (oldSlot == -1) {
                    oldSlot = mc.player.inventory.currentItem;
                    getNebula().getHotbarManager().sendSlotChange(slot, swapping.getValue().swapType);

                    // reset our swap timer (useful for servers like 2bpvp that have a swap delay preventing silent swap)
                    swapTimer.resetTime();

                    // if swapDelay is 0, we can just start placing right away
                    // if not, we have to wait for the swapTimer to finish
                    return swapDelay.getValue() == 0;
                } else {
                    // check if our swap timer has passed. if so, we can then place crystals
                    // or if we are packet swapping, we'll be swapping back and forth rapidly anyway so there's no point in waiting
                    return swapTimer.hasElapsed(swapDelay.getValue(), TimeFormat.TICKS) || swapping.getValue().equals(SwapType.SERVER);
                }
            } else {
                // if we cannot swap, let's hope you're holding onto crystals or sum
                return InventoryUtil.isHolding(Items.END_CRYSTAL);
            }
        }

        return true;
    }

    /**
     * Spoofs rotations sent to the server
     * @param rotation the requested rotation
     * @param allowLimit if limiting should be done
     * @return if we should limit and wait
     */
    private boolean sendRotations(Rotation rotation, boolean allowLimit) {
        if (rotation.isValid()) {

            // if we shouldn't rotate in the first place, disregard
            if (rotate.getValue().equals(Rotate.NONE)) {
                return false;
            }

            if (!allowLimit) {
                getNebula().getRotationManager().setRotation(rotation);
                return false;
            }

            if (nextRotation != null) {
                getNebula().getRotationManager().setRotation(nextRotation);
                nextRotation = null;
                return false;
            } else {
                float yaw = getNebula().getRotationManager().getYaw();
                float pitch = getNebula().getRotationManager().getPitch();


                // check for changes in yaw/pitch
                float yawDiff = Math.abs(yaw - rotation.getYaw());
                float pitchDiff = Math.abs(pitch - rotation.getPitch());

                boolean yawExceeded = yawDiff >= maxYawRate.getValue();
                boolean pitchExceeded = pitchDiff >= maxYawRate.getValue();

                if (yawExceeded) {
                    yawDiff /= 2.0f;
                }

                if (pitchExceeded) {
                    pitchDiff /= 2.0f;
                }

                float halfwayYaw = yaw;
                if (rotation.getYaw() > yaw) {
                    halfwayYaw += yawDiff;
                } else if (rotation.getYaw() < yaw) {
                    halfwayYaw -= yawDiff;
                }

                float halfwayPitch = pitch;
                if (rotation.getPitch() > pitch) {
                    halfwayPitch += pitchDiff;
                } else if (rotation.getPitch() < pitch) {
                    halfwayPitch -= pitch;
                }

                nextRotation = rotation;

                getNebula().getRotationManager().setRotation(
                        new Rotation(halfwayYaw, halfwayPitch).setType(rotate.getValue().rotationType));

                // limit
                return true;
            }
        }

        return false;
    }

    /**
     * Swaps back to your old slot
     */
    private void swapBack() {
        if (oldSlot != -1) {
            getNebula().getHotbarManager().sendSlotChange(oldSlot, swapping.getValue().swapType);
            oldSlot = -1;
        }
    }

    public enum Interact {
        /**
         * Normal interaction. Does the following:
         *
         * - Facing is EnumFacing.UP
         * - FacingX, FacingY, FacingZ are all 0.0f
         */
        NORMAL,

        /**
         * Strict interaction, more vanilla-like interactions
         *
         * - Facing is the closest facing we can see
         *  - For example, if one of the sides is exposed to us, we'll place on that side in the packet
         *  - However, if we are under the block, DOWN is what we'll sue
         *  - But UP will be the default otherwise
         * - FacingX, FacingY, FacingZ are all vanilla like, and are never all 0.0f
         */
        STRICT
    }

    public enum Protocol {
        /**
         * Native 1.12.2 crystal placements, no 1x1 holes
         */
        NATIVE,

        /**
         * Updated 1.13+ server crystal placements, 1x1 holes
         */
        UPDATED
    }

    public enum Rotate {
        /**
         * Do not rotate
         */
        NONE(RotationType.NONE),

        /**
         * Rotate client side
         */
        CLIENT(RotationType.CLIENT),

        /**
         * Rotate server side
         */
        SERVER(RotationType.SERVER),

        /**
         * Rotate server side, but limit rotations.
         *
         * If the YawRate > yaw/pitch difference, it'll split up rotations into multiple ticks
         */
        LIMITED(RotationType.SERVER);

        private final RotationType rotationType;

        Rotate(RotationType type) {
            rotationType = type;
        }

    }

    public enum YawLimit {
        /**
         * Only limits rotations when placing
         */
        SEMI,

        /**
         * Limits rotations while placing and exploding crystals
         */
        FULL
    }

    public enum Merge {
        /**
         * Does no merging
         */
        NONE,

        /**
         * Removes the crystal from the world instantly after sending the attack
         */
        INSTANT,

        /**
         * Removes the crystal from the world instantly once a packet has confirmed that crystal is gone
         */
        CONFIRM
    }

    public enum Swapping {
        NONE(SwapType.NONE),

        CLIENT(SwapType.CLIENT),

        SERVER(SwapType.SERVER),

        ALT_SILENT(SwapType.SERVER);

        private final SwapType swapType;

        Swapping(SwapType swapType) {
            this.swapType = swapType;
        }
    }
}

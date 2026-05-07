package com.hhdxcz.strinova.forge.client;

import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.paper.StrinovaWallBlacklist;
import com.hhdxcz.strinova.paper.WaPaperState;
import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpClientState;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = StrinovaMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StrinovaKeyBindingsForge {

    private static KeyMapping stringifyKey;
    private static KeyMapping wallKey;
    private static String wallKeyName = "key.keyboard.r";
    private static boolean lastCtrlDown;
    private static boolean lastJumpDown;
    private static boolean lastGrounded;
    private static boolean lastWallDown;
    private static boolean airJumpArmed;
    private static int clientAirJumpsUsed;
    private static boolean wa$clearPaperAfterFly;
    private static boolean wa$flyUsedInAir;

    private StrinovaKeyBindingsForge() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        stringifyKey = new KeyMapping(
                "key.wa.stringify",
                GLFW.GLFW_KEY_LEFT_CONTROL,
                "key.categories.wa"
        );
        wallKey = new KeyMapping(
                "key.wa.wall",
                GLFW.GLFW_KEY_R,
                "key.categories.wa"
        );
        wallKeyName = "key.keyboard.r";
        event.register(stringifyKey);
        event.register(wallKey);
    }

    public static boolean rebindWallKey(String rawKey) {
        if (wallKey == null) {
            return false;
        }
        InputConstants.Key key = parseKey(rawKey);
        if (key == InputConstants.UNKNOWN) {
            return false;
        }
        wallKey.setKey(key);
        wallKeyName = key.getName();
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        return true;
    }

    public static String getWallKeyName() {
        return wallKeyName;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        var playerId = player.getUUID();
        boolean ctrlDown = stringifyKey != null && stringifyKey.isDown();
        boolean jumpDown = client.options.keyJump.isDown();
        boolean grounded = player.onGround() || player.isInWater() || player.isInLava();
        boolean wallDown = wallKey != null && wallKey.isDown();
        if (wallDown && wa$isHoldingTaczGun(player)) {
            wallDown = false;
        }

        if (wa$shouldSkipInputsForCompat(client)) {
            lastCtrlDown = ctrlDown;
            lastJumpDown = jumpDown;
            lastGrounded = grounded;
            lastWallDown = wallDown;
            return;
        }

        boolean justPressed = ctrlDown && !lastCtrlDown;

        boolean isFly = WaPaperState.isFly(playerId);
        if (isFly && (player.onGround() || player.isInWater() || player.isInLava())) {
            WaPaperState.setFly(playerId, false);
            StrinovaNetwork.sendFly(false);
            isFly = false;
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            wa$onFlyEnded(playerId);
        }

        if (!WaPaperState.isWall(playerId) && WaPaperState.isCtrlPaper(playerId) && (player.isInWater() || player.isInLava())) {
            WaPaperState.setPaper(playerId, false);
            StrinovaNetwork.sendPaper(false);
        }

        boolean jumpPressed = jumpDown && !lastJumpDown;

        if (grounded) {
            airJumpArmed = false;
            clientAirJumpsUsed = 0;
            wa$flyUsedInAir = false;
        } else {
            if (lastGrounded) {
                airJumpArmed = false;
            }
            if (!jumpDown) {
                airJumpArmed = true;
            }
            int maxExtra = StrinovaAirJumpClientState.getExtraJumps(playerId);
            boolean infinite = maxExtra == StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS;
            if (jumpPressed && airJumpArmed && (infinite || clientAirJumpsUsed < maxExtra) && !isFly && !WaPaperState.isWall(playerId) && !player.isFallFlying()) {
                applyClientDoubleJump(player);
                StrinovaNetwork.sendDoubleJump();
                airJumpArmed = false;
                if (!infinite) {
                    clientAirJumpsUsed++;
                }
            }
        }

        lastGrounded = grounded;
        lastJumpDown = jumpDown;

        boolean wallPressed = wallDown && !lastWallDown;

        if (justPressed) {
            boolean inAir = !player.onGround() && !player.isInWater() && !player.isInLava();
            if (!isFly && inAir && !wa$flyUsedInAir) {
                Vec3 look = player.getViewVector(1.0F);
                double fx = look.x;
                double fz = look.z;
                double lenSq = fx * fx + fz * fz;
                if (lenSq > 1.0E-4D) {
                    double speed = 0.35D;
                    double scale = speed / Math.sqrt(lenSq);
                    fx *= scale;
                    fz *= scale;
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(fx, motion.y, fz);
                }
                WaPaperState.setFly(playerId, true);
                StrinovaNetwork.sendFly(true);
                isFly = true;
                wa$flyUsedInAir = true;
                wa$clearPaperAfterFly = WaPaperState.isCtrlPaper(playerId);
            } else if (isFly) {
                WaPaperState.setFly(playerId, false);
                StrinovaNetwork.sendFly(false);
                isFly = false;
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(0.0D, motion.y, 0.0D);
                wa$onFlyEnded(playerId);
            } else {
                if (!WaPaperState.isWall(playerId)) {
                    boolean ctrlPaper = WaPaperState.isCtrlPaper(playerId);
                    boolean next = !ctrlPaper;
                    if (next && (player.isInWater() || player.isInLava())) {
                        WaPaperState.setPaper(playerId, false);
                        StrinovaNetwork.sendPaper(false);
                    } else {
                        WaPaperState.setPaper(playerId, next);
                        StrinovaNetwork.sendPaper(next);
                    }
                }
            }
        }

        lastCtrlDown = ctrlDown;
        lastWallDown = wallDown;

        if (wallPressed) {
            boolean walling = WaPaperState.isWall(playerId);
            boolean nearWall = !walling && hasSolidWallInFront(player, player.getDirection());
            if (nearWall && !walling) {
                if (isFly) {
                    WaPaperState.setFly(playerId, false);
                    StrinovaNetwork.sendFly(false);
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(0.0D, motion.y, 0.0D);
                    isFly = false;
                    wa$onFlyEnded(playerId);
                }
                Direction dir = player.getDirection();
                boolean synced = false;
                if (dir.getAxis().isHorizontal()) {
                    if (dir.getAxis() == Direction.Axis.X) {
                        double base = Math.floor(player.getX());
                        double value = dir == Direction.EAST ? base + 1.0D + WaPaperState.WALL_GAP : base - WaPaperState.WALL_GAP;
                        WaPaperState.setWall(playerId, true);
                        WaPaperState.setWallAnchorY(playerId, player.getY());
                        WaPaperState.setWallPlane(playerId, true, value);
                        StrinovaNetwork.sendWall(true, true, value, player.getY());
                        synced = true;
                    } else {
                        double base = Math.floor(player.getZ());
                        double value = dir == Direction.SOUTH ? base + 1.0D + WaPaperState.WALL_GAP : base - WaPaperState.WALL_GAP;
                        WaPaperState.setWall(playerId, true);
                        WaPaperState.setWallAnchorY(playerId, player.getY());
                        WaPaperState.setWallPlane(playerId, false, value);
                        StrinovaNetwork.sendWall(true, false, value, player.getY());
                        synced = true;
                    }
                }
                if (!synced) {
                    WaPaperState.setWall(playerId, false);
                    StrinovaNetwork.sendWallOff();
                }
            } else if (walling) {
                WaPaperState.setWall(playerId, false);
                StrinovaNetwork.sendWallOff();
                if (WaPaperState.isCtrlPaper(playerId)) {
                    WaPaperState.setPaper(playerId, false);
                    StrinovaNetwork.sendPaper(false);
                }
                if (WaPaperState.isFly(playerId)) {
                    WaPaperState.setFly(playerId, false);
                    StrinovaNetwork.sendFly(false);
                    wa$onFlyEnded(playerId);
                }
            }
        }

        if (WaPaperState.isWall(playerId)) {
            boolean wDown = client.options.keyUp.isDown();
            boolean sDown = client.options.keyDown.isDown();
            if (wDown != sDown) {
                WaPaperState.setWallFrontToCamera(playerId, wDown);
            }
        }
    }
    
    private static void wa$onFlyEnded(java.util.UUID playerId) {
        if (playerId == null) {
            wa$clearPaperAfterFly = false;
            return;
        }
        if (wa$clearPaperAfterFly && WaPaperState.isCtrlPaper(playerId)) {
            WaPaperState.setPaper(playerId, false);
            StrinovaNetwork.sendPaper(false);
        }
        wa$clearPaperAfterFly = false;
    }

    private static boolean wa$isHoldingTaczGun(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        var stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.toString().contains("tacz:modern_kinetic_gun");
    }

    private static boolean wa$shouldSkipInputsForCompat(Minecraft client) {
        if (client == null || client.screen == null) {
            return false;
        }
        String name = client.screen.getClass().getName();
        if (name == null) {
            return false;
        }
        String s = name.toLowerCase(Locale.ROOT);
        return s.contains("xaero") || s.contains("xfox");
    }

    private static boolean hasSolidWallInFront(LocalPlayer player, Direction dir) {
        if (player == null || dir == null || !dir.getAxis().isHorizontal()) {
            return false;
        }
        Level level = player.level();
        if (level == null) {
            return false;
        }
        // 使用玩家身体中心高度来获取 BlockPos，避免因轻微下沉（Y < 整数位）导致误判地面为前方墙壁
        BlockPos base = BlockPos.containing(player.getX(), player.getY() + 0.5D, player.getZ());
        BlockPos front = base.relative(dir);
        return isSolid(level, front) || isSolid(level, front.above());
    }

    private static boolean isSolid(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        if (StrinovaWallBlacklist.isBlockedClient(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static InputConstants.Key parseKey(String rawKey) {
        if (rawKey == null) {
            return InputConstants.UNKNOWN;
        }
        String s = rawKey.trim();
        if (s.isEmpty()) {
            return InputConstants.UNKNOWN;
        }
        if ("reset".equalsIgnoreCase(s) || "default".equalsIgnoreCase(s)) {
            return InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R);
        }
        if (s.length() == 1) {
            char c = s.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c - 'A' + 'a');
            }
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                return InputConstants.getKey("key.keyboard." + c);
            }
        }
        if (s.startsWith("mouse.")) {
            s = "key." + s;
        }
        if (!s.startsWith("key.")) {
            s = "key.keyboard." + s.toLowerCase();
        }
        return InputConstants.getKey(s);
    }

    private static void applyClientDoubleJump(LocalPlayer player) {
        double yVel = 0.42D;
        MobEffectInstance jumpBoost = player.getEffect(MobEffects.JUMP);
        if (jumpBoost != null) {
            yVel += (double) ((jumpBoost.getAmplifier() + 1) * 0.1F);
        }
        Vec3 motion = player.getDeltaMovement();
        double newY = motion.y >= (yVel - 1.0E-3D) ? Math.min(motion.y + 0.1D, 1.0D) : yVel;
        player.setDeltaMovement(motion.x, newY, motion.z);
        player.hasImpulse = true;
        player.fallDistance = 0.0F;
    }
}

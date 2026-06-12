package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.InputCompatibilityBridge;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityPose;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class NodeMovementCommandExecutor {
    private final Node owner;

    NodeMovementCommandExecutor(Node owner) {
        this.owner = owner;
    }

    void executeLookCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.LOOK_ORIENTATION, Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        float yaw = (float) owner.getDoubleParameter("Yaw", client.player.getYaw());
        float pitch = MathHelper.clamp((float) owner.getDoubleParameter("Pitch", client.player.getPitch()), -90.0F, 90.0F);
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
        client.player.setHeadYaw(yaw);
        future.complete(null);
    }

    void executeWalkCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.LOOK_ORIENTATION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        double durationSeconds = Math.max(0.0, owner.getDoubleParameter("Duration", 1.0));
        double distance = Math.max(0.0, owner.getDoubleParameter("Distance", 0.0));
        boolean useDistance = distance > 0.0;
        NodeParameter durationParameter = owner.getParameter("Duration");
        boolean durationExplicitlyEdited = durationParameter != null && durationParameter.isUserEdited();

        Node slotOneParameter = owner.getAttachedParameter(1);
        if (slotOneParameter != null && slotOneParameter.getType() == NodeType.VARIABLE) {
            Node resolved = owner.resolveVariableValueNode(slotOneParameter, 1, null);
            if (resolved != null) {
                slotOneParameter = resolved;
            }
        }
        boolean distanceDrivenByParameter = slotOneParameter != null
            && (slotOneParameter.getType() == NodeType.PARAM_DISTANCE
                || slotOneParameter.getType() == NodeType.SENSOR_DISTANCE_BETWEEN);
        if (!useDistance && durationSeconds <= 0.0) {
            future.complete(null);
            return;
        }

        new Thread(() -> {
            boolean interrupted = false;
            try {
                owner.runOnClientThread(client, () -> {
                    owner.orientPlayerTowardsRuntimeTarget(client, owner.runtimeState().runtimeParameterData);
                    if (client.options != null && client.options.forwardKey != null) {
                        client.options.forwardKey.setPressed(true);
                    }
                });

                if (useDistance) {
                    Vec3d startPos = owner.supplyFromClient(client,
                        () -> {
                            if (client.player == null) {
                                return null;
                            }
                            Vec3d pos = EntityCompatibilityBridge.getPos(client.player);
                            if (pos != null) {
                                return pos;
                            }
                            return new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
                        });
                    if (startPos != null) {
                        double targetDistanceSquared = distance * distance;
                        long startTime = System.currentTimeMillis();
                        // Always keep a finite timeout fallback so a stalled distance-based walk
                        // can't hold the forward key forever if the guard never trips or movement
                        // fails to advance.
                        long maxDurationMs = durationSeconds > 0.0
                            ? (long) (durationSeconds * 1000)
                            : Long.MAX_VALUE;
                        String stopReason = "unknown";
                        while (true) {
                            Thread.sleep(Node.CONTROL_POLL_INTERVAL_MS);
                            if (owner.shouldAbortForRepeatUntilGuard()) {
                                stopReason = "repeatUntilConditionMet";
                                break;
                            }
                            if (System.currentTimeMillis() - startTime >= maxDurationMs) {
                                stopReason = "timeout";
                                break;
                            }
                            Vec3d currentPos = owner.supplyFromClient(client,
                                () -> {
                                    if (client.player == null) {
                                        return null;
                                    }
                                    Vec3d pos = EntityCompatibilityBridge.getPos(client.player);
                                    if (pos != null) {
                                        return pos;
                                    }
                                    return new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
                                });
                            if (currentPos == null) {
                                stopReason = "currentPosNull";
                                break;
                            }
                            double dx = currentPos.x - startPos.x;
                            double dz = currentPos.z - startPos.z;
                            if ((dx * dx + dz * dz) >= targetDistanceSquared) {
                                stopReason = "distanceReached";
                                break;
                            }
                        }
                    }
                } else {
                    long durationMs = (long) (durationSeconds * 1000);
                    long startTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - startTime < durationMs) {
                        if (owner.shouldAbortForRepeatUntilGuard()) {
                            break;
                        }
                        long remaining = durationMs - (System.currentTimeMillis() - startTime);
                        Thread.sleep(Math.min(Node.CONTROL_POLL_INTERVAL_MS, Math.max(1L, remaining)));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted = true;
            } finally {
                try {
                    owner.runOnClientThread(client, () -> {
                        if (client.options != null && client.options.forwardKey != null) {
                            client.options.forwardKey.setPressed(false);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interrupted = true;
                }
                if (interrupted) {
                    future.completeExceptionally(new InterruptedException());
                } else {
                    future.complete(null);
                }
            }
        }, "Pathmind-Walk").start();
    }

    void executeJumpCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        client.execute(() -> {
            if (client.player != null) {
                client.player.jump();
            }
            future.complete(null);
        });
    }

    void executePressKeyCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        RuntimeParameterData parameterData = owner.runtimeState().runtimeParameterData;
        String buttonValue = parameterData != null && parameterData.resolvedButtonValue != null
            ? parameterData.resolvedButtonValue
            : owner.getStringParameter("Key", "GLFW_KEY_SPACE");
        boolean useMouseButton = parameterData != null && parameterData.resolvedButtonIsMouse;

        if (useMouseButton) {
            Integer mouseButton = owner.resolveMouseButtonCode(buttonValue);
            if (mouseButton == null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.unknownMouseButton", buttonValue));
                future.complete(null);
                return;
            }
            InputUtil.Key inputKey = InputUtil.Type.MOUSE.createFromCode(mouseButton);
            KeyBinding.onKeyPressed(inputKey);
            boolean buttonAlreadyDown = InputCompatibilityBridge.isMouseButtonPressed(client, mouseButton);
            if (buttonAlreadyDown) {
                future.complete(null);
                return;
            }
            KeyBinding.setKeyPressed(inputKey, true);
            Node.MESSAGE_SCHEDULER.schedule(() -> {
                net.minecraft.client.MinecraftClient releaseClient = net.minecraft.client.MinecraftClient.getInstance();
                if (releaseClient == null) {
                    future.complete(null);
                    return;
                }
                releaseClient.execute(() -> {
                    KeyBinding.setKeyPressed(inputKey, false);
                    future.complete(null);
                });
            }, 75L, TimeUnit.MILLISECONDS);
            return;
        }

        Integer keyCode = owner.resolveKeyCode(buttonValue);
        if (keyCode == null) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.unknownKey", buttonValue));
            future.complete(null);
            return;
        }

        InputUtil.Key inputKey = InputUtil.Type.KEYSYM.createFromCode(keyCode);
        KeyBinding.onKeyPressed(inputKey);

        boolean keyAlreadyDown = InputCompatibilityBridge.isKeyPressed(client, keyCode);
        if (keyAlreadyDown) {
            future.complete(null);
            return;
        }

        KeyBinding.setKeyPressed(inputKey, true);
        Node.MESSAGE_SCHEDULER.schedule(() -> {
            net.minecraft.client.MinecraftClient releaseClient = net.minecraft.client.MinecraftClient.getInstance();
            if (releaseClient == null) {
                future.complete(null);
                return;
            }
            releaseClient.execute(() -> {
                KeyBinding.setKeyPressed(inputKey, false);
                future.complete(null);
            });
        }, 75L, TimeUnit.MILLISECONDS);
    }
    
    void executeCrouchCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        boolean target = !client.player.isSneaking();
        applyCrouchState(client, target);
        future.complete(null);
    }

    void executeCrawlCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        boolean active = client.player.getPose() != EntityPose.SWIMMING || !client.player.isSwimming();
        client.player.setSwimming(active);
        client.player.setPose(active ? EntityPose.SWIMMING : EntityPose.STANDING);
        if (client.options != null && client.options.sneakKey != null) {
            client.options.sneakKey.setPressed(false);
        }
        future.complete(null);
    }

    private void applyCrouchState(net.minecraft.client.MinecraftClient client, boolean active) {
        owner.applySneakState(client, active);
    }
    void executeSprintCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        boolean active = !client.player.isSprinting();

        boolean previous = client.player.isSprinting();
        client.player.setSprinting(active);
        if (client.player.networkHandler != null && previous != active) {
            ClientCommandC2SPacket.Mode mode = active ? ClientCommandC2SPacket.Mode.START_SPRINTING : ClientCommandC2SPacket.Mode.STOP_SPRINTING;
            client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(client.player, mode));
        }
        future.complete(null);
    }

    void executeFlyCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        if (client.player.getAbilities() == null) {
            future.complete(null);
            return;
        }

        boolean active = !client.player.getAbilities().flying;
        if (active && !client.player.getAbilities().allowFlying) {
            future.complete(null);
            return;
        }

        // Lift off first so enabling flight works reliably even when starting grounded.
        if (active && client.player.isOnGround()) {
            client.player.jump();
        }

        client.player.getAbilities().flying = active;
        client.player.sendAbilitiesUpdate();
        future.complete(null);
    }
}

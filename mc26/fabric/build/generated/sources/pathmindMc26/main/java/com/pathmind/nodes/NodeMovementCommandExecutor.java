package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.mojang.blaze3d.platform.InputConstants;
import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.InputCompatibilityBridge;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

final class NodeMovementCommandExecutor {
    private final Node owner;

    NodeMovementCommandExecutor(Node owner) {
        this.owner = owner;
    }

    void executeLookCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.LOOK_ORIENTATION, Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        float yaw = (float) owner.getDoubleParameter("Yaw", client.player.getYRot());
        float pitch = Mth.clamp((float) owner.getDoubleParameter("Pitch", client.player.getXRot()), -90.0F, 90.0F);
        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
        client.player.setYHeadRot(yaw);
        future.complete(null);
    }

    void executeWalkCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.LOOK_ORIENTATION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
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
                    if (client.options != null && client.options.keyUp != null) {
                        client.options.keyUp.setDown(true);
                    }
                });

                if (useDistance) {
                    net.minecraft.core.BlockPos startBlockPos = owner.supplyFromClient(client,
                        () -> {
                            if (client.player == null) {
                                return null;
                            }
                            return client.player.blockPosition();
                        });
                    if (startBlockPos != null) {
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
                            net.minecraft.core.BlockPos currentBlockPos = owner.supplyFromClient(client,
                                () -> {
                                    if (client.player == null) {
                                        return null;
                                    }
                                    return client.player.blockPosition();
                                });
                            if (currentBlockPos == null) {
                                stopReason = "currentPosNull";
                                break;
                            }
                            double dx = currentBlockPos.getX() - startBlockPos.getX();
                            double dy = currentBlockPos.getY() - startBlockPos.getY();
                            double dz = currentBlockPos.getZ() - startBlockPos.getZ();
                            if ((dx * dx + dy * dy + dz * dz) >= targetDistanceSquared) {
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
                        if (client.options != null && client.options.keyUp != null) {
                            client.options.keyUp.setDown(false);
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
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        client.execute(() -> {
            if (client.player != null) {
                client.player.jumpFromGround();
            }
            future.complete(null);
        });
    }

    void executePressKeyCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        RuntimeParameterData parameterData = owner.runtimeState().runtimeParameterData;
        String buttonValue = parameterData != null && parameterData.resolvedButtonValue != null
            ? parameterData.resolvedButtonValue
            : owner.getStringParameter("Key", "GLFW_KEY_SPACE");
        boolean useMouseButton = parameterData != null && parameterData.resolvedButtonIsMouse;
        boolean useHoldDuration = owner.isAmountInputEnabled();
        long holdDurationMs = useHoldDuration
            ? Math.max(0L, Math.round(owner.getDoubleParameter("Duration", 0.0) * 1000.0))
            : 75L;

        if (useMouseButton) {
            Integer mouseButton = owner.resolveMouseButtonCode(buttonValue);
            if (mouseButton == null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.unknownMouseButton", buttonValue));
                future.complete(null);
                return;
            }
            InputConstants.Key inputKey = InputConstants.Type.MOUSE.getOrCreate(mouseButton);
            KeyMapping.click(inputKey);
            boolean buttonAlreadyDown = InputCompatibilityBridge.isMouseButtonPressed(client, mouseButton);
            if (buttonAlreadyDown) {
                future.complete(null);
                return;
            }
            KeyMapping.set(inputKey, true);
            Node.MESSAGE_SCHEDULER.schedule(() -> {
                net.minecraft.client.Minecraft releaseClient = net.minecraft.client.Minecraft.getInstance();
                if (releaseClient == null) {
                    future.complete(null);
                    return;
                }
                releaseClient.execute(() -> {
                    KeyMapping.set(inputKey, false);
                    future.complete(null);
                });
            }, holdDurationMs, TimeUnit.MILLISECONDS);
            return;
        }

        Integer keyCode = owner.resolveKeyCode(buttonValue);
        if (keyCode == null) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.unknownKey", buttonValue));
            future.complete(null);
            return;
        }

        InputConstants.Key inputKey = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
        boolean[] handledByScreen = {false};
        try {
            owner.runOnClientThread(client, () -> {
                if (client.gui.screen() != null) {
                    handledByScreen[0] = InputCompatibilityBridge.dispatchScreenKeyPressed(client.gui.screen(), keyCode, 0, 0);
                }
                KeyMapping.click(inputKey);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
            return;
        }

        if (handledByScreen[0]) {
            future.complete(null);
            return;
        }

        boolean keyAlreadyDown = InputCompatibilityBridge.isKeyPressed(client, keyCode);
        if (keyAlreadyDown) {
            future.complete(null);
            return;
        }

        KeyMapping.set(inputKey, true);
        Node.MESSAGE_SCHEDULER.schedule(() -> {
            net.minecraft.client.Minecraft releaseClient = net.minecraft.client.Minecraft.getInstance();
            if (releaseClient == null) {
                future.complete(null);
                return;
            }
            releaseClient.execute(() -> {
                KeyMapping.set(inputKey, false);
                future.complete(null);
            });
        }, holdDurationMs, TimeUnit.MILLISECONDS);
    }
    
    void executeCrouchCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        boolean target = !client.player.isShiftKeyDown();
        applyCrouchState(client, target);
        future.complete(null);
    }

    void executeCrawlCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        boolean active = client.player.getPose() != Pose.SWIMMING || !client.player.isSwimming();
        client.player.setSwimming(active);
        client.player.setPose(active ? Pose.SWIMMING : Pose.STANDING);
        if (client.options != null && client.options.keyShift != null) {
            client.options.keyShift.setDown(false);
        }
        future.complete(null);
    }

    private void applyCrouchState(net.minecraft.client.Minecraft client, boolean active) {
        owner.applySneakState(client, active);
    }
    void executeSprintCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        boolean active = !client.player.isSprinting();

        boolean previous = client.player.isSprinting();
        client.player.setSprinting(active);
        if (client.player.connection != null && previous != active) {
            ServerboundPlayerCommandPacket.Action mode = active ? ServerboundPlayerCommandPacket.Action.START_SPRINTING : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING;
            client.player.connection.send(new ServerboundPlayerCommandPacket(client.player, mode));
        }
        future.complete(null);
    }

    void executeFlyCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        if (client.player.getAbilities() == null) {
            future.complete(null);
            return;
        }

        boolean active = !client.player.getAbilities().flying;
        if (active && !client.player.getAbilities().mayfly) {
            future.complete(null);
            return;
        }

        // Lift off first so enabling flight works reliably even when starting grounded.
        if (active && client.player.onGround()) {
            client.player.jumpFromGround();
        }

        client.player.getAbilities().flying = active;
        client.player.onUpdateAbilities();
        future.complete(null);
    }
}

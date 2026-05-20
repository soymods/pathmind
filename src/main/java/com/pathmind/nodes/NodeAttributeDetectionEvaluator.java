package com.pathmind.nodes;

import com.pathmind.util.EntityStateOptions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class NodeAttributeDetectionEvaluator {
    private final Node owner;

    NodeAttributeDetectionEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateAttributeDetectionSensor() {
        owner.normalizeAttributeDetectionParameters();
        Node parameterNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(0), 0);
        MinecraftClient client = MinecraftClient.getInstance();
        if (parameterNode == null) {
            if (client != null) {
                owner.sendNodeErrorMessage(client, owner.getType().getDisplayName() + " requires a target parameter.");
            }
            return false;
        }

        AttributeDetectionConfig.TargetKind targetKind = AttributeDetectionConfig.inferTargetKind(parameterNode.getType());
        if (targetKind == null) {
            owner.sendIncompatibleParameterMessage(parameterNode);
            return false;
        }

        AttributeDetectionConfig.AttributeOption attribute =
            AttributeDetectionConfig.getAttribute(Node.getParameterString(owner, "Attribute"));
        if (attribute == null || !attribute.supports(targetKind)) {
            attribute = AttributeDetectionConfig.getDefaultAttribute(targetKind);
        }

        String expectedValue = Node.getParameterString(owner, "Value");
        if (expectedValue == null) {
            expectedValue = "";
        }

        return switch (targetKind) {
            case ENTITY, PLAYER -> evaluateEntityAttributeDetection(parameterNode, attribute, expectedValue);
            case ITEM -> evaluateItemAttributeDetection(parameterNode, attribute, expectedValue);
        };
    }

    private boolean evaluateEntityAttributeDetection(Node parameterNode,
                                                     AttributeDetectionConfig.AttributeOption attribute,
                                                     String expectedValue) {
        RuntimeParameterData data = new RuntimeParameterData();
        Optional<Vec3d> resolved = owner.resolvePositionTarget(parameterNode, data, null);
        if (resolved.isEmpty() || data.targetEntity == null) {
            return false;
        }
        Entity entity = data.targetEntity;
        return switch (attribute) {
            case NAME -> evaluateStringAttribute(entity.getName().getString(), expectedValue);
            case CUSTOM_NAME -> evaluateStringAttribute(getEntityCustomName(entity), expectedValue);
            case HAS_CUSTOM_NAME -> evaluateBooleanAttribute(entity.hasCustomName(), expectedValue);
            case TYPE -> evaluateStringAttribute(getEntityTypeId(entity), expectedValue);
            case UUID -> evaluateStringAttribute(entity.getUuidAsString(), expectedValue);
            case HEALTH -> entity instanceof LivingEntity livingEntity
                && evaluateNumericAttribute(livingEntity.getHealth(), expectedValue);
            case MAX_HEALTH -> entity instanceof LivingEntity livingEntity
                && evaluateNumericAttribute(livingEntity.getMaxHealth(), expectedValue);
            case X -> evaluateNumericAttribute(entity.getX(), expectedValue);
            case Y -> evaluateNumericAttribute(entity.getY(), expectedValue);
            case Z -> evaluateNumericAttribute(entity.getZ(), expectedValue);
            case YAW -> evaluateNumericAttribute(entity.getYaw(), expectedValue);
            case PITCH -> evaluateNumericAttribute(entity.getPitch(), expectedValue);
            case IS_ALIVE -> evaluateBooleanAttribute(entity.isAlive(), expectedValue);
            case IS_ON_GROUND -> evaluateBooleanAttribute(entity.isOnGround(), expectedValue);
            case IS_ON_FIRE -> evaluateBooleanAttribute(entity.isOnFire(), expectedValue);
            case IS_SNEAKING -> evaluateBooleanAttribute(entity.isSneaking(), expectedValue);
            case IS_SPRINTING -> evaluateBooleanAttribute(entity.isSprinting(), expectedValue);
            case IS_SWIMMING -> evaluateBooleanAttribute(entity.isSwimming(), expectedValue);
            case IS_BABY -> evaluateBooleanAttribute(EntityStateOptions.matchesState(entity, "age=baby"), expectedValue);
            case TAG -> evaluateTagAttribute(entity.getCommandTags(), expectedValue);
            default -> false;
        };
    }

    private boolean evaluateItemAttributeDetection(Node parameterNode,
                                                   AttributeDetectionConfig.AttributeOption attribute,
                                                   String expectedValue) {
        Optional<ItemEntity> resolved = resolveItemEntityParameter(parameterNode);
        if (resolved.isEmpty()) {
            return false;
        }
        ItemEntity itemEntity = resolved.get();
        ItemStack stack = itemEntity.getStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return switch (attribute) {
            case NAME -> evaluateStringAttribute(stack.getName().getString(), expectedValue);
            case CUSTOM_NAME -> evaluateStringAttribute(getItemCustomName(stack), expectedValue);
            case HAS_CUSTOM_NAME -> evaluateBooleanAttribute(stack.get(DataComponentTypes.CUSTOM_NAME) != null, expectedValue);
            case ITEM_ID -> evaluateStringAttribute(getItemId(stack), expectedValue);
            case COUNT -> evaluateNumericAttribute(stack.getCount(), expectedValue);
            case MAX_COUNT -> evaluateNumericAttribute(stack.getMaxCount(), expectedValue);
            case DAMAGE -> evaluateNumericAttribute(stack.getDamage(), expectedValue);
            case MAX_DAMAGE -> evaluateNumericAttribute(stack.getMaxDamage(), expectedValue);
            case X -> evaluateNumericAttribute(itemEntity.getX(), expectedValue);
            case Y -> evaluateNumericAttribute(itemEntity.getY(), expectedValue);
            case Z -> evaluateNumericAttribute(itemEntity.getZ(), expectedValue);
            case IS_STACKABLE -> evaluateBooleanAttribute(stack.isStackable(), expectedValue);
            case IS_ENCHANTED -> evaluateBooleanAttribute(stack.hasEnchantments(), expectedValue);
            case IS_DAMAGEABLE -> evaluateBooleanAttribute(stack.isDamageable(), expectedValue);
            default -> false;
        };
    }

    private Optional<ItemEntity> resolveItemEntityParameter(Node parameterNode) {
        if (parameterNode == null || parameterNode.getType() != NodeType.PARAM_ITEM) {
            return Optional.empty();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        List<String> itemIds = owner.resolveItemIdsFromParameter(parameterNode);
        if (itemIds.isEmpty()) {
            return Optional.empty();
        }
        double range = Node.parseNodeDouble(parameterNode, "Range", Node.PARAMETER_SEARCH_RADIUS);
        ItemEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item item = Registries.ITEM.get(identifier);
            Optional<ItemEntity> candidate = findNearestDroppedItemEntity(client, item, range);
            if (candidate.isEmpty()) {
                continue;
            }
            double distance = candidate.get().squaredDistanceTo(client.player);
            if (nearest == null || distance < nearestDistance) {
                nearest = candidate.get();
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private Optional<ItemEntity> findNearestDroppedItemEntity(MinecraftClient client, Item item, double range) {
        if (client == null || client.player == null || client.world == null || item == null) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        List<ItemEntity> entities = client.world.getEntitiesByClass(ItemEntity.class, searchBox,
            entity -> entity != null && !entity.isRemoved() && !entity.getStack().isEmpty() && entity.getStack().isOf(item));
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        ItemEntity nearest = Collections.min(entities, Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return Optional.of(nearest);
    }

    private boolean evaluateStringAttribute(String actualValue, String expectedValue) {
        String actual = actualValue == null ? "" : actualValue.trim();
        String expected = expectedValue == null ? "" : expectedValue.trim();
        String actualLower = actual.toLowerCase(Locale.ROOT);
        String expectedLower = expected.toLowerCase(Locale.ROOT);
        return !expectedLower.isEmpty() && actualLower.contains(expectedLower);
    }

    private boolean evaluateTagAttribute(Set<String> actualTags, String expectedValue) {
        if (actualTags == null || actualTags.isEmpty()) {
            return false;
        }
        String expected = expectedValue == null ? "" : expectedValue.trim().toLowerCase(Locale.ROOT);
        if (expected.isEmpty()) {
            return false;
        }
        boolean matched = false;
        for (String tag : actualTags) {
            if (tag == null) {
                continue;
            }
            String candidate = tag.trim().toLowerCase(Locale.ROOT);
            if (candidate.isEmpty()) {
                continue;
            }
            matched = candidate.contains(expected);
            if (matched) {
                break;
            }
        }
        return matched;
    }

    private boolean evaluateNumericAttribute(double actualValue, String expectedValue) {
        Double expected = Node.parseDoubleOrNull(expectedValue);
        if (expected == null) {
            return false;
        }
        return actualValue >= expected;
    }

    private boolean evaluateBooleanAttribute(boolean actualValue, String expectedValue) {
        boolean expected = parseBooleanLike(expectedValue);
        return actualValue == expected;
    }

    private boolean parseBooleanLike(String value) {
        return NodeAttributeParameters.parseBooleanLike(value);
    }

    private String getEntityCustomName(Entity entity) {
        if (entity == null || entity.getCustomName() == null) {
            return "";
        }
        return entity.getCustomName().getString();
    }

    private String getEntityTypeId(Entity entity) {
        if (entity == null) {
            return "";
        }
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());

        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private String getItemCustomName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Text customName = stack.get(DataComponentTypes.CUSTOM_NAME);
        return customName != null ? customName.getString() : "";
    }
}

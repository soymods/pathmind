package com.pathmind.schematic;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Reads the Sponge schematic v2/v3 {@code .schem} format into Pathmind's build plan. */
public final class SchematicLoader {
    private static final long MAX_SCHEMATIC_VOLUME = 4_000_000L;

    private SchematicLoader() {
    }

    public static SchematicBuildPlan load(Path source) throws SchematicLoadException {
        if (source == null) {
            throw new SchematicLoadException("No schematic was selected.");
        }
        String filename = source.getFileName() == null ? "" : source.getFileName().toString();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".schem")) {
            throw new SchematicLoadException("Only Sponge .schem files are supported right now: " + filename);
        }

        CompoundTag root;
        try {
            root = NbtIo.readCompressed(source, NbtAccounter.create(64L * 1024L * 1024L));
        } catch (IOException exception) {
            throw new SchematicLoadException("Could not read " + filename + " as a compressed Sponge schematic.", exception);
        }

        // Sponge v2 stores the payload at the root. Newer exporters (including
        // Litematica's Sponge export) wrap it in a `Schematic` compound and put
        // palette/data in a nested `Blocks` compound. Normalize both layouts
        // before parsing so a valid .schem never looks dimensionless.
        CompoundTag schematicPayload = readCompound(root, "Schematic");
        if (schematicPayload == null) {
            schematicPayload = root;
        }

        int width = readPositiveDimension(schematicPayload, "Width", filename);
        int height = readPositiveDimension(schematicPayload, "Height", filename);
        int length = readPositiveDimension(schematicPayload, "Length", filename);
        long volume = (long) width * height * length;
        if (volume > MAX_SCHEMATIC_VOLUME) {
            throw new SchematicLoadException(filename + " contains " + volume + " blocks; the current planning limit is "
                + MAX_SCHEMATIC_VOLUME + ".");
        }

        CompoundTag blocksPayload = readCompound(schematicPayload, "Blocks");
        if (blocksPayload == null) {
            blocksPayload = schematicPayload;
        }
        Map<Integer, PaletteEntry> palette = readPalette(blocksPayload, filename);
        byte[] blockData = readByteArray(blocksPayload, "BlockData");
        if (blockData.length == 0) {
            blockData = readByteArray(blocksPayload, "Data");
        }
        if (blockData.length == 0 && volume != 0) {
            throw new SchematicLoadException(filename + " has no BlockData payload.");
        }
        List<Integer> paletteIndices = decodeVarInts(blockData, volume, filename);
        BlockPos offset = readOffset(schematicPayload);

        List<SchematicBuildPlan.Placement> placements = new ArrayList<>();
        Map<String, Integer> materials = new HashMap<>();
        int ignoredAirBlocks = 0;
        for (int linearIndex = 0; linearIndex < paletteIndices.size(); linearIndex++) {
            PaletteEntry paletteEntry = palette.get(paletteIndices.get(linearIndex));
            if (paletteEntry == null) {
                throw new SchematicLoadException(filename + " references missing palette entry " + paletteIndices.get(linearIndex)
                    + " at block index " + linearIndex + ".");
            }
            if (paletteEntry.state().isAir()) {
                ignoredAirBlocks++;
                continue;
            }
            int x = linearIndex % width;
            int z = (linearIndex / width) % length;
            int y = linearIndex / (width * length);
            placements.add(new SchematicBuildPlan.Placement(new BlockPos(x, y, z), paletteEntry.state(), paletteEntry.stateId()));
            String materialId = BuiltInRegistries.ITEM.getKey(paletteEntry.state().getBlock().asItem()).toString();
            materials.merge(materialId, 1, Integer::sum);
        }

        // Bottom-up gives later construction passes a deterministic dependency-friendly starting order.
        placements.sort(Comparator
            .comparingInt((SchematicBuildPlan.Placement placement) -> placement.relativePosition().getY())
            .thenComparingInt(placement -> placement.relativePosition().getZ())
            .thenComparingInt(placement -> placement.relativePosition().getX()));
        return new SchematicBuildPlan(source, new SchematicBuildPlan.Dimensions(width, height, length), offset,
            placements, new LinkedHashMap<>(materials), ignoredAirBlocks);
    }

    private static int readPositiveDimension(CompoundTag root, String key, String filename) throws SchematicLoadException {
        int value = readNumber(root, "getShort", key);
        if (value <= 0) {
            value = readNumber(root, "getInt", key);
        }
        if (value <= 0) {
            throw new SchematicLoadException(filename + " has an invalid " + key + " dimension.");
        }
        return value;
    }

    private static Map<Integer, PaletteEntry> readPalette(CompoundTag root, String filename) throws SchematicLoadException {
        CompoundTag paletteTag = readCompound(root, "Palette");
        if (paletteTag == null) {
            throw new SchematicLoadException(filename + " has no Sponge Palette.");
        }
        Map<Integer, PaletteEntry> palette = new HashMap<>();
        for (String stateId : paletteTag.keySet()) {
            int index = readNumber(paletteTag, "getInt", stateId);
            if (index < 0 || palette.put(index, new PaletteEntry(parseBlockState(stateId, filename), stateId)) != null) {
                throw new SchematicLoadException(filename + " has an invalid or duplicate palette index: " + index + ".");
            }
        }
        if (palette.isEmpty()) {
            throw new SchematicLoadException(filename + " has an empty Palette.");
        }
        return palette;
    }

    private static List<Integer> decodeVarInts(byte[] data, long expectedCount, String filename) throws SchematicLoadException {
        List<Integer> values = new ArrayList<>((int) expectedCount);
        int value = 0;
        int shift = 0;
        for (byte raw : data) {
            int unsigned = raw & 0xFF;
            value |= (unsigned & 0x7F) << shift;
            if ((unsigned & 0x80) == 0) {
                values.add(value);
                value = 0;
                shift = 0;
                if (values.size() > expectedCount) {
                    throw new SchematicLoadException(filename + " has more BlockData entries than its dimensions allow.");
                }
            } else {
                shift += 7;
                if (shift >= 35) {
                    throw new SchematicLoadException(filename + " has an invalid BlockData varint.");
                }
            }
        }
        if (shift != 0 || values.size() != expectedCount) {
            throw new SchematicLoadException(filename + " BlockData does not match its dimensions (expected " + expectedCount
                + " entries, found " + values.size() + ").");
        }
        return values;
    }

    private static BlockPos readOffset(CompoundTag root) {
        int[] offset = readIntArray(root, "Offset");
        return offset.length == 3 ? new BlockPos(offset[0], offset[1], offset[2]) : BlockPos.ZERO;
    }

    /**
     * CompoundTag changed its typed getters from direct values to Optional values
     * during the supported 1.21 range. Keep that version seam out of the parser.
     */
    private static Object readTagValue(CompoundTag tag, String methodName, String key) throws SchematicLoadException {
        try {
            Method method = CompoundTag.class.getMethod(methodName, String.class);
            Object value = method.invoke(tag, key);
            return value instanceof Optional<?> optional ? optional.orElse(null) : value;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new SchematicLoadException("Could not read schematic NBT field " + key + ".", exception);
        }
    }

    private static int readNumber(CompoundTag tag, String methodName, String key) throws SchematicLoadException {
        Object value = readTagValue(tag, methodName, key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static byte[] readByteArray(CompoundTag tag, String key) throws SchematicLoadException {
        Object value = readTagValue(tag, "getByteArray", key);
        return value instanceof byte[] array ? array : new byte[0];
    }

    private static int[] readIntArray(CompoundTag tag, String key) {
        try {
            Object value = readTagValue(tag, "getIntArray", key);
            return value instanceof int[] array ? array : new int[0];
        } catch (SchematicLoadException ignored) {
            return new int[0];
        }
    }

    private static CompoundTag readCompound(CompoundTag tag, String key) throws SchematicLoadException {
        Object value = readTagValue(tag, "getCompound", key);
        return value instanceof CompoundTag compound ? compound : null;
    }

    private static BlockState parseBlockState(String stateId, String filename) throws SchematicLoadException {
        int propertyStart = stateId.indexOf('[');
        String blockId = propertyStart < 0 ? stateId : stateId.substring(0, propertyStart);
        if (propertyStart >= 0 && (!stateId.endsWith("]") || propertyStart == stateId.length() - 1)) {
            throw new SchematicLoadException(filename + " has malformed block state " + stateId + ".");
        }
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
            throw new SchematicLoadException(filename + " references unavailable block " + blockId + ".");
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
        if (block == null) {
            throw new SchematicLoadException(filename + " references unavailable block " + blockId + ".");
        }
        BlockState state = block.defaultBlockState();
        if (propertyStart < 0) {
            return state;
        }
        String properties = stateId.substring(propertyStart + 1, stateId.length() - 1);
        for (String assignment : properties.split(",")) {
            String[] pair = assignment.split("=", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                throw new SchematicLoadException(filename + " has malformed property in " + stateId + ".");
            }
            Property<?> property = state.getBlock().getStateDefinition().getProperty(pair[0]);
            if (property == null) {
                throw new SchematicLoadException(filename + " uses unknown property " + pair[0] + " for " + blockId + ".");
            }
            state = applyProperty(state, property, pair[1], filename, stateId);
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property property, String value, String filename, String stateId)
        throws SchematicLoadException {
        Optional<Comparable> parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            throw new SchematicLoadException(filename + " uses invalid value " + value + " in " + stateId + ".");
        }
        return state.setValue(property, parsed.get());
    }

    private record PaletteEntry(BlockState state, String stateId) {
    }
}

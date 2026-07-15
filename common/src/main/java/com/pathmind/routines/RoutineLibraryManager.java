package com.pathmind.routines;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.nodes.NodeType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/** Persistent routine library stored independently from workspace presets. */
public final class RoutineLibraryManager {
    private static final String FILE_NAME = "routine-library.json";
    private static long cachedModified = Long.MIN_VALUE;
    private static long lastCacheCheckNanos = Long.MIN_VALUE;
    private static List<NodeGraphData.RoutineDefinitionData> cachedRoutines = List.of();

    public record ImportResult(NodeGraphData.RoutineDefinitionData routine, boolean added, String message) {}

    private RoutineLibraryManager() {}

    public static Path getLibraryPath() {
        return PresetManager.getBaseDirectory().resolve(FILE_NAME);
    }

    public static synchronized List<NodeGraphData.RoutineDefinitionData> list() {
        Path path = getLibraryPath();
        long now = System.nanoTime();
        if (cachedModified == Long.MIN_VALUE || lastCacheCheckNanos == Long.MIN_VALUE
            || now - lastCacheCheckNanos >= 500_000_000L) {
            long modified = modified(path);
            if (modified != cachedModified) {
                cachedRoutines = list(path);
                cachedModified = modified;
            }
            lastCacheCheckNanos = now;
        }
        return List.copyOf(cachedRoutines);
    }

    static List<NodeGraphData.RoutineDefinitionData> list(Path path) {
        if (path == null || !Files.exists(path)) return List.of();
        NodeGraphData data = NodeGraphPersistence.loadNodeGraphFromPath(path);
        if (data == null) return List.of();
        return RoutineLifecycle.sorted(data.getRoutines()).stream().map(RoutineLibraryManager::copy).toList();
    }

    public static boolean share(NodeGraphData.RoutineDefinitionData routine) {
        return share(getLibraryPath(), routine);
    }

    /** Saves edits to a routine that already belongs to the global library. */
    public static boolean save(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null || blank(routine.getId())) return false;
        return save(getLibraryPath(), routine);
    }

    static boolean save(Path path, NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null || blank(routine.getId())) return false;
        return share(path, routine);
    }

    public static boolean delete(String routineId) {
        return delete(getLibraryPath(), routineId);
    }

    public static boolean rename(String routineId, String name) {
        return rename(getLibraryPath(), routineId, name);
    }

    static boolean rename(Path path, String routineId, String name) {
        if (path == null || blank(routineId) || blank(name) || !Files.exists(path)) return false;
        NodeGraphData library = NodeGraphPersistence.loadNodeGraphFromPath(path);
        if (library == null) return false;
        NodeGraphPersistence.sanitizeRoutineDefinitions(library);
        NodeGraphData.RoutineDefinitionData target = find(library.getRoutines(), routineId);
        String requested = name.trim();
        if (target == null || library.getRoutines().stream().filter(Objects::nonNull)
            .anyMatch(routine -> !routineId.equals(routine.getId())
                && requested.equalsIgnoreCase(safe(routine.getName()).trim()))) return false;
        new RoutineBuilderModel(target).renameRoutine(requested);
        boolean saved = NodeGraphPersistence.saveNodeGraphDataToPath(library, path);
        if (saved && path.equals(getLibraryPath())) {
            cachedModified = Long.MIN_VALUE;
            lastCacheCheckNanos = Long.MIN_VALUE;
        }
        return saved;
    }

    static boolean delete(Path path, String routineId) {
        if (path == null || blank(routineId) || !Files.exists(path)) return false;
        NodeGraphData library = NodeGraphPersistence.loadNodeGraphFromPath(path);
        if (library == null) return false;
        NodeGraphPersistence.sanitizeRoutineDefinitions(library);
        boolean removed = library.getRoutines().removeIf(routine -> routine != null && routineId.equals(routine.getId()));
        if (!removed) return false;
        boolean saved = NodeGraphPersistence.saveNodeGraphDataToPath(library, path);
        if (saved && path.equals(getLibraryPath())) {
            cachedModified = Long.MIN_VALUE;
            lastCacheCheckNanos = Long.MIN_VALUE;
        }
        return saved;
    }

    /** Shares the selected routine and any local routine dependencies it calls. */
    public static boolean share(NodeGraphData.RoutineDefinitionData routine,
                                List<NodeGraphData.RoutineDefinitionData> registry) {
        return share(getLibraryPath(), routine, registry);
    }

    static boolean share(Path path, NodeGraphData.RoutineDefinitionData routine,
                         List<NodeGraphData.RoutineDefinitionData> registry) {
        if (routine == null) return false;
        Map<String, NodeGraphData.RoutineDefinitionData> byId = new LinkedHashMap<>();
        if (registry != null) for (NodeGraphData.RoutineDefinitionData item : registry)
            if (item != null && !blank(item.getId())) byId.put(item.getId(), item);
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        return shareRecursive(path, routine, byId, visited);
    }

    static boolean share(Path path, NodeGraphData.RoutineDefinitionData routine) {
        if (path == null || routine == null || blank(routine.getId())) return false;
        NodeGraphData library = Files.exists(path) ? NodeGraphPersistence.loadNodeGraphFromPath(path) : new NodeGraphData();
        if (library == null) library = new NodeGraphData();
        NodeGraphPersistence.sanitizeRoutineDefinitions(library);
        NodeGraphData.RoutineDefinitionData shared = copy(routine);
        shared.setLibraryRoutineId(null);
        int existingIndex = indexById(library.getRoutines(), shared.getId());
        if (existingIndex >= 0) {
            library.getRoutines().set(existingIndex, shared);
        } else {
            shared.setName(uniqueName(shared.getName(), library.getRoutines()));
            library.getRoutines().add(shared);
        }
        boolean saved = NodeGraphPersistence.saveNodeGraphDataToPath(library, path);
        if (saved && path.equals(getLibraryPath())) {
            cachedModified = Long.MIN_VALUE;
            lastCacheCheckNanos = Long.MIN_VALUE;
        }
        return saved;
    }

    public static ImportResult importInto(NodeGraphData preset, String libraryRoutineId) {
        return importInto(preset, libraryRoutineId, getLibraryPath());
    }

    static ImportResult importInto(NodeGraphData preset, String libraryRoutineId, Path path) {
        if (preset == null || blank(libraryRoutineId)) return new ImportResult(null, false, "invalid");
        List<NodeGraphData.RoutineDefinitionData> library = path.equals(getLibraryPath()) ? list() : list(path);
        NodeGraphData.RoutineDefinitionData source = find(library, libraryRoutineId);
        if (source == null) return new ImportResult(null, false, "missing");
        return importRecursive(preset, source, library, new LinkedHashMap<>(), new LinkedHashSet<>());
    }

    private static ImportResult importRecursive(NodeGraphData preset, NodeGraphData.RoutineDefinitionData source,
                                                List<NodeGraphData.RoutineDefinitionData> library,
                                                Map<String, String> importedIds, LinkedHashSet<String> visiting) {
        String libraryRoutineId = source.getId();
        if (importedIds.containsKey(libraryRoutineId)) {
            NodeGraphData.RoutineDefinitionData existing = find(preset.getRoutines(), importedIds.get(libraryRoutineId));
            return new ImportResult(existing, false, "dependency-present");
        }
        if (!visiting.add(libraryRoutineId)) return new ImportResult(null, false, "recursive");
        NodeGraphData.RoutineDefinitionData imported = copy(source);
        String originalId = imported.getId();
        String localId = originalId;
        if (indexById(preset.getRoutines(), localId) >= 0) localId = java.util.UUID.randomUUID().toString();
        remapOwnedRoutineId(imported, originalId, localId);
        importedIds.put(originalId, localId);
        for (String dependencyId : calledRoutineIds(imported)) {
            if (dependencyId.equals(originalId)) continue;
            if (importedIds.containsKey(dependencyId)) {
                remapRoutineCalls(imported, dependencyId, importedIds.get(dependencyId));
                continue;
            }
            NodeGraphData.RoutineDefinitionData dependency = find(library, dependencyId);
            if (dependency == null) continue;
            ImportResult dependencyResult = importRecursive(preset, dependency, library, importedIds, visiting);
            if (dependencyResult.routine() != null) remapRoutineCalls(imported, dependencyId, dependencyResult.routine().getId());
        }
        visiting.remove(libraryRoutineId);
        imported.setName(uniqueName(imported.getName(), preset.getRoutines()));
        imported.setLibraryRoutineId(originalId);
        preset.getRoutines().add(imported);
        NodeGraphPersistence.sanitizeRoutineDefinitions(preset);
        return new ImportResult(imported, true, "imported");
    }

    private static NodeGraphData.RoutineDefinitionData copy(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null) return null;
        NodeGraphData wrapper = new NodeGraphData();
        wrapper.setRoutines(new ArrayList<>(List.of(routine)));
        NodeGraphData parsed = NodeGraphPersistence.parseNodeGraphData(NodeGraphPersistence.toPrettyJson(wrapper));
        return parsed == null || parsed.getRoutines().isEmpty() ? null : parsed.getRoutines().get(0);
    }

    private static boolean shareRecursive(Path path, NodeGraphData.RoutineDefinitionData routine,
                                          Map<String, NodeGraphData.RoutineDefinitionData> registry,
                                          LinkedHashSet<String> visited) {
        if (routine == null || !visited.add(routine.getId())) return true;
        for (String dependencyId : calledRoutineIds(routine)) {
            NodeGraphData.RoutineDefinitionData dependency = registry.get(dependencyId);
            if (dependency != null && !shareRecursive(path, dependency, registry, visited)) return false;
        }
        return share(path, routine);
    }

    private static LinkedHashSet<String> calledRoutineIds(NodeGraphData.RoutineDefinitionData routine) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (routine == null || routine.getGraph() == null) return ids;
        for (NodeGraphData.NodeData node : routine.getGraph().getNodes()) {
            if (node != null && node.getType() == NodeType.ROUTINE_CALL && !blank(node.getRoutineId())) ids.add(node.getRoutineId());
        }
        return ids;
    }

    private static void remapRoutineCalls(NodeGraphData.RoutineDefinitionData routine, String oldId, String newId) {
        if (routine == null || routine.getGraph() == null || Objects.equals(oldId, newId)) return;
        for (NodeGraphData.NodeData node : routine.getGraph().getNodes()) {
            if (node != null && node.getType() == NodeType.ROUTINE_CALL && Objects.equals(oldId, node.getRoutineId())) {
                node.setRoutineId(newId);
            }
        }
    }

    private static void remapOwnedRoutineId(NodeGraphData.RoutineDefinitionData routine, String oldId, String newId) {
        routine.setId(newId);
        if (routine.getGraph() == null) return;
        for (NodeGraphData.NodeData node : routine.getGraph().getNodes()) {
            if (node == null || !Objects.equals(oldId, node.getRoutineId())) continue;
            if (node.getType() == NodeType.ROUTINE_ENTRY || node.getType() == NodeType.ROUTINE_INPUT
                || node.getType() == NodeType.ROUTINE_CALL) node.setRoutineId(newId);
        }
    }

    private static NodeGraphData.RoutineDefinitionData find(List<NodeGraphData.RoutineDefinitionData> routines, String id) {
        if (routines == null) return null;
        return routines.stream().filter(Objects::nonNull).filter(routine -> Objects.equals(id, routine.getId()))
            .findFirst().orElse(null);
    }

    private static int indexById(List<NodeGraphData.RoutineDefinitionData> routines, String id) {
        if (routines == null) return -1;
        for (int i = 0; i < routines.size(); i++) if (routines.get(i) != null && Objects.equals(id, routines.get(i).getId())) return i;
        return -1;
    }

    private static String uniqueName(String requested, List<NodeGraphData.RoutineDefinitionData> routines) {
        String base = blank(requested) ? "Routine" : requested.trim();
        String candidate = base;
        int suffix = 2;
        while (containsName(routines, candidate)) candidate = base + " " + suffix++;
        return candidate;
    }

    private static boolean containsName(List<NodeGraphData.RoutineDefinitionData> routines, String name) {
        return routines != null && routines.stream().filter(Objects::nonNull)
            .anyMatch(routine -> safe(routine.getName()).equalsIgnoreCase(name));
    }

    private static long modified(Path path) {
        try { return path != null && Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1L; }
        catch (Exception ignored) { return -1L; }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String safe(String value) { return value == null ? "" : value; }
}

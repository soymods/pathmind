package com.pathmind.routines;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutineLibraryManagerTest {
    @TempDir Path tempDir;

    @Test
    void libraryIsSeparateAndPreservesStableRoutineAndInputIds() {
        Path libraryPath = tempDir.resolve("routine-library.json");
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Mine");
        NodeGraphData.RoutineInputData input = new RoutineBuilderModel(routine).addInput("block", RoutineValueKind.BLOCK);

        assertTrue(RoutineLibraryManager.share(libraryPath, routine));
        List<NodeGraphData.RoutineDefinitionData> library = RoutineLibraryManager.list(libraryPath);

        assertEquals(1, library.size());
        assertEquals(routine.getId(), library.get(0).getId());
        assertEquals(input.getId(), library.get(0).getInputs().get(0).getId());
    }

    @Test
    void libraryRoutineCanBeDeleted() {
        Path libraryPath = tempDir.resolve("routine-library.json");
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Temporary");
        assertTrue(RoutineLibraryManager.share(libraryPath, routine));

        assertTrue(RoutineLibraryManager.delete(libraryPath, routine.getId()));
        assertTrue(RoutineLibraryManager.list(libraryPath).isEmpty());
        assertFalse(RoutineLibraryManager.delete(libraryPath, routine.getId()));
    }

    @Test
    void importHandlesIdAndNameConflictsWithoutChangingInputIds() {
        Path libraryPath = tempDir.resolve("routine-library.json");
        NodeGraphData.RoutineDefinitionData source = RoutineBuilderModel.createRoutine("Build");
        NodeGraphData.RoutineInputData input = new RoutineBuilderModel(source).addInput("block", RoutineValueKind.BLOCK);
        assertTrue(RoutineLibraryManager.share(libraryPath, source));

        NodeGraphData preset = new NodeGraphData();
        NodeGraphData.RoutineDefinitionData conflicting = RoutineBuilderModel.createRoutine("Build");
        conflicting.setId(source.getId());
        preset.getRoutines().add(conflicting);

        RoutineLibraryManager.ImportResult imported = RoutineLibraryManager.importInto(
            preset, source.getId(), libraryPath);

        assertTrue(imported.added());
        assertNotEquals(source.getId(), imported.routine().getId());
        assertNotEquals(source.getName(), imported.routine().getName());
        assertEquals(input.getId(), imported.routine().getInputs().get(0).getId());
    }

    @Test
    void sharingAndImportingIncludesRoutineDependencies() {
        Path libraryPath = tempDir.resolve("routine-library.json");
        NodeGraphData.RoutineDefinitionData helper = RoutineBuilderModel.createRoutine("Helper");
        NodeGraphData.RoutineDefinitionData owner = RoutineBuilderModel.createRoutine("Owner");
        owner.setGraph(NodeGraphPersistence.createGraphData(List.of(Node.createRoutineCall(helper, 40, 40)), List.of()));

        assertTrue(RoutineLibraryManager.share(libraryPath, owner, List.of(owner, helper)));
        NodeGraphData preset = new NodeGraphData();
        RoutineLibraryManager.ImportResult result = RoutineLibraryManager.importInto(
            preset, owner.getId(), libraryPath);

        assertTrue(result.added());
        assertEquals(2, preset.getRoutines().size());
        NodeGraphData.NodeData call = result.routine().getGraph().getNodes().stream()
            .filter(node -> node.getType() == NodeType.ROUTINE_CALL).findFirst().orElseThrow();
        assertTrue(preset.getRoutines().stream().anyMatch(routine -> routine.getId().equals(call.getRoutineId())));
    }
}

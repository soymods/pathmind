package com.pathmind.execution;

import com.pathmind.execution.ExecutionManager.RuntimeList;
import com.pathmind.execution.ExecutionManager.RuntimeListEntry;
import com.pathmind.execution.ExecutionManager.RuntimeVariable;
import com.pathmind.execution.ExecutionManager.RuntimeVariableEntry;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.RuntimeValueScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ExecutionRuntimeValueStore {
    interface Host {
        Scope findScopeForStart(Node startNode);
        Scope currentScope();
        Collection<Scope> activeScopes();
        List<Node> activeNodes();
        List<Node> workspaceNodes();
    }

    static final class Scope {
        final Node startNode;
        final Scope parentScope;
        final List<Node> graphNodes;
        final Map<String, RuntimeVariable> runtimeVariables = new ConcurrentHashMap<>();
        final Map<String, RuntimeList> runtimeLists = new ConcurrentHashMap<>();

        Scope(Node startNode, Scope parentScope, List<Node> graphNodes) {
            this.startNode = startNode;
            this.parentScope = parentScope;
            this.graphNodes = graphNodes;
        }
    }

    private final Host host;
    private final Map<String, RuntimeVariable> globalRuntimeVariables = new ConcurrentHashMap<>();
    private final Map<String, RuntimeList> globalRuntimeLists = new ConcurrentHashMap<>();

    ExecutionRuntimeValueStore(Host host) {
        this.host = host;
    }

    boolean setRuntimeVariable(Node startNode, String name, RuntimeVariable value) {
        return setRuntimeVariable(startNode, name, value, RuntimeValueScope.GLOBAL);
    }

    boolean setRuntimeVariable(Node startNode, String name, RuntimeVariable value, RuntimeValueScope scope) {
        RuntimeValueScope resolvedScope = RuntimeValueScope.orGlobal(scope);
        if (name == null || name.trim().isEmpty() || value == null
            || (startNode == null && resolvedScope != RuntimeValueScope.GLOBAL)) {
            return false;
        }
        Scope runtimeScope = startNode == null ? null : host.findScopeForStart(startNode);
        return storeRuntimeVariable(runtimeScope, name.trim(), value, resolvedScope);
    }

    RuntimeVariable getRuntimeVariable(Node startNode, String name) {
        return getRuntimeVariable(startNode, name, RuntimeValueScope.GLOBAL);
    }

    RuntimeVariable getRuntimeVariable(Node startNode, String name, RuntimeValueScope scope) {
        RuntimeValueScope resolvedScope = RuntimeValueScope.orGlobal(scope);
        if (name == null || name.trim().isEmpty()
            || (startNode == null && resolvedScope != RuntimeValueScope.GLOBAL)) {
            return null;
        }
        Scope runtimeScope = startNode == null ? null : host.findScopeForStart(startNode);
        return resolveRuntimeVariable(runtimeScope, name.trim(), resolvedScope);
    }

    boolean setGlobalRuntimeVariable(String name, RuntimeVariable value) {
        return setRuntimeVariable(null, name, value, RuntimeValueScope.GLOBAL);
    }

    RuntimeVariable getGlobalRuntimeVariable(String name) {
        return getRuntimeVariable(null, name, RuntimeValueScope.GLOBAL);
    }

    boolean setRuntimeVariableForAnyActiveChain(String name, RuntimeVariable value) {
        if (name == null || name.trim().isEmpty() || value == null) {
            return false;
        }
        globalRuntimeVariables.put(name.trim(), value);
        return true;
    }

    RuntimeVariable getRuntimeVariableFromAnyActiveChain(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        Scope currentScope = host.currentScope();
        if (currentScope != null) {
            RuntimeVariable currentValue = resolveRuntimeVariable(currentScope, name.trim(), RuntimeValueScope.CHAIN);
            if (currentValue != null) {
                return currentValue;
            }
        }
        for (Scope runtimeScope : host.activeScopes()) {
            RuntimeVariable value = resolveRuntimeVariable(runtimeScope, name.trim(), RuntimeValueScope.CHAIN);
            if (value != null) {
                return value;
            }
        }
        return globalRuntimeVariables.get(name.trim());
    }

    boolean setRuntimeList(Node startNode, String name, RuntimeList list) {
        return setRuntimeList(startNode, name, list, RuntimeValueScope.GLOBAL);
    }

    boolean setRuntimeList(Node startNode, String name, RuntimeList list, RuntimeValueScope scope) {
        RuntimeValueScope resolvedScope = RuntimeValueScope.orGlobal(scope);
        if (name == null || name.trim().isEmpty() || list == null
            || (startNode == null && resolvedScope != RuntimeValueScope.GLOBAL)) {
            return false;
        }
        Scope runtimeScope = startNode == null ? null : host.findScopeForStart(startNode);
        return storeRuntimeList(runtimeScope, name.trim(), list, resolvedScope);
    }

    RuntimeList getRuntimeList(Node startNode, String name) {
        return getRuntimeList(startNode, name, RuntimeValueScope.GLOBAL);
    }

    RuntimeList getRuntimeList(Node startNode, String name, RuntimeValueScope scope) {
        RuntimeValueScope resolvedScope = RuntimeValueScope.orGlobal(scope);
        if (name == null || name.trim().isEmpty()
            || (startNode == null && resolvedScope != RuntimeValueScope.GLOBAL)) {
            return null;
        }
        Scope runtimeScope = startNode == null ? null : host.findScopeForStart(startNode);
        return resolveRuntimeList(runtimeScope, name.trim(), resolvedScope);
    }

    boolean setGlobalRuntimeList(String name, RuntimeList list) {
        return setRuntimeList(null, name, list, RuntimeValueScope.GLOBAL);
    }

    RuntimeList getGlobalRuntimeList(String name) {
        return getRuntimeList(null, name, RuntimeValueScope.GLOBAL);
    }

    RuntimeValueScope resolveRuntimeListScope(Node startNode, String name, RuntimeValueScope fallback) {
        RuntimeValueScope resolvedFallback = RuntimeValueScope.orGlobal(fallback);
        if (name == null || name.trim().isEmpty()) {
            return resolvedFallback;
        }
        String normalizedName = name.trim();
        Scope runtimeScope = startNode == null ? null : host.findScopeForStart(startNode);
        for (Scope current = runtimeScope; current != null; current = current.parentScope) {
            RuntimeValueScope declared = findRuntimeListDeclarationScope(current.graphNodes, normalizedName);
            if (declared != null) {
                return declared;
            }
        }
        RuntimeValueScope declared = findRuntimeListDeclarationScope(host.activeNodes(), normalizedName);
        if (declared != null) {
            return declared;
        }
        declared = findRuntimeListDeclarationScope(host.workspaceNodes(), normalizedName);
        return declared != null ? declared : resolvedFallback;
    }

    private RuntimeValueScope findRuntimeListDeclarationScope(List<Node> sourceNodes, String name) {
        if (sourceNodes == null || sourceNodes.isEmpty()) {
            return null;
        }
        for (Node candidate : sourceNodes) {
            if (candidate == null || candidate.getType() != NodeType.CREATE_LIST) {
                continue;
            }
            NodeParameter parameter = candidate.getParameter("List");
            String candidateName = parameter != null ? parameter.getStringValue() : null;
            if (candidateName != null && name.equals(candidateName.trim())) {
                return candidate.getRuntimeValueScope();
            }
        }
        return null;
    }

    List<RuntimeVariableEntry> getRuntimeVariableEntries() {
        List<RuntimeVariableEntry> entries = new ArrayList<>();
        for (Scope runtimeScope : host.activeScopes()) {
            if (runtimeScope == null || runtimeScope.runtimeVariables.isEmpty()) {
                continue;
            }
            String startId = runtimeScope.startNode != null ? runtimeScope.startNode.getId() : "";
            for (Map.Entry<String, RuntimeVariable> entry : runtimeScope.runtimeVariables.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                entries.add(new RuntimeVariableEntry(startId, entry.getKey(), entry.getValue(), RuntimeValueScope.CHAIN));
            }
        }
        for (Map.Entry<String, RuntimeVariable> entry : globalRuntimeVariables.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                entries.add(new RuntimeVariableEntry("", entry.getKey(), entry.getValue(), RuntimeValueScope.GLOBAL));
            }
        }
        return entries;
    }

    List<RuntimeListEntry> getRuntimeListEntries() {
        List<RuntimeListEntry> entries = new ArrayList<>();
        for (Scope runtimeScope : host.activeScopes()) {
            if (runtimeScope == null || runtimeScope.runtimeLists.isEmpty()) {
                continue;
            }
            String startId = runtimeScope.startNode != null ? runtimeScope.startNode.getId() : "";
            for (Map.Entry<String, RuntimeList> entry : runtimeScope.runtimeLists.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                entries.add(new RuntimeListEntry(startId, entry.getKey(), entry.getValue(), RuntimeValueScope.CHAIN));
            }
        }
        for (Map.Entry<String, RuntimeList> entry : globalRuntimeLists.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                entries.add(new RuntimeListEntry("", entry.getKey(), entry.getValue(), RuntimeValueScope.GLOBAL));
            }
        }
        return entries;
    }

    Set<String> getKnownRuntimeVariableNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Scope runtimeScope : host.activeScopes()) {
            if (runtimeScope == null || runtimeScope.runtimeVariables.isEmpty()) {
                continue;
            }
            for (String name : runtimeScope.runtimeVariables.keySet()) {
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name.trim());
                }
            }
        }
        for (String name : globalRuntimeVariables.keySet()) {
            if (name != null && !name.trim().isEmpty()) {
                names.add(name.trim());
            }
        }
        return names;
    }

    void seedRuntimeVariables(Node startNode, Map<String, RuntimeVariable> runtimeVariables) {
        if (startNode == null || runtimeVariables == null || runtimeVariables.isEmpty()) {
            return;
        }
        for (Map.Entry<String, RuntimeVariable> entry : runtimeVariables.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            setRuntimeVariable(startNode, entry.getKey(), entry.getValue());
        }
    }

    private boolean storeRuntimeVariable(Scope runtimeScope, String name, RuntimeVariable value, RuntimeValueScope scope) {
        if (name == null || name.isEmpty() || value == null) {
            return false;
        }
        RuntimeValueScope resolvedScope = RuntimeValueScope.orGlobal(scope);
        if (resolvedScope == RuntimeValueScope.GLOBAL) {
            globalRuntimeVariables.put(name, value);
            return true;
        }
        if (runtimeScope == null) {
            return false;
        }
        if (resolvedScope == RuntimeValueScope.CHAIN) {
            rootScope(runtimeScope).runtimeVariables.put(name, value);
            return true;
        }
        return false;
    }

    private RuntimeVariable resolveRuntimeVariable(Scope runtimeScope, String name, RuntimeValueScope scope) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        RuntimeValueScope resolvedScope = RuntimeValueScope.orGlobal(scope);
        if (resolvedScope == RuntimeValueScope.GLOBAL) {
            return globalRuntimeVariables.get(name);
        }
        for (Scope current = runtimeScope; current != null; current = current.parentScope) {
            RuntimeVariable value = current.runtimeVariables.get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean storeRuntimeList(Scope runtimeScope, String name, RuntimeList list, RuntimeValueScope scope) {
        if (name == null || name.isEmpty() || list == null) {
            return false;
        }
        RuntimeValueScope resolvedScope = RuntimeValueScope.orGlobal(scope);
        if (resolvedScope == RuntimeValueScope.GLOBAL) {
            globalRuntimeLists.put(name, list);
            return true;
        }
        if (runtimeScope == null) {
            return false;
        }
        if (resolvedScope == RuntimeValueScope.CHAIN) {
            rootScope(runtimeScope).runtimeLists.put(name, list);
            return true;
        }
        return false;
    }

    private RuntimeList resolveRuntimeList(Scope runtimeScope, String name, RuntimeValueScope scope) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        RuntimeValueScope resolvedScope = RuntimeValueScope.orGlobal(scope);
        if (resolvedScope == RuntimeValueScope.GLOBAL) {
            return globalRuntimeLists.get(name);
        }
        for (Scope current = runtimeScope; current != null; current = current.parentScope) {
            RuntimeList value = current.runtimeLists.get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    void clear() {
        globalRuntimeVariables.clear();
        globalRuntimeLists.clear();
    }

    private Scope rootScope(Scope runtimeScope) {
        Scope root = runtimeScope;
        while (root != null && root.parentScope != null) {
            root = root.parentScope;
        }
        return root;
    }
}

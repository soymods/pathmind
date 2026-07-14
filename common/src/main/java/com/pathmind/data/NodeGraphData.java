package com.pathmind.data;

import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.NodeMode;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.nodes.StartScreenTarget;
import com.pathmind.nodes.RuntimeValueScope;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable data structure for saving and loading node graphs.
 */
public class NodeGraphData {
    private List<NodeData> nodes;
    private List<ConnectionData> connections;
    private CustomNodeDefinition customNodeDefinition;
    private List<RoutineDefinitionData> routines;
    
    public NodeGraphData() {
        this.nodes = new ArrayList<>();
        this.connections = new ArrayList<>();
        this.routines = new ArrayList<>();
    }
    
    public NodeGraphData(List<NodeData> nodes, List<ConnectionData> connections) {
        this.nodes = nodes;
        this.connections = connections;
        this.routines = new ArrayList<>();
    }
    
    public List<NodeData> getNodes() {
        return nodes;
    }
    
    public void setNodes(List<NodeData> nodes) {
        this.nodes = nodes;
    }
    
    public List<ConnectionData> getConnections() {
        return connections;
    }
    
    public void setConnections(List<ConnectionData> connections) {
        this.connections = connections;
    }

    public CustomNodeDefinition getCustomNodeDefinition() {
        return customNodeDefinition;
    }

    public void setCustomNodeDefinition(CustomNodeDefinition customNodeDefinition) {
        this.customNodeDefinition = customNodeDefinition;
    }

    public List<RoutineDefinitionData> getRoutines() {
        if (routines == null) {
            routines = new ArrayList<>();
        }
        return routines;
    }

    public void setRoutines(List<RoutineDefinitionData> routines) {
        this.routines = routines == null ? new ArrayList<>() : routines;
    }

    /** Serialized routine metadata owned by this preset. */
    public static class RoutineDefinitionData {
        private String id;
        private String name;
        private Integer interfaceVersion;
        private Integer implementationRevision;
        private String interfaceSignature;
        private String implementationSignature;
        private List<RoutineInputData> inputs;
        private NodeGraphData graph;

        public RoutineDefinitionData() {
            this.inputs = new ArrayList<>();
            this.graph = new NodeGraphData();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getInterfaceVersion() { return interfaceVersion; }
        public void setInterfaceVersion(Integer interfaceVersion) { this.interfaceVersion = interfaceVersion; }
        public Integer getImplementationRevision() { return implementationRevision; }
        public void setImplementationRevision(Integer implementationRevision) { this.implementationRevision = implementationRevision; }
        public String getInterfaceSignature() { return interfaceSignature; }
        public void setInterfaceSignature(String interfaceSignature) { this.interfaceSignature = interfaceSignature; }
        public String getImplementationSignature() { return implementationSignature; }
        public void setImplementationSignature(String implementationSignature) { this.implementationSignature = implementationSignature; }
        public List<RoutineInputData> getInputs() {
            if (inputs == null) {
                inputs = new ArrayList<>();
            }
            return inputs;
        }
        public void setInputs(List<RoutineInputData> inputs) {
            this.inputs = inputs == null ? new ArrayList<>() : inputs;
        }
        public NodeGraphData getGraph() { return graph; }
        public void setGraph(NodeGraphData graph) { this.graph = graph; }
    }

    /** Serialized stable input identity and interface metadata for a routine. */
    public static class RoutineInputData {
        private String id;
        private String label;
        private String valueKind;
        private List<String> acceptedTraits;
        private Boolean required;
        private String defaultValue;
        private Integer order;

        public RoutineInputData() {
            this.acceptedTraits = new ArrayList<>();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getValueKind() { return valueKind; }
        public void setValueKind(String valueKind) { this.valueKind = valueKind; }
        public List<String> getAcceptedTraits() {
            if (acceptedTraits == null) {
                acceptedTraits = new ArrayList<>();
            }
            return acceptedTraits;
        }
        public void setAcceptedTraits(List<String> acceptedTraits) {
            this.acceptedTraits = acceptedTraits == null ? new ArrayList<>() : acceptedTraits;
        }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public Integer getOrder() { return order; }
        public void setOrder(Integer order) { this.order = order; }
    }

    public static class CustomNodeDefinition {
        private String presetName;
        private String name;
        private Integer version;
        private String signature;
        private List<CustomNodePort> inputs;
        private List<CustomNodePort> outputs;

        public CustomNodeDefinition() {
            this.inputs = new ArrayList<>();
            this.outputs = new ArrayList<>();
        }

        public String getPresetName() { return presetName; }
        public void setPresetName(String presetName) { this.presetName = presetName; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }

        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }

        public List<CustomNodePort> getInputs() { return inputs; }
        public void setInputs(List<CustomNodePort> inputs) { this.inputs = inputs; }

        public List<CustomNodePort> getOutputs() { return outputs; }
        public void setOutputs(List<CustomNodePort> outputs) { this.outputs = outputs; }
    }

    public static class CustomNodePort {
        private String name;
        private String type;
        private String defaultValue;

        public CustomNodePort() {
        }

        public CustomNodePort(String name, String type, String defaultValue) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    }
    
    /**
     * Data structure for a single node
     */
    public static class NodeData {
        private String id;
        private NodeType type;
        private NodeMode mode;
        private int x, y;
        private List<ParameterData> parameters;
        private String attachedSensorId;
        private String parentControlId;
        private String attachedActionId;
        private String parentActionControlId;
        private String attachedParameterId;
        private String parentParameterHostId;
        private List<ParameterAttachmentData> parameterAttachments;
        private Boolean booleanToggleValue;
        private Integer parameterSlotCount;
        private Integer startNodeNumber;
        private StartLaunchMode startLaunchMode;
        private StartScreenTarget startScreenTarget;
        private String runtimeSourceNodeId;
        private RuntimeValueScope runtimeValueScope;
        private List<String> messageLines;
        private Boolean messageClientSide;
        private String bookText;
        private String stickyNoteText;
        private Integer stickyNoteWidth;
        private Integer stickyNoteHeight;
        private Boolean gotoAllowBreakWhileExecuting;
        private Boolean gotoAllowPlaceWhileExecuting;
        private Boolean keyPressedActivatesInGuis;
        private String templateName;
        private Integer templateVersion;
        private Boolean customNodeInstance;
        private NodeGraphData templateGraph;

        public NodeData() {
            this.parameters = new ArrayList<>();
            this.parameterAttachments = new ArrayList<>();
        }

        public NodeData(String id, NodeType type, NodeMode mode, int x, int y, List<ParameterData> parameters) {
            this.id = id;
            this.type = type;
            this.mode = mode;
            this.x = x;
            this.y = y;
            this.parameters = parameters;
            this.attachedSensorId = null;
            this.parentControlId = null;
            this.attachedActionId = null;
            this.parentActionControlId = null;
            this.attachedParameterId = null;
            this.parentParameterHostId = null;
            this.parameterAttachments = new ArrayList<>();
        }

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public NodeType getType() { return type; }
        public void setType(NodeType type) { this.type = type; }
        
        public NodeMode getMode() { return mode; }
        public void setMode(NodeMode mode) { this.mode = mode; }
        
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }

        public List<ParameterData> getParameters() { return parameters; }
        public void setParameters(List<ParameterData> parameters) { this.parameters = parameters; }

        public String getAttachedSensorId() { return attachedSensorId; }
        public void setAttachedSensorId(String attachedSensorId) { this.attachedSensorId = attachedSensorId; }

        public String getParentControlId() { return parentControlId; }
        public void setParentControlId(String parentControlId) { this.parentControlId = parentControlId; }

        public String getAttachedActionId() { return attachedActionId; }
        public void setAttachedActionId(String attachedActionId) { this.attachedActionId = attachedActionId; }

        public String getParentActionControlId() { return parentActionControlId; }
        public void setParentActionControlId(String parentActionControlId) { this.parentActionControlId = parentActionControlId; }

        public String getAttachedParameterId() { return attachedParameterId; }
        public void setAttachedParameterId(String attachedParameterId) { this.attachedParameterId = attachedParameterId; }

        public String getParentParameterHostId() { return parentParameterHostId; }
        public void setParentParameterHostId(String parentParameterHostId) { this.parentParameterHostId = parentParameterHostId; }

        public List<ParameterAttachmentData> getParameterAttachments() { return parameterAttachments; }
        public void setParameterAttachments(List<ParameterAttachmentData> parameterAttachments) { this.parameterAttachments = parameterAttachments; }

        public Boolean getBooleanToggleValue() {
            return booleanToggleValue;
        }

        public void setBooleanToggleValue(Boolean booleanToggleValue) {
            this.booleanToggleValue = booleanToggleValue;
        }

        public Integer getParameterSlotCount() {
            return parameterSlotCount;
        }

        public void setParameterSlotCount(Integer parameterSlotCount) {
            this.parameterSlotCount = parameterSlotCount;
        }

        public Integer getStartNodeNumber() {
            return startNodeNumber;
        }

        public void setStartNodeNumber(Integer startNodeNumber) {
            this.startNodeNumber = startNodeNumber;
        }

        public StartLaunchMode getStartLaunchMode() {
            return startLaunchMode;
        }

        public void setStartLaunchMode(StartLaunchMode startLaunchMode) {
            this.startLaunchMode = startLaunchMode;
        }

        public StartScreenTarget getStartScreenTarget() {
            return startScreenTarget;
        }

        public void setStartScreenTarget(StartScreenTarget startScreenTarget) {
            this.startScreenTarget = startScreenTarget;
        }

        public RuntimeValueScope getRuntimeValueScope() {
            return runtimeValueScope;
        }

        public void setRuntimeValueScope(RuntimeValueScope runtimeValueScope) {
            this.runtimeValueScope = runtimeValueScope;
        }

        public String getRuntimeSourceNodeId() {
            return runtimeSourceNodeId;
        }

        public void setRuntimeSourceNodeId(String runtimeSourceNodeId) {
            this.runtimeSourceNodeId = runtimeSourceNodeId;
        }

        public List<String> getMessageLines() {
            return messageLines;
        }

        public void setMessageLines(List<String> messageLines) {
            this.messageLines = messageLines;
        }

        public Boolean getMessageClientSide() {
            return messageClientSide;
        }

        public void setMessageClientSide(Boolean messageClientSide) {
            this.messageClientSide = messageClientSide;
        }

        public String getBookText() {
            return bookText;
        }

        public void setBookText(String bookText) {
            this.bookText = bookText;
        }

        public String getStickyNoteText() {
            return stickyNoteText;
        }

        public void setStickyNoteText(String stickyNoteText) {
            this.stickyNoteText = stickyNoteText;
        }

        public Integer getStickyNoteWidth() {
            return stickyNoteWidth;
        }

        public void setStickyNoteWidth(Integer stickyNoteWidth) {
            this.stickyNoteWidth = stickyNoteWidth;
        }

        public Integer getStickyNoteHeight() {
            return stickyNoteHeight;
        }

        public void setStickyNoteHeight(Integer stickyNoteHeight) {
            this.stickyNoteHeight = stickyNoteHeight;
        }

        public Boolean getGotoAllowBreakWhileExecuting() {
            return gotoAllowBreakWhileExecuting;
        }

        public void setGotoAllowBreakWhileExecuting(Boolean gotoAllowBreakWhileExecuting) {
            this.gotoAllowBreakWhileExecuting = gotoAllowBreakWhileExecuting;
        }

        public Boolean getGotoAllowPlaceWhileExecuting() {
            return gotoAllowPlaceWhileExecuting;
        }

        public void setGotoAllowPlaceWhileExecuting(Boolean gotoAllowPlaceWhileExecuting) {
            this.gotoAllowPlaceWhileExecuting = gotoAllowPlaceWhileExecuting;
        }

        public Boolean getKeyPressedActivatesInGuis() {
            return keyPressedActivatesInGuis;
        }

        public void setKeyPressedActivatesInGuis(Boolean keyPressedActivatesInGuis) {
            this.keyPressedActivatesInGuis = keyPressedActivatesInGuis;
        }

        public String getTemplateName() {
            return templateName;
        }

        public void setTemplateName(String templateName) {
            this.templateName = templateName;
        }

        public Integer getTemplateVersion() {
            return templateVersion;
        }

        public void setTemplateVersion(Integer templateVersion) {
            this.templateVersion = templateVersion;
        }

        public Boolean getCustomNodeInstance() {
            return customNodeInstance;
        }

        public void setCustomNodeInstance(Boolean customNodeInstance) {
            this.customNodeInstance = customNodeInstance;
        }

        public NodeGraphData getTemplateGraph() {
            return templateGraph;
        }

        public void setTemplateGraph(NodeGraphData templateGraph) {
            this.templateGraph = templateGraph;
        }
    }
    
    /**
     * Data structure for a connection between nodes
     */
    public static class ConnectionData {
        private String outputNodeId;
        private String inputNodeId;
        private int outputSocket;
        private int inputSocket;
        
        public ConnectionData() {}
        
        public ConnectionData(String outputNodeId, String inputNodeId, int outputSocket, int inputSocket) {
            this.outputNodeId = outputNodeId;
            this.inputNodeId = inputNodeId;
            this.outputSocket = outputSocket;
            this.inputSocket = inputSocket;
        }
        
        // Getters and setters
        public String getOutputNodeId() { return outputNodeId; }
        public void setOutputNodeId(String outputNodeId) { this.outputNodeId = outputNodeId; }
        
        public String getInputNodeId() { return inputNodeId; }
        public void setInputNodeId(String inputNodeId) { this.inputNodeId = inputNodeId; }
        
        public int getOutputSocket() { return outputSocket; }
        public void setOutputSocket(int outputSocket) { this.outputSocket = outputSocket; }
        
        public int getInputSocket() { return inputSocket; }
        public void setInputSocket(int inputSocket) { this.inputSocket = inputSocket; }
    }
    
    /**
     * Data structure for a node parameter
     */
    public static class ParameterData {
        private String id;
        private String name;
        private String value;
        private String type;
        private Boolean userEdited;
        
        public ParameterData() {}
        
        public ParameterData(String name, String value, String type) {
            this.name = name;
            this.value = value;
            this.type = type;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Boolean getUserEdited() { return userEdited; }
        public void setUserEdited(Boolean userEdited) { this.userEdited = userEdited; }
    }

    public static class ParameterAttachmentData {
        private int slotIndex;
        private String parameterNodeId;

        public ParameterAttachmentData() {
        }

        public ParameterAttachmentData(int slotIndex, String parameterNodeId) {
            this.slotIndex = slotIndex;
            this.parameterNodeId = parameterNodeId;
        }

        public int getSlotIndex() {
            return slotIndex;
        }

        public void setSlotIndex(int slotIndex) {
            this.slotIndex = slotIndex;
        }

        public String getParameterNodeId() {
            return parameterNodeId;
        }

        public void setParameterNodeId(String parameterNodeId) {
            this.parameterNodeId = parameterNodeId;
        }
    }
}

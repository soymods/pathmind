package com.pathmind.data;

import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.NodeMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable data structure for saving and loading node graphs.
 */
public class NodeGraphData {
    private List<NodeData> nodes;
    private List<ConnectionData> connections;
    private CustomNodeDefinition customNodeDefinition;
    
    public NodeGraphData() {
        this.nodes = new ArrayList<>();
        this.connections = new ArrayList<>();
    }
    
    public NodeGraphData(List<NodeData> nodes, List<ConnectionData> connections) {
        this.nodes = nodes;
        this.connections = connections;
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
        private Integer startNodeNumber;
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

        public Integer getStartNodeNumber() {
            return startNodeNumber;
        }

        public void setStartNodeNumber(Integer startNodeNumber) {
            this.startNodeNumber = startNodeNumber;
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

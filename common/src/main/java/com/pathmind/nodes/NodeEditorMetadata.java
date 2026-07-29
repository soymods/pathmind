package com.pathmind.nodes;

import com.pathmind.data.NodeGraphData;

final class NodeEditorMetadata {
    private final NodeType type;
    private String templateName;
    private int templateVersion;
    private NodeGraphData templateGraphData;
    private RuntimeValueScope runtimeValueScope;

    NodeEditorMetadata(NodeType type) {
        this.type = type;
        this.templateName = usesTemplateBacking() ? "Template" : "";
        this.templateVersion = 0;
        this.templateGraphData = null;
        this.runtimeValueScope = RuntimeValueScope.GLOBAL;
    }

    String getTemplateName() {
        if (!usesTemplateBacking()) {
            return "";
        }
        return (templateName == null || templateName.isEmpty()) ? "Template" : templateName;
    }

    void setTemplateName(String templateName) {
        if (!usesTemplateBacking()) {
            return;
        }
        this.templateName = (templateName == null || templateName.isBlank()) ? "Template" : templateName.trim();
    }

    int getTemplateVersion() {
        return usesTemplateBacking() ? templateVersion : 0;
    }

    void setTemplateVersion(int templateVersion) {
        if (!usesTemplateBacking()) {
            return;
        }
        this.templateVersion = Math.max(0, templateVersion);
    }

    NodeGraphData getTemplateGraphData() {
        return usesTemplateBacking() ? templateGraphData : null;
    }

    void setTemplateGraphData(NodeGraphData templateGraphData) {
        if (!usesTemplateBacking()) {
            return;
        }
        this.templateGraphData = templateGraphData;
    }

    RuntimeValueScope getRuntimeValueScope() {
        return RuntimeValueScope.orGlobal(runtimeValueScope);
    }

    void setRuntimeValueScope(RuntimeValueScope runtimeValueScope) {
        this.runtimeValueScope = RuntimeValueScope.orGlobal(runtimeValueScope);
    }

    void toggleRuntimeValueScope() {
        runtimeValueScope = getRuntimeValueScope() == RuntimeValueScope.GLOBAL
            ? RuntimeValueScope.CHAIN
            : RuntimeValueScope.GLOBAL;
    }

    boolean supportsRuntimeValueScope() {
        return RuntimeValueScope.appliesTo(type);
    }

    private boolean usesTemplateBacking() {
        return type == NodeType.TEMPLATE;
    }
}

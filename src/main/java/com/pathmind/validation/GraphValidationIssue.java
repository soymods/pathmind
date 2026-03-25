package com.pathmind.validation;

public final class GraphValidationIssue {
    private final GraphValidationSeverity severity;
    private final String code;
    private final String message;
    private final String nodeId;

    public GraphValidationIssue(GraphValidationSeverity severity, String code, String message, String nodeId) {
        this.severity = severity;
        this.code = code == null ? "" : code;
        this.message = message == null ? "" : message;
        this.nodeId = nodeId;
    }

    public GraphValidationSeverity getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getNodeId() {
        return nodeId;
    }

    public boolean hasNodeTarget() {
        return nodeId != null && !nodeId.isBlank();
    }
}

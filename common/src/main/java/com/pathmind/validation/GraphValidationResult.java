package com.pathmind.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GraphValidationResult {
    private final List<GraphValidationIssue> issues;
    private final int errorCount;
    private final int warningCount;

    public static GraphValidationResult empty() {
        return new GraphValidationResult(Collections.emptyList());
    }

    public GraphValidationResult(List<GraphValidationIssue> issues) {
        List<GraphValidationIssue> safeIssues = issues == null ? Collections.emptyList() : new ArrayList<>(issues);
        this.issues = Collections.unmodifiableList(safeIssues);
        int errors = 0;
        int warnings = 0;
        for (GraphValidationIssue issue : safeIssues) {
            if (issue == null) {
                continue;
            }
            if (issue.getSeverity() == GraphValidationSeverity.ERROR) {
                errors++;
            } else if (issue.getSeverity() == GraphValidationSeverity.WARNING) {
                warnings++;
            }
        }
        this.errorCount = errors;
        this.warningCount = warnings;
    }

    public List<GraphValidationIssue> getIssues() {
        return issues;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public boolean hasWarnings() {
        return warningCount > 0;
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }
}

package com.pathmind.nodes;

/**
 * Persistence and runtime scope for named variables and lists.
 */
public enum RuntimeValueScope {
    CHAIN,
    GLOBAL;

    public static RuntimeValueScope orGlobal(RuntimeValueScope scope) {
        return scope == null ? GLOBAL : scope;
    }

    public static boolean appliesTo(NodeType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case VARIABLE,
                CREATE_LIST -> true;
            default -> false;
        };
    }
}

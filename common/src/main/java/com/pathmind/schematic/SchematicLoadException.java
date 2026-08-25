package com.pathmind.schematic;

/** A user-facing failure while reading or validating a schematic. */
public final class SchematicLoadException extends Exception {
    public SchematicLoadException(String message) {
        super(message);
    }

    public SchematicLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

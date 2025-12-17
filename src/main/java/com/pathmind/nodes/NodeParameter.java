package com.pathmind.nodes;

/**
 * Represents a parameter for a node in the Pathmind visual editor.
 * Each parameter has a name, value, and type.
 */
public class NodeParameter {
    private final String name;
    private final ParameterType type;
    private String stringValue;
    private int intValue;
    private double doubleValue;
    private boolean boolValue;

    public NodeParameter(String name, ParameterType type, String defaultValue) {
        this.name = name;
        this.type = type;
        this.stringValue = defaultValue;
        this.intValue = 0;
        this.doubleValue = 0.0;
        this.boolValue = false;
        
        // Try to parse the default value based on type
        if (type == ParameterType.INTEGER) {
            try {
                this.intValue = Integer.parseInt(defaultValue);
            } catch (NumberFormatException e) {
                this.intValue = 0;
            }
        } else if (type == ParameterType.DOUBLE) {
            try {
                this.doubleValue = Double.parseDouble(defaultValue);
            } catch (NumberFormatException e) {
                this.doubleValue = 0.0;
            }
        } else if (type == ParameterType.BOOLEAN) {
            this.boolValue = Boolean.parseBoolean(defaultValue);
        }
    }

    public String getName() {
        return name;
    }

    public ParameterType getType() {
        return type;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String value) {
        this.stringValue = value;
        
        // Update typed values
        if (type == ParameterType.INTEGER) {
            try {
                this.intValue = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Keep current intValue if parsing fails
            }
        } else if (type == ParameterType.DOUBLE) {
            try {
                this.doubleValue = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // Keep current doubleValue if parsing fails
            }
        } else if (type == ParameterType.BOOLEAN) {
            this.boolValue = Boolean.parseBoolean(value);
        }
    }

    public int getIntValue() {
        return intValue;
    }

    public void setIntValue(int value) {
        this.intValue = value;
        this.stringValue = String.valueOf(value);
    }

    public double getDoubleValue() {
        return doubleValue;
    }

    public void setDoubleValue(double value) {
        this.doubleValue = value;
        this.stringValue = String.valueOf(value);
    }

    public boolean getBoolValue() {
        return boolValue;
    }

    public void setBoolValue(boolean value) {
        this.boolValue = value;
        this.stringValue = String.valueOf(value);
    }

    public String getDisplayValue() {
        switch (type) {
            case INTEGER:
                return String.valueOf(intValue);
            case DOUBLE:
                return String.format("%.2f", doubleValue);
            case BOOLEAN:
                return boolValue ? "True" : "False";
            case STRING:
            default:
                return stringValue;
        }
    }
}

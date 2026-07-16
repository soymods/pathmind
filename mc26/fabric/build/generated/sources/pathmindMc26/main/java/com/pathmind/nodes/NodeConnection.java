package com.pathmind.nodes;

/**
 * Represents a connection between two nodes in the visual editor.
 * Similar to Blender's node connections, these link outputs to inputs.
 */
public class NodeConnection {
    private final Node outputNode;
    private final Node inputNode;
    private final int outputSocket;
    private final int inputSocket;

    public NodeConnection(Node outputNode, Node inputNode, int outputSocket, int inputSocket) {
        this.outputNode = outputNode;
        this.inputNode = inputNode;
        this.outputSocket = outputSocket;
        this.inputSocket = inputSocket;
    }

    public Node getOutputNode() {
        return outputNode;
    }

    public Node getInputNode() {
        return inputNode;
    }

    public int getOutputSocket() {
        return outputSocket;
    }

    public int getInputSocket() {
        return inputSocket;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NodeConnection that = (NodeConnection) obj;
        return outputSocket == that.outputSocket &&
               inputSocket == that.inputSocket &&
               outputNode.equals(that.outputNode) &&
               inputNode.equals(that.inputNode);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(outputNode, inputNode, outputSocket, inputSocket);
    }
}

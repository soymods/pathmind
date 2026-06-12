package com.pathmind.nodes;

import net.minecraft.client.MinecraftClient;

interface NodeParameterListEntryBehavior {
    Node.ListValueEntry resolveListValueEntry(Node owner, Node parameterNode, MinecraftClient client);
}

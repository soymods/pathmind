package com.pathmind.nodes;

import net.minecraft.client.Minecraft;

interface NodeParameterListEntryBehavior {
    Node.ListValueEntry resolveListValueEntry(Node owner, Node parameterNode, Minecraft client);
}

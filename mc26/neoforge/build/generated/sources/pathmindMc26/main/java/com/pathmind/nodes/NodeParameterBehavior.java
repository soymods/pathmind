package com.pathmind.nodes;

import java.util.Map;

interface NodeParameterBehavior {
    Map<String, String> exportValues(Node node, Map<String, String> baseValues);
}

import java.util.Properties

plugins {
    base
}

val repositoryRoot = layout.projectDirectory.dir("..").asFile
val repositoryProperties = Properties().apply {
    repositoryRoot.resolve("gradle.properties").inputStream().use(::load)
}
val compatibilityManifest = Properties().apply {
    repositoryRoot.resolve("gradle/minecraft-versions.properties").inputStream().use(::load)
}
val minecraftVersion = providers.gradleProperty("mc_version").orElse("26.1").get()
val prefix = "version.$minecraftVersion."

fun manifestValue(name: String): String = compatibilityManifest.getProperty(prefix + name)
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: throw GradleException("Missing compatibility manifest property '${prefix + name}'")

if (manifestValue("packaging_generation") != "mc26-unobfuscated") {
    throw GradleException("Minecraft $minecraftVersion is not an mc26-unobfuscated target")
}

extra["repositoryRoot"] = repositoryRoot
extra["minecraftVersion"] = minecraftVersion
extra["fabricLoaderVersion"] = manifestValue("fabric_loader")
extra["fabricLoaderMinimumVersion"] = manifestValue("fabric_loader_min")
extra["fabricApiVersion"] = manifestValue("fabric_api")
extra["neoForgeVersion"] = manifestValue("neoforge")
extra["targetJavaVersion"] = manifestValue("java_version").toInt()
extra["compatibilityFamily"] = manifestValue("compatibility_family")
extra["commonSourceFamily"] = manifestValue("common_source_family")
extra["fabricBaseFamily"] = manifestValue("fabric_base_family")
extra["neoForgeUiFamily"] = manifestValue("neoforge_ui_family")

val sharedSourceTransforms = linkedMapOf(
    "GuiGraphics" to "GuiGraphicsExtractor",
    "getGuiGraphicsExtractor()" to "getGuiGraphics()",
    "ClickType" to "ContainerInput",
    "handleInventoryMouseClick" to "handleContainerInput",
    ".drawCenteredString(" to ".centeredText(",
    ".drawString(" to ".text(",
    ".hLine(" to ".horizontalLine(",
    ".vLine(" to ".verticalLine(",
    ".renderItem(" to ".item(",
    "renderWithTooltipAndSubtitles" to "extractRenderStateWithTooltipAndSubtitles",
    "@Inject(method = \"render\", at = @At(\"HEAD\"), cancellable = true)" to
        "@Inject(method = \"extractRenderState\", at = @At(\"HEAD\"), cancellable = true)",
    "@Inject(method = \"render\", at = @At(\"TAIL\"))" to
        "@Inject(method = \"extractRenderState\", at = @At(\"TAIL\"))",
    "@Inject(method = \"renderSelectedItemName\", at = @At(\"TAIL\"))" to
        "@Inject(method = \"extractSelectedItemName\", at = @At(\"TAIL\"))",
    "method = \"renderItem(" to "method = \"item(",
    "method = \"renderFakeItem(" to "method = \"fakeItem(",
    "public void renderWidget(GuiGraphicsExtractor" to "public void extractWidgetRenderState(GuiGraphicsExtractor",
    "protected void renderContents(GuiGraphicsExtractor" to "protected void extractContents(GuiGraphicsExtractor",
    "public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)" to
        "public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)",
    "super.render(context, mouseX, mouseY, delta)" to "super.extractRenderState(context, mouseX, mouseY, delta)",
    "parent.render(context, mouseX, mouseY, delta)" to "parent.extractRenderState(context, mouseX, mouseY, delta)",
    "self.render(context, mouseX, mouseY, delta)" to "self.extractRenderState(context, mouseX, mouseY, delta)",
    "field.render(context," to "field.extractRenderState(context,",
    "inlinePresetRenameField.render(context," to "inlinePresetRenameField.extractRenderState(context,",
    "nodeSearchField.render(context," to "nodeSearchField.extractRenderState(context,",
    "settingsNodeSearchField.render(context," to "settingsNodeSearchField.extractRenderState(context,",
    "publishDescriptionField.render(context," to "publishDescriptionField.extractRenderState(context,",
    "searchField.render(context," to "searchField.extractRenderState(context,",
    "state.getValues()" to "com.pathmind.compat.Mc26BlockStateValues.asMap(state)",
    "entity.getTags()" to "entity.entityTags()",
    "client.level.getDayTime()" to "client.level.getOverworldClockTime()",
    "interact(client.player, entityHit.getEntity(), hand)" to
        "interact(client.player, entityHit.getEntity(), entityHit, hand)",
    "interact(client.player, targetEntity, hand)" to
        "interact(client.player, targetEntity, new EntityHitResult(targetEntity), hand)",
    "interact(client.player, entity, hand)" to
        "interact(client.player, entity, entityHit, hand)",
    "Item spawnEgg = SpawnEggItem.byId(entityType);" to
        "Item spawnEgg = SpawnEggItem.byId(entityType).map(holder -> holder.value()).orElse(null);"
)
val sourceTransformVersion = if (minecraftVersion.startsWith("26.1.")) "26.1" else minecraftVersion
val release26_2SourceTransforms = linkedMapOf(
    "this.minecraft.setScreen(" to "this.minecraft.gui.setScreen(",
    "releaseClient.setScreen(" to "releaseClient.gui.setScreen(",
    "client.setScreen(" to "client.gui.setScreen(",
    "minecraft.setScreen(" to "minecraft.gui.setScreen(",
    "releaseClient.screen" to "releaseClient.gui.screen()",
    "client.screen" to "client.gui.screen()",
    "minecraft.screen" to "minecraft.gui.screen()",
    ".isSingleplayer()" to ".hasSingleplayerServer()",
    ".getMainCamera()" to ".mainCamera()",
    ".collectPerFrameGizmos()" to ".collectPerFrameRenderThreadGizmos()",
    "\"collectPerFrameGizmos\"" to "\"collectPerFrameRenderThreadGizmos\"",
    "@Mixin(net.minecraft.client.gui.Gui.class)" to "@Mixin(net.minecraft.client.gui.Hud.class)",
    "method = \"renderLevel\"" to "method = \"render\"",
    "EntityType.VILLAGER" to "net.minecraft.world.entity.EntityTypes.VILLAGER",
    // NeoForge 26.2.0.93 folded EntityInteractSpecific into EntityInteract (now with getLocation()).
    "NeoForge.EVENT_BUS.addListener(this::onEntityInteractSpecific);" to
        "// EntityInteract also covers EntityInteractSpecific on NeoForge 26.2.",
    "PlayerInteractEvent.EntityInteractSpecific event" to "PlayerInteractEvent.EntityInteract event"
)
val versionSourceTransforms: Map<String, String> = when (sourceTransformVersion) {
    "26.1" -> emptyMap()
    "26.2" -> release26_2SourceTransforms
    "26.3" -> release26_2SourceTransforms
    else -> throw GradleException("No source-transform contract for Minecraft $minecraftVersion")
}
// Minecraft 26.3 replaced GLFW with SDL. Rewrite the authored GLFW input surface
// onto the 26.3 InputConstants/SDL API. Only 26.3 needs this; the 26.1/26.2 rows
// still ship GLFW.
val mc26SdlSourceTransforms: Map<String, String> = if (sourceTransformVersion == "26.3") {
    val transforms = linkedMapOf<String, String>()
    val swingDefault = "net.minecraft.world.item.component.SwingAnimation.DEFAULT"

    // Whole-expression rewrites that depend on the GLFW call shapes.
    transforms["GLFW.glfwGetKey(window.handle(), keyCode) == GLFW.GLFW_PRESS"] =
        "InputConstants.isKeyDown(keyCode)"
    transforms["GLFW.glfwGetKey(handle, fallbackKeyCode) == GLFW.GLFW_PRESS"] =
        "InputConstants.isKeyDown(fallbackKeyCode)"
    transforms["GLFW.glfwGetMouseButton(window.handle(), buttonCode) == GLFW.GLFW_PRESS"] =
        "com.pathmind.compat.Mc26Input.isMouseButtonPressed(client, buttonCode)"
    transforms["GLFW.glfwSetCursorPos(window.handle(), x, y);"] =
        "com.pathmind.compat.Mc26Input.warpCursor(window.handle(), x, y);"
    transforms["GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);"] =
        "com.pathmind.compat.Mc26Input.hideCursor();"
    transforms["GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);"] =
        "com.pathmind.compat.Mc26Input.showCursor();"
    transforms["GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);"] =
        "com.pathmind.compat.Mc26Input.hideCursor();"
    transforms["GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);"] =
        "com.pathmind.compat.Mc26Input.showCursor();"

    // Legacy GLFW names whose InputConstants spelling differs.
    mapOf(
        "GLFW_KEY_ENTER" to "KEY_RETURN",
        "GLFW_KEY_EQUAL" to "KEY_EQUALS",
        "GLFW_KEY_GRAVE_ACCENT" to "KEY_GRAVE",
        "GLFW_KEY_LEFT_BRACKET" to "KEY_LBRACKET",
        "GLFW_KEY_RIGHT_BRACKET" to "KEY_RBRACKET",
        "GLFW_KEY_RIGHT_ALT" to "KEY_RALT",
        "GLFW_KEY_KP_ENTER" to "KEY_NUMPADENTER",
        "GLFW_KEY_KP_EQUAL" to "KEY_NUMPADEQUALS"
    ).forEach { (from, to) -> transforms["GLFW.$from"] = "InputConstants.$to" }
    (0..9).forEach { digit -> transforms["GLFW.GLFW_KEY_KP_$digit"] = "InputConstants.KEY_NUMPAD$digit" }
    transforms["GLFW.GLFW_KEY_KP_ADD"] = "InputConstants.KEY_ADD"
    transforms["GLFW.GLFW_KEY_KP_MULTIPLY"] = "InputConstants.KEY_MULTIPLY"
    // SDL keeps no named constants for these numpad scancodes.
    transforms["GLFW.GLFW_KEY_KP_DIVIDE"] = "84"
    transforms["GLFW.GLFW_KEY_KP_SUBTRACT"] = "86"
    transforms["GLFW.GLFW_KEY_KP_DECIMAL"] = "99"
    transforms["GLFW.GLFW_KEY_UNKNOWN"] = "-1"
    ('A'..'Z').forEach { letter -> transforms["GLFW.GLFW_KEY_$letter"] = "InputConstants.KEY_$letter" }
    (0..9).forEach { digit -> transforms["GLFW.GLFW_KEY_$digit"] = "InputConstants.KEY_$digit" }
    (1..8).forEach { button -> transforms["GLFW.GLFW_MOUSE_BUTTON_$button"] = "InputConstants.MOUSE_BUTTON_$button" }
    transforms["GLFW.GLFW_MOUSE_BUTTON_LEFT"] = "InputConstants.MOUSE_BUTTON_LEFT"
    transforms["GLFW.GLFW_MOUSE_BUTTON_MIDDLE"] = "InputConstants.MOUSE_BUTTON_MIDDLE"
    transforms["GLFW.GLFW_MOUSE_BUTTON_RIGHT"] = "InputConstants.MOUSE_BUTTON_RIGHT"
    transforms["GLFW.GLFW_MOD_SHIFT"] = "InputConstants.MOD_SHIFT"
    transforms["GLFW.GLFW_MOD_CONTROL"] = "InputConstants.MOD_CONTROL"
    transforms["GLFW.GLFW_MOD_ALT"] = "InputConstants.MOD_ALT"
    transforms["GLFW.GLFW_MOD_SUPER"] = "InputConstants.MOD_SUPER"
    transforms["GLFW.GLFW_PRESS"] = "InputConstants.PRESS"
    transforms["GLFW.GLFW_RELEASE"] = "InputConstants.RELEASE"

    transforms["org.lwjgl.glfw.GLFW"] = "com.mojang.blaze3d.platform.InputConstants"
    transforms["GLFW.class"] = "InputConstants.class"
    transforms["GLFW."] = "InputConstants."
    transforms["InputConstants.Type.KEYSYM"] = "InputConstants.Type.KEYBOARD"

    // 26.3 moved arm animation onto SwingAnimation and URI opening onto Blaze3D.
    transforms[".swing(InteractionHand.MAIN_HAND);"] = ".swing(InteractionHand.MAIN_HAND, $swingDefault, true);"
    transforms[".swing(hand);"] = ".swing(hand, $swingDefault, true);"
    transforms[".swing(resolvedHand);"] = ".swing(resolvedHand, $swingDefault, true);"
    transforms["Util.getPlatform().openUri(url);"] =
        "com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(url));"
    transforms["Util.getPlatform().openUri(BaritoneDependencyChecker.DOWNLOAD_URL);"] =
        "com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(BaritoneDependencyChecker.DOWNLOAD_URL));"
    transforms["Util.getPlatform().openUri(downloadUrl);"] =
        "com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(downloadUrl));"

    // 26.3 renamed the KeyEvent scancode accessor and the TradeSet accessor, and
    // dropped the bundled tinyfd file dialogs.
    transforms["input.scancode()"] = "input.keycode()"
    // 26.3 reports SDL mouse buttons (left=1); Pathmind's GUI logic uses the legacy 0-based order.
    transforms["click.button()"] = "com.pathmind.compat.Mc26Input.legacyButton(click.button())"
    // NeoForge 26.3 renamed the physical-key accessor to match the SDL scancode name.
    transforms["event.getKeyCode()"] = "event.getKey()"
    // 26.3 split GameRenderer.render(DeltaTracker, boolean) into extract/render; the
    // overlay-capture injections still bind to the state-extraction phase.
    transforms["        method = \"render\","] = "        method = \"extract\","
    transforms["tradeSet.getTrades()"] = "tradeSet.trades()"
    transforms["org.lwjgl.util.tinyfd.TinyFileDialogs"] = "com.pathmind.compat.SdlFileDialogs"
    transforms["TinyFileDialogs."] = "SdlFileDialogs."

    transforms
} else {
    emptyMap()
}
extra["mc26SharedSourceTransforms"] = sharedSourceTransforms
extra["mc26VersionSourceTransforms"] = versionSourceTransforms
extra["mc26SdlSourceTransforms"] = mc26SdlSourceTransforms
extra["mc26SourceTransforms"] = sharedSourceTransforms + mc26SdlSourceTransforms + versionSourceTransforms
extra["mc26SourceTransformRevision"] = 16

subprojects {
    group = repositoryProperties.getProperty("maven_group")
    version = "${repositoryProperties.getProperty("mod_version")}+mc$minecraftVersion"
}

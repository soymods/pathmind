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
val versionSourceTransforms: Map<String, String> = when (minecraftVersion) {
    "26.1" -> emptyMap()
    "26.2" -> linkedMapOf(
        "this.minecraft.setScreen(" to "this.minecraft.gui.setScreen(",
        "releaseClient.setScreen(" to "releaseClient.gui.setScreen(",
        "client.setScreen(" to "client.gui.setScreen(",
        "minecraft.setScreen(" to "minecraft.gui.setScreen(",
        "releaseClient.screen" to "releaseClient.gui.screen()",
        "client.screen" to "client.gui.screen()",
        "minecraft.screen" to "minecraft.gui.screen()",
        ".getMainCamera()" to ".mainCamera()",
        ".collectPerFrameGizmos()" to ".collectPerFrameRenderThreadGizmos()",
        "EntityType.VILLAGER" to "net.minecraft.world.entity.EntityTypes.VILLAGER"
    )
    else -> throw GradleException("No source-transform contract for Minecraft $minecraftVersion")
}
extra["mc26SharedSourceTransforms"] = sharedSourceTransforms
extra["mc26VersionSourceTransforms"] = versionSourceTransforms
extra["mc26SourceTransforms"] = sharedSourceTransforms + versionSourceTransforms
extra["mc26SourceTransformRevision"] = 8

subprojects {
    group = repositoryProperties.getProperty("maven_group")
    version = "${repositoryProperties.getProperty("mod_version")}+mc$minecraftVersion"
}

# Pathmind

[![Minecraft](https://img.shields.io/badge/Minecraft-Multi--Version-00AA00?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.17.2-CC6E3E?style=for-the-badge&logo=modrinth)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21+-FF6B6B?style=for-the-badge&logo=openjdk)](https://openjdk.java.net)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-lightgrey?style=for-the-badge)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge)](https://github.com/soymods/pathmind)

A Minecraft Fabric mod that introduces a visual node editor system for creating complex workflows and automation sequences through an intuitive graphical interface.

## Quick Start

### Prerequisites
- **Minecraft**: Any supported version (match the `+mc<version>` suffix on the jar you download)
- **Fabric Loader**: 0.17.2 or higher
- **Fabric API**: release that matches your chosen Minecraft version
- **Baritone API**: version that matches your Minecraft/Fabric combo (download separately)
- **Java**: 21 or higher

### Installation

1. **Install Fabric Loader**
   - Download and install Fabric Loader for your chosen Minecraft version
   - [Download from FabricMC](https://fabricmc.net/use/installer/)

2. **Install Fabric API**
   - Download the Fabric API build that matches your chosen Minecraft version
   - [Download from Modrinth](https://modrinth.com/mod/fabric-api)

3. **Install Baritone API**
   - Download the Baritone API build corresponding to your Minecraft/Fabric version
   - Place the `baritone-api-fabric-*.jar` in your `mods` folder
   - [Download from GitHub](https://github.com/cabaletta/baritone/releases/latest)

4. **Install Pathmind**
   - Download the Pathmind jar whose filename ends with your Minecraft version (every release ships with `+mc<version>` in the filename)
   - Place it in your `mods` folder

5. **Launch and Enjoy!**
   - Start Minecraft with Fabric Loader
   - Use your configured keybind to open the visual editor

## Compatibility

- Release jars are suffixed with `+mc<version>` so you can keep multiple Minecraft targets side-by-side (e.g., `pathmind-1.0.5+mc1.21.11.jar`).
- The default Gradle build targets the version listed in `gradle.properties`, but passing `-Pmc_version=<version>` (or using the auto-generated `buildMc<version>` tasks) compiles the identical codebase against any entry in `supportedMinecraftVersions`.
- Use the `buildAllTargets` task to batch-build jars for every configured version.

## Development

### Building from Source

1. **Clone the Repository**
   ```bash
   git clone https://github.com/soymods/pathmind.git
   cd pathmind
   ```

2. **Generate Sources**
   ```bash
   ./gradlew genSources
   ```

3. **Import to IDE**
   - Import as a Gradle project
   - Wait for dependencies to resolve

4. **Build the Mod**
   ```bash
   ./gradlew build
   ```
   Output will be in `build/libs/`

5. **Run in Development**
   ```bash
   ./gradlew runClient
   ```

### Building for Specific Minecraft Versions

- `./gradlew build` creates a jar for the default target (set in `gradle.properties`), but the source code is the same across all supported versions.
- To build for another version, override the property:  
  `./gradlew build -Pmc_version=<minecraftVersion>`
- Convenience tasks are available:
  - `./gradlew buildMc1_21_11` (build only for `1.21.11`, as an example)
  - `./gradlew buildAllTargets` (build every configured version sequentially)
- Each jar is versioned as `pathmind-<modVersion>+mc<gameVersion>.jar` so you can publish multiple targets side by side.

## Version Information

| Component | Version |
|-----------|---------|
| **Mod Version** | 1.0.5 |
| **Minecraft Version** | Matches the `+mc<version>` suffix on each jar |
| **Yarn Mappings** | Automatically selected per target version |
| **Fabric Loader** | 0.17.2 |
| **Fabric API** | Automatically selected per target version |
| **Baritone API** | 1.15.0 (external dependency) |

### Development Guidelines
- Follow Java coding conventions
- Add comments for complex logic
- Test your changes thoroughly
- Update documentation as needed

## License

This project is distributed under the custom **Pathmind License (All Rights Reserved)** as described in the [`LICENSE`](LICENSE)
file. In summary:

- Redistribution, modification, or re-uploading of the mod is **not permitted** without explicit written permission from the
  author.
- You **may create and monetize videos** featuring the mod.
- Inclusion in modpacks is allowed only if monetization is limited to CurseForge or Modrinth (including sponsored links or
  banners) unless prior written permission is granted. Modpacks distributed elsewhere must clearly credit the Pathmind project,
  must not be easily confused with Pathmind or the author's other projects, and must end any additional monetization upon
  request.
- The mod is provided “as is” without warranty—use at your own risk.

## Bug Reports & Feature Requests

Found a bug or have an idea? We'd love to hear from you!

- **Bug Reports**: [Open an Issue](https://github.com/soymods/pathmind/issues/new?template=bug_report.md)
- **Feature Requests**: [Open an Issue](https://github.com/soymods/pathmind/issues/new?template=feature_request.md)
- **General Discussion**: [Join our Discord](https://discord.gg/zWT2zxQm)

## Acknowledgments

- **FabricMC Team** for the modding framework
- **Baritone Team** for the pathfinding API
- **Blender Foundation** & **Scratch Foundation** for inspiring the node-based interface design

## Support

Need help? Here are some resources:

- **Documentation**: Check this README and in-game tooltips
- **Issues**: [GitHub Issues](https://github.com/soymods/pathmind/issues)
- **Discord**: [Join our Community](https://discord.gg/zWT2zxQm)

---

<div align="center">

[![GitHub](https://img.shields.io/badge/GitHub-Repository-black?style=for-the-badge&logo=github)](https://github.com/soymods/pathmind)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00D5AA?style=for-the-badge&logo=modrinth)](https://modrinth.com/mod/pathmind)

</div>

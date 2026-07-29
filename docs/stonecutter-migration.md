# Stonecutter migration

Pathmind is migrating its compatibility-family source copies to a canonical,
Stonecutter-managed source tree. Stonecutter is pinned to `0.9.7`.

The production build continues to use `settings.gradle.kts` while the migration
is incomplete. The alternate controller can be inspected with:

```shell
./gradlew -p common projects
```

The initial proof targets are Minecraft 1.21.11 and 26.1. They share the
canonical `PathmindSettingsPopupController` under `common/src/stonecutter/java`.
Older source families retain their existing overrides until their differences
are represented explicitly and verified.

Stonecutter conditionals should be limited to small API or syntax differences.
Behaviorally distinct implementations belong behind compatibility interfaces,
not inside large conditional blocks.

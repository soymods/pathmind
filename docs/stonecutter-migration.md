# Stonecutter migration

Pathmind is migrating its compatibility-family source copies to a canonical,
Stonecutter-managed source tree. Stonecutter is pinned to `0.9.7`.

The production build continues to use `settings.gradle.kts` while the migration
is incomplete. The alternate controller can be inspected with:

```shell
./gradlew -p common projects
```

The initial proof targets were Minecraft 1.21.11 and 26.1. Shared settings,
marketplace, and visual-editor UI classes now live under
`common/src/stonecutter/java`. The marketplace screen, popup controller, preview
renderer, preview loader, and settings popup controller are generated for the
pre-1.21.11 families during the production build; only those selected generated
classes are added to the main source set. The visual editor's single input-event
API delta is isolated behind `CharacterEventModifiers` implementations in the
relevant compatibility families.

Stonecutter conditionals should be limited to small API or syntax differences.
Behaviorally distinct implementations belong behind compatibility interfaces,
not inside large conditional blocks.

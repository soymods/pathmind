# Pathmind Marketplace UI Architecture

This document describes how marketplace UI code is split up. The goal is to make new marketplace work easy to place without growing the main screens again.

## High-Level Rule

Keep service calls, workflows, request construction, media loading, and UI state in separate owners:

1. raw marketplace or auth service call: `PathmindMarketplaceAsyncController`
2. repeated multi-step marketplace workflow: `PathmindMarketplaceFlowController`
3. request construction or pure marketplace helper: `PathmindMarketplaceActions`
4. avatar or preview graph loading: `PathmindMarketplaceAvatarLoader` or `PathmindMarketplacePreviewLoader`
5. popup state, status text, rendering, mouse handling, and navigation: screen/controller classes

Screens should read like UI orchestration. They can decide what the user sees, but they should not directly chain marketplace service calls when a reusable workflow exists.

## Async Controller

`PathmindMarketplaceAsyncController` is the thin async boundary over `MarketplaceAuthManager` and `MarketplaceService`.

Use it when a screen or helper needs one raw operation:

- refresh auth session
- start Discord sign-in
- sign out
- fetch marketplace listings
- fetch liked preset ids
- fetch moderator status
- fetch one preset by id
- download a preset file
- increment download count
- publish or update preset metadata
- like or delete a preset

The controller guarantees callbacks return on the Minecraft client thread. It should stay boring. Do not put popup state, navigation decisions, status messages, or preset-manager side effects here.

## Flow Controller

`PathmindMarketplaceFlowController` owns repeated marketplace workflows that are more than one async step.

Current workflows:

- `resolveLinkedPreset`: validates the session, fetches a linked marketplace preset, and returns `LinkedPresetResult`.
- `submitPublish`: validates the session, checks publish rate limits for new publishes, submits publish/update, records successful publish rate-limit usage, and returns `PublishResult`.
- `submitPresetUpdate`: named wrapper for updating an existing marketplace preset from a local preset file.

Flow methods return structured result objects. Screens translate those results into UI behavior.

For example, the flow can return `SESSION_EXPIRED`, `RATE_LIMITED`, `FOUND`, `NOT_FOUND`, or `COMPLETED`; the screen decides which popup status message, color, navigation, refresh, or selection change happens next.

Do not put rendering, popup animation, text-field mutation, list scrolling, or screen switching directly into flow methods.

## Marketplace Actions

`PathmindMarketplaceActions` is for request construction and pure marketplace helpers.

Good fits:

- build `MarketplaceService.PublishRequest`
- build update-from-local requests
- normalize or parse tags
- decide whether the current user owns or can manage a preset
- extract a readable error message from a throwable
- transform preset lists without touching UI state

This class should not start async work or mutate screen fields.

## Media Loaders

`PathmindMarketplaceAvatarLoader` and `PathmindMarketplacePreviewLoader` isolate media work from the marketplace screen.

Use these for:

- avatar HTTP loading and texture registration
- preview graph fetching
- preview thumbnail/graph preparation
- avoiding duplicate in-flight loads

Screens should request media and render the resulting cached state. They should not own HTTP/download mechanics.

## Screen Ownership

Screens and UI controllers still own user-facing behavior:

- popup visibility and animation
- status text and colors
- form validation messages
- selected preset, filters, scroll positions, hover state, and busy flags
- navigation to another screen
- list upserts, preview invalidation, and visible refresh decisions

If a behavior is only about what the user sees after a result, keep it in the screen. If it is a reusable marketplace operation that multiple UI paths can share, move it to the flow controller.

## Adding A Marketplace Feature

Use this decision path:

1. Is it a single service/auth request that needs a client-thread callback?
   Add or use a method in `PathmindMarketplaceAsyncController`.

2. Is it a repeated multi-step workflow?
   Add a result-based method in `PathmindMarketplaceFlowController`.

3. Does it build request data or answer a pure marketplace question?
   Put it in `PathmindMarketplaceActions`.

4. Does it load or cache avatar/preview media?
   Put it in the appropriate media loader.

5. Is it popup state, status messaging, rendering, navigation, or mouse/key behavior?
   Keep it in the screen or an extracted UI controller.

Prefer structured result enums over boolean flags for workflows. They make call sites easier to audit and give contributors an obvious place to add new outcomes later.

## Compatibility Copies

Marketplace screen helpers currently exist under each compat source set:

- `common/src/compat/...`
- `fabric/src/compat/...`

The root `src` tree is inactive and must not receive compatibility copies. When changing shared helper classes such as `PathmindMarketplaceAsyncController`, `PathmindMarketplaceFlowController`, `PathmindMarketplaceActions`, or the media loaders, keep the active common/Fabric copies synchronized unless a documented API or loader difference requires a fork.

The main marketplace/editor files already contain documented product-level drift between common and Fabric. Do not blindly overwrite one with the other. See [`minecraft-compatibility-baseline.md`](minecraft-compatibility-baseline.md) for the inventory and [`minecraft-multiversion-roadmap.md`](minecraft-multiversion-roadmap.md) for the plan to replace these copies with compatibility contracts.

Useful checks:

- `./gradlew compileJava -q`
- `git diff --check`
- `shasum` across copied helper files when the helper should be identical in every source set

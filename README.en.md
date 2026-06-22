# Kraft UI DSL

Kraft UI DSL is a Kotlin toolkit for describing, validating, optimizing, previewing, and generating user interfaces for Minecraft mods and other JVM hosts.

The main path is compile-time oriented:

```text
Kotlin UI description -> ScreenProgram -> diagnostics -> optimization -> preview -> generated target code/resources
```

The library is still evolving. Its public API, compiler passes, generated output, and styling features may change while the architecture settles.

## Goals

- Keep one source of truth for a screen instead of duplicating layout between game code, previews, and generated code.
- Catch layout, text, unsupported primitive, and target compatibility issues before opening Minecraft.
- Generate target-specific rendering code and resources instead of shipping a heavy UI runtime when the host only needs fixed drawing instructions.
- Make preview output close enough to the in-game target that visual problems are found during the build pipeline.
- Keep actions typed and explicit, so UI descriptions do not depend on stringly-typed commands or opaque captured lambdas.

## Architecture

- `foundation` contains UI elements, values, colors, modifiers, actions, and basic drawing concepts.
- `program` compiles a UI tree into `ScreenProgram`, analyzes it, optimizes it, compares targets, and generates target code.
- `preview` renders programs into images for development and build reports.
- `style`-level primitives are represented by data, including procedural textures such as checkerboard fills and brass frames.
- Target modules decide how a checked and optimized program becomes host code, for example Minecraft `GuiGraphics` calls.

The runtime executor is kept as a reference path for validation, preview, and target comparison. It is not the preferred final delivery mechanism for serious Minecraft screens.

## Usage Model

1. Describe the interface in Kotlin using the DSL and typed state/action models.
2. Compile it into `ScreenProgram`.
3. Run diagnostics for text, layout, hit regions, unsupported target features, and invalid sizes.
4. Optimize the program with explicit compiler options.
5. Render previews and compare generated targets against the reference path.
6. Generate target code and generated resources for the host project.
7. Handle emitted typed actions in the host code.

UI code should describe what can happen. Domain changes, game state mutation, networking, and server authority stay outside the UI description.

## Current API Status

The exact DSL surface is still changing. Use the current tests and consuming projects as executable examples until the public API is declared stable.

The intended direction is stable even while the syntax changes: UI descriptions should use typed state, typed actions, explicit modifiers, explicit compiler options, and generated targets that do not need to walk the original UI tree at runtime.

## Optimization

Optimization is explicit and reportable. The compiler can fold constants, remove dead branches, precompute hit regions, cache text layout, group visibility blocks, and bake proven-static drawing into textures.

Every optimization should be explainable in the report:

```text
StaticTextureBaking:
  applied: /root/window, 58 ops -> 1 texture, 232x140
  skipped: /root/mixture/list, contains dynamic text
```

Optimization must not change behavior. Targets are compared through snapshots and input events to catch drift between the reference renderer, preview output, and generated code.

## Styling

Styles are data, not hand-written rendering code. Current primitives include:

- solid colors and surfaces;
- layered textures;
- checkerboard fills;
- nine-slice and segmented texture regions;
- procedural brass-like frames;
- static texture baking for complex proven-static regions.

The intended direction is a broad style system that can be shared across mods while still compiling to small target-specific output.

## Consuming From Source

The library is intended to be consumed from source through a Git submodule or a Gradle composite build:

```bash
git submodule add https://github.com/CertifiedBadIdeas/ui-dsl vendor/ui-dsl
```

```kotlin
includeBuild("vendor/ui-dsl")
```

Then depend on the module from the consuming project:

```kotlin
dependencies {
    implementation("ru.lazyhat:kraft-ui-dsl")
}
```

For generated Minecraft screens, the final mod should depend on generated code and resources, not on a large retained UI runtime unless that runtime is deliberately needed.

## Verification

```bash
./gradlew test
```

Consuming projects should also run their preview generation and target comparison tasks as part of normal checks.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

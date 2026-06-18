# Kraft UI DSL

Kraft UI DSL is a small Kotlin/JVM retained-mode UI DSL extracted from
Compukter Kraft. It provides a pure UI tree, modifiers, layout/program
compilation, and a runtime executor that renders through host-provided backend
interfaces.

The module is intended to be consumed from source through a Git submodule or a
Gradle composite build:

```kotlin
includeBuild("vendor/ui-dsl")
```

```kotlin
dependencies {
    implementation("ru.lazyhat:kraft-ui-dsl")
}
```

## Verification

```bash
./gradlew test
```

## License

GPL-3.0-or-later. See `LICENSE`.

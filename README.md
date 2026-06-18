# Kraft UI DSL

Kraft UI DSL is a small Kotlin/JVM retained-mode UI DSL extracted from
Compukter Kraft. It provides a pure UI tree, modifiers, layout/program
compilation, and a runtime executor that renders through host-provided backend
interfaces.

## API shape

- `ru.lazyhat.kraftui.foundation` contains the retained UI tree, the `ui { ... }`
  builder, `Color`, `Value`, dynamic `value { ... }` expressions, and basic
  canvas primitives.
- `ru.lazyhat.kraftui.foundation.modifier` contains layout, drawing, input, and
  focus modifiers such as `size`, `background`, `padding`, `clickable`,
  `focusable`, `hoverable`, and `tooltip`.
- `ru.lazyhat.kraftui.program` compiles a `UiElement` tree into a flat
  `ScreenProgram` and executes it against a host-provided `RenderBackend`.
- `ru.lazyhat.kraftui.editor` contains the small host-facing contracts needed by
  the code editor element.

The library does not own a window, OpenGL context, Minecraft screen, or event
loop. Hosts compile UI trees, pass input events to `ScreenRuntimeExecutor`, and
render through their own `RenderBackend` implementation.

## Consuming from source

The module is intended to be consumed from source through a Git submodule or a
Gradle composite build. Add it to the consuming repository:

```bash
git submodule add https://github.com/CertifiedBadIdeas/ui-dsl vendor/ui-dsl
```

Then include it as a composite build in `settings.gradle.kts`:

```kotlin
includeBuild("vendor/ui-dsl")
```

Depend on the module from the consumer project:

```kotlin
dependencies {
    implementation("ru.lazyhat:kraft-ui-dsl")
}
```

## Minimal usage

```kotlin
import ru.lazyhat.kraftui.editor.EditorViewModel
import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.ui
import ru.lazyhat.kraftui.foundation.value
import ru.lazyhat.kraftui.program.RenderBackend
import ru.lazyhat.kraftui.program.ScreenProgramCompiler
import ru.lazyhat.kraftui.program.ScreenRuntimeExecutor

val program =
    ScreenProgramCompiler().compile(
        ui {
            box(
                modifier = Modifier.size(64, 24).background(Color.rgb(12, 24, 48)),
            ) {
                text(text = value { "Hello DSL" }, color = Color.White)
            }
        },
    )

val backend =
    object : RenderBackend {
        override fun fillRect(x: Int, y: Int, width: Int, height: Int, color: Color) {
            println("rect $x $y $width $height ${color.value}")
        }

        override fun drawText(x: Int, y: Int, text: String, color: Color) {
            println("text $x $y $text ${color.value}")
        }

        override fun drawTerminalSurface(x: Int, y: Int, snapshot: Any) = Unit

        override fun pushClip(x: Int, y: Int, width: Int, height: Int) = Unit

        override fun popClip() = Unit

        override fun drawCodeEditor(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            viewModel: EditorViewModel,
            fontWidth: Int,
            fontHeight: Int,
        ) = Unit

        override fun measureText(text: String): Int = text.length * 6
    }

ScreenRuntimeExecutor(program).render(backend)
```

## Verification

```bash
./gradlew test
```

## License

GPL-3.0-or-later. See `LICENSE`.

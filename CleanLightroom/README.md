# Lightroom Pro+ (CleanLR)

A clean-room modern Android photo editing application powered by OpenGL ES 3.0 and Jetpack Compose.

## 🚀 Key Features

- **High-Performance GLSL Pipeline**: Real-time rendering with custom fragment shaders (`blit.vert`, `pipeline.frag`).
- **Full Tone & Light Controls**:
  - Exposure, Contrast, Highlights, Shadows, Whites, Blacks
  - Temp, Tint, Vibrance, Saturation, Clarity, Dehaze, Vignette
- **Color Grading & HSL Mixer**:
  - 3-Way Color Grading Wheels (Shadows, Midtones, Highlights)
  - 8-Channel HSL Mixer (Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta)
- **Tone Curves**:
  - Parametric tone curve widget with smooth cubic spline interpolation
- **Interactive Histogram**:
  - Live RGB and Luminance histogram analysis computed via GPU/Render pipeline
- **Non-Destructive Editing Engine**:
  - Full state snapshotting (`EditRecipe`)
  - Multi-level Undo / Redo history management
- **Background Headless Export**:
  - Offscreen rendering and high-resolution JPEG export using WorkManager
- **Modern Jetpack Compose UI**:
  - Sleek dark theme, ramped sliders, touch loupe canvas, and keyboard shortcuts

## 🛠️ Tech Stack

- **Language**: Kotlin 2.0+
- **UI**: Jetpack Compose, Material3
- **Graphics**: OpenGL ES 3.0, GLSL shaders
- **Architecture**: MVVM with Kotlin Coroutines & Flow
- **Build System**: Gradle with Kotlin DSL

## 📦 Project Structure

```text
CleanLightroom/
├── app/
│   ├── src/main/
│   │   ├── assets/shaders/      # GLSL Vertex & Fragment shaders
│   │   ├── kotlin/com/cleanlr/
│   │   │   ├── engine/          # OpenGL Pipeline, Color Science & Histogram
│   │   │   ├── export/          # Headless renderer & WorkManager exporter
│   │   │   ├── history/         # Undo/Redo & History manager
│   │   │   ├── model/           # EditRecipe data schema
│   │   │   └── ui/              # Compose UI, wheels, sliders & curves
│   │   └── res/                 # Resources, values, themes
├── build.gradle.kts
└── settings.gradle.kts
```

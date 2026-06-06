# AGENTS.md

## Build & Test

```bash
./gradlew assembleDebug      # build debug APK
./gradlew test                # JVM unit tests
./gradlew connectedAndroidTest # instrumented (needs emulator/device)
adb reverse tcp:8000 tcp:8000 # emulator ↔ host backend
```

- JDK 17+, Gradle 9.3.1, AGP 9.1.1, Kotlin 2.0.0
- Target SDK 36, Min SDK 33
- Backend base URL: `http://10.0.2.2:8000/api/` (live in `di/NetworkModule.kt`)

## Architecture

- **Single-module** (`:app`), package `com.geoagent`
- **Koin DI** (NOT Hilt) — 5 modules in `di/`: `dataStoreModule`, `networkModule`, `databaseModule`, `repositoryModule`, `viewModelModule`
- **MVVM** with Repository pattern — no UseCase layer (docs reference one but it wasn't implemented)
- **Theme**: `GeoAgentTheme` / `AnimatedGeoAgentTheme` in `ui/theme/Theme.kt`, uses `LocalGeoPalette` CompositionLocal for color tokens. Color tokens are static vals in `Color.kt` prefixed `Static*`.
- **Navigation**: Jetpack Navigation Compose, routes defined in `navigation/Routes.kt`, NavHost in `navigation/GeoNavHost.kt`

## Tech stack gotchas

- **SSE** uses `com.launchdarkly:okhttp-eventsource:4.1.1` (NOT OkHttp's built-in SSE). Two SSE clients: `SseClient` (chat) and `SearchSseClient` (search).
- **NanoHTTPD** embedded server is **disabled by default** (`USE_EMBEDDED_SERVER = false` in `GeoAgentApp.kt`).
- **Markdown rendering**: `com.mikepenz:multiplatform-markdown-renderer:0.26.0` — do NOT bump to 0.27.x, it doesn't exist on Maven Central.
- **Network security**: cleartext HTTP allowed only for `localhost` and `10.0.2.2` via `network_security_config.xml`.
- **Room** is declared in `libs.versions.toml` but NOT used in dependencies — not yet integrated.

## Docs priority

- **README.md** is the most up-to-date source of truth for project structure and status.
- **CLAUDE.md** is stale: claims Hilt (should be Koin), says project is "only a template XML-based MainActivity" (it's fully built with Compose).
- **`Android-docs/`** are design specs that have partially drifted from implementation. `API-ENDPOINTS.md` is reliable for DTO shapes; `UI-SPEC.md` describes the DeepSeek target design but the actual theme uses different color token names.

## Style conventions

- No comments unless a business rule or non-obvious constraint needs explaining.
- Compose previews are rarely used — don't add them by default.
- Theme colors are accessed via `GeoAgentTheme.geoPalette` CompositionLocal, not direct static imports.
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GeoAgent Android is a mobile client for an AI geology literature Q&A system, communicating with a FastAPI backend via HTTP + SSE. Built with Kotlin + Jetpack Compose, single-module MVVM + Repository pattern, Koin DI.

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Build + install to emulator/device
./gradlew test                   # JVM unit tests (no device needed)
./gradlew connectedAndroidTest   # Instrumented tests (needs emulator/device)
adb reverse tcp:8000 tcp:8000    # Emulator ↔ host backend
```

- JDK 17+, Gradle 9.3.1, AGP 9.1.1, Kotlin 2.0.0
- Target SDK 36, Min SDK 33
- Backend base URL: `http://10.0.2.2:8000/api/` (defined in `di/NetworkModule.kt`)

## Architecture

**Single-module** (`:app`), package `com.geoagent`.

**Koin DI** — NOT Hilt. Five modules loaded in `GeoAgentApp.onCreate()`:
- `networkModule` — OkHttp (with AuthInterceptor + TokenAuthenticator + logging + cache), Retrofit (Gson), GeoAgentApi, SseClient, SearchSseClient
- `dataStoreModule` — TokenDataStore, UserPrefsDataStore, AvatarLocalStore (DataStore Preferences, singletons)
- `databaseModule` — Empty placeholder. Room is **not used** due to KSP/Kotlin 2.0/AGP 9.x compat issues.
- `repositoryModule` — Binds Auth/Chat/Document/Search repository interfaces to implementations
- `viewModelModule` — Five ViewModels: Auth, Chat, ChatList, Document, Settings

**Navigation** — Jetpack Navigation Compose with **dual NavHost**:
- Outer NavHost (`GeoNavHost.kt`): Splash → Login → Register → ForgotPassword → Main
- Inner NavHost (`MainScreen.kt`): Chat, Documents, Settings inside a ModalNavigationDrawer
- Start destination: `chat/detail/0` (new/blank chat), not chat list
- Routes defined in `navigation/Routes.kt`

**SSE** — Two OkHttp-based SSE clients (not Retrofit):
- `SseClient` — chat streaming (`POST chat/stream`), emits `Flow<ChatEvent>`
- `SearchSseClient` — search streaming (`POST search/deep`), emits `Flow<SearchEvent>`
- Uses `com.launchdarkly:okhttp-eventsource:4.1.1`, not OkHttp's built-in SSE

**Agent system** (`agent/`) — Local keyword-based command routing:
- `IntentRouter` — Multi-tier scoring: L1 exact prefix match (`/convert`), L2 keyword hit counting, fallback null
- `UnitConversionAgent` — Local geological unit conversion (length, pressure, temperature, angle) without network calls
- ChatViewModel routes messages through IntentRouter first; if a command matches, it dispatches locally instead of calling the SSE API

**Embedded server** — NanoHTTPD mock server at `127.0.0.1:8080`, **disabled by default** (`USE_EMBEDDED_SERVER = false` in `GeoAgentApp.kt`). The real FastAPI backend at `10.0.2.2:8000` is the target.

## Theme System

- `AnimatedGeoAgentTheme` in `ui/theme/Theme.kt` — crossfade transition between light/dark
- Colors accessed via `GeoAgentTheme.geoPalette` CompositionLocal, not direct static imports
- `AppThemeMode` enum: LIGHT, DARK, SYSTEM
- DeepSeek-inspired design: brand blue `#3964fe`, soft blue `#edf3fe`, pill-shaped components

## Tech Stack Gotchas

- **Markdown renderer**: `com.mikepenz:multiplatform-markdown-renderer:0.26.0` — do NOT bump to 0.27.x, it doesn't exist on Maven Central
- **Network security**: Cleartext HTTP allowed only for `localhost` and `10.0.2.2` via `network_security_config.xml`
- **Room** is declared in `libs.versions.toml` but NOT used in dependencies — not integrated
- **No KSP** — Room and other annotation processors that need KSP are blocked by Kotlin 2.0 + AGP 9.x compat
- **UseCase layer** — Referenced in docs but not implemented; features go directly through Repository → ViewModel

## Docs Priority

- **README.md** — Most up-to-date source of truth for project structure and features
- **`Android-docs/`** — Design specs that have partially drifted from implementation. `API-ENDPOINTS.md` is reliable for DTO shapes; `UI-SPEC.md` describes the target design but actual theme token names differ.
- **AGENTS.md** — Contains additional architecture notes and style conventions

## Style Conventions

- No comments unless a business rule or non-obvious constraint needs explaining
- Compose previews are rarely used — don't add them by default
- Theme colors accessed via CompositionLocal, not direct static imports
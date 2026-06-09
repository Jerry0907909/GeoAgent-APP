# Repository Guidelines

## Project Structure & Module Organization
This is a single-module Android project (`:app`) for the `com.geoagent` package. Main Kotlin code lives in `app/src/main/java/com/geoagent/`: `ui/` contains Activities, chat, auth, document, settings, theme, and navigation code; `data/` contains API, local storage, and repository implementations; `domain/` defines models and repository contracts; `agent/` contains local agent routing and V2 runtime code; `di/` contains Hilt modules. Resources are under `app/src/main/res/`: layouts, drawables, menus, values, and network XML. JVM tests live in `app/src/test/java`; instrumentation tests live in `app/src/androidTest/java`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root:

```bash
./gradlew assembleDebug          # build a debug APK
./gradlew installDebug           # install debug build on a connected device/emulator
./gradlew test                   # run JVM unit tests
./gradlew connectedAndroidTest   # run instrumentation tests; requires device/emulator
adb reverse tcp:8000 tcp:8000    # let emulator reach host backend on port 8000
```

Requires JDK 17+, AGP 9.1.1, Kotlin 2.0.0, minSdk 33, and targetSdk 36.

## Coding Style & Naming Conventions
Write Kotlin with 4-space indentation and follow the existing Android/Kotlin style. Keep changes surgical: do not add abstractions, previews, or refactors unless required. Name Activities as `*Activity`, ViewModels as `*ViewModel`, repositories as `*Repository`/`*RepositoryImpl`, DTOs under `data/api/dto`, and tests as `*Test`. Prefer existing MVVM + Repository patterns and Hilt modules. Avoid comments unless documenting a business rule or non-obvious constraint.

## Testing Guidelines
Use JUnit for local tests and AndroidX/Espresso for instrumentation tests. Add focused tests for routing, parsing, repository behavior, and other touched logic. Run `./gradlew test` before claiming completion; run `connectedAndroidTest` when UI/device behavior is affected.

## Commit & Pull Request Guidelines
Recent history mixes Chinese summaries and conventional prefixes, for example `feat: ...` and `已经实现UI的更新以及动画特效的升级`. Use concise imperative messages and include a scope when helpful. PRs should describe the change, list verification commands, note backend/API assumptions, link related issues, and include screenshots or screen recordings for UI changes.

## Security & Configuration Tips
Do not commit secrets. Local API keys and mail settings are read from `.env` into `BuildConfig`. The default backend URL is `http://10.0.2.2:8000/api/`; cleartext HTTP is limited by `network_security_config.xml`.

## Agent-Specific Instructions
Before editing, state assumptions and success checks. Modify only files directly required by the request, preserve unrelated worktree changes, and never declare completion without verification or a clear explanation of any blocked check.

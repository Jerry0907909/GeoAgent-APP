# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android project: `settings.gradle.kts` includes only `:app`. Kotlin source lives under `app/src/main/java/com/geoagent/`, organized by layer: `ui/` for Activities, adapters, and view models; `data/` for API, local storage, and repository implementations; `domain/` for models and repository contracts; `di/` for Hilt modules; and `agent/` plus `agent/v2/` for local agent runtime logic. XML layouts, drawables, menus, themes, and configuration files live in `app/src/main/res/`. JVM tests are under `app/src/test/java/`; device or emulator tests are under `app/src/androidTest/java/`. Project notes and API/UI specs are in `Android-docs/`.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper from the repository root:

```bash
./gradlew assembleDebug        # Build the debug APK
./gradlew installDebug         # Install debug build on a connected device/emulator
./gradlew test                 # Run JVM unit tests
./gradlew testDebugUnitTest    # Run debug-variant unit tests
./gradlew connectedAndroidTest # Run instrumentation tests; requires device/emulator
./gradlew clean                # Remove Gradle build outputs
```

JDK 17+ and an Android SDK compatible with the project settings are required.

## Coding Style & Naming Conventions

Use Kotlin with 4-space indentation and the existing Android Views/XML style. Keep package placement aligned with the current layers; do not add new architectural layers for one-off changes. Name Activities and adapters with Android conventions, for example `ChatActivity`, `DocumentAdapter`, and `SettingsRepository`. Test classes should end with `Test`. Resource names should stay lowercase snake case, such as `activity_chat.xml`, `item_message_user.xml`, and `bg_send_button.xml`.

## Testing Guidelines

Add focused JVM tests in `app/src/test/java/com/geoagent/` for routing, parsing, repository, and runtime behavior. Use instrumentation tests only for Android framework or UI behavior that cannot be covered on the JVM. Before claiming completion for behavioral changes, run the narrow relevant test first, then `./gradlew test` when practical.

## Commit & Pull Request Guidelines

This checkout has no accessible Git history, so use clear imperative commit messages such as `Fix V2 file routing` or `Add document parser tests`. Pull requests should describe the user-visible change, list tests run, call out configuration or `.env` needs, and include screenshots for UI changes.

## Security & Configuration Tips

Keep secrets in `.env`; `app/build.gradle.kts` reads API, SMTP, and model keys from that file into `BuildConfig`. Do not commit real credentials, `local.properties`, build outputs, or generated IDE files. For local backend access from an emulator, use `10.0.2.2` or `adb reverse tcp:8000 tcp:8000`.

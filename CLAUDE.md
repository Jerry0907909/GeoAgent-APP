# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GeoAgent Android is a mobile client for an AI geology literature Q&A system, communicating with a FastAPI backend via HTTP + SSE. The project target is Kotlin + Jetpack Compose with MVVM + Clean Architecture, but is currently in early stages — only a template XML-based MainActivity exists.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests (JVM)
./gradlew test

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Emulator port forwarding (so emulator can reach backend at localhost:8000)
adb reverse tcp:8000 tcp:8000
```

- Gradle 9.3.1, AGP 9.1.1, JDK 17+
- Target SDK 36, Min SDK 33, Java 11 bytecode
- Backend base URL: `http://10.0.2.2:8000/api/` (Android emulator alias for host localhost)

## Architecture (Planned)

The `Android-docs/` directory contains the full technical specification for what needs to be built:

| Doc | Content |
|-----|---------|
| `TECHNICAL-SPEC.md` | Clean Architecture layers, Hilt DI setup, SSE client, auth interceptor, Room/DataStore schemas, dependency versions |
| `UI-SPEC.md` | DeepSeek-style design system (colors, typography, spacing, radii), per-screen Compose layouts, interaction specs |
| `API-ENDPOINTS.md` | All backend REST + SSE endpoints with request/response DTOs |
| `README.md` | Project overview, module structure, external service dependencies (SiliconFlow, Tavily, QQ SMTP, MySQL, Redis, ChromaDB) |

**Target package:** `com.geoagent` (note: current template uses `com.example.geoagent`)

**Key planned dependencies not yet in `libs.versions.toml`:** Compose BOM, Hilt, Navigation Compose, Retrofit, OkHttp EventSource, Room, DataStore, Coil.

## Design System

DeepSeek-inspired theme with brand blue `#3964fe`, soft blue `#edf3fe`, and pill-shaped components. Full color tokens, typography scale, and component specs are in `UI-SPEC.md`.

## Current State

The app currently contains a single `MainActivity` using XML layout (`ConstraintLayout` + "Hello World" `TextView`) with edge-to-edge insets handling. All Compose, navigation, DI, and feature modules described in the docs still need to be implemented.
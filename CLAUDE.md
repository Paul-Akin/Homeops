# CLAUDE.md

This repo is **HomeOps**, an Android household cleaning assistant. The full specification is in `DESIGN.md`. Read it before making changes.

## How to work in this repo

- Build milestone by milestone in the order given in `DESIGN.md` Section 11. Do not start a milestone until the previous one meets its acceptance criteria.
- At the start of each milestone, summarize the plan (files to create or change) before writing code.
- Keep all formulas and tunable constants in the `domain` package (`domain/Tuning.kt` for constants). Domain code must not import Android classes.
- Write unit tests for every domain function, using the hand-check fixtures in `DESIGN.md` Section 10.
- Run `./gradlew test` and `./gradlew lint` before declaring a milestone done.
- Never put API keys or secrets in the Android app. LLM calls go through the proxy in `proxy/`.
- Room schema changes require a migration and an exported schema.

## Stack summary

Kotlin, Jetpack Compose (Material 3), MVVM, Hilt, Room, DataStore, WorkManager, CameraX, Retrofit + kotlinx.serialization, Vico charts. Min SDK 26. Proxy: Cloudflare Worker (TypeScript).

## Owner preferences

- The owner is a mechanical/structural engineer comfortable with Python, newer to Kotlin. Explain non-obvious Kotlin or Android idioms briefly in comments or summaries.
- Units in the UI and data model are U.S. customary (ft², minutes).
- Prefer explicit, testable math over opaque heuristics.

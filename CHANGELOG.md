# Changelog

All notable changes to KMPWorker will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0-beta02] — 2026-06-14

### Added (contributed by [@muhammadsobananjum](https://github.com/muhammadsobananjum) via [#1](https://github.com/neuralheads/kmpworker/pull/1))
- **`TaskGraph` + `TaskGraphExecutor`** — DAG-based parallel task execution with dependency edges
- **`TelemetryCollector`** interface + **`SqlDelightTelemetryCollector`** — persistent execution history (timing, retries, errors)
- **`ExecutionRecord`** — data class for telemetry records; queryable via `getExecutionHistory()`
- **`RateLimiter`** — semaphore-based concurrency cap for task execution
- **`ChainPolicy`** — `KEEP` / `REPLACE` / `ALLOW_DUPLICATE` for chain deduplication
- **`TaskType.Windowed`** — execute within a time window (maps to flex interval on Android, `earliestBeginDate` on iOS)
- **`Constraints.requiresDeviceIdle`** — device idle constraint (Android API 23+)
- **`Constraints.contentUris`** — content URI change triggers for Android tasks
- **`ForegroundConfig`** — foreground service notification config for high-priority Android tasks
- **`FlowWrapper<T>`** + **`TaskStateObserver`** — Swift-friendly callback wrapper for Kotlin Flows
- **`:transfer` module** — background file download/upload via native HTTP (no Ktor):
  - `TransferManager` interface with `download()`, `upload()`, `observeProgress()`
  - `DownloadRequest` — URL, save path, checksum verification, resume support
  - `UploadRequest` — file path, HTTP method, headers
  - `TransferProgress` — bytes transferred, total, percent complete
  - SHA-256 checksum verification (`expect`/`actual` for Android + iOS)
- **`KmpWorkerTestRule`** — JUnit4 test rule for coroutine-safe worker testing
- **`KmpWorkerDsl`** improvements — `taskGraph {}` DSL builder
- **`enqueueBatch()` / `cancelBatch()`** — bulk operations on `KmpWorker`
- **`getExecutionHistory()` / `clearExecutionHistory()`** — telemetry access on `KmpWorker`
- Instrumented test: `KmpTaskWorkerInstrumentedTest`

### Changed
- **`TaskState.Running`** changed from `data object` to `data class` with optional `progress: Float?` and `message: String?` — **breaking change** for consumers pattern-matching on `TaskState.Running`
- Default KMP hierarchy template re-enabled (`applyDefaultHierarchyTemplate=false` removed from root)
- `umbrella/build.gradle.kts` simplified — `iosMain` now uses standard `iosMain.dependencies {}` block

### Fixed
- Maven Central publishing: Android AAR was publishing empty; iOS artifacts returning 404 — resolved by hierarchy template fix
- Data race in `SqlDelightTelemetryCollector.startTimes` — now protected by `Mutex`
- Data race in `TaskGraphExecutor` — parallel nodes now use `async/awaitAll` instead of mutable shared sets

---

## [0.1.0-beta01] — 2026-05-23 🎉 API Freeze


> **Public API is now frozen.** No breaking changes will be made after this release.

### Added
- **`KmpWorkerException`** — structured exception hierarchy replacing raw `RuntimeException`:
  - `TaskAlreadyEnqueuedException` — duplicate enqueue guard
  - `TaskNotFoundException` — unknown task ID
  - `TaskTimeoutException` — task exceeded configured timeout
  - `ChainExecutionException` — step failure in a `TaskChain`
  - `InvalidTaskRequestException` — malformed `TaskRequest`
- **`TaskState.TimedOut(afterMillis)`** — new terminal state for timed-out tasks
- **`KmpWorkerConfig.taskTimeout: Duration?`** — global max execution time per task
- **`TaskRequest.label: String?`** — human-readable display name (future foreground service use)
- **`TaskRequest.displayName`** — convenience property: `label ?: id`
- **`Flow<TaskState>.onTimedOut { }`** — new Flow extension matching `TaskState.TimedOut`
- **Instrumented test job** in CI — real Android emulator (API 34, KVM-accelerated)
- **GitHub Pages docs workflow** — Dokka HTML deployed to `neuralheads.github.io/kmpworker`
- **3 new guide docs**: `task-chaining.md`, `typed-payloads.md`, `testing.md`

### Fixed
- CI branch names corrected from `main` → `master`
- Snapshot publish now requires all 4 test jobs to pass (lint + unit + instrumented + iOS)

### Changed
- `TaskState.isTerminal` now includes `TimedOut` alongside `Success`, `Cancelled`, `Failed`
- `onTerminal { }` doc updated to reflect `TimedOut`


### Added
- Core task API: `KmpWorker`, `TaskRequest`, `TaskType`, `TaskState`
- `RetryPolicy`: None, Linear, Exponential with `RetryEngine`
- `Constraints`: `requiresInternet`, `requiresCharging`, `batteryNotLow`
- Android WorkManager integration via `AndroidKmpWorker` + `AndroidTaskScheduler`
- iOS BGTaskScheduler integration via `IOSKmpWorker`
- Flow-based state monitoring via `TaskMonitor` (replay=1, SharedFlow)
- SQLDelight-backed persistence via `SqlDelightTaskRepository`
- Offline queue engine via `OfflineQueue` with `CoroutineExceptionHandler`
- Testing utilities: `FakeKmpWorker`, `FakeTaskRepository`, `FakeNetworkMonitor`
- `KmpWorkerConfig` — global config with `maxRetries`, `logLevel`
- `KmpWorkerLogger` — pluggable logger interface, silent by default
- `KmpWorkerDsl` — `oneTime {}` and `periodic {}` DSL with `kotlin.time.Duration`
- Jetpack App Startup `KmpWorkerInitializer` for zero-config Android setup
- Thread-safe `TaskRegistry` via `ConcurrentHashMap`
- **Typed Payload API** — `withPayload<T>()` / `decodePayload<T>()` (no raw JSON)
- **Persistent EventStore** — SQLDelight-backed cold-launch terminal state replay
- **Task Chaining** — `enqueueChain()` / `observeChain()` with crash-safe step persistence
- **NSURLSession iOS Download Bridge** — `IOSBackgroundDownloadWorker` survives app kill
- `exponentialRetry()` and `linearRetry()` factory functions
- `FakeNetworkMonitor` in `:testing` module
- Android consumer ProGuard rules
- Apache 2.0 License
- GitHub Actions CI pipeline + Maven Central publish workflow

### Fixed
- `RetryPolicy.None` tasks no longer incorrectly hit the exhausted-retry path when
  `runAttemptCount > 0` (WorkManager default in test mode)
- Robolectric coroutine flow tests now use `UnconfinedTestDispatcher` for reliable
  emission capture without timing races

---

## [0.1.0-alpha03] — 2026-05-23

### Fixed
- **`OfflineQueue.executeNow` is now `suspend`** — previously non-suspend caused non-deterministic
  test behaviour where offline tasks could run outside the test coroutine scope
- **`FakeKmpWorker.reset()` now clears the `SharedFlow` replay cache** — stale emissions no longer
  leaked between test cases
- **`AndroidNetworkMonitor`** — removed unnecessary `ACCESS_WIFI_STATE` usage and tightened
  `NetworkCapabilities` checks to `TRANSPORT_CELLULAR` + `TRANSPORT_WIFI` only
- **ProGuard / R8 consumer rules** added to `:core` and `:android` modules — prevents obfuscation
  of `TaskHandler`, `TaskRequest`, and WorkManager worker classes in minified builds
- **Package names aligned** — all public classes now consistently under
  `io.neuralheads.kmpworker.*`; documentation and import examples corrected
- **Vanniktech publish plugin upgraded `0.30.0 → 0.33.0`** — resolves Sonatype Central Portal
  upload flow and signing API changes

### Changed
- `KmpWorkerConfig` logger defaults remain silent; `logLevel` property documentation clarified

---

## [0.1.0-alpha02] — 2026-05-23

### Added
- Android consumer ProGuard rules for `:core` module
- `FakeNetworkMonitor` in `:testing` module for offline queue unit tests
- `exponentialRetry()` and `linearRetry()` factory extension functions

### Fixed
- `RetryPolicy.None` tasks no longer incorrectly hit the exhausted-retry path when
  `runAttemptCount > 0` (WorkManager default in test mode)
- Robolectric coroutine flow tests now use `UnconfinedTestDispatcher` for reliable
  emission capture without timing races

---

## Version Roadmap

| Version | Status | Highlights |
|---------|--------|-----------|
| v0.1.0-alpha01 | Released | Core API, Android + iOS, EventStore, Chaining, NSURLSession |
| v0.1.0-alpha02 | Released | ProGuard rules, FakeNetworkMonitor, retry factory functions |
| v0.1.0-alpha03 | Released | Bug fixes — suspend OfflineQueue, FakeKmpWorker reset, AndroidNetworkMonitor |
| v0.1.0-beta01  | Released | Public API freeze, instrumented tests on device |
| v0.1.0-beta02  | **Current** | Telemetry, DAG graphs, progress tracking, transfer module, iOS FlowWrapper |
| v0.1.0         | Planned | Stable release, full docs, iOS Swift package |
| v0.2.0         | Planned | Priority queues, advanced scheduling |
| v1.0.0         | Planned | Production-hardened, full iOS parity |


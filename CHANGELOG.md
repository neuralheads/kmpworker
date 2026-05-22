# Changelog

All notable changes to KMPWorker will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0-alpha01] — 2026-05-23

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

## [Unreleased]

*(next items go here)*

---

## Version Roadmap

| Version | Status | Highlights |
|---------|--------|-----------|
| v0.1.0-alpha01 | **Current** | Core API, Android + iOS, EventStore, Chaining, NSURLSession |
| v0.1.0-beta01 | Planned | Public API freeze, instrumented tests on device |
| v0.1.0 | Planned | Stable release, full docs, iOS Swift package |
| v0.2.0 | Planned | Priority queues, task dependencies graph |
| v1.0.0 | Planned | Production-hardened, full iOS parity |

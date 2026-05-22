# Contributing to KMPWorker

Thank you for your interest in contributing! KMPWorker is an open-source Kotlin Multiplatform library and welcomes contributions from the community.

---

## Development Environment

### Requirements

| Tool | Version |
|------|---------|
| JDK | 17+ |
| Android Studio | Hedgehog (2023.1.1)+ |
| Xcode | 15+ (for iOS compilation) |
| Kotlin | 2.1.0 |
| Gradle | 8.7 |

### Clone & Build

```bash
git clone https://github.com/yourname/kmpworker.git
cd kmpworker
./gradlew build
```

### Run All Tests

```bash
# JVM / Android
./gradlew :core:allTests
./gradlew :persistence:allTests
./gradlew :queue:allTests

# iOS (requires macOS + Xcode)
./gradlew :core:iosSimulatorArm64Test
./gradlew :ios:iosSimulatorArm64Test
```

---

## Contribution Guidelines

### Branching

- `main` — stable, always publishable
- `develop` — integration branch
- Feature branches: `feature/your-feature-name`
- Bug fixes: `fix/description`

### Commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(core): add TaskState.Cancelled
fix(android): prevent duplicate WorkManager enqueue
docs(ios): clarify BGTaskScheduler limitations
test(queue): add replay edge case test
```

### Pull Request Process

1. Open an issue first to discuss major changes
2. Fork → branch from `develop`
3. Add tests for any new behaviour
4. Ensure all existing tests pass: `./gradlew allTests`
5. Update `CHANGELOG.md` under `[Unreleased]`
6. Submit PR against `develop`

---

## Project Structure

```
kmpworker/
├── core/        # Public API, models, retry engine, monitoring
├── scheduler/   # Common scheduler interface
├── android/     # WorkManager integration
├── ios/         # BGTaskScheduler integration
├── persistence/ # SQLDelight task storage
├── queue/       # Offline queue engine
├── testing/     # Fake implementations for testing
└── sample/      # Demo app
```

---

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- All public API must have KDoc documentation
- No platform-specific code in `:core` or `:scheduler`
- No reflection or annotation processing (explicit registration only)

---

## Reporting Issues

Please include:
- KMPWorker version
- Platform (Android / iOS)
- Minimal reproduction case
- Expected vs actual behaviour
- Relevant logs (with `KmpWorkerLogger` enabled)

---

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

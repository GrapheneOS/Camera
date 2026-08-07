# Agents Guidelines — Camera

Shared guidelines for all AI coding agents working on GrapheneOS Camera.
`CLAUDE.md` and `GEMINI.md` are symlinks to this file — edit this one.

---

## Project Overview

Android camera app built on CameraX. Single `:app` module of app Kotlin plus vendored AndroidX
Java under `androidxc/` (do not modify or restyle). The app is migrating incrementally from
Views/XML to Compose.

**This repository exists to raise PRs against upstream GrapheneOS Camera.** Every change must stand
on its own merits to a reviewer with no context beyond the diff. No big-bang rewrites.

### Key Coordinates

| Key         | Value                                         |
|-------------|-----------------------------------------------|
| Package     | `app.grapheneos.camera`                       |
| minSdk      | 29 — the one that constrains API choices      |
| targetSdk   | tracks compileSdk                             |
| Build types | `debug` (`.dev`), `release`, `play` (`.play`) |
| Toolchain   | JDK 17 (CI runs Gradle itself on a newer JDK) |

Versions live in `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`. Read
them there — they are the source of truth, and a number copied into this document is a number that
will be wrong.

### Target Layout

The current tree is flat Views-era code — read it, don't memorize it from here. **New code lands in
this shape**; anything extracted or rewritten moves toward it, never away:

Each layer splits per feature, and each feature splits by role:

```
app/src/main/java/app/grapheneos/camera/
  data/
    settings/
      model/            CameraSettings, per-mode setting values
      repository/       SettingsRepository (entry-mode-scoped, never application-scoped)
      store/            prefs-backed stores, EphemeralSharedPrefs namespace
    camera/
      model/            CameraCapabilities, lens/extension descriptors
      repository/       CameraProviderSource
      store/            ExtensionAvailabilityStore
    media/
      model/            CapturedItem and friends
      repository/       CapturedItemStore
      store/            MediaStoreDataSource, SafDataSource
    location/
      repository/       LocationRepository
  domain/
    camera/usecase/     bind, rebind, lens/flash/zoom/focus
    capture/usecase/    capture image, start/stop/pause recording
    qr/usecase/         barcode scanning
    gallery/usecase/    share, edit, delete (the guarded variants from CapturedItems.kt)
  ui/
    core/               Theme.kt, Preview.kt
    common/components/  composables shared across screens
    viewfinder/
      screen/           ViewfinderScreen, ViewfinderViewModel, ViewfinderEffectHandler
        model/          ViewfinderUiState, ViewfinderAction, ViewfinderScreenEffect, NavEvent
        mapper/         domain → UiState mappers
      components/       CaptureButton, ModeTabStrip, ZoomSlider, GridOverlay, FocusRing, ...
    gallery/            same screen/{model,mapper} + components/ shape
    videoplayer/        "
    settings/           "  (viewfinder settings sheet)
    moresettings/       "
  di/
    core/               app-wide modules, qualifiers
    <feature>/          one module package per feature (camera, capture, gallery, ...)
```

Roles: `model/` = plain data types, `repository/` = the feature's public data API,
`store/` = persistence/platform sources behind it, `mapper/` = pure transformation functions,
`usecase/` = one verb per class. A package appears when its first class does — don't pre-create
empty directories.

`app/src/main/java/androidxc/` is vendored AndroidX Java — do not modify.

### Activity entry points

```
MainActivity ← SecureMainActivity ← QrTile
             ← VideoOnlyActivity
             ← CaptureActivity ← SecureCaptureActivity
                                ← VideoCaptureActivity
```

Plus `InAppGallery`, `VideoPlayer`, `MoreSettings ← MoreSettingsSecure`, and the `CameraLauncher`
activity-alias. The inheritance chain is today's configuration mechanism — it is how each entry
point differs. Treat any change to it as a change to the manifest contract.

---

## Build & Run

```sh
./gradlew :app:compileDebugKotlin      # fast check — run this after writing Kotlin
./gradlew :app:assembleDebug           # debug APK
./gradlew build --no-daemon            # what CI runs
./gradlew :app:dependencies            # after touching build files — check what CameraX resolved to
```

**The debug build installs as `app.grapheneos.camera.dev`.** The plain `app.grapheneos.camera`
package is the stock system app that ships with the OS. Install with `./gradlew installDebug` and
verify against `.dev` — verifying against the stock package makes a working change look dead.

---

## Testing

| Suite            | Location               | Command                                    | Device |
|------------------|------------------------|--------------------------------------------|:------:|
| **Instrumented** | `app/src/androidTest/` | `./gradlew :app:connectedDebugAndroidTest` |  yes   |
| **Unit**         | `app/src/test/`        | `./gradlew :app:testDebugUnitTest`         |   no   |

The instrumented tests are Espresso/UiAutomator against the View hierarchy.
**Each one encodes a real incident** — video double-start crashes, SAF grant `SecurityException`s,
extension bind `UnsupportedOperationException`s, gallery NPEs.

- **Never delete a regression test without its replacement in the same commit.**
- Run a single class with:
  ```sh
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=app.grapheneos.camera.VideoCapturerRegressionTest
  ```
- **Known flake:**
  `VideoCapturerRegressionTest.leavingACaptureSessionWhileRecording_defersThePreview`
  fails only in full-suite runs, and does so on unmodified `main` too. Re-run it alone before
  attributing the failure to your diff.

---

## Architecture

### Legacy

The pre-migration code has no DI, no ViewModels, no coroutines in the camera path (raw
`thread {}`, `Executors`, `Handler`).
`CamConfig` holds `private val mActivity: MainActivity` and some of its properties read the View
tree directly (e.g. `requireLocation`'s getter returns
`mActivity.settingsDialog.locToggle.isChecked`). This coupling is the thing the migration exists to
undo — do not add to it.

### Target

Compose + Hilt + per-screen unidirectional data flow + `data`/`domain`/`ui` layering; Material3
Expressive styling.
Strategy is foundation-first: extract a testable domain layer underneath the existing Views
(keeping the instrumented regression suite green *and unmodified*), then replace the UI one screen
at a time — **leaf screens first, viewfinder last**.

### Architectural rules (new code)

Every migrated feature follows the same shape — when in doubt, open an already-migrated feature in
this repo and copy it.

**Layering.** Dependency direction is `ui → domain → data`; `data` and `domain` never import `ui`,
and nothing below `ui` touches Compose or an Activity.

**Everything injectable is an interface + `Impl` pair.** Callers depend on `interface
PhotosRepository`; the implementation is `internal class PhotosRepositoryImpl` bound to it in a DI
module. Both live in the **same file, named after the interface** (`PhotosRepository.kt`). This
holds for repositories, use cases, mappers, effect handlers — anything that gets injected — so
every dependency can be faked in tests and previews. The one naming exception is ViewModels: the
interface is `<X>ScreenModel` and the implementation is `<X>ViewModel` (no `Impl`), both in
`<X>ViewModel.kt` — see the screen contract below.

Roles:

- **Repository** (`data/<feature>/repository/`): the feature's public data API. Exposes `Flow`s
  and `suspend` functions; applies `flowOn(dispatcher)` itself so callers never think about
  threads.
- **Use case** (`domain/<feature>/usecase/`): one verb per class, named as the verb
  (`ShareCapturedItem`), interface exposing `suspend operator fun invoke(...)`. Returns a
  sealed result type from `domain/<feature>/model/`, not exceptions.
- **Mapper**: pure `map(input): output` — no side effects, no Context.
- Dispatchers are injected via qualifiers (`@IoDispatcher`, `@DefaultDispatcher`) declared in
  `di/core/`, never referenced as `Dispatchers.IO` inline.
- **DI** (`di/<feature>/`): one `@Module @InstallIn(SingletonComponent::class)` abstract class per
  feature with `@Binds @Reusable` for each interface→Impl pair. Everything is `internal`.

**Unidirectional data flow per screen** (`ui/<feature>/screen/`):

- The ViewModel implements a `<Screen>ScreenModel` interface exposing exactly
  `uiState: StateFlow<UiState>`, `effects: Flow<ScreenEffect>`, `onAction(Action)`. The screen
  composable takes the **interface** (defaulted to `viewModel<...>()`), so previews and tests
  substitute a fake without Hilt. The screen collects `uiState` with
  `collectAsStateWithLifecycle()` — never plain `collectAsState()`.
- `UiState` (`screen/model/`): `@Immutable` data class, every field defaulted so `State()` is the
  loading state; lists are `kotlinx.collections.immutable.ImmutableList`. Nested per-item types are
  `<X>UiModel`s in the same package, built by a `screen/mapper/` UiStateMapper.
- `Action`: sealed interface of user events, named past-tense from the UI's point of view
  (`ShutterClicked`, `LensSwitchClicked`) — never imperative commands. The ViewModel's `onAction`
  is a single exhaustive `when`.
- `ScreenEffect`: sealed interface of one-shot events, emitted through
  `Channel(capacity = Channel.BUFFERED)` exposed as `receiveAsFlow()` — never a StateFlow, which
  would replay. Navigation is its own `NavEvent` sealed type (or an `onNavigateBack`-style lambda
  for simple back).
- **EffectHandler** (`screen/`): interface + `Impl` constructed with the Activity — the *only*
  place intents, toasts, clipboard, and `finish()` live. The screen collects
  `screenModel.effects` in a `LaunchedEffect(screenModel)` and forwards to the handler via
  `rememberUpdatedState`. For Camera this is where the security-sensitive behavior concentrates:
  the handler holds the real Activity, so prefs stay entry-mode-scoped and intent launches stay
  behind `QrTile`'s keyguard interceptor by construction.
- Screen file shape: public `<X>Screen` wires the model and effects; a private, stateless
  `<X>Content(uiState, onAction, ...)` renders it; `@PreviewLightDark` previews call `<X>Content`
  with literal state. In-file aliases keep signatures readable:
  `import ...model.ViewfinderAction as Action`.
- A ViewModel that outgrows one file splits into `delegate/` classes by responsibility
  (selection, optimistic updates, ...), not into a bigger ViewModel.

---

## Coding Conventions

These govern **new and rewritten code**. Existing files predate them; do not reformat a file you are
not otherwise changing — whitespace churn buries the diff and makes the migration unreviewable.

### Kotlin

- **No expression-body functions.** Always a block body with an explicit return type:
  ```kotlin
  // WRONG
  fun currentMode() = camConfig.currentMode

  // CORRECT
  fun currentMode(): CameraMode {
      return camConfig.currentMode
  }
  ```
  Return type is omitted for functions returning `Unit`; write `fun bind() {`, not
  `fun bind(): Unit {`.
- **No fully-qualified names in code.** Import the type and use the short name. Qualify only to
  resolve an import conflict.
- **Named arguments** for Kotlin calls — constructors, factories, builders. Exceptions: unambiguous
  single-argument calls (`listOf(item)`, `launch(defaultDispatcher)`), stdlib higher-order functions
  (`map { }`, `filter { }`), and Java interop.
- **Descriptive names, no abbreviations.** `context` not `ctx`, `manager` not `mgr`. Short names are
  fine only when universally unambiguous: `id`, `uri`, `i`/`j` in tight loops, `{ it }`.
- **Parameter formatting:** one line if it fits; otherwise one parameter per line with a trailing
  comma. Same for call sites.
- **Trailing commas** in every multi-line parameter list, argument list, `when` branch list and
  collection literal. Never on a single line.
- **Never break the line after `=`.** The right-hand side starts on the same line as the assignment;
  wrap inside it. Breaking after `=` costs a line and an indent level and separates the name from
  the thing that produces it — ktlint's `multiline-expression-wrapping` would impose it, which is
  one reason this project's `.editorconfig` selects `android_studio` over `ktlint_official`.

  ```kotlin
  // WRONG
  val info =
      packageManager.getActivityInfo(
          ComponentName(context.packageName, "$PACKAGE.ui.activities.QrTile"),
          PackageManager.MATCH_ALL,
      )

  // CORRECT
  val info = packageManager.getActivityInfo(
      ComponentName(context.packageName, "$PACKAGE.ui.activities.QrTile"),
      PackageManager.MATCH_ALL,
  )

  // CORRECT — when the call itself does not fit, break the chain instead
  val info = packageManager
      .getActivityInfo(
          ComponentName(context.packageName, "$PACKAGE.ui.activities.QrTile"),
          PackageManager.MATCH_ALL,
      )
  ```

  Break after `=` only when nothing else fits — a `when`/`if` expression body, or a single call whose
  own name already overruns the line.
- **Never `!!` outside tests.** Prefer `?.`, `?:`, and `requireNotNull(x) { "why" }`.
- **Explicit dispatcher on every `scope.launch(...)`.** Never rely on the scope's implicit
  dispatcher. Pass it positionally, not as `context = ...`.
- **`internal` by default** for anything not needed outside the module; `private` aggressively for
  implementation details.
- **No wildcard imports.**
- **Top-level declarations are for genuinely shared, standalone things.** A constant, function, or
  extension function that relates to a specific class/interface — or is `private` to its file —
  belongs inside that class (or its `companion object`), not at top level. Reserve the top level
  for declarations with no owning type.
- **Constants** are `private const val` in `UPPER_SNAKE_CASE` — in a `companion object` placed last
  in the class body when they relate to a class, at file top level only otherwise.
- **Prefer top-level functions over `object`.** Use `object` only for a genuine stateful singleton
  or
  to implement an interface.
- **Prefer `when` over `if` for value-producing expressions** — `val x = when {`, not
  `val x = if (`.
- **Functions stay focused and compact**, with **no more than 2 `return`s**.
- **Shared helpers take an explicit `activity`/`context` parameter — do not write them as `Activity`
  extensions.** `CapturedItems.kt`'s `shareCapturedItem(activity, item)` is the pattern to follow.
  An extension hides which Activity a call is scoped to; the secure-session prefs isolation and
  `QrTile`'s keyguard interceptor both depend on that being visible at the call site.
- **Best-practice verification:** if you are not certain about a framework or API behavior, check
  current official documentation before changing it. CameraX in particular has moved a great deal.

### Comments

**The default is no comment.** Code that needs prose to be understood is code that needs rewriting:
a clearer name, a smaller function, or a named intermediate `val` solves more comprehension problems
than any sentence placed above the line. Reach for one of those first, every time.

A comment earns its place only by carrying what the code cannot — **why**, never **what**. Before
writing one, say what a reader loses if it is deleted. If that answer is a paraphrase of the code,
it is not an answer; delete the comment.

Worth writing:

- A constraint from outside the file — a platform or OEM bug, an API that documents one thing and
  does another, an ordering the framework requires. These are invisible in the code and expensive to
  rediscover.
- Why the obvious approach was rejected, where a reader would otherwise "fix" it back.
- KDoc on a public interface whose contract its signature does not convey: what a caller may assume,
  what it must not.
- A `TODO`/`FIXME` naming the condition that resolves it.

Not worth writing:

- Restating the next line, the signature, or the type.
- Section banners, decorative rules, `// endregion` scaffolding.
- Narrating the edit rather than the code — "now handles X", "moved from Y", "new". The diff and the
  commit message carry history; a comment describes the code as it stands.
- Explaining language or framework basics, or restating a rule from this document.

Two consequences worth stating outright. Comment density is not a quality signal and a comment is
not a way to show work — a file whose every comment is a *why* reads faster than one where each
comment must be checked against the code to find the two that matter. And a comment that has drifted
out of true is worse than no comment: when you change a line, the comments above it are part of that
change.

### Testability

Design new code so its behavior is unit-testable without a device — that is the whole
payoff of the migration:

- Extract interfaces for data sources and repositories so they can be faked.
- Anything holding business logic must be constructible without an Android `Context`; inject
  dependencies through the constructor.
- Prefer pure functions for mappers and state transitions.
- Camera bind ordering is order-sensitive. Settings that trigger a rebind stay **synchronous
  write-through** — StateFlow-collector-driven rebinds conflate and reorder emissions.

### Compose

- **Material 3 only.** All colors and typography go through `CameraTheme` / `MaterialTheme` — never
  hardcode a color in a composable.
- **Dynamic color first.** The theme uses the user's device colors (`dynamicDarkColorScheme` /
  `dynamicLightColorScheme`). Introduce a custom color only when a real need can't be met by an
  existing `MaterialTheme.colorScheme` role, and add it as a theme extension — not inline in a
  composable.
- **State hoisting:** composables below screen level are stateless, receiving state as parameters
  and
  emitting events via lambdas. No ViewModel access below screen level.
- **`modifier: Modifier = Modifier`** as the first optional parameter; chain modifiers, never
  reassign. Pass it to the outermost layout the composable emits, exactly once — a composable that
  drops its `modifier` or applies it to an inner child breaks its callers' layout expectations.
- **`LaunchedEffect` keys** are stable inputs only — wrap changing callbacks in
  `rememberUpdatedState` rather than keying on them.
- One primary public composable per file, `PascalCase`, file named after it. `@Preview` functions
  stay in the file that declares the composable they preview.

### Resources

User-visible strings go in `res/values/strings.xml` — never hardcoded in Kotlin. Dimensions shared
with XML layouts live in `dimens.xml`; in Compose use `dp`/`sp` directly.

---

## Dependencies

`gradle/libs.versions.toml` is the single source of truth. **Never put a raw version string in a
`build.gradle.kts`.**

- **Do not add a dependency before something uses it.** Every task here is an upstream PR, and "adds
  a dependency nothing references" is the shape of PR a maintainer rejects — correctly. Each
  dependency lands in the change whose code first needs it.
- Keep version, library and plugin lists **sorted case-insensitively**; blank-line groups (runtime,
  test, tooling) are fine, each sorted internally.
- **CameraX is strictly pinned.** The app imports three CameraX `internal` APIs that carry no
  compatibility guarantee, so a bump can break capture *at runtime* while CI stays green. The
  catalog uses `strictly` so that a bump fails resolution instead. Read the comment on `camerax` in
  `gradle/libs.versions.toml` before touching it; replacing the three imports with supported
  equivalents is its own change, and comes first.
- **Dependency hash verification is enforced** via `gradle/verification-metadata.xml` — every
  artifact's checksum is pinned, so any new or changed dependency fails the build until its hashes
  are recorded there. On a verification error: **stop and ask the user to fix it.** Do not edit
  `verification-metadata.xml`, regenerate it, or pass `--write-verification-metadata` yourself —
  the whole point of the file is that a human vouches for each hash.

---

## File Naming

| Type          | Convention                                         | Example                         |
|---------------|----------------------------------------------------|---------------------------------|
| Kotlin source | PascalCase                                         | `VideoCapturer.kt`              |
| Injectable    | Named after the interface; `Impl` in the same file | `PhotosRepository.kt`           |
| Composable    | PascalCase, matches composable                     | `CaptureButton.kt`              |
| UI state      | PascalCase + `UiState`                             | `ViewfinderUiState.kt`          |
| Extensions    | PascalCase + `Extensions`                          | `SharedPrefsExtensions.kt`      |
| Test          | Subject + `RegressionTest`/`Test`                  | `PhotoQualityRegressionTest.kt` |
| Resources     | snake_case                                         | `settings_dialog.xml`           |

---

## Misc

- **Do not commit unless the user explicitly asks.** Never `git push` unasked.
- **Never add a commit co-author unless the user explicitly asks.**
- Commit messages: imperative mood, describing the behavior change rather than the mechanism —
  match the existing log ("Don't initialize the camera while its permission is not granted").
- Test-facing seams in `CamConfig` (`mPlayer`, `photoQuality`, `camera`, `switchMode`,
  `SettingValues`) are written to by the instrumented suite. They stay writable until the screen
  that owns them is migrated.

# Recipes — Kotlin Multiplatform REST API client (Лабораторна робота №4–7)

Compose Multiplatform app (Android, iOS, Desktop) that consumes the open
[dummyjson](https://dummyjson.com/docs/recipes) **recipes** API.

## What is implemented (lab checklist)

| # | Requirement | Where |
|---|-------------|-------|
| 1 | New KMP project | `composeApp` module (Android + iOS + Desktop targets) |
| 2 | UI designed with an AI tool | Claude Design (Claude) mockups: onboarding ×3, recipe list, recipe details, settings |
| 3 | Custom theme (colors, fonts) | `ui/theme/` — basil-green/tangerine palette (light + dark), Manrope font in `composeResources/font` |
| 4 | Kermit + multiplatform-settings | `Logger.withTag(...)` in `data/`, `ui/`, `App.kt`; `settings/AppSettings.kt` stores language, theme and "onboarding completed" |
| 5 | Onboarding with `Pager` (3–5 slides) | `ui/onboarding/OnboardingScreen.kt` — `HorizontalPager`, 3 slides, dots, Skip / Next / Get started |
| 6 | Localization: Ukrainian + English | `composeResources/values/strings.xml`, `values-uk/strings.xml`; language switch in Settings (`platform/LocalAppLocale`) |

REST API client: Ktor (`data/RecipesApi.kt`) → `GET /recipes`, `/recipes/search?q=`, `/recipes/{id}`.

## Lab 5 — navigation, DI, MVI, authorization

| # | Requirement | Where |
|---|-------------|-------|
| 1 | Navigation 3 | `navigation/Routes.kt`, `NavDisplay` in `App.kt` |
| 2 | Koin | `di/AppModule.kt`, `KoinApplication` in `App.kt` |
| 3 | multiplatform-settings | `settings/AppSettings.kt` (used by `OnboardingRepositoryImpl`) |
| 4 | Onboarding MVI + use case | `ui/onboarding/*Contract/ViewModel`, `SaveOnboardingResultUseCase` -> `OnboardingRepository` -> settings |
| 5-6 | Auth screen + onboarding -> auth transition | `ui/auth/AuthScreen.kt`, `OnboardingRoute -> AuthRoute` |
| 7-8 | Main ViewModel + start screen by onboarding result | `ui/main/MainViewModel.kt`, `IsOnboardingCompletedUseCase` |
| 9 | Auth design, MVI, validation, keyboard actions | `ui/auth/` (`AuthValidator`, Next -> password, Done -> Sign in) |

Flow: first launch `Onboarding -> Auth -> Recipes`; after onboarding is passed the app opens `Recipes` directly.

## Lab 6 — authorization over the network, secure token storage

Backend: the open [dummyjson auth API](https://dummyjson.com/docs/auth) (`POST /auth/login`, `POST /auth/refresh`).
Test account: **`emilys` / `emilyspass`** (any other dummyjson user works as well).

| # | Requirement | Where |
|---|-------------|-------|
| 1 | Ktor for networking | `data/network/HttpClients.kt` (plain client for login/refresh, authorized client with the `Auth` plugin) |
| 2 | KSafe for secure storage | `data/storage/KSafeTokenStorage.kt` (AES-256-GCM, key in Android Keystore / iOS Keychain / OS secret store), KSafe instance: `platform/PlatformModule*.kt` |
| 3 | Network stack: requests, models, repository, use cases | `data/auth/` (`AuthApi`, DTO, error mapping), `domain/repository/AuthRepository`, `data/repository/AuthRepositoryImpl`, `domain/usecase/` (`LoginUseCase`, `LogoutUseCase`, `RefreshTokensUseCase`, `IsUserAuthorizedUseCase`, `ObserveAuthStateUseCase`) |
| 4 | Tokens stored securely on the device | `TokenStorage` -> `KSafeTokenStorage` |
| 5 | Automatic token refresh | Ktor bearer provider: on `401` it calls `RefreshTokensUseCase`, repeats the request with the new tokens; if the server rejects the refresh token the session ends |
| 6 | MVI of the auth screen connected to use cases | `ui/auth/AuthViewModel` -> `LoginUseCase` |
| 7 | Error handling + snackbar through events | `AuthContract.Effect.ShowError(AuthFailure)` -> `SnackbarHost` in `AuthScreen` (wrong credentials, no connection, server error, unknown) |
| 8 | Empty home screen + navigation to it after sign-in | `HomeRoute`, `ui/home/HomeScreen.kt` |
| 9 | Navigation takes the "user is authorized" flag into account | `MainViewModel` (start screen: onboarding / home / sign-in), `ObserveAuthStateUseCase` in `App.kt` (back to sign-in when the session ends), "Sign out" in Settings |

Flow: `Onboarding -> Auth -> Home`. On the next launches the app opens `Home` straight away while the tokens are stored;
after sign-out or when the refresh token expires it opens `Auth`.

Tests (`./gradlew :composeApp:desktopTest`): login/errors, `401 -> refresh -> retry`, rejected refresh token, sign-out
(Ktor `MockEngine`), and a check that Koin resolves the whole auth graph.

Note for Linux desktop: KSafe keeps its key in the OS secret store (libsecret / KWallet). If it is locked or there is
no D-Bus session, the tokens are kept only in memory for the current run and the user signs in again after a restart.

## Lab 7 — adaptive layouts (WindowSizeClass)

`ui/adaptive/WindowSize.kt` wraps `currentWindowAdaptiveInfo().windowSizeClass`
(`org.jetbrains.compose.material3.adaptive:adaptive`) into `WindowSize(width, height)` with
Compact (<600dp) / Medium (600-839dp) / Expanded (>=840dp) buckets; it is recalculated on resize and rotation.

| Screen | Compact (phone, portrait) | Medium (tablet, portrait) | Expanded (desktop, tablet landscape) / phone landscape |
|--------|---------------------------|---------------------------|--------------------------------------------------------|
| Onboarding (`ui/onboarding/OnboardingScreen.kt`) | one column: illustration above the text | illustration and text side by side, content up to 960dp, compact button | same, bigger illustration |
| Login (`ui/auth/AuthScreen.kt`) | full-width column | centered column, up to 480dp | two panes: title panel on the left, form on the right |

Try it on desktop by resizing the window; on Android by rotating the device or using a tablet emulator.

## Lab 8 — paged recipe list (Paging 3)

The home list of recipes is loaded page by page (`androidx.paging:paging-common` + `paging-compose`, `commonMain`).

| Layer | What was added |
|-------|----------------|
| API | `RecipesApi.getRecipesPage(query, mealType, limit, skip)`; the response has `total`, so it is known whether there is more data |
| Repository | `RecipesRepository.getRecipesPage(...)`: a plain `suspend` call returning `Result<RecipesPage>` (items + `nextOffset`, null = last page); no paging types in data/domain |
| Use case | `GetRecipesPageUseCase`, registered in Koin (`di/AppModule.kt`) |
| PagingSource | `ui/list/RecipesPagingSource`: key = offset (null = first page), `nextKey = nextOffset`, errors -> `LoadResult.Error`, `getRefreshKey = null` (refresh starts from the first page) |
| ViewModel | `RecipesViewModel`: `Pager(PagingConfig(pageSize = 10, initialLoadSize = 20, enablePlaceholders = false))`; a new `PagingSource` is created when the search text (debounced) or the meal filter changes; `pager.flow.cachedIn(viewModelScope)` |
| UI | `collectAsLazyPagingItems()` + `LazyColumn` with `itemKey { it.id }`; first load -> spinner, error -> message + **Retry** (`retry()`), next page -> spinner / error row at the end of the list, pull to refresh -> `refresh()` |

Search by name uses `/recipes/search`, the meal filter uses `/recipes/meal-type/{type}`; when both are set, the repository
searches and filters the found recipes by meal type (the API cannot do both at once).

**List-detail layout.** Compact width (phone, portrait): the list only, a click opens the details screen.
Medium / expanded width (tablet, desktop, phone in landscape): the list on the left and the details of the selected recipe
on the right (`RecipeListScreen`).

Tests: `RecipesPagingTest.kt` (PagingSource keys and errors, repository paging, filter + search) - `./gradlew :composeApp:desktopTest`.

## Run

* **Desktop:** `./gradlew :composeApp:run`
* **Android:** open the project in Android Studio and run the `composeApp` configuration
  (or `./gradlew :composeApp:installDebug` with a device/emulator attached).
* **iOS:** the shared framework (`ComposeApp`) and `MainViewController()` are ready.
  Create the Xcode host app with the KMP wizard (<https://kmp.jetbrains.com>) and call
  `MainViewControllerKt.MainViewController()` from SwiftUI, or copy `iosApp/` from a wizard project.

## Libraries

Compose Multiplatform 1.10.3 · Kotlin 2.2.21 · Ktor 3.1 · kotlinx.serialization · Coil 3 ·
**Kermit 2.0.6** (logging) · **multiplatform-settings 1.3.0** (user settings) · **Navigation 3 1.1.1** · **Koin 4.1.1** · **KSafe 3.0.0** (secure storage) · Ktor Auth (bearer + token refresh) · **Paging 3 (3.4.2)**.

Font: [Manrope](https://github.com/sharanda/manrope) (SIL Open Font License, see `FONT_LICENSE_OFL.txt`).

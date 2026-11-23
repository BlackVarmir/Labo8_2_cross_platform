package ua.chnu.recipes.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import ua.chnu.recipes.data.RecipesApi
import ua.chnu.recipes.data.auth.AuthApi
import ua.chnu.recipes.data.network.createAuthorizedHttpClient
import ua.chnu.recipes.data.network.createHttpClient
import ua.chnu.recipes.data.repository.AuthRepositoryImpl
import ua.chnu.recipes.data.repository.OnboardingRepositoryImpl
import ua.chnu.recipes.data.repository.RecipesRepositoryImpl
import ua.chnu.recipes.data.storage.KSafeTokenStorage
import ua.chnu.recipes.data.storage.TokenStorage
import ua.chnu.recipes.domain.repository.AuthRepository
import ua.chnu.recipes.domain.repository.OnboardingRepository
import ua.chnu.recipes.domain.repository.RecipesRepository
import ua.chnu.recipes.domain.usecase.GetRecipesPageUseCase
import ua.chnu.recipes.domain.usecase.IsOnboardingCompletedUseCase
import ua.chnu.recipes.domain.usecase.IsUserAuthorizedUseCase
import ua.chnu.recipes.domain.usecase.LoginUseCase
import ua.chnu.recipes.domain.usecase.LogoutUseCase
import ua.chnu.recipes.domain.usecase.ObserveAuthStateUseCase
import ua.chnu.recipes.domain.usecase.RefreshTokensUseCase
import ua.chnu.recipes.domain.usecase.SaveOnboardingResultUseCase
import ua.chnu.recipes.settings.AppSettings
import ua.chnu.recipes.ui.RecipesViewModel
import ua.chnu.recipes.ui.auth.AuthViewModel
import ua.chnu.recipes.ui.main.MainViewModel
import ua.chnu.recipes.ui.onboarding.OnboardingViewModel

private val PlainClient = named("plainHttpClient")
private val AuthorizedClient = named("authorizedHttpClient")

/** Koin module with all dependencies of the app (KSafe itself comes from the platform module). */
val appModule = module {
    // Network: the plain client is used by login/refresh, the authorized one adds the bearer token and refreshes it
    single<HttpClient>(PlainClient) { createHttpClient() }
    single<HttpClient>(AuthorizedClient) {
        createAuthorizedHttpClient(
            tokenStorage = get(),
            refreshTokens = { get<RefreshTokensUseCase>()() },
        )
    }

    // Data
    single { AppSettings() }
    single { RecipesApi(get(AuthorizedClient)) }
    single { AuthApi(get(PlainClient)) }
    singleOf(::KSafeTokenStorage) bind TokenStorage::class
    singleOf(::OnboardingRepositoryImpl) bind OnboardingRepository::class
    singleOf(::RecipesRepositoryImpl) bind RecipesRepository::class
    single<AuthRepository> {
        AuthRepositoryImpl(
            api = get(),
            storage = get(),
            // the authorized client caches the tokens it has used: drop them on sign-in / sign-out
            onSessionChanged = { get<HttpClient>(AuthorizedClient).authProvider<BearerAuthProvider>()?.clearToken() },
        )
    }

    // Use cases
    factoryOf(::SaveOnboardingResultUseCase)
    factoryOf(::IsOnboardingCompletedUseCase)
    factoryOf(::LoginUseCase)
    factoryOf(::LogoutUseCase)
    factoryOf(::RefreshTokensUseCase)
    factoryOf(::IsUserAuthorizedUseCase)
    factoryOf(::ObserveAuthStateUseCase)
    factoryOf(::GetRecipesPageUseCase)

    // ViewModels
    viewModelOf(::MainViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::RecipesViewModel)
}

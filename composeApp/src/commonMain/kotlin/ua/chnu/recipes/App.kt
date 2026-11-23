package ua.chnu.recipes

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import ua.chnu.recipes.di.appModule
import ua.chnu.recipes.navigation.AuthRoute
import ua.chnu.recipes.navigation.HomeRoute
import ua.chnu.recipes.navigation.OnboardingRoute
import ua.chnu.recipes.navigation.RecipeDetailRoute
import ua.chnu.recipes.navigation.RecipeListRoute
import ua.chnu.recipes.navigation.Route
import ua.chnu.recipes.navigation.SettingsRoute
import ua.chnu.recipes.ui.auth.AuthScreen
import ua.chnu.recipes.ui.main.MainViewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import org.jetbrains.compose.resources.stringResource
import ua.chnu.recipes.platform.LocalAppLocale
import ua.chnu.recipes.platform.rememberPlatformModule
import ua.chnu.recipes.resources.Res
import ua.chnu.recipes.resources.nav_home
import ua.chnu.recipes.resources.nav_recipes
import ua.chnu.recipes.resources.nav_settings
import ua.chnu.recipes.settings.AppSettings
import ua.chnu.recipes.domain.usecase.LogoutUseCase
import ua.chnu.recipes.domain.usecase.ObserveAuthStateUseCase
import ua.chnu.recipes.ui.detail.RecipeDetailScreen
import ua.chnu.recipes.ui.home.HomeScreen
import ua.chnu.recipes.ui.list.RecipeListScreen
import ua.chnu.recipes.ui.onboarding.OnboardingScreen
import ua.chnu.recipes.ui.settings.SettingsScreen
import ua.chnu.recipes.ui.theme.RecipesTheme

private val log = Logger.withTag("App")

/** Navigation 3 needs to know all [NavKey] subtypes on non-Android targets to (de)serialize the back stack. */
private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(OnboardingRoute::class, OnboardingRoute.serializer())
            subclass(AuthRoute::class, AuthRoute.serializer())
            subclass(HomeRoute::class, HomeRoute.serializer())
            subclass(RecipeListRoute::class, RecipeListRoute.serializer())
            subclass(SettingsRoute::class, SettingsRoute.serializer())
            subclass(RecipeDetailRoute::class, RecipeDetailRoute.serializer())
        }
    }
}

@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }

    val platformModule = rememberPlatformModule()
    KoinApplication(application = { modules(appModule, platformModule) }) {
        AppContent()
    }
}

@Composable
private fun AppContent() {
    val settings = koinInject<AppSettings>()
    val mainViewModel = koinViewModel<MainViewModel>()

    // Back stack lives outside of key(language) so that changing the language keeps the current screen.
    val backStack = rememberNavBackStack(navConfig, mainViewModel.startRoute)

    // The "user is signed in" flag drives the navigation: when it drops (sign-out, or the refresh token
    // was rejected and the session expired) every screen behind the sign-in is replaced by the sign-in screen.
    val isAuthorized by koinInject<ObserveAuthStateUseCase>()().collectAsState()
    val logout = koinInject<LogoutUseCase>()
    val scope = rememberCoroutineScope()
    LaunchedEffect(isAuthorized) {
        val current = backStack.lastOrNull()
        if (!isAuthorized && current != null && current !is AuthRoute && current !is OnboardingRoute) {
            log.i { "user is not authorized any more -> sign-in screen" }
            backStack.replaceAllWith(AuthRoute)
        }
    }

    val onTabSelected: (MainTab) -> Unit = { tab ->
        if (backStack.lastOrNull() != tab.route) backStack.replaceAllWith(tab.route)
    }

    var language by remember { mutableStateOf(settings.language) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }

    // Overriding the locale + key(language) re-creates the UI tree so that
    // string resources are re-read in the newly selected language.
    CompositionLocalProvider(LocalAppLocale provides language) {
        key(language) {
            RecipesTheme(themeMode) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<OnboardingRoute> {
                            OnboardingScreen(
                                viewModel = koinViewModel(),
                                onNavigateToAuth = { backStack.replaceAllWith(if (isAuthorized) HomeRoute else AuthRoute) },
                            )
                        }
                        entry<AuthRoute> {
                            AuthScreen(
                                viewModel = koinViewModel(),
                                onSignedIn = { backStack.replaceAllWith(HomeRoute) },
                            )
                        }
                        entry<HomeRoute> {
                            MainScaffold(selected = MainTab.Home, onSelect = onTabSelected) { padding ->
                                HomeScreen(modifier = Modifier.padding(padding))
                            }
                        }
                        entry<RecipeListRoute> {
                            MainScaffold(selected = MainTab.Recipes, onSelect = onTabSelected) { padding ->
                                RecipeListScreen(
                                    viewModel = koinViewModel(),
                                    api = koinInject(),
                                    onRecipeClick = { backStack.add(RecipeDetailRoute(it)) },
                                    modifier = Modifier.padding(padding),
                                )
                            }
                        }
                        entry<SettingsRoute> {
                            MainScaffold(selected = MainTab.Settings, onSelect = onTabSelected) { padding ->
                                SettingsScreen(
                                    language = language,
                                    onLanguageChange = {
                                        log.i { "Language changed to $it" }
                                        settings.language = it
                                        language = it
                                    },
                                    themeMode = themeMode,
                                    onThemeModeChange = {
                                        settings.themeMode = it
                                        themeMode = it
                                    },
                                    onShowOnboarding = { backStack.replaceAllWith(OnboardingRoute) },
                                    onSignOut = { scope.launch { logout() } },
                                    modifier = Modifier.padding(padding),
                                )
                            }
                        }
                        entry<RecipeDetailRoute> { route ->
                            RecipeDetailScreen(
                                recipeId = route.recipe.id,
                                initial = route.recipe,
                                api = koinInject(),
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun NavBackStack<NavKey>.replaceAllWith(route: Route) {
    clear()
    add(route)
}

/** Tabs of the bottom navigation bar. */
private enum class MainTab(val route: Route) {
    Home(HomeRoute),
    Recipes(RecipeListRoute),
    Settings(SettingsRoute),
}

@Composable
private fun MainScaffold(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = {
                            Icon(
                                when (tab) {
                                    MainTab.Home -> Icons.Default.Home
                                    MainTab.Recipes -> Icons.AutoMirrored.Filled.List
                                    MainTab.Settings -> Icons.Default.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                stringResource(
                                    when (tab) {
                                        MainTab.Home -> Res.string.nav_home
                                        MainTab.Recipes -> Res.string.nav_recipes
                                        MainTab.Settings -> Res.string.nav_settings
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = colors,
                    )
                }
            }
        },
        content = content,
    )
}

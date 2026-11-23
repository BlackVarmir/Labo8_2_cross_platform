package ua.chnu.recipes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import co.touchlab.kermit.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import ua.chnu.recipes.data.Recipe
import ua.chnu.recipes.domain.usecase.GetRecipesPageUseCase
import ua.chnu.recipes.ui.list.RecipesPagingSource

/** Meal-type filter chips; [apiValue] is the value used by dummyjson's `mealType` field. */
enum class MealFilter(val apiValue: String?) {
    All(null),
    Breakfast("Breakfast"),
    Lunch("Lunch"),
    Dinner("Dinner"),
}

/** Page sizes of the recipe list. */
private const val PAGE_SIZE = 10
private const val INITIAL_LOAD_SIZE = 20

/** State holder for the recipe list screen: search + meal-type filter + the paged list of recipes. */
class RecipesViewModel(
    private val getRecipesPage: GetRecipesPageUseCase,
) : ViewModel() {
    private val log = Logger.withTag("RecipesViewModel")

    var query by mutableStateOf("")
        private set
    var mealFilter by mutableStateOf(MealFilter.All)
        private set

    /** Recipe opened in the details pane (used when the screen is wide enough for list + details). */
    var selectedRecipe by mutableStateOf<Recipe?>(null)
        private set

    private val pagerConfig = PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = INITIAL_LOAD_SIZE,
        prefetchDistance = PAGE_SIZE / 2,
        enablePlaceholders = false,
    )

    /**
     * The list of recipes. A new Pager (and a new PagingSource) is created every time the search text
     * or the filter changes; the old one is dropped. cachedIn keeps the loaded pages while the screen
     * is rotated or recomposed.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val recipes: Flow<PagingData<Recipe>> = combine(
        snapshotFlow { query.trim() }.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        snapshotFlow { mealFilter },
    ) { text, meal -> text to meal }
        .distinctUntilChanged()
        .flatMapLatest { (text, meal) ->
            log.d { "new pager: query='$text', filter=$meal" }
            Pager(pagerConfig) { RecipesPagingSource(getRecipesPage, text, meal.apiValue) }.flow
        }
        .cachedIn(viewModelScope)

    fun onQueryChange(value: String) {
        query = value
    }

    fun onMealFilterChange(value: MealFilter) {
        log.d { "filter -> $value" }
        mealFilter = value
    }

    fun onRecipeSelected(recipe: Recipe) {
        selectedRecipe = recipe
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}

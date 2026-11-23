package ua.chnu.recipes.ui.list

import androidx.paging.PagingSource
import androidx.paging.PagingState
import ua.chnu.recipes.data.Recipe
import ua.chnu.recipes.domain.usecase.GetRecipesPageUseCase

/**
 * Feeds the list with recipes page by page. The key is the offset of the page (null = the first page).
 * One instance serves one combination of [query] + [mealType]; a new one is created when they change.
 */
class RecipesPagingSource(
    private val getPage: GetRecipesPageUseCase,
    private val query: String,
    private val mealType: String?,
) : PagingSource<Int, Recipe>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Recipe> {
        val offset = params.key ?: 0
        return getPage(query, mealType, params.loadSize, offset).fold(
            onSuccess = { page ->
                LoadResult.Page(
                    data = page.items,
                    prevKey = null, // the list is only loaded forward
                    nextKey = page.nextOffset, // null: no more data
                )
            },
            onFailure = { error -> LoadResult.Error(error) },
        )
    }

    // null: after refresh() the list is loaded again from the first page
    override fun getRefreshKey(state: PagingState<Int, Recipe>): Int? = null
}

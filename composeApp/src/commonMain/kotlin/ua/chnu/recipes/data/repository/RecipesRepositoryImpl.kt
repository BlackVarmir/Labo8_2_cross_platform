package ua.chnu.recipes.data.repository

import kotlinx.coroutines.CancellationException
import ua.chnu.recipes.data.RecipesApi
import ua.chnu.recipes.domain.model.RecipesPage
import ua.chnu.recipes.domain.repository.RecipesRepository

class RecipesRepositoryImpl(
    private val api: RecipesApi,
) : RecipesRepository {

    override suspend fun getRecipesPage(
        query: String,
        mealType: String?,
        limit: Int,
        offset: Int,
    ): Result<RecipesPage> = try {
        Result.success(load(query, mealType, limit, offset))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun load(query: String, mealType: String?, limit: Int, offset: Int): RecipesPage {
        // The API can filter either by name or by meal type, not by both at once.
        // With a search query the meal type is applied here, to the found recipes.
        val filterHere = query.isNotBlank() && mealType != null
        var skip = offset
        while (true) {
            val response = api.getRecipesPage(query, mealType.takeUnless { filterHere }, limit, skip)
            skip += response.recipes.size
            val hasMore = response.recipes.isNotEmpty() && skip < response.total
            val items = if (filterHere) {
                response.recipes.filter { recipe -> recipe.mealType.any { it.equals(mealType, ignoreCase = true) } }
            } else {
                response.recipes
            }
            // an empty page in the middle of the list would stop the paging, so keep reading until something is left
            if (items.isNotEmpty() || !hasMore) {
                return RecipesPage(items, nextOffset = skip.takeIf { hasMore })
            }
        }
    }
}

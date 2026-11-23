package ua.chnu.recipes.domain.repository

import ua.chnu.recipes.domain.model.RecipesPage

interface RecipesRepository {
    /**
     * Loads one page: up to [limit] recipes starting at [offset].
     * [mealType] is the API value of the meal filter (null = any meal).
     * A failure is returned as [Result.failure], it is not thrown.
     */
    suspend fun getRecipesPage(query: String, mealType: String?, limit: Int, offset: Int): Result<RecipesPage>
}

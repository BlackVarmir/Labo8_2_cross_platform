package ua.chnu.recipes.domain.usecase

import ua.chnu.recipes.domain.model.RecipesPage
import ua.chnu.recipes.domain.repository.RecipesRepository

/** Loads one page of recipes through the repository. */
class GetRecipesPageUseCase(private val repository: RecipesRepository) {
    suspend operator fun invoke(query: String, mealType: String?, limit: Int, offset: Int): Result<RecipesPage> =
        repository.getRecipesPage(query, mealType, limit, offset)
}

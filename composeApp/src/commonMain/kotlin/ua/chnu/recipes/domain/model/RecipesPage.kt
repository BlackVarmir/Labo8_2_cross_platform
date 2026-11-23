package ua.chnu.recipes.domain.model

import ua.chnu.recipes.data.Recipe

/**
 * One loaded page of recipes.
 * [nextOffset] is the offset of the next page, or null when there is no more data.
 */
data class RecipesPage(
    val items: List<Recipe>,
    val nextOffset: Int?,
)

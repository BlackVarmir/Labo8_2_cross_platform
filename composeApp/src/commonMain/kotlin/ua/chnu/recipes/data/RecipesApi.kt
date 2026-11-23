package ua.chnu.recipes.data

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Thin REST client over the open dummyjson "recipes" API. */
class RecipesApi(private val client: HttpClient) {
    private val log = Logger.withTag("RecipesApi")

    /** All recipes, or the ones matching [query] when it is not blank. */
    suspend fun getRecipes(query: String = ""): List<Recipe> {
        log.d { "getRecipes(query='$query')" }
        val response: RecipesResponse = if (query.isBlank()) {
            client.get("recipes") { parameter("limit", 0) }.body()
        } else {
            client.get("recipes/search") {
                parameter("q", query.trim())
                parameter("limit", 0)
            }.body()
        }
        log.d { "loaded ${response.recipes.size} recipes" }
        return response.recipes
    }

    /**
     * One page of recipes: [limit] items starting after [skip] items. The response has `total`,
     * so the caller can tell whether there is more data.
     *  - not blank [query]: search by name,
     *  - [mealType] set: only recipes of this meal type,
     *  - otherwise: all recipes.
     */
    suspend fun getRecipesPage(query: String, mealType: String?, limit: Int, skip: Int): RecipesResponse {
        log.d { "getRecipesPage(query='$query', mealType=$mealType, limit=$limit, skip=$skip)" }
        return when {
            query.isNotBlank() -> client.get("recipes/search") {
                parameter("q", query.trim())
                parameter("limit", limit)
                parameter("skip", skip)
            }
            mealType != null -> client.get("recipes/meal-type/$mealType") {
                parameter("limit", limit)
                parameter("skip", skip)
            }
            else -> client.get("recipes") {
                parameter("limit", limit)
                parameter("skip", skip)
            }
        }.body()
    }

    suspend fun getRecipe(id: Int): Recipe {
        log.d { "getRecipe($id)" }
        return client.get("recipes/$id").body()
    }
}

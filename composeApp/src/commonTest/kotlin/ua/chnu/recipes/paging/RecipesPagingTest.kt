package ua.chnu.recipes.paging

import androidx.paging.PagingSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import ua.chnu.recipes.data.Recipe
import ua.chnu.recipes.data.RecipesApi
import ua.chnu.recipes.data.network.createHttpClient
import ua.chnu.recipes.data.repository.RecipesRepositoryImpl
import ua.chnu.recipes.domain.model.RecipesPage
import ua.chnu.recipes.domain.repository.RecipesRepository
import ua.chnu.recipes.domain.usecase.GetRecipesPageUseCase
import ua.chnu.recipes.ui.list.RecipesPagingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private fun recipe(id: Int, meal: String = "Dinner") = Recipe(id = id, name = "Recipe $id", mealType = listOf(meal))

private class FakeRepository(private val result: (offset: Int) -> Result<RecipesPage>) : RecipesRepository {
    var lastOffset: Int? = null
    var lastLimit: Int? = null
    override suspend fun getRecipesPage(query: String, mealType: String?, limit: Int, offset: Int): Result<RecipesPage> {
        lastOffset = offset
        lastLimit = limit
        return result(offset)
    }
}

class RecipesPagingSourceTest {
    private fun source(repo: RecipesRepository) = RecipesPagingSource(GetRecipesPageUseCase(repo), "", null)

    @Test
    fun firstPageIsLoadedFromOffsetZeroAndKeepsNextKey() = runTest {
        val repo = FakeRepository { Result.success(RecipesPage(listOf(recipe(1), recipe(2)), nextOffset = 2)) }

        val result = source(repo).load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

        val page = assertIs<PagingSource.LoadResult.Page<Int, Recipe>>(result)
        assertEquals(0, repo.lastOffset)
        assertEquals(20, repo.lastLimit)
        assertEquals(listOf(1, 2), page.data.map { it.id })
        assertEquals(2, page.nextKey)
        assertNull(page.prevKey)
    }

    @Test
    fun nextPageUsesTheKeyAndTheLastPageHasNoNextKey() = runTest {
        val repo = FakeRepository { Result.success(RecipesPage(listOf(recipe(3)), nextOffset = null)) }

        val result = source(repo).load(PagingSource.LoadParams.Append(key = 2, loadSize = 10, placeholdersEnabled = false))

        val page = assertIs<PagingSource.LoadResult.Page<Int, Recipe>>(result)
        assertEquals(2, repo.lastOffset)
        assertNull(page.nextKey)
    }

    @Test
    fun failureBecomesLoadResultError() = runTest {
        val error = IllegalStateException("no network")
        val repo = FakeRepository { Result.failure(error) }

        val result = source(repo).load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

        assertEquals(error, assertIs<PagingSource.LoadResult.Error<Int, Recipe>>(result).throwable)
    }
}

class RecipesRepositoryTest {
    private fun json(ids: List<Int>, total: Int, meals: Map<Int, String> = emptyMap()): String {
        val items = ids.joinToString(",") { """{"id":$it,"name":"R$it","mealType":["${meals[it] ?: "Dinner"}"]}""" }
        return """{"recipes":[$items],"total":$total,"skip":0,"limit":${ids.size}}"""
    }

    private fun repository(requests: MutableList<String>, body: (String) -> String): RecipesRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request.url.toString()
            respond(body(request.url.toString()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return RecipesRepositoryImpl(RecipesApi(createHttpClient(engine)))
    }

    @Test
    fun nextOffsetIsSetWhileThereIsMoreData() = runTest {
        val requests = mutableListOf<String>()
        val repo = repository(requests) { json(listOf(1, 2, 3), total = 7) }

        val page = repo.getRecipesPage("", null, limit = 3, offset = 3).getOrThrow()

        assertEquals(6, page.nextOffset)
        assertEquals(true, requests.single().contains("skip=3") && requests.single().contains("limit=3"))
    }

    @Test
    fun lastPageHasNoNextOffset() = runTest {
        val repo = repository(mutableListOf()) { json(listOf(7), total = 7) }

        assertNull(repo.getRecipesPage("", null, limit = 3, offset = 6).getOrThrow().nextOffset)
    }

    @Test
    fun serverErrorIsReturnedAsFailure() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        val repo = RecipesRepositoryImpl(RecipesApi(createHttpClient(engine)))

        assertEquals(true, repo.getRecipesPage("", null, 10, 0).isFailure)
    }

    @Test
    fun mealFilterWithSearchKeepsReadingUntilSomethingMatches() = runTest {
        val requests = mutableListOf<String>()
        // the first page has no breakfast recipes, the second one has
        val repo = repository(requests) { url ->
            if (url.contains("skip=0")) json(listOf(1, 2), total = 4) else json(listOf(3, 4), total = 4, meals = mapOf(4 to "Breakfast"))
        }

        val page = repo.getRecipesPage("pasta", "Breakfast", limit = 2, offset = 0).getOrThrow()

        assertEquals(listOf(4), page.items.map { it.id })
        assertNull(page.nextOffset)
        assertEquals(2, requests.size)
    }
}

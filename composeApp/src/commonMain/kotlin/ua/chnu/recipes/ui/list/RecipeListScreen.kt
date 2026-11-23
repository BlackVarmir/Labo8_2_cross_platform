package ua.chnu.recipes.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import ua.chnu.recipes.data.Recipe
import ua.chnu.recipes.resources.Res
import ua.chnu.recipes.resources.empty_results
import ua.chnu.recipes.resources.kcal_short
import ua.chnu.recipes.resources.load_error
import ua.chnu.recipes.resources.minutes_short
import ua.chnu.recipes.resources.recipes_subtitle
import ua.chnu.recipes.resources.recipes_title
import ua.chnu.recipes.resources.retry
import ua.chnu.recipes.resources.search_hint
import ua.chnu.recipes.ui.MealFilter
import ua.chnu.recipes.ui.RecipesViewModel
import ua.chnu.recipes.ui.difficultyLabel
import ua.chnu.recipes.ui.label

@Composable
fun RecipeListScreen(
    viewModel: RecipesViewModel,
    onRecipeClick: (Recipe) -> Unit,
    modifier: Modifier = Modifier,
) {
    RecipeListPane(viewModel, selectedId = null, onRecipeClick = onRecipeClick, modifier = modifier)
}

/**
 * Header + search + filter chips + the paged list. The list is a [LazyColumn] over [LazyPagingItems]:
 * the next page is requested by Paging when the user scrolls close to the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListPane(
    viewModel: RecipesViewModel,
    selectedId: Int?,
    onRecipeClick: (Recipe) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipes = viewModel.recipes.collectAsLazyPagingItems()
    val refresh = recipes.loadState.refresh
    val append = recipes.loadState.append

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(Res.string.recipes_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(Res.string.recipes_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MealFilter.entries) { filter ->
                FilterChip(
                    selected = viewModel.mealFilter == filter,
                    onClick = { viewModel.onMealFilterChange(filter) },
                    label = { Text(filter.label(), style = MaterialTheme.typography.labelLarge) },
                    shape = RoundedCornerShape(22.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            // first page is loading
            recipes.itemCount == 0 && refresh is LoadState.Loading -> CenteredBox { CircularProgressIndicator() }
            // first page failed
            recipes.itemCount == 0 && refresh is LoadState.Error -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(Res.string.load_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { recipes.retry() }) { Text(stringResource(Res.string.retry)) }
                }
            }
            recipes.itemCount == 0 -> CenteredBox {
                Text(
                    stringResource(Res.string.empty_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Column(Modifier.fillMaxSize()) {
                // refresh() failed, the old items are still shown
                if (refresh is LoadState.Error) ErrorRow(onRetry = { recipes.retry() })
                PullToRefreshBox(
                    isRefreshing = refresh is LoadState.Loading,
                    onRefresh = { recipes.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(count = recipes.itemCount, key = recipes.itemKey { it.id }) { index ->
                            recipes[index]?.let { recipe ->
                                RecipeCard(
                                    recipe,
                                    selected = recipe.id == selectedId,
                                    onClick = { onRecipeClick(recipe) },
                                )
                            }
                        }
                        // state of the next page: spinner while loading, message with "Retry" on error
                        when (append) {
                            is LoadState.Loading -> item(key = "append-loading") {
                                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                }
                            }
                            is LoadState.Error -> item(key = "append-error") {
                                ErrorRow(onRetry = { recipes.retry() })
                            }
                            is LoadState.NotLoading -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorRow(onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.load_error),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun RecipeCard(recipe: Recipe, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncImage(
                model = recipe.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Column(Modifier.weight(1f).align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${recipe.cuisine} · ${difficultyLabel(recipe.difficulty)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.minutes_short, recipe.totalMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(Res.string.kcal_short, recipe.caloriesPerServing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "★ ${recipe.rating}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

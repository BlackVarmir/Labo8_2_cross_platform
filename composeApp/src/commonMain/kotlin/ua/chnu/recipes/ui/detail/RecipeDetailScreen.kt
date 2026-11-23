package ua.chnu.recipes.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import ua.chnu.recipes.data.Recipe
import ua.chnu.recipes.data.RecipesApi
import ua.chnu.recipes.resources.Res
import ua.chnu.recipes.resources.back
import ua.chnu.recipes.resources.detail_calories
import ua.chnu.recipes.resources.detail_cook
import ua.chnu.recipes.resources.detail_ingredients
import ua.chnu.recipes.resources.detail_instructions
import ua.chnu.recipes.resources.detail_prep
import ua.chnu.recipes.resources.detail_reviews
import ua.chnu.recipes.resources.detail_servings
import ua.chnu.recipes.resources.kcal_short
import ua.chnu.recipes.resources.load_error
import ua.chnu.recipes.resources.minutes_short
import ua.chnu.recipes.resources.retry
import ua.chnu.recipes.ui.difficultyLabel

private val log = Logger.withTag("RecipeDetail")

@Composable
fun RecipeDetailScreen(
    recipeId: Int,
    initial: Recipe?,
    api: RecipesApi,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    var recipe by remember(recipeId) { mutableStateOf(initial) }
    var failed by remember(recipeId) { mutableStateOf(false) }
    var attempt by remember(recipeId) { mutableStateOf(0) }

    LaunchedEffect(recipeId, attempt) {
        failed = false
        try {
            recipe = api.getRecipe(recipeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Failed to load recipe $recipeId" }
            if (recipe == null) failed = true
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val current = recipe
        when {
            current != null -> RecipeDetailContent(current)
            failed -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.load_error), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        if (showBackButton) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(16.dp).size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RecipeDetailContent(recipe: Recipe) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AsyncImage(
            model = recipe.image,
            contentDescription = recipe.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Surface(
            modifier = Modifier.offset(y = (-28).dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${recipe.cuisine} · ${difficultyLabel(recipe.difficulty)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "★ ${recipe.rating} " + stringResource(Res.string.detail_reviews, recipe.reviewCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(stringResource(Res.string.detail_prep), stringResource(Res.string.minutes_short, recipe.prepTimeMinutes), Modifier.weight(1f))
                        StatTile(stringResource(Res.string.detail_cook), stringResource(Res.string.minutes_short, recipe.cookTimeMinutes), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(stringResource(Res.string.detail_servings), recipe.servings.toString(), Modifier.weight(1f))
                        StatTile(stringResource(Res.string.detail_calories), stringResource(Res.string.kcal_short, recipe.caloriesPerServing), Modifier.weight(1f))
                    }
                }

                if (recipe.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recipe.tags.take(4).forEach { tag ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

                Section(stringResource(Res.string.detail_ingredients)) {
                    recipe.ingredients.forEach { ingredient ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                            Text(ingredient, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                Section(stringResource(Res.string.detail_instructions)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        recipe.instructions.forEachIndexed { index, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                                }
                                Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

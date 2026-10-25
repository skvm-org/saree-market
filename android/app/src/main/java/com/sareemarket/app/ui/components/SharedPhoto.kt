package com.sareemarket.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** The two scopes a shared element needs: the layout that animates, and the screen that is entering or leaving. */
@OptIn(ExperimentalSharedTransitionApi::class)
class SharedScopes(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

val LocalSharedScopes = compositionLocalOf<SharedScopes?> { null }

/** Makes the screen it wraps part of a shared-photo transition. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProvideSharedScopes(transition: SharedTransitionScope, visibility: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSharedScopes provides SharedScopes(transition, visibility), content = content)
}

/**
 * Marks a saree photo so that opening it from a card grows that same photo into the detail page, and closing it
 * shrinks it back. Without the scopes (previews, tests) it does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPhoto(itemId: Long): Modifier {
    val scopes = LocalSharedScopes.current ?: return this
    return with(scopes.transition) {
        this@sharedPhoto.sharedElement(rememberSharedContentState(key = "photo-$itemId"), scopes.visibility)
    }
}

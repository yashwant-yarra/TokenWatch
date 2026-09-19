package com.tokenwatch.core.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space the floating bottom bar covers, so scrolling lists can end above it while still passing under it. */
val LocalBottomBarPadding = compositionLocalOf<Dp> { 0.dp }

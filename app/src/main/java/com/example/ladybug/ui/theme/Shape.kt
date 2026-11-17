package com.example.ladybug.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * LADYBUG DESIGN PRINCIPLES: SHAPE & CORNER ROUNDNESS
 *
 * All cards (pop-up cards, provider cards, and Tier 2 sub-cards/buttons) follow a unified
 * roundness standard of 12.dp (`RoundedCornerShape(12.dp)`).
 *
 * This matches the visual curvature of the SearchInputBar (56dp height × 20% = 11.2dp ≈ 12dp).
 */
val LadybugCornerRadius = 12.dp
val LadybugCardShape = RoundedCornerShape(LadybugCornerRadius)

val LadybugShapes = Shapes(
    extraSmall = RoundedCornerShape(LadybugCornerRadius),
    small = LadybugCardShape,
    medium = LadybugCardShape,
    large = LadybugCardShape,
    extraLarge = LadybugCardShape
)

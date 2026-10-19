package com.mysound.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icônes du lecteur dessinées d'après les images fournies.
 * La couleur ici n'a pas d'importance : elle est remplacée par le paramètre `tint` de l'Icon
 * (blanc/gris au repos, bleu quand le mode est activé).
 */

/** Lecture aléatoire : deux flèches qui se croisent (la flèche du haut passe "sous" l'autre). */
val ShuffleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Shuffle",
        defaultWidth = 24.dp,
        defaultHeight = 20.7.dp,
        viewportWidth = 51f,
        viewportHeight = 44f
    ).apply {
        // Flèche du bas-gauche vers le haut-droite (trait continu)
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 4.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5.5f, 33f)
            horizontalLineTo(8f)
            curveTo(13f, 33f, 15.5f, 30f, 19.5f, 24.5f)
            lineTo(25f, 17f)
            curveTo(28f, 12.5f, 31f, 9f, 36.5f, 9f)
            horizontalLineTo(44f)
        }
        // Flèche du haut-gauche vers le bas-droite (interrompue au croisement)
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 4.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5.5f, 7.2f)
            horizontalLineTo(8f)
            curveTo(13f, 7.2f, 15.5f, 9.5f, 19.2f, 15.2f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 4.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(26.8f, 25.6f)
            curveTo(29.5f, 29.8f, 32f, 31.5f, 38f, 31.5f)
            horizontalLineTo(44f)
        }
        // Pointes des deux flèches
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 4.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(40.5f, 3.5f)
            lineTo(46f, 9f)
            lineTo(40.5f, 14.5f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 4.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(40.5f, 26f)
            lineTo(46f, 31.5f)
            lineTo(40.5f, 37f)
        }
    }.build()
}

/** Lecture en boucle : rectangle arrondi ouvert en bas à gauche, avec une flèche de retour. */
val LoopIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Loop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 320f,
        viewportHeight = 320f
    ).apply {
        // Contour : part du bas à gauche (coupe nette), fait le tour, revient vers la flèche
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 26f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(92f, 249f)
            horizontalLineTo(73f)
            arcTo(48f, 48f, 0f, false, true, 25f, 201f)
            verticalLineTo(80f)
            arcTo(48f, 48f, 0f, false, true, 73f, 32f)
            horizontalLineTo(248f)
            arcTo(48f, 48f, 0f, false, true, 296f, 80f)
            verticalLineTo(201f)
            arcTo(48f, 48f, 0f, false, true, 248f, 249f)
            horizontalLineTo(142f)
        }
        // Pointe de la flèche (vers la gauche)
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 26f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(189f, 202f)
            lineTo(142f, 249f)
            lineTo(189f, 296f)
        }
    }.build()
}
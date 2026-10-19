package com.mysound.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icône "déjà téléchargé" : un cercle ouvert avec une coche qui traverse l'ouverture.
 * La couleur ici n'a pas d'importance : elle est remplacée par le paramètre `tint` de l'Icon.
 */
val DownloadedIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "DownloadedCheck",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Cercle ouvert (ouverture en haut à droite)
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21.08f, 8.87f)
            arcTo(9.6f, 9.6f, 0f, true, true, 15.28f, 2.98f)
        }
        // Coche
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7.66f, 11.14f)
            lineTo(11.54f, 14.43f)
            lineTo(20.37f, 3.43f)
        }
    }.build()
}

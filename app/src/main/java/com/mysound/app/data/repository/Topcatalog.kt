package com.mysound.app.data.repository

// Un "Top" proposé sur l'accueil (une playlist YouTube Music)
data class TopPlaylist(
    val id: String,
    val name: String,
    val url: String
)

// La liste des tops. Le PREMIER est celui affiché/chargé au démarrage de l'accueil.
// Pour en ajouter un : une entrée ici suffit. Pour en retirer un : supprimer l'entrée,
// ses musiques et images devenues inutiles seront nettoyées à la prochaine synchronisation.
object TopCatalog {
    // Les tops changent rarement : à l'ouverture de l'appli, on ne les met à jour que si la
    // dernière vérification date de plus d'un jour (même délai pour les trois).
    const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000

    val all = listOf(
        TopPlaylist(
            id = "clips_tendance",
            name = "Les 20 clips tendance France",
            url = "https://music.youtube.com/playlist?list=OLAK5uy_mnRyLDuByhBA_r8-L9ugjllTxfVytwAp0"
        ),
        TopPlaylist(
            id = "top_quotidien",
            name = "Top quotidien France",
            url = "https://music.youtube.com/playlist?list=PL4fGSI1pDJn4jkpIghTDva5AhM3tkkwc9"
        ),
        TopPlaylist(
            id = "top_100",
            name = "Top 100 des clips musicaux France",
            url = "https://music.youtube.com/playlist?list=PL4fGSI1pDJn50iCQRUVmgUjOrCggCQ9nR"
        )
    )

    val default: TopPlaylist get() = all.first()
}
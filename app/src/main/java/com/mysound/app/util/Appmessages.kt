package com.mysound.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Affichage de messages à l'utilisateur depuis n'importe quel thread / n'importe quelle
 * couche (ViewModel, Repository...) + traduction des erreurs techniques en phrases claires.
 */
object AppMessages {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Affiche un message (Toast). Peut être appelé depuis un thread quelconque. */
    fun show(context: Context, message: String) {
        val appContext = context.applicationContext
        mainHandler.post {
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Transforme l'exception d'un téléchargement en message compréhensible.
     * On regarde le type ET le texte de toute la chaîne d'erreurs (yt-dlp renvoie
     * ses erreurs sous forme de texte brut).
     */
    fun downloadErrorMessage(title: String, error: Throwable): String {
        val details = generateSequence(error) { it.cause }
            .take(6)
            .joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }
            .lowercase()

        val reason = when {
            "no space left" in details || "enospc" in details ->
                "Plus assez d'espace de stockage sur le téléphone."

            "yt-dlp non initialisé" in details ->
                "Le moteur de téléchargement n'est pas prêt. Fermez puis rouvrez l'application."

            "confirm your age" in details || "age-restricted" in details || "age restricted" in details ->
                "Cette vidéo est soumise à une restriction d'âge."

            "not a bot" in details || "sign in to confirm" in details ->
                "YouTube demande une vérification anti-robot. Réessayez plus tard."

            "private video" in details || "video unavailable" in details ||
                    "has been removed" in details || "not available" in details ||
                    "copyright" in details ->
                "Cette vidéo n'est plus disponible."

            "http error 429" in details || "too many requests" in details ->
                "Trop de requêtes envoyées à YouTube. Patientez quelques minutes."

            "http error 403" in details || "forbidden" in details ->
                "YouTube a refusé l'accès (erreur 403). Réessayez plus tard."

            "unknownhost" in details || "name or service not known" in details ||
                    "temporary failure in name resolution" in details ||
                    "network is unreachable" in details || "unable to download webpage" in details ||
                    "timed out" in details || "timeout" in details ||
                    "connection reset" in details || "connection refused" in details ||
                    "failed to connect" in details ->
                "Connexion impossible. Vérifiez votre accès à internet."

            "exportexception" in details ->
                "La conversion audio a échoué."

            "securityexception" in details || "mediastore" in details ->
                "Impossible d'enregistrer le fichier dans le dossier Musique."

            else -> {
                val raw = error.message?.lineSequence()?.firstOrNull()?.take(80)
                if (raw.isNullOrBlank()) "Erreur inattendue." else "Erreur inattendue : $raw"
            }
        }

        val shortTitle = if (title.length > 40) title.take(37) + "…" else title
        return "Échec du téléchargement de « $shortTitle ».\n$reason"
    }
}
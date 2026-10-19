package com.mysound.app.util

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Envoie les idées / suggestions des utilisateurs par mail grâce à Web3Forms
 * (https://web3forms.com) : pas de serveur ni de base de données à gérer.
 *
 * ÉTAPES :
 *  1. Va sur https://web3forms.com, saisis TON adresse mail, tu reçois une "Access Key".
 *  2. Colle-la ci-dessous à la place de "VOTRE_CLE_ACCES_ICI".
 */
object FeedbackSender {

    // >>> COLLE TA CLÉ D'ACCÈS WEB3FORMS ICI <<<
    private const val ACCESS_KEY = "2dc9d183-2c17-4086-8f15-5ace258ab345"

    private const val ENDPOINT = "https://api.web3forms.com/submit"

    /** Vrai tant que la clé n'a pas été remplacée. */
    val isConfigured: Boolean
        get() = ACCESS_KEY.isNotBlank() && ACCESS_KEY != "VOTRE_CLE_ACCES_ICI"

    suspend fun send(message: String, appVersion: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(isConfigured) { "Clé d'accès Web3Forms non configurée" }

                // On ajoute la version de l'appli et le modèle du téléphone au mail :
                // très utile pour comprendre une suggestion ou un bug.
                val fullMessage = buildString {
                    append(message.trim())
                    append("\n\n---\n")
                    append("Version de l'appli : $appVersion\n")
                    append("Appareil : ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                }

                val body = JSONObject().apply {
                    put("access_key", ACCESS_KEY)
                    put("subject", "Suggestion MySound")
                    put("from_name", "Utilisateur MySound")
                    put("message", fullMessage)
                }.toString()

                val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }

                try {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                    val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                    val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val success = runCatching { JSONObject(response).optBoolean("success", false) }.getOrDefault(false)

                    if (!success) {
                        val reason = runCatching { JSONObject(response).optString("message") }.getOrNull()
                        error(reason?.takeIf { it.isNotBlank() } ?: "Erreur ${connection.responseCode}")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
}

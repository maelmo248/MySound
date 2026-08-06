package com.mysound.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mysound.app.MainActivity
import com.mysound.app.R

/**
 * Service qui héberge le vrai lecteur (ExoPlayer) et la MediaSession associée.
 *
 * C'est ce service — et non plus [AudioPlayerManager] directement — qui possède
 * l'ExoPlayer. Grâce à ça :
 *  - la lecture continue même si l'appli passe en fond ou si l'écran s'éteint
 *    (le service est "foreground", donc le système ne le tue pas) ;
 *  - Android affiche automatiquement une notification avec le titre/artiste,
 *    la pochette et les boutons play/pause/suivant/précédent ;
 *  - les mêmes contrôles apparaissent sur l'écran de verrouillage, à la place
 *    de l'heure (exactement comme Spotify).
 *
 * [AudioPlayerManager] ne fait plus que se connecter à ce service via un
 * MediaController et lui envoyer des commandes (play, pause, etc.).
 */
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Icône utilisée dans la notification (obligatoire, sinon Android
        // affiche un carré blanc par défaut).
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )

        // Configuration d'un User-Agent personnalisé pour que les serveurs de streaming
        // (comme Fun Radio, NRJ, RTL2) acceptent les connexions des flux audio.
        //
        // IMPORTANT : les flux des radios commerciales (Fun Radio, NRJ, RTL2...) passent
        // par des CDN (Arkena, Akamai, 6play...) qui répondent souvent par une redirection
        // HTTP 301/302, parfois en changeant de protocole (http <-> https) ou de domaine.
        // Par défaut, ExoPlayer REFUSE ces redirections "cross-protocol" et abandonne la
        // lecture sans rien afficher à l'écran. C'est très probablement pour ça que seule
        // Skyrock fonctionnait : c'est le seul flux ici qui pointe directement vers un
        // serveur Icecast sans redirection intermédiaire.
        // setAllowCrossProtocolRedirects(true) autorise ces redirections.
        // On augmente aussi les timeouts par défaut (8s) car certains de ces CDN sont
        // plus lents à répondre, en particulier en 4G.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        // IMPORTANT : DefaultHttpDataSource ne sait lire QUE des URLs http/https.
        // Or les musiques téléchargées localement sont lues via des URI "file://"
        // (voir SongEntity.toMediaItem dans AudioPlayerManager). En passant l'usine
        // HTTP directement à DefaultMediaSourceFactory, plus aucun fichier local ne
        // pouvait être ouvert : seule la radio (qui est en https) fonctionnait encore.
        // DefaultDataSource.Factory résout ça : il aiguille automatiquement vers
        // FileDataSource pour les fichiers locaux (file://, content://...) et vers
        // notre httpDataSourceFactory (avec User-Agent, redirections, timeouts) pour
        // tout ce qui est http/https, comme les radios.
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        // Permet de rouvrir l'appli en tapant sur la notification ou sur les
        // contrôles de l'écran de verrouillage.
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent)
            .build()
    }

    // Appelé quand un MediaController (notre AudioPlayerManager, ou le système
    // pour la notification / l'écran de verrouillage) veut se connecter.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // Si l'utilisateur retire l'appli des tâches récentes alors que rien ne
    // joue, on arrête le service au lieu de le laisser tourner pour rien.
    // S'il y a une musique en cours, on le laisse continuer (comportement
    // attendu d'un lecteur de musique en fond).
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
package com.tfg.pruebaseria.Exoplayer.callbacks

import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.tfg.pruebaseria.Exoplayer.FirebaseMusicResource

class MusicPlaybackPreparer(
    //Dos parámetros:
    //la clase que contiene la informacion para leer los datos de la base de datos de Firebase
    private val firebaseMusicResource: FirebaseMusicResource,
    // Lambda que solo será posible llamar cuando el player esté perparado,
    // así funcionará cuando la llamemos en la clase de MusicService
    private val playerPrepared : (MediaMetadataCompat?) -> Unit
) : MediaSessionConnector.PlaybackPreparer {

    override fun onCommand(
        player: Player,
        command: String,
        extras: Bundle?,
        cb: ResultReceiver?
    ) = false

    override fun getSupportedPrepareActions(): Long {
        return PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
    }

    override fun onPrepare(playWhenReady: Boolean) = Unit

    override fun onPrepareFromMediaId(mediaId: String, playWhenReady: Boolean, extras: Bundle?) {
        firebaseMusicResource.whenReady {
            //mediante el mediaId vamos a buscar las canciones que queremos rerpoducir
            val itemToPlay = firebaseMusicResource.songs.find { mediaId == it.description.mediaId }
            //cuando se haya elegido la cacncion el reproductor está preparado, por lo que ahora llamamos a la funcion lambda,
            // para que establezca que ese objeto esta preparado
            playerPrepared(itemToPlay)
        }
    }

    //esta funcion maneja la captura de voz de google
    override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) = Unit

    override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit

}
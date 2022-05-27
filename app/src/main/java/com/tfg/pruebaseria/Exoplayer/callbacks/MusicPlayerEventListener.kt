package com.tfg.pruebaseria.Exoplayer.callbacks

import android.widget.Toast
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.tfg.pruebaseria.Exoplayer.MusicService

class MusicPlayerEventListener(
    private val musicService : MusicService
) : Player.Listener {

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        super.onPlayerStateChanged(playWhenReady, playbackState)
        if (playbackState == Player.STATE_READY && !playWhenReady) {
            musicService.stopForeground(false)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        Toast.makeText(musicService, "Unknown error ocurred", Toast.LENGTH_LONG).show()
    }
}
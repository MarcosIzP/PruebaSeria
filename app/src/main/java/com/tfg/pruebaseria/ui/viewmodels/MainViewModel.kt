package com.tfg.pruebaseria.ui.viewmodels

import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tfg.pruebaseria.Exoplayer.MusicServiceConnection
import com.tfg.pruebaseria.Exoplayer.isPlayEnabled
import com.tfg.pruebaseria.Exoplayer.isPlaying
import com.tfg.pruebaseria.Exoplayer.isPrepared
import com.tfg.pruebaseria.data.entities.Song
import com.tfg.pruebaseria.otros.Constantes.MEDIA_ROOT_ID
import com.tfg.pruebaseria.otros.Resource


class MainViewModel @ViewModelInject constructor(
    private val musicServiceConnection : MusicServiceConnection
) : ViewModel() {

    lateinit var context: Context

    var provideMusicServiceConnection : MusicServiceConnection = MusicServiceConnection(
        context
    )

    private val _mediaItems = MutableLiveData<Resource<List<Song>>>()
    val mediaItems : LiveData<Resource<List<Song>>> = _mediaItems

    val isConnected = musicServiceConnection.isConnected
    val networkError = musicServiceConnection.networkError
    val curPlayingSong = musicServiceConnection.curPlayingSong
    val playbackState = musicServiceConnection.playbackState

    init {

        _mediaItems.postValue(Resource.loading(null))
        musicServiceConnection.subscribe(MEDIA_ROOT_ID, object : MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(
                parentId: String,
                children: MutableList<MediaBrowserCompat.MediaItem>
            ) {
                super.onChildrenLoaded(parentId, children)
                val items = children.map {
                    Song(
                        it.mediaId!!,
                        it.description.title.toString(),
                        it.description.subtitle.toString(),
                        it.description.mediaUri.toString(),
                        it.description.iconUri.toString()
                    )
                }
                _mediaItems.postValue(Resource.success(items))
            }
        })
    }

    fun skipToNextSong() {
        musicServiceConnection.transportControls.skipToNext()
    }

    fun skipToPreviousSong() {
        musicServiceConnection.transportControls.skipToPrevious()
    }

    fun seekTo(pos : Long) {
        musicServiceConnection.transportControls.seekTo(pos)
    }


    fun playOrToggleSong(mediaItem : Song, toggle : Boolean = false) {
        val  isPrepared = playbackState.value?.isPrepared ?: false
        if (isPrepared && mediaItem.media_id == curPlayingSong?.value?.getString(METADATA_KEY_MEDIA_ID)) {

            playbackState.value?.let { playbackState ->
                when {
                    playbackState.isPlaying -> if(toggle) musicServiceConnection.transportControls.pause()
                    playbackState.isPlayEnabled -> musicServiceConnection.transportControls.play()
                    else -> Unit
                }
            }
        } else {
            musicServiceConnection.transportControls.playFromMediaId(mediaItem.media_id, null)
        }
    }


    override fun onCleared() {
        super.onCleared()
        musicServiceConnection.unsubscribe(MEDIA_ROOT_ID, object : MediaBrowserCompat.SubscriptionCallback() {})

    }



}
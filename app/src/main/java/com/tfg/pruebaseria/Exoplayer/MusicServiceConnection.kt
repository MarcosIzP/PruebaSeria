package com.tfg.pruebaseria.Exoplayer

import android.content.ComponentName
import android.content.Context
import android.media.browse.MediaBrowser
import android.media.session.PlaybackState
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tfg.pruebaseria.otros.Constantes.NETWORK_ERROR
import com.tfg.pruebaseria.otros.Event
import com.tfg.pruebaseria.otros.Resource

class MusicServiceConnection(
    context: Context
) {

    //Contendrá datos en tiempo real sobre el estado de la conexion entre el servicio y la actividad
    //Será un objeto mutable de tipo evento, que a su vez sera un recurso, que ha su vez será un Booleano
    private val _isConnected = MutableLiveData<Event<Resource<Boolean>>>()
    //Live data que veremos desde otras clases/actividades/fragmentos
    val isConnected : LiveData<Event<Resource<Boolean>>> = _isConnected

    private val _networkError = MutableLiveData<Event<Resource<Boolean>>>()
    val networkError : LiveData<Event<Resource<Boolean>>> = _networkError

    //Objeto para el estado del playback, es decir, lo que nos informa de si el reproductor está pausado ...
    private val _playbackState = MutableLiveData<PlaybackStateCompat?>()
    val playbackState : LiveData<PlaybackStateCompat?> = _playbackState

    //Será un objeto de tipo MediaMetaData que contendrá metadatos sobre la cancion que se esté reproduciendo
    private val _curPlayingSong = MutableLiveData<MediaMetadataCompat?>()
    val curPlayingSong : LiveData<MediaMetadataCompat?> = _curPlayingSong

    //Objeto que utilizaremos para:
        //Proporcionará acceso a los controles de uso, es decir a parar o continuar una cancion, pasar una canciony demás
        //Y registrar algunas funciones de callback que serán muy útiles
    lateinit var mediaController : MediaControllerCompat

    private val mediaBrowserConnectionCallback = MediaBrowserConnectionCallback(context)

    //Objeto necesario para suscribirnos al ID, ya que nos da acceso a la clase de MusicService
    private val mediaBrowser = MediaBrowserCompat(
        //Parámetros:
        context,
        //un componente, que será el servicio
    ComponentName(
        context,
        MusicService::class.java
    ),
        //funcion callback que hemos creado
        mediaBrowserConnectionCallback,
        null
    ).apply { connect() }
    //mediante el apply ejecutamos la funcion sobrecargada de onConnected, para poder conectar

    //Acceso a los controles de uso
    val transportControls: MediaControllerCompat.TransportControls
        //La instanciamos con un get(), ya que si lo hacemos con =,
    // intentaría obtener de manera instantánea esos controles, los cuales no se han inicializado todavía por ser lateinit.
    // La razon por la que mediaController es lateinit, es porque necesita el token de la sesion del servicio de música, token que llamaremos mediante una función de callback
        get() = mediaController.transportControls

    //Funcion para suscribirse a un ID
    //Parámetros: El Id al que nos vamos a subsribir, y un callback que informa de cuando la suscripcion ha finalizado
    fun subscribe(parentId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
        mediaBrowser.subscribe(parentId, callback)
    }

    //para desusribirse
    fun unsubscribe(parentId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
        mediaBrowser.unsubscribe(parentId, callback)
    }

    private inner class MediaBrowserConnectionCallback(
        private val context: Context
        //Hereda de:
    ) : MediaBrowserCompat.ConnectionCallback() {

        //Cuando el servicio de conexión de la música (Clase que estamos creando) este lista, se ejecutará está función
        override fun onConnected() {
            //Aquí instanciaremos mediaController, porque si se ejecuta esta función significa que el servicio ha conectado y podemos obtener el token
            //Ya que tenemos que pasarle el token, de segundo parámetro le pssamos el objeto que hemos creado con acceso a la clase del servicio
            mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken).apply {
                registerCallback(MediaControllerCallback())
            }
            _isConnected.postValue(Event(Resource.success(true)))
        }

        override fun onConnectionSuspended() {
            _isConnected.postValue(Event(Resource.error(
                "The connection was suspended", false
            )))
        }

        override fun onConnectionFailed() {
            _isConnected.postValue(Event(Resource.error(
                "Couldn't connect to MediaBrowser", false
            )))
        }
    }

    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        //Cada vez que se cambia el estado del playback esta función se ejecuta,
        // por lo que lo podemos utilizar para cambiar el valor del livedata object _playbackState que contiene el valor en forma de Booleano del estado del Playback
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            //Declaramos en nuevo valor de la valor state, de esa manera podremos observar el estado desde el exterior
            _playbackState.postValue(state)
        }

        //Cada vez que el valor de los metadatos de una cancion cambien se ejecuta esta funcion
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            //Los mismo que en la anterior pero relacionado a los metadatos
            _curPlayingSong.postValue(metadata)
        }

        //Esta funcion la usaremos para que se nos notifique en caso de error
        override fun onSessionEvent(event: String?, extras: Bundle?) {
            super.onSessionEvent(event, extras)
            when (event) {
                NETWORK_ERROR -> _networkError.postValue(
                    Event(
                        Resource.error(
                            "Couldn't connect to the server. Pleade check your internet connection",
                            null
                        )
                    )
                )
            }
        }

        //si por alguna razón la sesion acaba ejecutamos la función que hemos sobrecargado
        override fun onSessionDestroyed() {
            mediaBrowserConnectionCallback.onConnectionSuspended()
        }
    }


}
package com.tfg.pruebaseria.Exoplayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.tfg.pruebaseria.Exoplayer.callbacks.MusicPlaybackPreparer
import com.tfg.pruebaseria.Exoplayer.callbacks.MusicPlayerEventListener
import com.tfg.pruebaseria.Exoplayer.callbacks.MusicPlayerNotificationListener
import com.tfg.pruebaseria.otros.Constantes.MEDIA_ROOT_ID
import com.tfg.pruebaseria.otros.Constantes.NETWORK_ERROR
import kotlinx.coroutines.*
import javax.inject.Inject

private const val SERVICE_TAG = "MusicService"

class MusicService : MediaBrowserServiceCompat() {

    lateinit var context: Context

    @Inject
    lateinit var firebaseMusicResource: FirebaseMusicResource

    //Creamos la variable que contendrá el proveedor de los datos
    var provideDataSourceFactory: DefaultDataSourceFactory = DefaultDataSourceFactory(
        context, Util.getUserAgent(context,"NowCast")
    )

    //Creamos las variables que nos permiitirán crear el reproductor
    //este será el manual con los metadatos del reproductor
    var provideAudioAttributes = com.google.android.exoplayer2.audio.AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()
    //La variable que recogerá los metadatos junto con el contexto y creará el reproductor
    var provideExoPlayer: SimpleExoPlayer = SimpleExoPlayer.Builder(context)
        .build().apply {
            setAudioAttributes(provideAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
        }

    //Objeto del tipo MusicNotificationManger, que es la clase que hemos creado nosotros mismos que controla el servicio de notificaion
    private lateinit var musicNotificationManager: MusicNotificationManager

    //para que la que caundo se quiera cargar una cacnión de la base de datos, está no afecte al funcionamiento de el hilo principal,
    // se crea una rutina, que funcionará fuera de la ejecucion principal de la app, es decir, se ejecutará de manera asíncrona
    private val serviceJob = Job()

    // serviceScope se encarga de buscar el servicio de música y todos los hilos que se hayan creado a parir de él, que se estén ejecutando de forma asíncrona
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob) //CorountineScope busca todos los hilos asíncronos

    //con las siguientes variables queremos obtener informacion de la sesion actual que se esé ejecutando de MediaSessionBrowser,
    // ya que más tarde necesitaremos esa información
    private lateinit var mediaSession : MediaSessionCompat //la que contendrá info
    private lateinit var mediaSessionConnector : MediaSessionConnector // la que permitirá la conexion para recoger esa info

    // en esta variable guardaremos si el servicio se encuentra en segundo plano o no. Por default estarça en false
    var isForegroundService = false

    private var curPlayingSong : MediaMetadataCompat? = null

    //Variable que utilizaremos para comprobar si el repro está inicializado. Por defecto, no está inicializado obviamente.
    private var isPlayerInitialized = false

    private lateinit var musicPlayerEventListener : MusicPlayerEventListener

    companion object {
        var curSongDuration = 0L
            private set //Al hacerlo privado significa que solo se podrá cambiar el valor de la variable dentro del servicio,
        // pero podremos leerlo desde fuera del servicio
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            //Funcion de la que obetenemos todos los metadatos de las canciones de la base de datos
            firebaseMusicResource.fetchMediaData()
        }

        // Ahora nos encargaremos de crear un intent para que cuando pulsemos en la notificacion de la app nos lleve a la página de la app de la canción
        val activityIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this,0, it,0)
        }

        //cada mediaSession viene con un token, algo que podemos utilizar para obtener informacion de la sesion.
        // Este token existe gracias a la clase que hemos asignado a esta actividad " MediaBrowserServiceCompat", que contiene la propiedad de "mediasessiontoken"
        mediaSession = MediaSessionCompat(this, SERVICE_TAG).apply {
            setSessionActivity(activityIntent)
            isActive = true
        }

        //ahora asignaremos ese token a nuestro servicio
        sessionToken = mediaSession.sessionToken

        //inicializacion de controlador de notificacion
        musicNotificationManager = MusicNotificationManager(
            this,
            mediaSession.sessionToken,
            //le pasasmos como parámetro la clase que hemos creado que controla lo que pasas si deslizamos la aplicacion
            MusicPlayerNotificationListener(this)
        ) {
            //Actualizaremos la duracion de la cancion que estaba antes cuando se cambia de cancion, para que esté mostrada la duracion de la cancion actual
            curSongDuration = provideExoPlayer.duration
        }

        //funcion de callback que se ejecutará cada vez que el usuario quiera ejecutar una nueva cancion
        val musicPlaybackPreparer = MusicPlaybackPreparer(firebaseMusicResource) {

            curPlayingSong = it
            //Llamada de la función que creamos más abajo, con los parámetros necesarios
            preparePlayer(
                //los metadatos
                firebaseMusicResource.songs,
                //representa el item que pulsa el usuario, es decir, la cancion que quiere reproducir
                it,
                //cuando el ususrario pulse pasará a ser true
                true
            )
        }

        mediaSessionConnector = MediaSessionConnector(mediaSession)//Le pasasmos la sesion del intent
        mediaSessionConnector.setPlaybackPreparer(musicPlaybackPreparer)

        mediaSessionConnector.setQueueNavigator(MusicQueueNavigator())

        mediaSessionConnector.setPlayer(provideExoPlayer) //Le proporcionamos un reproductor mediante la variable que hemos creado antes

        //le especificamos como Listener la clase que hemos creado ("MusicPlayerEventListener"),
        // que maneja en que estado se encuentra la cancion para que se pueda o no quitar la notificacion. Por eso se le pasa como parámetro el servicio de música
        musicPlayerEventListener = MusicPlayerEventListener(this)
        provideExoPlayer.addListener(musicPlayerEventListener)

        //debemos llamar a la funcion que creamos en la clase"MusicNotificationManager" para que se vea la notificación, pasándole como parámetro el reproductor
        musicNotificationManager.showNotification(provideExoPlayer)

    }

    //FUncion que permitirá que se comparta la información sobre la musica de el servicio de música hacia el servicio de notificacion
    //esta funcion hereda de Timelinequeuenavigator
    private inner class MusicQueueNavigator : TimelineQueueNavigator(mediaSession) {
        //ctrl + i
        //Esta funcion será llamada siempre que el servidor necesite la descripcion de un nuevo item, como una cancion,
        // por ejemplo, la cancion cambia y la notificacion tiene que mostrar el nombre e icono de la nueva cancion
        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
            return firebaseMusicResource.songs[windowIndex].description  //windowindex representa la posicion en el indice de la cancion que se esta reproduciendo
        }
    }

    //Funcion que prepara el Exoplayer
    private fun preparePlayer(
        //parámetros:
        //La lista de canciones en la que se encuentra
        songs : List<MediaMetadataCompat>,
        //La cancion actual que se quiere reproducir
        itemToPlay : MediaMetadataCompat?,
        //para marcar cuando esta preparado o no
        playNow : Boolean
    ) {
        //Si no se ha elegido concretamente ninguna cancion entonces se reproducirá la primera,
        // si no, entonces obtenemos el indice de la cancion  de "songs" y lo guardamos en la variable "curSongIndex"
        val curSongIndex = if(curPlayingSong == null) 0 else songs.indexOf(itemToPlay)
        //preparamos el reproductor, y le pasasaos la fuente de los datos
        provideExoPlayer.prepare(firebaseMusicResource.asMediaSource(provideDataSourceFactory))
        //indicamos que el segundo del que se empieza es 0
        provideExoPlayer.seekTo(curSongIndex, 0)
        //asignamos valor al booleano cuando el repoductor esté listo. El booleano pasará a true cuando el ususario pulse play, y será false cuando el usuario pulse pause
        provideExoPlayer.playWhenReady = playNow
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        provideExoPlayer.stop()
    }

    //Ahora nos aseguraremos de que todas las corutinas generadas por "ServiceScoped" (hilos generados a parte del principal) mueran cuando el servicio lo haga
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        provideExoPlayer.removeListener(musicPlayerEventListener)
        provideExoPlayer.release()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    //la funcion onLoadChildren es llamada pronto, antes de que se obtengan las canciones. por lo que querremos decirle a "result" que los items no están listos, pero que lo estarán
    //para que el cliente escuche una cancion o acceda a una playlist, deberá suscribirse a un id
    override fun onLoadChildren(
        //Id al que el cliente se suscribe
        parentId: String,
        //el resultado de esa suscripcion es una lista de mediaitems
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        when(parentId) {
            //comprobaremos si parentid eesigual al id que hemos creado nosotros
            MEDIA_ROOT_ID -> {
                //Si es igual
                val resultSent = firebaseMusicResource.whenReady { isInitialized ->
                    if (isInitialized) {
                        result.sendResult(firebaseMusicResource.asMediaItems())
                        //Ahora necesitamos perparar el reproductor, pero necesitamos una variable para saber si el reproductor ya ha sido inicializado, ya que si no lo manejamos eso podría causar que sonase la cancion nada más abrir la app
                        //si el repro no está incializado (false), o si la lista de canciones tiene al menos un item cargado
                        if (!isPlayerInitialized && firebaseMusicResource.songs.isNotEmpty()) {
                            //preparará el reproductor con la lista de canciones y cargará la que esté en primera posición.
                            // Tambíen le diremos que no empiece a reproducir automáticamente indicando "false" en "playNow"
                            preparePlayer(firebaseMusicResource.songs, firebaseMusicResource.songs[0], false)
                            isPlayerInitialized = true
                        } else {
                            //Si el reproductor está preparado pero no inicializado, le indicamos null por lo que no nos enviará ninguna canción
                            mediaSession.sendSessionEvent(NETWORK_ERROR, null)
                            result.sendResult(null)
                        }
                    }
                }
                //si los resultados no han sido enviados, llamamos a un método que volverá checkear más tarde si los resultados han sido enviados
                if (!resultSent) {
                    result.detach()
                }
            }
        }
    }
}


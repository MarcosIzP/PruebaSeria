package com.tfg.pruebaseria.Exoplayer

import android.content.Context
import android.media.MediaMetadata
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.core.net.toUri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.tfg.pruebaseria.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject


class FirebaseMusicResource @Inject constructor(
    private val musicDatabase: MusicDatabase
) {

    //Objeto que contendra metadatos sobre las canciones
    var songs = emptyList<MediaMetadataCompat>()

    //funcion que obtendra todos los objetos tipo "Song" de firebase.
    // COn dispatchers lo que hacemos es cambiar el hilo de ejecucion al hilo "IO" (internet operations), ya que está mejor optimizado para operaciones a través de la red
    suspend fun fetchMediaData() = withContext(Dispatchers.IO) {
        //Ahora queremos obteenr las canciones de firebase como lista.
        // Para ello hemos añadido como constructor la clase de MusicDatabase en la que hemos creado una lista con los campos necesarios

        state = State.STATE_INITIALIZING //Marcamos que el estado va a pasar a comienzo

        //Creamos una lista del tipo que hemos instanciado como constructor, y le pasamos el método "getAllSongs()"
        val allSongs = musicDatabase.getAllSongs()

        //Ahora necesitaremos utilizar la funcion de .map para poder ajustar los objetos que tienen los metadatos y nos ayudan a crear la lista, que son la clase "Song" y el objeto "MediaMetadataCompat"
        songs = allSongs.map { song -> //esto significa que iteremos por cada cancion que existe y loe pasaremos estos parámetros
            MediaMetadataCompat.Builder()
                //NOMBRE DEL ARTISTA
                .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artista)
                //ID DE LA CANCION
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, song.media_id)
                //NOMBRE DE LA CANCION
                .putString(MediaMetadata.METADATA_KEY_TITLE, song.nombre)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, song.nombre)
                //ALBUM, IMAGEN DEL ALBUM
                .putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, song.imageURL)
                //GENERO
                .putString(MediaMetadata.METADATA_KEY_GENRE, song.genero)
                //ICONO DE LA NOTIFICACCION
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, song.imageURL)
                //CANCION
                .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, song.songURL)
                //NOMBRE DEL ARTISTA, Y NOMBRE DEL ARTISTA APARECIENDO EN PANTALLA
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, song.artista)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, song.artista)
                .build()

        }
        //Una vez se hayan recogido los datos cambiamos el estado
        state = State.STATE_INITIALIZED
    }

    lateinit var context: Context

    //Creamos la variable que contendrá el proveedor de los datos
    var provideDataSourceFactory: DefaultDataSourceFactory = DefaultDataSourceFactory(
        context, Util.getUserAgent(context,"NowCast")
    )


    //La siguiente funcion permitirá el hecho de que las canciones se reproduzcan una detras de otra como una playlist,
    // para ello  necesitamos pasar un parámetro a la función para que permita la concatenación
    fun asMediaSource(dataSourceFactory: DefaultDataSourceFactory) : ConcatenatingMediaSource {
        val concatenatingMediaSource = ConcatenatingMediaSource()
        songs.forEach { song -> //Declarar aquí el objeto songs servirá para pasasr por todas las canciones que se recogan

            //Ahora iremos creando objetos, con el origen de cada cancion, para cada cancion, y luego añdirlas al objeto que se encarga de concatenarlas
            val mediaSource = ProgressiveMediaSource.Factory(provideDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(song.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).toUri()))
            //Gracias a este método se concatenan las canciones que recogamos con "mediaSource"
            concatenatingMediaSource.addMediaSource(mediaSource)

        }
        return  concatenatingMediaSource
    }

    //En la siguiente funcion es donde se podrá administrar que al pulsar en la interfaz, suene una cancion,
    // pero tambíen se puede manejar que se abra una playlist o un album
    fun asMediaItems() = songs.map { song ->

        val desc = MediaDescriptionCompat.Builder()
            .setMediaUri(song.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).toUri())
            .setTitle(song.description.title)
            .setSubtitle(song.description.subtitle)
            .setMediaId(song.description.mediaId)
            .setIconUri(song.description.iconUri)
            .build()
        //Aquí sería donde se manejaria si ese acceso es a una cancion o a una lista de cancione como una playlist, etc
        MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }.toMutableList() //se transforma de una lista normal a una lista mutable, porque la funcion que vamos utilizar para manejar que pasas cuando se pulsa un mediaitem, necesita una lista Mutable


    // Objeto que contendrá una lista de funciones lambda que nos permitirá establecer estados al servicio de descarga de datos
    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()

    //Objeto que se encargara de checkear en que estado se encuentra el servicio de musica de lectura de datos
    private var state : State = State.STATE_CREATED //Se le asignara este estado cuando para cuando el servicio de musica empieze a
        set(value) {
            // Comprobaremos el estado del servicio de descarga de datos,
            // A la vez sabremos que el servicio ya ha terminado, ya que los dos son estados finales
            if (value == State.STATE_INITIALIZED || value == State.STATE_ERROR){
                synchronized(onReadyListeners) {

                    //Al utilizar syn lo que paso dentro de este bloque, solo se podrá acceder desde el mismo hilo y ningún otro hilo podrá acceder a este bloque a la vez
                    //"field" se refiere al actual estado del hilo, y "value" es el nuevo valor que adquirirá el estado,
                    // de esa manera asignaremos el nuevo valor del estado a esa variable de estado ("state")
                    field = value

                    //Ahora asiganermos valores booleanos al servicio mediante la lista de funciones lambda, es decir,
                    // si el estado es "INITIALIZED" el valor será "true", si no será false
                    onReadyListeners.forEach { listener ->
                        listener(state == State.STATE_INITIALIZED)
                    }
                }
            } else {
                //Ya que solo existen dos posibles estados si el estado no es el INITIALIZED, será el de ERROR, y ese estado se asignará aquí
                field = value
            }
        }

    //Esta funcion tambien tomará como parámetro una funcion lambda.
    // Action indica la accion que queremos realizar cuando el servicio esté listo. esta funcion devolverá un booleano, en cualquiera de los dos casos.
    fun whenReady(action: (Boolean) -> Unit): Boolean {
        //Solo queremos que el servicio realice el action cuando el estado esté preparado y no en curso,
        // por lo que el primer valor será false
        if (state == State.STATE_CREATED || state == State.STATE_INITIALIZING) {

            onReadyListeners += action
            return false
        } else {
            //si se da este caso es porque el estado es o finalizado correctamente o de finalizado por error,
            // así que le diremos que realice la accion en caso de que sea estado "INITIALIZED", y si se da el caso de que es error simplemente realizará la accion devolviendo un false
            action(state == State.STATE_INITIALIZED)
            return true
            //al devolver, significará que el servicio está listo
        }
    }

    //Se definiran los estados en los que la música puede ser leída
    enum class State{
        STATE_CREATED, //estado para cuando se empiecen a enviar peticiones a la base de datos
        STATE_INITIALIZING, // estado para cuando se empiecen a descargar las canciones
        STATE_INITIALIZED, // estado para cuando ya se hayan descargado
        STATE_ERROR, //estado por si ocurre un error
    }
}
package com.tfg.pruebaseria.Exoplayer

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.tfg.pruebaseria.R
import com.tfg.pruebaseria.otros.Constantes.NOTIFICATION_CHANNEL_ID
import com.tfg.pruebaseria.otros.Constantes.NOTIFICATION_ID

class MusicNotificationManager(
    //Esta clase cogerá el contexto
    private val context : Context,

    // el token que identifica al servicio de musica
    sessionToken : MediaSessionCompat.Token,

    // un listener que contien funciones relacionadas con las posibilidades de la notificación
    notificationListener : PlayerNotificationManager.NotificationListener,

    //Funcion que se encargará de establecer el nuevo tiempo de duracion de la cancion en la notificacion
    private val newSongCallback: () -> Unit
) {

    //En la clase se creará un manejador de notificaciones. La clase nos la proporciona exoplayer
    private val notificationManager: PlayerNotificationManager

    //Al inciarlizarla, para pasasrle parámetros, no hace mucho se utilizaba este método "createWithNotificationChannel()", pero actualmente Exoplayer lo ha eliminado de sus librerías por lo que hay que utilizar "Builder()"
    init {
        val mediaController = MediaControllerCompat(context, sessionToken)

        notificationManager = PlayerNotificationManager.Builder(
            context,
            //El ID de la notificación y del canal de notificacion. Son contantes creadas en el objeto de "Constants", y que importamos aquí
            NOTIFICATION_ID, NOTIFICATION_CHANNEL_ID )
            //Nombre que tendrá el canal
            .setChannelNameResourceId(R.string.notification_channel_name)
            //Descripcion del canal, utilizando recursos que se crearán en string.xml
            .setChannelDescriptionResourceId(R.string.notification_channel_description)
            //Funcion que hemos creado para el adaptador de descripcion
            .setMediaDescriptionAdapter(DescriptionAdapter(mediaController))
            //El listener con las funciones
            .setNotificationListener(notificationListener)
            .build()
            .apply {
                //aplicamos el icono que tendrá la not
                setSmallIcon(R.drawable.ic_music)
                //aplicamos el token de la sesion dle servicio,
                // para que así el manejador de notificaciones pueda acceder a el token actual y ver los cambios que van ocurriendo
                setMediaSessionToken(sessionToken)
            }

    }

    //funcion para que se pueda ver la notificacion como una notificacion de app de musica
    fun showNotification(player: Player) {
        notificationManager.setPlayer(player)
    }

    private inner class DescriptionAdapter (
        private val mediaController : MediaControllerCompat
    ) :PlayerNotificationManager.MediaDescriptionAdapter {
        //ctrl + i

        override fun getCurrentContentTitle(player: Player): CharSequence {
            //Aquí se recogerá el nombre de la cacnion actual.
            // Lo podemos hacer gracias a que el parámetro que le hemos pasado se trata de un objeto que contiene el token de la sesion actual
            return mediaController.metadata.description.title.toString()
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            //Tendremos que declarar el intent que nos llevará a la pantalla de la aplicacion que queremos
            return mediaController.sessionActivity
        }

        override fun getCurrentContentText(player: Player): CharSequence? {
            return mediaController.metadata.description.subtitle.toString()
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            //como en la metadata no guardamos datos de bitmap,
            // y Exoplayer necesita este tipo para poder mostrar una imagen en la notif, cargaremos la imagen mediante "glide"
            Glide.with(context).asBitmap()
                .load(mediaController.metadata.description.iconUri)
                .into(object : CustomTarget<Bitmap>() {
                    //ctrl + i, para que se llamend dos funciones que se ejecutarán cuando la imagen esté cargada
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        //podemos llamar a la funcion de callback para que no se tome tiempo innecesrio cargando
                        callback.onBitmap(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) = Unit
                })


            return null
        }
    }
}
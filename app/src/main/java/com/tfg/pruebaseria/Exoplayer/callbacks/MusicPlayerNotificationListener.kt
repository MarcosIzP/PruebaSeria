package com.tfg.pruebaseria.Exoplayer.callbacks

import android.app.Notification
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.tfg.pruebaseria.Exoplayer.MusicService
import com.tfg.pruebaseria.otros.Constantes.NOTIFICATION_ID

class MusicPlayerNotificationListener(

    private val musicService: MusicService
    //hereda de la clase:
) : PlayerNotificationManager.NotificationListener {

    //ctrl + o
    //Aquí controlaremos que cuando el ususario deslice la notificacion porque, por ejmeplo, si se da el caso de que quiere quitarla, configuraremos que si la música está sonando, la notificacion no se puede ir,
    // y si la musica esta pausada se pueda quietar la notif y por lo tanto la aplicacion también se cerrara

    //si está pausada, se parará el servicio
    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
        super.onNotificationCancelled(notificationId, dismissedByUser)
        musicService.apply {
            stopForeground(true)
            isForegroundService = false
            stopSelf()
        }

    }

    override fun onNotificationPosted(
        notificationId: Int,
        notification: Notification,
        ongoing: Boolean
    ) {
        super.onNotificationPosted(notificationId, notification, ongoing)
        musicService.apply {
            //si el servicio está activo, y la variable que contiene el estado del servicio de segundo plano es distinta de su valor actual, que si recordamos puesimo como default "false"
            if (ongoing && !isForegroundService) {

                ContextCompat.startForegroundService(
                    this,
                    Intent(applicationContext, this::class.java)
                )
                startForeground(NOTIFICATION_ID, notification)
                isForegroundService = true

            }
        }
    }
}
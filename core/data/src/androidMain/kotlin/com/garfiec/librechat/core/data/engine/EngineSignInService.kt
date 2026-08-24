package com.garfiec.librechat.core.data.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger

/**
 * Le service qui garde l'application exécutable pendant qu'elle attend le portail.
 *
 * Il ne fait **rien**. C'est délibéré : tout le travail est dans [EngineSignInCoordinator], sur la
 * portée applicative. Ce service n'existe que pour un effet de bord du système — une application qui
 * en héberge un au premier plan n'est pas mise en cache, donc pas gelée, donc son `accept()` en
 * attente s'exécute quand la redirection arrive.
 *
 * Sans lui, le 24/08 : le navigateur a atteint le socket, le noyau a accepté la connexion, et
 * personne n'est venu la lire. « Connection timed out » côté navigateur, et pas la moindre trace
 * côté application — il n'y avait rien à tracer, elle ne tournait pas.
 *
 * ## Le type déclaré, et pourquoi celui-là
 *
 * `specialUse` plutôt que `shortService` : ce dernier est plafonné à environ trois minutes, et
 * l'attente ici en autorise cinq — un second facteur se lit parfois sur un autre appareil, et
 * couper à trois minutes rendrait la faute à la personne qui a pris son temps.
 */
class EngineSignInService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { demarrerAuPremierPlan() }
            .onFailure { echec ->
                // Un refus du système ne doit pas emporter la connexion : le tour continue sur la
                // portée applicative, seulement plus exposé au gel.
                Logger.i("Engine", echec) { "Le service au premier plan a été refusé" }
                stopSelf(startId)
            }
        // NOT_STICKY : rien à reprendre si le système nous tue. Le tour serait perdu de toute façon,
        // et un service ressuscité sans lui n'afficherait qu'une notification orpheline.
        return START_NOT_STICKY
    }

    private fun demarrerAuPremierPlan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val gestionnaire = getSystemService(NotificationManager::class.java)
            gestionnaire?.createNotificationChannel(
                NotificationChannel(CANAL, "Connexion au moteur", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Visible le temps d'une connexion au portail." },
            )
        }

        val notification: Notification = NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Connexion au portail")
            .setContentText("Terminez la connexion dans votre navigateur.")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, notification)
        }
    }

    private companion object {
        const val CANAL = "engine_sign_in"
        const val ID = 4207
    }
}

package cz.multio.doklady

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import cz.multio.doklady.data.PushSetup
import cz.multio.doklady.platform.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Upozornění z portálu (Firebase Cloud Messaging, 2.0). Když je aplikace zavřená, upozornění ukáže sám systém
 * (kanál „doklady“ z manifestu); když je otevřená, ukážeme ho tady.
 */
class PushService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        KeyValueStore.put("fcm_token", token)
        scope.launch { PushSetup.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        show(applicationContext, n.title ?: "Doklady", n.body ?: "", message.data["url"])
    }

    companion object {
        const val CHANNEL = "doklady"

        fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Upozornění od účetní", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Úkoly, chybějící doklady, zprávy, termíny a přiznání"
                    })
                }
            }
        }

        fun show(ctx: Context, title: String, body: String, url: String?) {
            createChannel(ctx)
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                url?.let { putExtra("open_url", it) }
            }
            val pi = PendingIntent.getActivity(ctx, title.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF1D6B4F.toInt())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), notification) }
        }
    }
}

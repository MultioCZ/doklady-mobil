package cz.multio.doklady

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cz.multio.doklady.platform.AndroidPlatform
import cz.multio.doklady.platform.IncomingLink
import cz.multio.doklady.platform.IncomingShare
import cz.multio.doklady.platform.readPicked

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidPlatform.init(applicationContext)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Fotka nebo PDF nasdílené z jiné aplikace (Gmail, galerie…). */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { IncomingLink.handle(it) }
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri?.let { readPicked(this, it) }?.let { IncomingShare.pending = it }
    }
}

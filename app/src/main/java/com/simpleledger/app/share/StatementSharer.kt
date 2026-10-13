package com.simpleledger.app.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.simpleledger.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Saves statement pages as PNG files and hands them to WhatsApp or the share sheet. */
object StatementSharer {
    private const val TAG = "SimpleLedger"
    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    suspend fun save(context: Context, pages: List<Bitmap>, baseName: String): List<Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safe = baseName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "statement" }
        pages.mapIndexed { i, bmp ->
            val suffix = if (pages.size > 1) "-${i + 1}" else ""
            val file = File(dir, "statement-$safe$suffix.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    private fun sendIntent(uris: List<Uri>, caption: String): Intent {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        // ClipData carries the read permission to the receiving app for every image.
        val clip = ClipData.newRawUri(null, uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return intent
            .setType("image/png")
            .putExtra(Intent.EXTRA_TEXT, caption)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = clip }
    }

    sealed interface Result {
        data object Opened : Result
        data object NotInstalled : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * WhatsApp's chat id for an Indian mobile number: "98765 43210" or "+91 98765 43210" → "919876543210@s.whatsapp.net".
     * Returns null when the number does not look like one.
     */
    fun whatsAppId(phone: String?): String? {
        val digits = phone?.filter { it.isDigit() } ?: return null
        val national = when {
            digits.length == 10 -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91${digits.drop(1)}"
            digits.length == 12 && digits.startsWith("91") -> digits
            else -> return null
        }
        return "$national@s.whatsapp.net"
    }

    /**
     * Opens WhatsApp (or WhatsApp Business). With [phone], it goes straight to that person's chat
     * using WhatsApp's "jid" extra (widely used, but not an official API: if WhatsApp ignores it,
     * its own contact list opens instead).
     */
    fun sendToWhatsApp(context: Context, uris: List<Uri>, caption: String, phone: String? = null): Result {
        val jid = whatsAppId(phone)
        for (pkg in WHATSAPP_PACKAGES) {
            try {
                val intent = sendIntent(uris, caption).setPackage(pkg)
                // Name WhatsApp's exact share screen. Asking for the package alone fails silently on
                // Android 16 Samsung phones with Dual Messenger (two WhatsApps): Samsung's automatic
                // "which WhatsApp" step is blocked by Android's intent-redirect protection. Naming the
                // screen makes Samsung show its picker instead, and with one WhatsApp it opens directly.
                val target = context.packageManager.queryIntentActivities(intent, 0).firstOrNull()?.activityInfo
                    ?: continue
                intent.component = ComponentName(target.packageName, target.name)
                if (jid != null) intent.putExtra("jid", jid)
                context.startActivity(intent)
                return Result.Opened
            } catch (e: ActivityNotFoundException) {
                // Try the next WhatsApp variant.
            } catch (e: Exception) {
                Log.w(TAG, "Could not open $pkg", e)
                return Result.Failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
            }
        }
        return Result.NotInstalled
    }

    fun sendAnyWay(context: Context, uris: List<Uri>, caption: String) {
        context.startActivity(Intent.createChooser(sendIntent(uris, caption), context.getString(R.string.share_title)))
    }
}

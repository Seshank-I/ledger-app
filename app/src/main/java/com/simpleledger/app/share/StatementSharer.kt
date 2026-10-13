package com.simpleledger.app.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.simpleledger.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Saves statement pages as PNG files and hands them to WhatsApp or the share sheet. */
object StatementSharer {
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

    /** Opens WhatsApp (or WhatsApp Business) directly. Returns false if neither is installed. */
    fun sendToWhatsApp(context: Context, uris: List<Uri>, caption: String): Boolean {
        for (pkg in WHATSAPP_PACKAGES) {
            try {
                context.startActivity(sendIntent(uris, caption).setPackage(pkg))
                return true
            } catch (e: ActivityNotFoundException) {
                // Try the next WhatsApp variant.
            }
        }
        return false
    }

    fun sendAnyWay(context: Context, uris: List<Uri>, caption: String) {
        context.startActivity(Intent.createChooser(sendIntent(uris, caption), context.getString(R.string.share_title)))
    }
}

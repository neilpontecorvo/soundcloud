package com.neilpontecorvo.soundcloudfiretv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.neilpontecorvo.soundcloudfiretv.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.max

object TvArtworkLoader {
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requests = WeakHashMap<ImageView, Future<*>>()
    private val memoryCache = object : LruCache<String, Bitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(
        context: Context,
        url: String?,
        target: ImageView,
        requestedWidth: Int,
        requestedHeight: Int
    ) {
        synchronized(requests) { requests.remove(target)?.cancel(true) }
        target.tag = url
        target.setImageDrawable(null)
        target.setBackgroundResource(R.drawable.artwork_placeholder)
        val normalized = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return
        val cacheKey = "$normalized@$requestedWidth:$requestedHeight"
        memoryCache.get(cacheKey)?.let { bitmap ->
            target.setImageBitmap(bitmap)
            return
        }

        val future = executor.submit {
            val bitmap = loadBitmap(context, normalized, requestedWidth, requestedHeight) ?: return@submit
            memoryCache.put(cacheKey, bitmap)
            mainHandler.post {
                if (target.tag == normalized || target.tag == url) target.setImageBitmap(bitmap)
            }
        }
        synchronized(requests) { requests[target] = future }
    }

    fun loadBitmap(
        context: Context,
        url: String?,
        requestedWidth: Int,
        requestedHeight: Int,
        callback: (Bitmap?) -> Unit
    ) {
        val normalized = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        if (normalized == null) {
            callback(null)
            return
        }
        val cacheKey = "$normalized@$requestedWidth:$requestedHeight"
        memoryCache.get(cacheKey)?.let {
            callback(it)
            return
        }
        executor.execute {
            val bitmap = loadBitmap(context, normalized, requestedWidth, requestedHeight)
            if (bitmap != null) memoryCache.put(cacheKey, bitmap)
            mainHandler.post { callback(bitmap) }
        }
    }

    private fun loadBitmap(
        context: Context,
        url: String,
        requestedWidth: Int,
        requestedHeight: Int
    ): Bitmap? {
        val cacheDirectory = File(context.cacheDir, "tv-artwork").apply { mkdirs() }
        val cachedFile = File(cacheDirectory, sha256(url))
        if (!cachedFile.isFile || cachedFile.length() == 0L) {
            val temporary = File(cacheDirectory, "${cachedFile.name}.tmp")
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 6_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "image/*")
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output, 16 * 1024) }
                }
                if (temporary.length() in 1..MAX_CACHE_FILE_BYTES) {
                    temporary.renameTo(cachedFile)
                } else {
                    temporary.delete()
                }
            }.onFailure { temporary.delete() }
        }
        if (!cachedFile.isFile) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cachedFile.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, requestedWidth, requestedHeight)
        }
        return BitmapFactory.decodeFile(cachedFile.absolutePath, options)
    }

    private fun sampleSize(width: Int, height: Int, requestedWidth: Int, requestedHeight: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val targetWidth = max(1, requestedWidth)
        val targetHeight = max(1, requestedHeight)
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private const val MAX_CACHE_FILE_BYTES = 12L * 1024L * 1024L
}

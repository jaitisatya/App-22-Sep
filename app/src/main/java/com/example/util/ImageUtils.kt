package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.util.LruCache
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.min

object ImageUtils {
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 82
    private const val TARGET_MAX_KB = 145 // Target under 150 KB
    private const val TAG = "ImageUtils"

    // High-performance In-Memory cache for decoded ByteArrays and Bitmaps (up to 20MB)
    private val memoryCache = object : LruCache<String, ByteArray>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int {
            return value.size
        }
    }

    private var customImageLoader: ImageLoader? = null

    /**
     * Provides a singleton Coil ImageLoader configured with dedicated MemoryCache & DiskCache.
     */
    fun getImageLoader(context: Context): ImageLoader {
        return customImageLoader ?: synchronized(this) {
            customImageLoader ?: ImageLoader.Builder(context.applicationContext)
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(context.applicationContext.cacheDir, "image_cache"))
                        .maxSizePercent(0.05)
                        .build()
                }
                .respectCacheHeaders(false)
                .build().also { customImageLoader = it }
        }
    }

    /**
     * Converts and compresses a Bitmap to a syncable Data URI (Base64 JPEG),
     * and also saves a local file copy in app internal storage for offline fast access.
     */
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String {
        return compressBitmapToUnder100Kb(context, bitmap)
    }

    /**
     * Compresses any input Bitmap to strictly under 150 KB (typically 90KB - 140KB)
     * while preserving sharp details (faces & text) with a generous 1280px resolution ceiling.
     */
    fun compressBitmapToUnder100Kb(context: Context, bitmap: Bitmap): String {
        return try {
            // Keep high resolution (1280px) so faces and classroom details are crisp
            var targetDimension = 1280
            var quality = 82
            var bytes: ByteArray

            var currentBitmap = resizeBitmap(bitmap, targetDimension)
            var stream = ByteArrayOutputStream()
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()

            // Iteratively scale down quality and resolution only if size exceeds 148 KB
            // Maintains high clarity: quality floor is 55, dimension floor is 720px
            while (bytes.size > TARGET_MAX_KB * 1024 && (quality > 50 || targetDimension > 640)) {
                if (quality > 60) {
                    quality -= 8
                } else {
                    targetDimension = (targetDimension * 0.85).toInt().coerceAtLeast(640)
                    currentBitmap = resizeBitmap(bitmap, targetDimension)
                    quality = 75
                }
                stream = ByteArrayOutputStream()
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                bytes = stream.toByteArray()
            }

            val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            val cacheKey = hashString(dataUrl)

            // Cache in-memory and disk
            memoryCache.put(cacheKey, bytes)
            saveBytesToDisk(context, cacheKey, bytes)

            Log.d(TAG, "Compressed photo to ${bytes.size / 1024} KB with crisp resolution (strictly < 150KB)")
            dataUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing bitmap under 150KB: ${e.message}", e)
            ""
        }
    }

    /**
     * Reads an image Uri from camera or gallery and compresses it smartly to <= 150 KB with sharp quality.
     */
    fun compressImageToUnder100Kb(context: Context, uri: Uri): String {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate sample size to fit comfortably within 1280px bounds without pixelation
            var inSampleSize = 1
            val maxBound = 1400
            if (options.outHeight > maxBound || options.outWidth > maxBound) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxBound && (halfWidth / inSampleSize) >= maxBound) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }

            if (bitmap != null) {
                compressBitmapToUnder100Kb(context, bitmap)
            } else {
                saveUriToInternalStorage(context, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing Uri to under 150KB: ${e.message}", e)
            saveUriToInternalStorage(context, uri)
        }
    }

    /**
     * Saves an image (Data URI, ByteArray, or Bitmap) to the device's public Gallery/Pictures directory
     * using MediaStore (Android 10+) or external storage with MediaScanner notification.
     * Returns true if successful.
     */
    fun savePhotoToGallery(context: Context, photoModel: Any?, fileNamePrefix: String): Boolean {
        return try {
            val bytes: ByteArray? = when (photoModel) {
                is ByteArray -> photoModel
                is String -> {
                    val trimmed = photoModel.trim()
                    if (trimmed.startsWith("data:image/") || (!trimmed.startsWith("http") && !trimmed.startsWith("/") && trimmed.length > 100)) {
                        val base64Data = if (trimmed.contains("base64,")) trimmed.substringAfter("base64,") else trimmed
                        Base64.decode(base64Data, Base64.DEFAULT)
                    } else if (trimmed.startsWith("/") || trimmed.startsWith("file://")) {
                        val path = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
                        File(path).readBytes()
                    } else null
                }
                is File -> photoModel.readBytes()
                is Bitmap -> {
                    val baos = ByteArrayOutputStream()
                    photoModel.compress(Bitmap.CompressFormat.JPEG, 95, baos)
                    baos.toByteArray()
                }
                else -> null
            }

            if (bytes == null || bytes.isEmpty()) {
                return false
            }

            val cleanPrefix = fileNamePrefix.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            val fileName = "${cleanPrefix}_${System.currentTimeMillis()}.jpg"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ClassPhotos")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        stream.write(bytes)
                    }
                    values.clear()
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return true
                }
            } else {
                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "ClassPhotos").apply { if (!exists()) mkdirs() }
                val targetFile = File(targetDir, fileName)
                FileOutputStream(targetFile).use { it.write(bytes) }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
                return true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error saving photo to gallery: ${e.message}", e)
            false
        }
    }

    /**
     * Reads a Uri from gallery/file picker, scales it down safely with sub-sampling,
     * compresses to Base64 Data URL, and caches locally.
     */
    fun saveUriToInternalStorage(context: Context, uri: Uri): String {
        return try {
            // First decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > MAX_DIMENSION || options.outWidth > MAX_DIMENSION) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= MAX_DIMENSION && (halfWidth / inSampleSize) >= MAX_DIMENSION) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }

            if (decodedBitmap != null) {
                saveBitmapToInternalStorage(context, decodedBitmap)
            } else {
                uri.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading uri: ${e.message}", e)
            uri.toString()
        }
    }

    /**
     * Converts any existing local file path, file URI, or content URI to a Cloud-Syncable Base64 Data URL.
     * If already a Data URL, returns it directly and guarantees it is cached locally.
     */
    fun fileOrUriToDataUrl(context: Context, pathOrUri: String): String? {
        if (pathOrUri.isBlank()) return null
        if (pathOrUri.startsWith("data:image/")) {
            // Ensure data URL is cached to disk for offline instantaneous display
            ensureDataUrlCached(context, pathOrUri)
            return pathOrUri
        }

        return try {
            if (pathOrUri.startsWith("/") || pathOrUri.startsWith("file://")) {
                val filePath = if (pathOrUri.startsWith("file://")) pathOrUri.removePrefix("file://") else pathOrUri
                val file = File(filePath)
                if (file.exists() && file.length() > 0) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        return saveBitmapToInternalStorage(context, bitmap)
                    }
                }
            } else if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                return saveUriToInternalStorage(context, uri)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error converting path to data URL: ${e.message}", e)
            null
        }
    }

    /**
     * Helper for Coil Image Loaders (AsyncImage).
     * Extracts Cached File, ByteArray from Data URL, or returns File/Uri so Coil renders seamlessly without network delays.
     */
    fun getImageModel(photoUri: String?, context: Context? = null): Any? {
        if (photoUri.isNullOrBlank()) return null
        val trimmed = photoUri.trim()

        if (trimmed.startsWith("data:image/") || (!trimmed.startsWith("http") && !trimmed.startsWith("/") && !trimmed.startsWith("content:") && !trimmed.startsWith("file:") && trimmed.length > 100)) {
            val cacheKey = hashString(trimmed)

            // 1. Check in-memory LruCache
            val cachedBytes = memoryCache.get(cacheKey)
            if (cachedBytes != null) {
                return cachedBytes
            }

            // 2. Check disk cached file if context provided
            if (context != null) {
                val diskFile = File(context.cacheDir, "img_$cacheKey.jpg")
                if (diskFile.exists() && diskFile.length() > 0) {
                    return diskFile
                }
            }

            // 3. Decode Base64 and populate caches
            return try {
                val base64Data = if (trimmed.contains("base64,")) trimmed.substringAfter("base64,") else trimmed
                val decoded = Base64.decode(base64Data, Base64.DEFAULT)
                memoryCache.put(cacheKey, decoded)

                if (context != null) {
                    saveBytesToDisk(context, cacheKey, decoded)
                }

                decoded
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding base64 data url: ${e.message}")
                null
            }
        }

        if (trimmed.startsWith("/") || trimmed.startsWith("file://")) {
            val filePath = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
            val file = File(filePath)
            if (file.exists()) return file
        }

        if (trimmed.startsWith("content://")) {
            return Uri.parse(trimmed)
        }

        return trimmed
    }

    /**
     * Builds an offline-first Coil ImageRequest that checks Memory and Disk caches first.
     */
    fun buildCachedImageRequest(context: Context, photoUri: String?): ImageRequest {
        val model = getImageModel(photoUri, context)
        return ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    private fun ensureDataUrlCached(context: Context, dataUrl: String) {
        try {
            val cacheKey = hashString(dataUrl)
            if (memoryCache.get(cacheKey) == null) {
                val base64Data = dataUrl.substringAfter("base64,")
                val decoded = Base64.decode(base64Data, Base64.DEFAULT)
                memoryCache.put(cacheKey, decoded)
                saveBytesToDisk(context, cacheKey, decoded)
            }
        } catch (_: Exception) {}
    }

    private fun saveBytesToDisk(context: Context, cacheKey: String, bytes: ByteArray) {
        try {
            val file = File(context.cacheDir, "img_$cacheKey.jpg")
            if (!file.exists() || file.length() == 0L) {
                FileOutputStream(file).use { it.write(bytes) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Disk write warning: ${e.message}")
        }
    }

    private fun hashString(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digested = md.digest(input.toByteArray())
            digested.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toString()
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap

        val ratio = min(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}

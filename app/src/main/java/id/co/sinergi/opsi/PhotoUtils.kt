package id.co.sinergi.opsi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

fun uriToDataUrl(context: Context, uri: Uri, maxSide: Int = 1400, quality: Int = 76): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: throw IllegalArgumentException("Foto tidak dapat dibaca")
    val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalArgumentException("Gagal memproses foto")
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val rotated = applyExifOrientation(bitmap, orientation)
    val scaled = scaleBitmap(rotated, maxSide)
    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
    if (scaled !== rotated) scaled.recycle()
    if (rotated !== bitmap) rotated.recycle()
    bitmap.recycle()
    return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

fun dataUrlToBitmap(data: String?, maxSide: Int = 1000): Bitmap? {
    if (data.isNullOrBlank()) return null
    return runCatching {
        val encoded = data.substringAfter("base64,", data)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { decoded ->
            val scaled = scaleBitmap(decoded, maxSide)
            if (scaled !== decoded) decoded.recycle()
            scaled
        }
    }.getOrNull()
}

private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    var w = width
    var h = height
    while (max(w, h) / sample > maxSide * 2) sample *= 2
    return sample.coerceAtLeast(1)
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        else -> return bitmap
    }
    return runCatching { Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) }.getOrDefault(bitmap)
}

private fun scaleBitmap(bitmap: Bitmap, maxSide: Int): Bitmap {
    val largest = max(bitmap.width, bitmap.height)
    if (largest <= maxSide) return bitmap
    val ratio = maxSide.toFloat() / largest.toFloat()
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
}

fun cameraOutputUri(context: Context): Uri {
    val folder = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(folder, "opsi_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

fun fileFromCache(context: Context, name: String): File {
    val folder = File(context.cacheDir, "exports").apply { mkdirs() }
    return File(folder, name)
}

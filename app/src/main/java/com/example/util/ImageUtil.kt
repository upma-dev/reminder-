package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Profile photos are shrunk to ~256px JPEG and stored as base64 in Firestore (no paid Storage needed). */
object ImageUtil {

    fun uriToBase64(context: Context, uri: Uri, size: Int = 256): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null

            // centre-crop to a square, then scale
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, size, size, true)

            var quality = 80
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 10
            } while (bytes.size > 120_000 && quality > 30)
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decode(b64: String?): Bitmap? {
        if (b64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

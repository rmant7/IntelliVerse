package com.example.shared.domain.usecases

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject


class ImageUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun convertUriToByteArray(imageUri: Uri): ByteArray? {
        var inputStream: InputStream? = null
        var byteArrayOutputStream: ByteArrayOutputStream? = null
        return try {
            // Open an InputStream from the URI
            inputStream = context.contentResolver.openInputStream(imageUri)
            // Decode the input stream to a Bitmap
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Compress the Bitmap into a ByteArrayOutputStream
            byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                100,
                byteArrayOutputStream
            )

            // Convert the ByteArrayOutputStream to a ByteArray
            val byteArray = byteArrayOutputStream.toByteArray()
            byteArrayOutputStream.close()
            return byteArray
        } catch (e: SecurityException) {
            Timber.d(e)
            null
        } catch (e: NullPointerException) {
            Timber.d(e)
            null
        } catch (e: Exception) {
            Timber.d(e)
            null
        } finally {
            inputStream?.close()
            byteArrayOutputStream?.close()
        }

    }

    fun convertUriToBase64(selectedUri: Uri): String {
        /*val bytes: ByteArray? = context.contentResolver
            .openInputStream(selectedUri)?.readBytes()*/
        val bytes = convertUriToByteArray(selectedUri)
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {

        val originalWidth = bitmap.getWidth()
        val originalHeight = bitmap.getHeight()
        val resizedWidth: Int
        val resizedHeight: Int

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = resizedHeight * originalWidth / originalHeight
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight = resizedWidth * originalHeight / originalWidth
        } else {
            resizedHeight = maxDimension
            resizedWidth = maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }

    fun getMimeType(uri: Uri): String? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            // Retrieve MIME type from content resolver
            context.contentResolver.getType(uri)
        } else {
            // If file URI, get MIME type based on extension
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            )
        }
    }
}
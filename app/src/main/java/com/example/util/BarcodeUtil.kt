package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.io.FileOutputStream

object BarcodeUtil {

    /**
     * Generates a QR Code bitmap for a student based on class, section, roll number, and school ID.
     */
    fun generateStudentQrCode(
        schoolId: String,
        className: String,
        section: String,
        rollNumber: String,
        studentId: String,
        width: Int = 512,
        height: Int = 512
    ): Bitmap? {
        val payload = "$schoolId:$className:$section:$rollNumber:$studentId"
        return generateBarcodeBitmap(payload, BarcodeFormat.QR_CODE, width, height)
    }

    /**
     * Generates a 1D Code 128 barcode bitmap from student ID for digital identity card.
     */
    fun generateStudent1DBarcode(
        studentId: String,
        width: Int = 600,
        height: Int = 200
    ): Bitmap? {
        // Sanitize string to standard ASCII for CODE_128
        val safeText = studentId.replace(" ", "-").trim()
        return generateBarcodeBitmap(safeText, BarcodeFormat.CODE_128, width, height)
    }

    /**
     * General barcode generation using ZXing MultiFormatWriter.
     */
    fun generateBarcodeBitmap(
        content: String,
        format: BarcodeFormat,
        width: Int,
        height: Int
    ): Bitmap? {
        return try {
            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(content, format, width, height)
            val w = bitMatrix.width
            val h = bitMatrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes a barcode or QR code from a Bitmap using ZXing MultiFormatReader.
     */
    fun decodeBarcodeFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts student ID from barcode or QR payload.
     * Supported formats:
     * - SCH-1001:10:A:01:STU-10-A-01 -> STU-10-A-01
     * - STU-10-A-01 -> STU-10-A-01
     */
    fun parseStudentIdFromScan(scannedText: String): String {
        val trimmed = scannedText.trim()
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            if (parts.isNotEmpty()) {
                return parts.last()
            }
        }
        return trimmed
    }

    /**
     * Saves bitmap to app cache and triggers share intent.
     */
    fun shareBitmap(context: Context, bitmap: Bitmap, title: String = "Share Student ID Card") {
        try {
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()
            val file = File(cachePath, "student_id_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        } catch (e: Exception) {
            // Fallback to text share
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, title)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        }
    }
}

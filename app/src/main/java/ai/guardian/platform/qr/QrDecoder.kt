package ai.guardian.platform.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Offline QR decoding with ZXing (pure Java, no network, no Play services). Chosen over ML Kit
 * because ML Kit pulls in telemetry that needs the INTERNET permission this build doesn't have.
 */
object QrDecoder {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    fun decode(source: LuminanceSource): String? {
        val reader = MultiFormatReader().apply { setHints(hints) }
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            // Light-on-dark codes: try the inverted image once.
            runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text }.getOrNull()
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    /** Decodes the luminance (Y) plane of a CameraX frame. */
    fun decode(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val rowStride = plane.rowStride
        val data = ByteArray(rowStride * image.height)
        buffer.get(data, 0, minOf(buffer.remaining(), data.size))
        val source = PlanarYUVLuminanceSource(data, rowStride, image.height, 0, 0, image.width, image.height, false)
        return decode(source)
    }

    fun decode(bitmap: Bitmap): String? {
        val scaled = scaleDown(bitmap)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        return decode(RGBLuminanceSource(scaled.width, scaled.height, pixels))
    }

    /** Decodes a QR from a picked or shared image (screenshots of QR codes work too). */
    fun decode(context: Context, uri: Uri): String? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        decode(bitmap)
    }.getOrNull()

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    private const val MAX_DIMENSION = 1600
}

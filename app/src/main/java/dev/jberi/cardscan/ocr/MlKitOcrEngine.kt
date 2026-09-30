package dev.jberi.cardscan.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * ML Kit Text Recognition v2, bundled Latin model. Proprietary but fully on-device: the model
 * ships in the APK, so this never needs Play services or a network.
 */
class MlKitOcrEngine : OcrEngine {
  private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

  override suspend fun recognize(bitmap: Bitmap): OcrResult {
    val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
    val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
      val box = line.boundingBox ?: return@mapNotNull null
      OcrLine(
        text = line.text,
        left = box.left,
        top = box.top,
        right = box.right,
        bottom = box.bottom,
        confidence = line.confidence,
        angle = line.angle,
        language = line.recognizedLanguage ?: "",
      )
    }
    return OcrResult(lines, bitmap.width, bitmap.height)
  }

  override fun close() = recognizer.close()
}

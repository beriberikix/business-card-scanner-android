package dev.jberi.cardscan.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jberi.cardscan.capture.loadBitmapForOcr
import dev.jberi.cardscan.model.ContactCard
import dev.jberi.cardscan.ocr.MlKitOcrEngine
import dev.jberi.cardscan.ocr.OcrResult
import dev.jberi.cardscan.ocr.QrContactDetector
import dev.jberi.cardscan.parse.CardParser
import dev.jberi.cardscan.parse.HeuristicCardParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScanUiState(
  val busy: Boolean = false,
  val bitmap: Bitmap? = null,
  val ocr: OcrResult? = null,
  val card: ContactCard? = null,
  val fromBarcode: Boolean = false,
  val error: String? = null,
  val ocrMillis: Long = 0,
)

class ScanViewModel(app: Application) : AndroidViewModel(app) {
  private val ocrEngine = MlKitOcrEngine()
  private val barcode = QrContactDetector()
  private val parser: CardParser = HeuristicCardParser()

  private val _state = MutableStateFlow(ScanUiState())
  val state: StateFlow<ScanUiState> = _state.asStateFlow()

  /** Runs the whole pipeline on one captured image: decode, barcode, OCR, parse. */
  /** When on, each scan is kept on the device so it can be pulled into the test fixture set. */
  var keepForTesting: Boolean = true

  fun process(uri: Uri, defaultRegion: String = "US") {
    viewModelScope.launch {
      _state.value = ScanUiState(busy = true)
      try {
        val bmp = loadBitmapForOcr(getApplication(), uri)
        val t0 = System.currentTimeMillis()
        val ocr = withContext(Dispatchers.Default) { ocrEngine.recognize(bmp) }
        val ocrMs = System.currentTimeMillis() - t0

        // A vCard barcode is exact data, so it wins over anything parsed from pixels.
        val fromQr = runCatching { barcode.detect(bmp) }.getOrNull()
        val card = fromQr ?: parser.parse(ocr, defaultRegion)

        Log.i(TAG, "ocr ${ocr.lines.size} lines in ${ocrMs}ms, barcode=${fromQr != null}")
        if (keepForTesting) saveForTesting(bmp, ocr)

        _state.value = ScanUiState(
          bitmap = bmp,
          ocr = ocr,
          card = card,
          fromBarcode = fromQr != null,
          ocrMillis = ocrMs,
        )
      } catch (t: Throwable) {
        Log.e(TAG, "pipeline failed", t)
        _state.value = ScanUiState(error = "${t::class.java.simpleName}: ${t.message}")
      }
    }
  }

  /**
   * Writes the captured image and its OCR result side by side under the app's external files
   * dir, named by timestamp. `tools/pull-scans.sh` copies them into `samples/`, which is how a
   * stack of real cards becomes a regression suite.
   */
  private suspend fun saveForTesting(bitmap: Bitmap, ocr: OcrResult) = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(getApplication<Application>().getExternalFilesDir(null), "captures")
        .apply { mkdirs() }
      val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
      File(dir, "$stamp.jpg").outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
      }
      File(dir, "$stamp.ocr.json").writeText(Json.encodeToString(ocr))
      Log.i(TAG, "saved scan $stamp")
    }.onFailure { Log.w(TAG, "could not save scan", it) }
    Unit
  }

  /** Re-parses the existing OCR result; used after the parser changes or a page is added. */
  fun edited(card: ContactCard) = _state.update { it.copy(card = card) }

  fun reset() = _state.update { ScanUiState() }

  fun fail(message: String) = _state.update { ScanUiState(error = message) }

  override fun onCleared() {
    ocrEngine.close()
    barcode.close()
  }

  companion object {
    const val TAG = "CardScan"
  }
}

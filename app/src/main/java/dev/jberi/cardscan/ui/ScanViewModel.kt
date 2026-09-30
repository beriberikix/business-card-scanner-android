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
        Log.i(TAG, "OCR_JSON ${Json.encodeToString(ocr)}")

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

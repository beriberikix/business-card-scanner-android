package dev.jberi.cardscan.spike

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import com.google.mlkit.genai.prompt.ModelConfig
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-off empirical probe of on-device Gemini Nano (ML Kit GenAI Prompt API) availability.
 *
 * The published supported-device list does not include Pixel "a" models, so this exists to
 * settle the question on the actual hardware rather than trusting docs. Results are logged
 * under the tag [TAG] so they can be read over adb, and returned for display.
 */
object NanoSpike {
  const val TAG = "NanoSpike"

  /** Sample OCR text from a synthetic card, used to measure structured-extraction quality. */
  private val SAMPLE_OCR = """
    NORTHWIND LOGISTICS
    Dana R. Whitfield
    Senior Operations Manager
    dana.whitfield@northwindlogistics.co.uk
    M +44 7700 900312
    T +44 20 7946 0821
    F +44 20 7946 0899
    18 Calder Wharf, Unit 4
    Manchester M3 4LZ
    United Kingdom
    www.northwindlogistics.co.uk
  """.trimIndent()

  private val PROMPT = """
    Extract the business card fields from the text below and reply with JSON only,
    no markdown fence, using exactly these keys:
    {"givenName":"","familyName":"","jobTitle":"","company":"","emails":[],"phones":[{"type":"","number":""}],"website":"","address":""}
    Phone type must be one of mobile, work, fax.

    TEXT:
  """.trimIndent()

  private fun line(sb: StringBuilder, s: String) {
    Log.i(TAG, s)
    sb.appendLine(s)
  }

  suspend fun run(): String {
    val sb = StringBuilder()
    line(sb, "=== Gemini Nano availability spike ===")

    val model = try {
      Generation.getClient()
    } catch (t: Throwable) {
      line(sb, "getClient() threw: ${t::class.java.name}: ${t.message}")
      return sb.toString()
    }

    val status = try {
      model.checkStatus()
    } catch (e: GenAiException) {
      line(sb, "checkStatus() GenAiException errorCode=${e.errorCode} msg=${e.message}")
      return sb.toString()
    } catch (t: Throwable) {
      line(sb, "checkStatus() threw: ${t::class.java.name}: ${t.message}")
      return sb.toString()
    }

    val statusName = when (status) {
      FeatureStatus.AVAILABLE -> "AVAILABLE"
      FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
      FeatureStatus.DOWNLOADING -> "DOWNLOADING"
      FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
      else -> "UNKNOWN($status)"
    }
    line(sb, "checkStatus() = $statusName")

    probe(sb, "baseModelName") { model.getBaseModelName() }
    probe(sb, "tokenLimit") { model.getTokenLimit().toString() }
    probe(sb, "structuredOutputAvailable") { model.isStructuredOutputFeatureAvailable().toString() }
    probe(sb, "systemPromptAvailable") { model.isSystemPromptAvailable().toString() }
    probe(sb, "thinkingModeAvailable") { model.isThinkingModeAvailable().toString() }
    probe(sb, "cachingAvailable") { model.isCachingFeatureAvailable().toString() }

    if (status == FeatureStatus.UNAVAILABLE) {
      // Default config is unavailable. Sweep the other model variants before concluding,
      // since release stage / preference select different AICore features.
      line(sb, "Default config UNAVAILABLE; sweeping other model configs...")
      for (stage in listOf(ModelReleaseStage.STABLE to "STABLE", ModelReleaseStage.PREVIEW to "PREVIEW")) {
        for (pref in listOf(ModelPreference.FAST to "FAST", ModelPreference.FULL to "FULL")) {
          val label = "stage=${stage.second} pref=${pref.second}"
          val r = try {
            val mc = ModelConfig.builder().apply {
              releaseStage = stage.first
              preference = pref.first
            }.build()
            val gc = GenerationConfig.Builder().apply { modelConfig = mc }.build()
            val m2 = Generation.getClient(gc)
            val st = m2.checkStatus()
            val name = runCatching { m2.getBaseModelName() }.getOrElse { "n/a (${(it as? GenAiException)?.errorCode})" }
            m2.close()
            "status=$st model=$name"
          } catch (e: GenAiException) {
            "GenAiException errorCode=${e.errorCode}: ${e.message}"
          } catch (t: Throwable) {
            "${t::class.java.name}: ${t.message}"
          }
          line(sb, "  $label -> $r")
        }
      }
      line(sb, "VERDICT: Prompt API not provisioned on this device. Heuristic parser is the offline path.")
      model.close()
      return sb.toString()
    }

    if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
      line(sb, "Model needs download; starting (5 min cap)...")
      val done = withTimeoutOrNull(5 * 60_000L) {
        try {
          model.download().collect { s ->
            when (s) {
              is DownloadStatus.DownloadStarted -> line(sb, "  download started, bytes=${s.bytesToDownload}")
              is DownloadStatus.DownloadProgress -> Log.d(TAG, "  progress ${s.totalBytesDownloaded}")
              is DownloadStatus.DownloadCompleted -> line(sb, "  download completed")
              is DownloadStatus.DownloadFailed -> line(sb, "  download FAILED: ${s.e.message}")
            }
          }
          true
        } catch (t: Throwable) {
          line(sb, "  download threw: ${t::class.java.name}: ${t.message}")
          false
        }
      }
      if (done != true) line(sb, "  download did not finish in time")
      line(sb, "checkStatus() after download = ${runCatching { model.checkStatus() }.getOrNull()}")
    }

    // Warm up so the measured latency is inference, not model load.
    val warmMs = timed(sb, "warmup") { model.warmup() }
    line(sb, "warmup took ${warmMs}ms")

    repeat(3) { i ->
      val t0 = System.currentTimeMillis()
      val out = try {
        model.generateContent("$PROMPT\n$SAMPLE_OCR").candidates.firstOrNull()?.text
      } catch (e: GenAiException) {
        "GenAiException errorCode=${e.errorCode}: ${e.message}"
      } catch (t: Throwable) {
        "${t::class.java.name}: ${t.message}"
      }
      val ms = System.currentTimeMillis() - t0
      line(sb, "--- run ${i + 1}: ${ms}ms")
      line(sb, out ?: "(null)")
    }

    model.close()
    line(sb, "VERDICT: Prompt API usable. Compare fields above against the sample card.")
    return sb.toString()
  }

  private suspend fun probe(sb: StringBuilder, label: String, block: suspend () -> String?) {
    val v = try {
      block()
    } catch (e: GenAiException) {
      "GenAiException errorCode=${e.errorCode}: ${e.message}"
    } catch (t: Throwable) {
      "${t::class.java.name}: ${t.message}"
    }
    line(sb, "$label = $v")
  }

  private suspend fun timed(sb: StringBuilder, label: String, block: suspend () -> Unit): Long {
    val t0 = System.currentTimeMillis()
    try {
      block()
    } catch (t: Throwable) {
      line(sb, "$label threw: ${t::class.java.name}: ${t.message}")
    }
    return System.currentTimeMillis() - t0
  }
}

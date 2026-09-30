package dev.jberi.cardscan

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import dev.jberi.cardscan.ocr.MlKitOcrEngine
import dev.jberi.cardscan.ocr.OcrResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs real on-device OCR over the sample card images and writes each result to the app's
 * external files dir as JSON.
 *
 * This is a recorder, not an assertion: the output is pulled off the device and committed under
 * `samples/`, which lets the parser tests run on the JVM against genuine ML Kit output instead of
 * text someone typed by hand. Re-run it whenever the sample images or the OCR engine change.
 */
class OcrFixtureRecorder {

  private val json = Json { prettyPrint = true }

  @Test
  fun recordFixtures() = runBlocking {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val assets = instrumentation.context.assets
    val outDir = File(
      instrumentation.targetContext.getExternalFilesDir(null),
      "ocr-fixtures",
    ).apply { mkdirs() }

    val names = assets.list("cards").orEmpty().filter { it.endsWith(".png") }
    assertTrue("no sample cards in androidTest/assets/cards", names.isNotEmpty())

    val engine = MlKitOcrEngine()
    try {
      names.forEach { file ->
        val bitmap = assets.open("cards/$file").use { BitmapFactory.decodeStream(it) }
        val result: OcrResult = engine.recognize(bitmap)
        val out = File(outDir, file.removeSuffix(".png") + ".ocr.json")
        out.writeText(json.encodeToString(result))
        println("FIXTURE ${out.absolutePath} lines=${result.lines.size}")
      }
    } finally {
      engine.close()
    }
  }
}

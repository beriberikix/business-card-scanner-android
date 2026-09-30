package dev.jberi.cardscan.parse

import dev.jberi.cardscan.ocr.OcrResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Expected fields for one sample card, kept alongside its OCR output in `samples/`. */
@Serializable
data class ExpectedCard(
  val note: String = "",
  val defaultRegion: String = "US",
  val givenName: String = "",
  val familyName: String = "",
  val jobTitle: String = "",
  val company: String = "",
  val phones: List<String> = emptyList(),
  val emails: List<String> = emptyList(),
  val urls: List<String> = emptyList(),
  val address: String = "",
)

data class Fixture(val name: String, val ocr: OcrResult, val expected: ExpectedCard)

object Fixtures {
  private val json = Json { ignoreUnknownKeys = true }

  /** Every card under test/resources/samples that has both an OCR dump and expectations. */
  fun all(): List<Fixture> {
    val names = requireNotNull(javaClass.getResourceAsStream("/samples/index.txt")) {
      "samples/index.txt missing"
    }.bufferedReader().readLines().map(String::trim).filter { it.isNotEmpty() }

    return names.map { name ->
      Fixture(
        name = name,
        ocr = json.decodeFromString(read("/samples/$name.ocr.json")),
        expected = json.decodeFromString(read("/samples/$name.expected.json")),
      )
    }
  }

  private fun read(path: String): String =
    requireNotNull(javaClass.getResourceAsStream(path)) { "missing $path" }
      .bufferedReader().readText()
}

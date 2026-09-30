package dev.jberi.cardscan.parse

import dev.jberi.cardscan.ocr.OcrResult
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Prints what the parser makes of a batch of freshly scanned cards, next to the raw OCR.
 *
 * This asserts nothing. It is the tool for eyeballing a new stack before deciding which cards
 * are worth turning into real fixtures with hand-written expectations.
 */
class RealScanReportTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun report() {
    val names = javaClass.getResourceAsStream("/scans/index.txt")
      ?.bufferedReader()?.readLines()?.map(String::trim)?.filter { it.isNotEmpty() }
      ?: return

    val parser = HeuristicCardParser()
    names.forEach { name ->
      val ocr: OcrResult = json.decodeFromString(
        javaClass.getResourceAsStream("/scans/$name.ocr.json")!!.bufferedReader().readText(),
      )
      val card = parser.parse(ocr, "US")
      println("================ $name")
      println("-- OCR (${ocr.lines.size} lines, tallest first shown in reading order)")
      ocr.readingOrder().forEach { println("   h=%-4d %s".format(it.height, it.text)) }
      println("-- parsed")
      println("   name    : '${card.prefix}' '${card.givenName}' '${card.middleName}' '${card.familyName}' '${card.suffix}'")
      println("   title   : '${card.jobTitle}'")
      println("   company : '${card.company}'")
      println("   phones  : " + card.phones.joinToString { "${it.number}/${it.type}${if (!it.normalized) " UNNORMALIZED(${it.raw})" else ""}" })
      println("   emails  : " + card.emails.joinToString { it.address + if (it.repaired) " REPAIRED(from ${it.raw})" else "" })
      println("   urls    : ${card.urls}")
      println("   address : '${card.address}'")
    }
  }
}

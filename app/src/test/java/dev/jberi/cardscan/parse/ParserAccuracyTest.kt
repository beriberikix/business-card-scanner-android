package dev.jberi.cardscan.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scores the parser on every sample card and prints a per-field table.
 *
 * Contact channels are gated: a wrong phone number or email address makes a saved contact
 * useless, and both come from patterns rather than judgement, so they are expected to be exact.
 * Name, title and company are reported but not asserted, because they are heuristic and a
 * regression there should be visible without blocking the build.
 */
class ParserAccuracyTest {

  private val parser = HeuristicCardParser()

  @Test
  fun `contact channels are exact on every sample`() {
    val fixtures = Fixtures.all()
    assertTrue("no fixtures found", fixtures.isNotEmpty())

    val report = StringBuilder("\nParser accuracy\n")
    var softHits = 0
    var softTotal = 0
    val failures = mutableListOf<String>()

    fixtures.forEach { f ->
      val got = parser.parse(f.ocr, f.expected.defaultRegion)
      report.appendLine("--- ${f.name}")

      fun soft(field: String, expected: String, actual: String) {
        val ok = expected.equals(actual, ignoreCase = true)
        softTotal++
        if (ok) softHits++
        report.appendLine("  ${if (ok) "OK  " else "MISS"} $field: expected='$expected' got='$actual'")
      }

      soft("givenName", f.expected.givenName, got.givenName)
      soft("familyName", f.expected.familyName, got.familyName)
      soft("jobTitle", f.expected.jobTitle, got.jobTitle)
      soft("company", f.expected.company, got.company)
      soft("address", f.expected.address, got.address)

      val gotPhones = got.phones.map { it.number }
      val gotEmails = got.emails.map { it.address }
      report.appendLine("  phones: expected=${f.expected.phones} got=$gotPhones")
      report.appendLine("  emails: expected=${f.expected.emails} got=$gotEmails")
      report.appendLine("  urls:   expected=${f.expected.urls} got=${got.urls}")

      if (gotPhones != f.expected.phones) {
        failures += "${f.name}: phones expected=${f.expected.phones} got=$gotPhones"
      }
      if (gotEmails != f.expected.emails) {
        failures += "${f.name}: emails expected=${f.expected.emails} got=$gotEmails"
      }
    }

    report.appendLine(
      "Heuristic fields: $softHits/$softTotal " +
        "(${if (softTotal == 0) 0 else softHits * 100 / softTotal}%)",
    )
    println(report)

    assertEquals("contact channel mismatches:\n${failures.joinToString("\n")}", 0, failures.size)
  }

  @Test
  fun `names split into parts`() {
    val p = HeuristicCardParser()
    assertEquals("Dana", p.splitName("Dana Whitfield").given)
    assertEquals("Whitfield", p.splitName("Dana Whitfield").family)
    assertEquals("Dana", p.splitName("Whitfield, Dana").given)
    assertEquals("Whitfield", p.splitName("Whitfield, Dana").family)
    assertEquals("R.", p.splitName("Dana R. Whitfield").middle)
    assertEquals("Dr", p.splitName("Dr Dana Whitfield").prefix)
    assertEquals("PhD", p.splitName("Dana Whitfield PhD").suffix)
    assertEquals("Whitfield", p.splitName("Dana Whitfield PhD").family)
  }
}

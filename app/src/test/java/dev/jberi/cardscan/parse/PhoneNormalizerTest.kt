package dev.jberi.cardscan.parse

import dev.jberi.cardscan.model.PhoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {

  @Test
  fun `normalizes to E164 using the default region`() {
    assertEquals("+12135550147", PhoneNormalizer.extract("+1 2135550147", "US").single().number)
    assertEquals("+14155552671", PhoneNormalizer.extract("(415) 555-2671", "US").single().number)
    assertEquals("+442079460821", PhoneNormalizer.extract("+44 20 7946 0821", "GB").single().number)
  }

  @Test
  fun `reads the type from a label on the same line`() {
    assertEquals(PhoneType.MOBILE, PhoneNormalizer.extract("Mobile +44 7700 900312", "GB").single().type)
    assertEquals(PhoneType.FAX_WORK, PhoneNormalizer.extract("F +44 20 7946 0899", "GB").single().type)
    assertEquals(PhoneType.WORK, PhoneNormalizer.extract("T: +44 20 7946 0821", "GB").single().type)
    assertEquals(PhoneType.MOBILE, PhoneNormalizer.extract("M +44 7700 900312", "GB").single().type)
  }

  @Test
  fun `repairs digits OCR confused for letters`() {
    assertEquals("+1 4155552671", PhoneNormalizer.repairDigits("+1 4l5555267I"))
    val p = PhoneNormalizer.extract("+1 4I5 555 267O", "US").single()
    assertEquals("+14155552670", p.number)
    assertTrue(p.normalized)
  }

  @Test
  fun `flags a number that cannot be normalized`() {
    val p = PhoneNormalizer.toPhone("123", "US", PhoneType.WORK)
    assertFalse(p.normalized)
  }

  @Test
  fun `ignores digit runs that are not phone numbers`() {
    assertTrue(PhoneNormalizer.extract("Suite 200, 1994", "US").isEmpty())
  }
}

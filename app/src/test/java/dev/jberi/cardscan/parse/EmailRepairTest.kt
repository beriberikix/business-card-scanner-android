package dev.jberi.cardscan.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailRepairTest {

  @Test
  fun `clean addresses pass through untouched`() {
    val e = EmailRepair.extract("dana.whitfield@northwind.co.uk").single()
    assertEquals("dana.whitfield@northwind.co.uk", e.address)
    assertTrue("a clean address must not be marked repaired", !e.repaired)
  }

  @Test
  fun `restores a dot OCR dropped before the TLD`() {
    // The exact failure seen on a real card, anonymised: the dot before the TLD is dropped.
    val e = EmailRepair.extract("marisol@veltrixio").single()
    assertEquals("marisol@veltrix.io", e.address)
    assertTrue(e.repaired)
    assertEquals("marisol@veltrixio", e.raw)
  }

  @Test
  fun `handles an at sign OCR read as a copyright symbol`() {
    assertEquals("dana@northwind.com", EmailRepair.repair("dana©northwind.com"))
  }

  @Test
  fun `gives up rather than guessing`() {
    assertNull(EmailRepair.repair("not-an-email"))
    assertNull(EmailRepair.repair("dana@nodotsorknowntld"))
  }
}

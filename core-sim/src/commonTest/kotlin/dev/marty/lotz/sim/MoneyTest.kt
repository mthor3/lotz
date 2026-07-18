package dev.marty.lotz.sim

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {
    @Test
    fun addsAndFormatsCents() {
        val total = Money.ofDollars(2) + Money(cents = 50)
        assertEquals(Money(cents = 250), total)
        assertEquals("$2.50", total.toString())
        assertEquals("-$0.07", Money(cents = -7).toString())
    }
}

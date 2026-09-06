package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How a price is written in a list row.
 *
 * The rounding rules are the interesting part: a price that would print as « 0 » is the same
 * misleading zero the scheduler goes out of its way not to send, so it is written as « <0.01 »
 * instead — small, but not free.
 */
class PerMillionTest {

    @Test
    fun `a whole number drops its decimals`() {
        assertEquals("5", perMillion(5.0))
        assertEquals("25", perMillion(25.0))
    }

    @Test
    fun `a fraction keeps only what it needs`() {
        assertEquals("0.6", perMillion(0.6))
        assertEquals("1.25", perMillion(1.25))
        assertEquals("6.25", perMillion(6.25))
    }

    @Test
    fun `a price too small to print is not printed as zero`() {
        assertEquals("<0.01", perMillion(0.004))
    }

    @Test
    fun `an absent price is a question mark, never a number`() {
        assertEquals("?", perMillion(null))
    }
}

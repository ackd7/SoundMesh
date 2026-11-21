package io.github.soundmesh.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FloatRingTest {
    @Test
    fun writeAndReadPreservesOrder() {
        val ring = FloatRing(8)
        ring.write(floatArrayOf(1f, 2f, 3f, 4f), 4)
        assertEquals(4, ring.available())

        val out = FloatArray(4)
        val n = ring.read(out, 4)
        assertEquals(4, n)
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), out)
        assertEquals(0, ring.available())
    }

    @Test
    fun wrapAroundAndOverflowDropsOldest() {
        val ring = FloatRing(6)
        ring.write(floatArrayOf(1f, 2f, 3f, 4f), 4)
        val tmp = FloatArray(2)
        assertEquals(2, ring.read(tmp, 2))
        assertContentEquals(floatArrayOf(1f, 2f), tmp)

        // Now ring has [3, 4], write 5 more -> capacity is 6, so oldest (3) is dropped -> [4, 5, 6, 7, 8, 9]
        ring.write(floatArrayOf(5f, 6f, 7f, 8f, 9f), 5)
        assertEquals(6, ring.available())

        val out = FloatArray(6)
        assertEquals(6, ring.read(out, 6))
        assertContentEquals(floatArrayOf(4f, 5f, 6f, 7f, 8f, 9f), out)
    }

    @Test
    fun skipAdvancesReadPosition() {
        val ring = FloatRing(8)
        ring.write(floatArrayOf(10f, 20f, 30f, 40f, 50f), 5)
        ring.skip(3)
        assertEquals(2, ring.available())

        val out = FloatArray(2)
        assertEquals(2, ring.read(out, 2))
        assertContentEquals(floatArrayOf(40f, 50f), out)
    }
}

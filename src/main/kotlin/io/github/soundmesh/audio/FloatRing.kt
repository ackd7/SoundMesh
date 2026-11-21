package io.github.soundmesh.audio

/**
 * A simple thread-safe ring buffer of interleaved float samples.
 * When full, the oldest samples are discarded.
 */
internal class FloatRing(private val capacity: Int) {
    private val buf = FloatArray(capacity)
    private var readPos = 0
    private var size = 0
    private val lock = Any()

    fun available(): Int = synchronized(lock) { size }

    fun clear() = synchronized(lock) {
        readPos = 0
        size = 0
    }

    fun write(src: FloatArray, len: Int) = synchronized(lock) {
        var offset = 0
        var n = len
        if (n > capacity) {
            offset = n - capacity
            n = capacity
        }
        val overflow = size + n - capacity
        if (overflow > 0) {
            readPos = (readPos + overflow) % capacity
            size -= overflow
        }
        var writePos = (readPos + size) % capacity
        var remaining = n
        while (remaining > 0) {
            val chunk = minOf(remaining, capacity - writePos)
            System.arraycopy(src, offset, buf, writePos, chunk)
            offset += chunk
            remaining -= chunk
            writePos = (writePos + chunk) % capacity
        }
        size += n
    }

    /** Reads up to [len] samples into [dst]; returns the number read. */
    fun read(dst: FloatArray, len: Int): Int = synchronized(lock) {
        val n = minOf(len, size)
        var offset = 0
        var remaining = n
        while (remaining > 0) {
            val chunk = minOf(remaining, capacity - readPos)
            System.arraycopy(buf, readPos, dst, offset, chunk)
            offset += chunk
            remaining -= chunk
            readPos = (readPos + chunk) % capacity
        }
        size -= n
        n
    }

    fun skip(n: Int) = synchronized(lock) {
        val k = minOf(n, size)
        readPos = (readPos + k) % capacity
        size -= k
    }
}

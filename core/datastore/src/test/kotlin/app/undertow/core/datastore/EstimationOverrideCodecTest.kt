package app.undertow.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimationOverrideCodecTest {

    @Test
    fun `encode is deterministic regardless of map order`() {
        val a = EstimationOverrideCodec.encode(
            mapOf("com.b.app" to 20, "com.a.app" to 3),
        )
        val b = EstimationOverrideCodec.encode(
            mapOf("com.a.app" to 3, "com.b.app" to 20),
        )

        assertEquals(a, b)
        assertEquals("com.a.app=3,com.b.app=20", a)
    }

    @Test
    fun `survives a round trip`() {
        val overrides = mapOf(
            "com.instagram.android" to 4,
            "com.google.android.youtube" to 12,
        )

        val decoded = EstimationOverrideCodec.decode(EstimationOverrideCodec.encode(overrides))

        assertEquals(overrides, decoded)
    }

    @Test
    fun `missing or blank input decodes to no overrides`() {
        assertEquals(emptyMap<String, Int>(), EstimationOverrideCodec.decode(null))
        assertEquals(emptyMap<String, Int>(), EstimationOverrideCodec.decode(""))
        assertEquals(emptyMap<String, Int>(), EstimationOverrideCodec.decode("   "))
    }

    @Test
    fun `malformed entries are dropped instead of throwing`() {
        val decoded = EstimationOverrideCodec.decode(
            "no-separator,=5,com.good.app=7,com.text.app=abc,com.zero.app=0,com.neg.app=-2, ,com.ok.app=2",
        )

        assertEquals(mapOf("com.good.app" to 7, "com.ok.app" to 2), decoded)
    }

    @Test
    fun `every decoded rate is positive`() {
        val decoded = EstimationOverrideCodec.decode("a=1,b=0,c=-1,d=x,e=9")

        assertTrue(decoded.values.all { it > 0 })
    }
}

package app.undertow.tracking.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EstimationPolicyTest {
    @Test fun estimatesWholeIntervalsOnly() {
        val policy = EstimationPolicy()
        assertEquals(0, policy.estimate(21_999))
        assertEquals(1, policy.estimate(22_000))
        assertEquals(2, policy.estimate(44_999))
    }

    @Test fun overrideChangesRate() {
        assertEquals(3, EstimationPolicy().estimate(90_000, 30))
    }
}

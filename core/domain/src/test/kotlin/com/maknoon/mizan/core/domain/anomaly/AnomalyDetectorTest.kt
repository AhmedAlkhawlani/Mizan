package com.maknoon.mizan.core.domain.anomaly

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AnomalyDetectorTest {

    private val detector = AnomalyDetector()

    private val megabyte = 1_000_000L

    @Test
    fun `usage below four times the median is silent`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.app" to 399 * megabyte),
            historyBytes = mapOf("com.example.app" to listOf(100 * megabyte, 100 * megabyte, 100 * megabyte)),
        )

        assertThat(anomalies).isEmpty()
    }

    @Test
    fun `usage exactly at four times the median raises the alert`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.app" to 400 * megabyte),
            historyBytes = mapOf("com.example.app" to listOf(100 * megabyte, 100 * megabyte, 100 * megabyte)),
        )

        val anomaly = anomalies.single()
        assertThat(anomaly.packageName).isEqualTo("com.example.app")
        assertThat(anomaly.usedBytes).isEqualTo(400 * megabyte)
        assertThat(anomaly.medianBytes).isEqualTo(100 * megabyte)
        assertThat(anomaly.thresholdBytes).isEqualTo(400 * megabyte)
        assertThat(anomaly.ratio).isWithin(0.0001).of(4.0)
    }

    @Test
    fun `usage above four times the median reports the observed ratio`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.app" to 600 * megabyte),
            historyBytes = mapOf("com.example.app" to listOf(100 * megabyte, 100 * megabyte, 100 * megabyte)),
        )

        assertThat(anomalies.single().ratio).isWithin(0.0001).of(6.0)
    }

    @Test
    fun `single history spike does not raise the median`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.app" to 400 * megabyte),
            historyBytes = mapOf(
                "com.example.app" to listOf(100 * megabyte, 100 * megabyte, 100 * megabyte, 10_000 * megabyte),
            ),
        )

        assertThat(anomalies.single().medianBytes).isEqualTo(100 * megabyte)
    }

    @Test
    fun `even sized history uses the midpoint of the middle values`() {
        val twoDayDetector = AnomalyDetector(minimumHistoryDays = 2)

        val anomalies = twoDayDetector.detect(
            todayBytes = mapOf("com.example.app" to 800 * megabyte),
            historyBytes = mapOf("com.example.app" to listOf(100 * megabyte, 300 * megabyte)),
        )

        assertThat(anomalies.single().medianBytes).isEqualTo(200 * megabyte)
    }

    @Test
    fun `even sized median of huge values does not overflow`() {
        val twoDayDetector = AnomalyDetector(
            minimumHistoryDays = 2,
            minimumBytes = 0L,
        )

        val anomalies = twoDayDetector.detect(
            todayBytes = mapOf("com.example.app" to Long.MAX_VALUE),
            historyBytes = mapOf("com.example.app" to listOf(Long.MAX_VALUE, Long.MAX_VALUE)),
        )

        assertThat(anomalies.single().medianBytes).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `fewer active days than the minimum cannot establish a habit`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.app" to 1_000 * megabyte),
            historyBytes = mapOf("com.example.app" to listOf(10 * megabyte, 10 * megabyte)),
        )

        assertThat(anomalies).isEmpty()
    }

    @Test
    fun `app without history is silent`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.new" to 1_000 * megabyte),
            historyBytes = emptyMap(),
        )

        assertThat(anomalies).isEmpty()
    }

    @Test
    fun `absolute minimum dominates a tiny median`() {
        val detectorWithSmallMedian = AnomalyDetector(minimumBytes = 50 * megabyte)

        val belowMinimum = detectorWithSmallMedian.detect(
            todayBytes = mapOf("com.example.app" to 49_999_999L),
            historyBytes = mapOf("com.example.app" to listOf(1 * megabyte, 1 * megabyte, 1 * megabyte)),
        )
        val atMinimum = detectorWithSmallMedian.detect(
            todayBytes = mapOf("com.example.app" to 50 * megabyte),
            historyBytes = mapOf("com.example.app" to listOf(1 * megabyte, 1 * megabyte, 1 * megabyte)),
        )

        assertThat(belowMinimum).isEmpty()
        assertThat(atMinimum.single().thresholdBytes).isEqualTo(50 * megabyte)
    }

    @Test
    fun `package in cooldown stays silent`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.app" to 1_000 * megabyte),
            historyBytes = mapOf("com.example.app" to listOf(10 * megabyte, 10 * megabyte, 10 * megabyte)),
            coolingDown = setOf("com.example.app"),
        )

        assertThat(anomalies).isEmpty()
    }

    @Test
    fun `zero usage today is never an anomaly`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.app" to 0L),
            historyBytes = mapOf("com.example.app" to listOf(1 * megabyte, 1 * megabyte, 1 * megabyte)),
        )

        assertThat(anomalies).isEmpty()
    }

    @Test
    fun `zero median cannot produce a ratio`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.app" to 1_000 * megabyte),
            historyBytes = mapOf("com.example.app" to listOf(0L, 0L, 0L)),
        )

        assertThat(anomalies).isEmpty()
    }

    @Test
    fun `negative history is treated as zero`() {
        val anomalies = detector.detect(
            todayBytes = mapOf("com.example.app" to 400 * megabyte),
            historyBytes = mapOf(
                "com.example.app" to listOf(-10 * megabyte, 100 * megabyte, 100 * megabyte, 100 * megabyte),
            ),
        )

        assertThat(anomalies.single().medianBytes).isEqualTo(100 * megabyte)
    }

    @Test
    fun `results are ordered by ratio then package name`() {
        val anomalies = detector.detect(
            todayBytes = mapOf(
                "com.example.slow" to 400 * megabyte,
                "com.example.fast" to 800 * megabyte,
                "com.example.tieB" to 500 * megabyte,
                "com.example.tieA" to 500 * megabyte,
            ),
            historyBytes = mapOf(
                "com.example.slow" to listOf(100 * megabyte, 100 * megabyte, 100 * megabyte),
                "com.example.fast" to listOf(100 * megabyte, 100 * megabyte, 100 * megabyte),
                "com.example.tieA" to listOf(100 * megabyte, 100 * megabyte, 100 * megabyte),
                "com.example.tieB" to listOf(100 * megabyte, 100 * megabyte, 100 * megabyte),
            ),
        )

        assertThat(anomalies.map { it.packageName }).containsExactly(
            "com.example.fast",
            "com.example.tieA",
            "com.example.tieB",
            "com.example.slow",
        ).inOrder()
    }

    @Test
    fun `multiplier must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { AnomalyDetector(multiplier = 0.0) }
    }

    @Test
    fun `minimum history days must be at least one`() {
        assertThrows(IllegalArgumentException::class.java) { AnomalyDetector(minimumHistoryDays = 0) }
    }

    @Test
    fun `minimum bytes must not be negative`() {
        assertThrows(IllegalArgumentException::class.java) { AnomalyDetector(minimumBytes = -1L) }
    }
}

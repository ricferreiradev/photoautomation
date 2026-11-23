package com.ricferreira.photoautomation

import org.junit.Assert.*
import org.junit.Test

class AnalysisBatchingTest {
    private fun ranges(total: Int, batchSize: Int = AnalysisBatching.IMAGES_PER_BATCH): List<IntRange> {
        val batches = mutableListOf<IntRange>()
        var start = 0
        while (start < total) {
            val end = minOf(start + batchSize, total)
            batches.add(start until end)
            start = AnalysisBatching.nextStart(start, end, total)
        }
        return batches
    }
    @Test fun moreThanFiftyImagesAreAllIncluded() {
        for (count in listOf(51, 100, 1001, 5000)) {
            val batches = ranges(count)
            assertEquals((0 until count).toSet(), batches.flatMap { it.toList() }.toSet())
            assertTrue(batches.all { it.count() <= AnalysisBatching.IMAGES_PER_BATCH })
        }
    }
    @Test fun overlapPreservesQuestionsAcrossBatchBoundaries() {
        val batches = ranges(51)
        batches.zipWithNext().forEach { (first, second) ->
            assertEquals(AnalysisBatching.OVERLAP, first.intersect(second.toSet()).size)
        }
    }
    @Test fun smallMemoryLimitedBatchStillMakesProgress() {
        assertEquals(51, ranges(51, 1).size)
        assertEquals((0 until 51).toSet(), ranges(51, 2).flatMap { it.toList() }.toSet())
    }
    @Test fun exactBatchAndSingleImageFinishWithoutRepeat() {
        assertEquals(1, ranges(1).size)
        assertEquals(1, ranges(12).size)
        assertEquals(2, ranges(13).size)
    }
    @Test fun batchAndConsolidationUseConfiguredPrompts() {
        val config = RemoteConfig(false, "root", 1.0, 1f,
            analysisPrompt = "Descreva as imagens.",
            batchPrompt = "Liste os elementos visíveis.",
            consolidationPrompt = "Reúna as descrições.")
        val batch = AnalysisBatching.batchPrompt(config)
        assertTrue(batch.contains(config.analysisPrompt))
        assertTrue(batch.contains(config.batchPrompt))
        assertFalse(batch.contains(AnalysisBatching.DEFAULT_BATCH_PROMPT))
        for (final in listOf(false, true)) {
            val merged = AnalysisBatching.consolidationPrompt(config, final)
            assertTrue(merged.contains(config.analysisPrompt))
            assertTrue(merged.contains(config.consolidationPrompt))
            assertFalse(merged.contains(AnalysisBatching.DEFAULT_CONSOLIDATION_PROMPT))
        }
    }
}

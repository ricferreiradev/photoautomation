package com.ricferreira.photoautomation

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AnalysisIntegrationTest {
    @Test fun analyzeConfiguredFolderUsingSavedPrompt() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runAnalysis") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Execute no celular servidor.", DeviceMode.current(context).canEdit)
        assertTrue("A chave OpenAI precisa estar configurada neste celular.", AnalysisKey.configured(context))
        val token = Drive.token(context)
        val id = requireNotNull(ConfigStore.selected(context))
        val original = ConfigStore.read(token, id)
        val saved = ConfigStore.save(token, id, original, original.config)
        val result = AnalysisClient.analyze(context, token, saved.config) { }
        assertTrue(result.getInt("imageCount") > 0)
        assertTrue(result.getString("answer").isNotBlank())
        File(context.filesDir, "last-analysis-test.json").writeText(result.toString(2))
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit()
            .putString("analysisResult", result.getString("answer"))
            .putString("analysisSummary", "${result.getInt("imageCount")} imagens analisadas").commit()
    }
}

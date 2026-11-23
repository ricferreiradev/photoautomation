package com.ricferreira.photoautomation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnalysisResponseTest {
    @Test fun preservesApiOrderDuplicatesSpacesAndMarkdownExactly() {
        val text = " \n## Resposta\nA - 1\nA - 1\nb - 2  \n"
        val response = JSONObject().put("status", "completed").put("output", org.json.JSONArray().put(
            JSONObject().put("content", org.json.JSONArray().put(JSONObject().put("type", "output_text").put("text", text)))))
        assertEquals(text, AnalysisClient.answer(response))
    }
    @Test fun joinsTextPartsWithoutInsertingCharacters() {
        val response = JSONObject("""{"status":"completed","output":[{"content":[{"type":"output_text","text":"A - "},{"type":"output_text","text":"1\n"}]}]}""")
        assertEquals("A - 1\n", AnalysisClient.answer(response))
    }
    @Test fun apiJsonTextIsNotParsedOrReformatted() {
        val text = "{ \"answer\" : \"A - 1\" }"
        val response = JSONObject().put("output", org.json.JSONArray().put(JSONObject().put("content",
            org.json.JSONArray().put(JSONObject().put("type", "output_text").put("text", text)))))
        assertEquals(text, AnalysisClient.answer(response))
    }
    @Test fun extractsTextAfterReasoningItems() {
        val json = JSONObject("""{"status":"completed","output":[{"type":"reasoning"},{"type":"message","content":[{"type":"output_text","text":"1 - A\n2 - C"}]}]}""")
        assertEquals("1 - A\n2 - C", AnalysisClient.answer(json))
    }
    @Test fun incompleteResultIsNotPresentedAsACompleteAnswer() {
        assertThrows(IllegalStateException::class.java) { AnalysisClient.answer(JSONObject("""{"status":"incomplete","output":[]}""")) }
    }
    @Test fun emptyResultIsReportedAsFailure() {
        assertThrows(IllegalStateException::class.java) { AnalysisClient.answer(JSONObject("""{"status":"completed","output":[]}""")) }
    }
}

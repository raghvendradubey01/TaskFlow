package com.example

import com.example.voice.VoiceAssistantController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun buildConfig_doesNotContainClientSideApiKeys() {
        val fieldNames = BuildConfig::class.java.declaredFields.map { it.name.uppercase() }
        val forbiddenSubstrings = listOf("GEMINI_API_KEY", "API_KEY", "SECRET", "TOKEN")
        for (field in fieldNames) {
            for (forbidden in forbiddenSubstrings) {
                assertFalse(
                    "BuildConfig must not expose client-side secret field: $field",
                    field.contains(forbidden)
                )
            }
        }
    }

    @Test
    fun voiceWakeWord_detectsHeyTaskFlowAndExtractsCommand() {
        val extracted = VoiceAssistantController.stripWakeWord("Hey TaskFlow call me at 5:30 PM to review slides")
        assertNotNull(extracted)
        assertEquals("call me at 5:30 PM to review slides", extracted)
    }
}

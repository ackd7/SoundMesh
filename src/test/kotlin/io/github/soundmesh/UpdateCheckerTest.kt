package io.github.soundmesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {
    @Test
    fun comparesSemanticVersionsCorrectly() {
        assertTrue(UpdateChecker.isNewerVersion("1.2.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.10.0", "1.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewerVersion("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.9", "1.1.0"))
    }

    @Test
    fun extractsEscapedJsonStrings() {
        val sample = """{"tag_name":"v1.2.0","html_url":"https://github.com/ackd7/SoundMesh/releases/tag/v1.2.0","body":"Line 1\n\"Quoted\" \\ end"}"""
        assertEquals("v1.2.0", UpdateChecker.extractJsonString(sample, "tag_name"))
        assertEquals(
            "https://github.com/ackd7/SoundMesh/releases/tag/v1.2.0",
            UpdateChecker.extractJsonString(sample, "html_url"),
        )
        assertEquals("Line 1\n\"Quoted\" \\ end", UpdateChecker.extractJsonString(sample, "body"))
    }
}

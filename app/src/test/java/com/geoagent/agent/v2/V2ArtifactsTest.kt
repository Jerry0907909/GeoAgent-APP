package com.geoagent.agent.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class V2ArtifactsTest {

    @Test
    fun jsonArtifactPreservesSpecialCharacters() {
        val artifact = v2Artifact(
            "reminder",
            mapOf(
                "workId" to "work=1;still-one-id",
                "title" to "检查 A=B;C 的报告"
            )
        )

        assertEquals("reminder", artifact.v2ArtifactType())
        assertEquals("work=1;still-one-id", artifact.v2ArtifactString("workId"))
        assertEquals("检查 A=B;C 的报告", artifact.v2ArtifactString("title"))
    }
}

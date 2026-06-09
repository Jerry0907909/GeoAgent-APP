package com.geoagent.ui.chat

import com.geoagent.agent.v2.V2AgentId
import com.geoagent.agent.v2.V2AgentRun
import com.geoagent.agent.v2.V2AgentRunStatus
import com.geoagent.agent.v2.V2Judgement
import com.geoagent.agent.v2.V2Plan
import com.geoagent.agent.v2.V2Reflection
import com.geoagent.agent.v2.V2OrchestrationResult
import com.geoagent.agent.v2.v2Artifact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2SystemActionMapperTest {

    @Test
    fun mapsCalendarArtifactToOpenCalendarAction() {
        val trace = traceWith(
                V2AgentRun(
                    taskId = "task-1",
                    agentId = V2AgentId.SCHEDULE,
                    status = V2AgentRunStatus.COMPLETED,
                    toolIds = emptySet(),
                    summary = "calendar ready",
                output = "日历描述",
                artifact = v2Artifact(
                    "calendar",
                    mapOf(
                        "title" to "检查 A=B;C 的报告",
                        "timezone" to "Asia/Shanghai",
                        "begin" to 1_725_346_800_000L,
                        "end" to 1_725_350_400_000L
                    )
                )
            )
        )

        val action = trace.toV2SystemAction()

        assertTrue(action is V2SystemAction.OpenCalendarInsert)
        action as V2SystemAction.OpenCalendarInsert
        assertEquals("检查 A=B;C 的报告", action.title)
        assertEquals("日历描述", action.description)
        assertEquals("Asia/Shanghai", action.timeZone)
        assertEquals(1_725_346_800_000L, action.beginTimeMillis)
        assertEquals(1_725_350_400_000L, action.endTimeMillis)
    }

    @Test
    fun mapsReminderArtifactToConfirmReminderAction() {
        val trace = traceWith(
                V2AgentRun(
                    taskId = "task-1",
                    agentId = V2AgentId.TASK,
                    status = V2AgentRunStatus.COMPLETED,
                    toolIds = emptySet(),
                    summary = "fallback title",
                output = "reminder ready",
                artifact = v2Artifact(
                    "reminder",
                    mapOf(
                        "workId" to "work=1;still-one-id",
                        "title" to "提交 A=B;C"
                    )
                )
            )
        )

        val action = trace.toV2SystemAction()

        assertTrue(action is V2SystemAction.ConfirmReminder)
        action as V2SystemAction.ConfirmReminder
        assertEquals("work=1;still-one-id", action.workId)
        assertEquals("提交 A=B;C", action.title)
    }

    @Test
    fun mapsScheduleCalendarCandidateToOpenCalendarAction() {
        val trace = traceWith(
            V2AgentRun(
                taskId = "task-1",
                agentId = V2AgentId.SCHEDULE,
                status = V2AgentRunStatus.COMPLETED,
                toolIds = emptySet(),
                summary = "schedule ready",
                output = "复习安排",
                artifact = v2Artifact(
                    "schedule",
                    mapOf(
                        "taskId" to "task-1",
                        "calendar" to true,
                        "timezone" to "Asia/Shanghai",
                        "begin" to 1_725_346_800_000L,
                        "end" to 1_725_350_400_000L
                    )
                )
            )
        )

        val action = trace.toV2SystemAction()

        assertTrue(action is V2SystemAction.OpenCalendarInsert)
        action as V2SystemAction.OpenCalendarInsert
        assertEquals("GeoAgent 日程安排", action.title)
        assertEquals("复习安排", action.description)
        assertEquals("Asia/Shanghai", action.timeZone)
        assertEquals(1_725_346_800_000L, action.beginTimeMillis)
        assertEquals(1_725_350_400_000L, action.endTimeMillis)
    }

    @Test
    fun ignoresScheduleWithoutCalendarCandidate() {
        val trace = traceWith(
            V2AgentRun(
                taskId = "task-1",
                agentId = V2AgentId.SCHEDULE,
                status = V2AgentRunStatus.COMPLETED,
                toolIds = emptySet(),
                summary = "schedule ready",
                output = "复习安排",
                artifact = v2Artifact(
                    "schedule",
                    mapOf(
                        "taskId" to "task-1",
                        "calendar" to false
                    )
                )
            )
        )

        val action = trace.toV2SystemAction()

        assertNull(action)
    }

    @Test
    fun ignoresPresentationOutlineArtifact() {
        val trace = traceWith(
                V2AgentRun(
                    taskId = "task-1",
                    agentId = V2AgentId.RESEARCH,
                    status = V2AgentRunStatus.COMPLETED,
                    toolIds = emptySet(),
                    summary = "outline ready",
                output = "汇报大纲",
                artifact = v2Artifact(
                    "presentation_outline",
                    mapOf(
                        "title" to "地质汇报",
                        "sections" to 4,
                        "mode" to "outline_only"
                    )
                )
            )
        )

        val action = trace.toV2SystemAction()

        assertNull(action)
    }

    private fun traceWith(run: V2AgentRun): V2OrchestrationResult =
        V2OrchestrationResult(
            traceId = "trace",
            input = "input",
            plan = V2Plan("input", emptyList(), parallelizable = false),
            runs = listOf(run),
            reflection = V2Reflection(emptyList(), emptyMap()),
            judgement = V2Judgement(passed = true, score = 1f, reasons = emptyList()),
            answer = "answer",
            events = emptyList()
        )
}

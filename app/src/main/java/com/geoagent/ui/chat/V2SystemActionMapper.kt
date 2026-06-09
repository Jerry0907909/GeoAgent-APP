package com.geoagent.ui.chat

import com.geoagent.agent.v2.V2OrchestrationResult
import com.geoagent.agent.v2.v2ArtifactString
import com.geoagent.agent.v2.v2ArtifactType

sealed class V2SystemAction {
    data class OpenCalendarInsert(
        val title: String,
        val description: String,
        val timeZone: String,
        val beginTimeMillis: Long?,
        val endTimeMillis: Long?
    ) : V2SystemAction()

    data class ConfirmReminder(
        val workId: String,
        val title: String
    ) : V2SystemAction()
}

fun V2OrchestrationResult.toV2SystemAction(): V2SystemAction? {
    val run = runs.firstOrNull {
        it.artifact?.v2ArtifactType() == "calendar" ||
            it.artifact?.v2ArtifactType() == "reminder" ||
            it.artifact?.v2ArtifactType() == "schedule" ||
            it.artifact?.v2ArtifactType() == "meeting" ||
            it.artifact?.v2ArtifactType() == "travel"
    } ?: return null
    val artifact = run.artifact.orEmpty()
    return when (artifact.v2ArtifactType()) {
        "calendar" -> V2SystemAction.OpenCalendarInsert(
            title = artifact.v2ArtifactString("title").ifBlank { "GeoAgent 日程" },
            description = run.output,
            timeZone = artifact.v2ArtifactString("timezone").ifBlank { java.util.TimeZone.getDefault().id },
            beginTimeMillis = artifact.v2ArtifactString("begin").toLongOrNull(),
            endTimeMillis = artifact.v2ArtifactString("end").toLongOrNull()
        )
        "reminder" -> V2SystemAction.ConfirmReminder(
            workId = artifact.v2ArtifactString("workId"),
            title = artifact.v2ArtifactString("title").ifBlank { run.summary }
        )
        "schedule",
        "meeting",
        "travel" -> {
            if (artifact.v2ArtifactString("calendar") != "true") {
                null
            } else {
                V2SystemAction.OpenCalendarInsert(
                    title = calendarTitleFor(artifact.v2ArtifactType().orEmpty(), run.summary),
                    description = run.output,
                    timeZone = artifact.v2ArtifactString("timezone").ifBlank { java.util.TimeZone.getDefault().id },
                    beginTimeMillis = artifact.v2ArtifactString("begin").toLongOrNull(),
                    endTimeMillis = artifact.v2ArtifactString("end").toLongOrNull()
                )
            }
        }
        else -> null
    }
}

private fun calendarTitleFor(type: String, fallback: String): String = when (type) {
    "schedule" -> "GeoAgent 日程安排"
    "meeting" -> "GeoAgent 会议"
    "travel" -> "GeoAgent 旅行计划"
    else -> fallback
}

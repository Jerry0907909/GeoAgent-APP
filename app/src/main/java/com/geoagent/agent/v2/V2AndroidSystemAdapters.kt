package com.geoagent.agent.v2

import android.content.Intent
import android.provider.CalendarContract

object V2CalendarContractAdapter {
    fun buildInsertIntent(request: V2CalendarEventRequest): Intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, request.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, request.description)
            .putExtra(CalendarContract.Events.EVENT_TIMEZONE, request.timeZone)
            .apply {
                request.beginTimeMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                request.endTimeMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            }
}

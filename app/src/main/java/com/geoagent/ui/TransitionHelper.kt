package com.geoagent.ui

import android.app.Activity
import com.geoagent.R

object TransitionHelper {

    /** Forward navigation: right → left slide */
    fun forward(activity: Activity) {
        activity.overridePendingTransition(
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
    }

    /** Back navigation: left → right slide */
    fun backward(activity: Activity) {
        activity.overridePendingTransition(
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
    }

    /** Splash → main: crossfade */
    fun fade(activity: Activity) {
        activity.overridePendingTransition(
            R.anim.fade_in,
            android.R.anim.fade_out
        )
    }
}

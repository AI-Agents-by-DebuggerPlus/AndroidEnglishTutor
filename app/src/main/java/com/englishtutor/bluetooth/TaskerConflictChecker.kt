package com.englishtutor.bluetooth

import android.content.Context
import android.content.pm.PackageManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskerConflictChecker @Inject constructor() {

    /** true, если у Tasker есть разрешение слушать media-кнопки (необходимое условие для Grab-конфликта). */
    fun taskerMayGrabMediaButtons(context: Context): Boolean {
        return runCatching {
            context.packageManager.checkPermission(
                MEDIA_KEY_LISTENER_PERMISSION,
                TASKER_PACKAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    companion object {
        private const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"
        private const val MEDIA_KEY_LISTENER_PERMISSION = "android.permission.SET_MEDIA_KEY_LISTENER"
    }
}

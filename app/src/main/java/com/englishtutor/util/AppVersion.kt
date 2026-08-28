package com.englishtutor.util

import com.englishtutor.BuildConfig

object AppVersion {
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
    val buildType: String = BuildConfig.BUILD_TYPE
    val label: String = "v$versionName ($versionCode) · $buildType"
}

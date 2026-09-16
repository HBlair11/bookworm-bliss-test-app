package com.bookwormbliss.app

/** Runtime view of the centralized build-time application identity. */
object AppIdentity {
    val appName: String = BuildConfig.APP_NAME
    val applicationId: String = BuildConfig.APPLICATION_ID
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
}

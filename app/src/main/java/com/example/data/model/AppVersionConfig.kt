package com.example.data.model

data class AppVersionConfig(
    val minSupportedVersionCode: Int = 1,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0",
    val forceUpdate: Boolean = false,
    val updateUrl: String = "",
    val title: String = "Please Update",
    val message: String = "A new version of the Jaiti Foundation App is available. Please update the app to continue."
)

enum class VersionStatus {
    CHECKING,
    SUPPORTED,
    UNSUPPORTED,
    CHECK_FAILED
}

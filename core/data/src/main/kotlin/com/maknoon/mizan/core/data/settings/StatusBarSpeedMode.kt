package com.maknoon.mizan.core.data.settings

/**
 * What the ongoing notification draws in its status-bar icon slot.
 *
 * The figure is generated as a bitmap rather than picked from resources, because the speed
 * changes every second and a static icon would force the user to open the app to read it.
 */
enum class StatusBarSpeedMode {
    DOWNLOAD,
    UPLOAD,
    OFF,
}

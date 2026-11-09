package dev.tomng.alarme.core.alarmengine.uninstall

import android.app.admin.DeviceAdminReceiver

/**
 * The device-admin receiver behind "prevent uninstall". It requests no policies at all: its only
 * purpose is that an active admin makes the package non-uninstallable until the user turns the
 * toggle off (or deactivates it in system settings).
 */
class AdminReceiver : DeviceAdminReceiver()

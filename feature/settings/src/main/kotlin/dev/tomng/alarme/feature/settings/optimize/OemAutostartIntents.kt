package dev.tomng.alarme.feature.settings.optimize

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep links into vendor "autostart" / "background activity" managers. These activities are not
 * public API and move between ROM versions, so every guide carries several candidates and the last
 * resort is always the app-details page — a dead link must never be a dead end.
 */
@Singleton
class OemAutostartIntents
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    fun candidates(guide: OemGuide): List<Intent> =
        when (guide) {
            OemGuide.XIAOMI ->
                listOf(
                    component(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    ),
                    component(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                    ),
                )

            OemGuide.HUAWEI ->
                listOf(
                    component(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    ),
                    component(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity",
                    ),
                )

            OemGuide.HONOR ->
                listOf(
                    component(
                        "com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    ),
                )

            OemGuide.OPPO ->
                listOf(
                    component(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    ),
                    component(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.startupapp.StartupAppListActivity",
                    ),
                )

            OemGuide.VIVO ->
                listOf(
                    component(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                    ),
                    component(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                    ),
                )

            OemGuide.ONEPLUS ->
                listOf(
                    component(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                    ),
                )

            OemGuide.GENERIC -> emptyList()
        }

    /** The first candidate the device can actually resolve, else the app-details page. */
    fun resolve(guide: OemGuide): Intent {
        val packageManager = context.packageManager
        val resolvable =
            candidates(guide).firstOrNull { packageManager.resolveActivity(it, 0) != null }
        return resolvable ?: appDetails()
    }

    fun appDetails(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))
}

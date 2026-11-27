package com.phantom.ghost.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

enum class AppCategory(val label: String, val intentCategory: String?) {
    MAPS("Maps", Intent.CATEGORY_APP_MAPS),
    CALENDAR("Calendar", Intent.CATEGORY_APP_CALENDAR),
    NOTES("Notes", null),
    BROWSER("Browser", Intent.CATEGORY_APP_BROWSER)
}

data class InstalledApp(val packageName: String, val label: String)

/**
 * Discovers only apps that advertise a supported Android app category or note action.
 * It deliberately avoids QUERY_ALL_PACKAGES and never treats arbitrary installed apps
 * as trusted automation targets. Discovery is performed on demand, so installs/removals
 * are reflected in the next plan.
 */
class DynamicAllowList(private val context: Context) {
    fun discover(): Map<AppCategory, List<InstalledApp>> =
        AppCategory.values().associateWith(::discover)

    fun discover(category: AppCategory): List<InstalledApp> {
        val intents = when (category) {
            AppCategory.NOTES -> listOf(Intent("android.intent.action.CREATE_NOTE"))
            else -> listOf(Intent(Intent.ACTION_MAIN).addCategory(requireNotNull(category.intentCategory)))
        }
        val pm = context.packageManager
        return intents.flatMap { intent ->
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.distinctBy { it.activityInfo.packageName }
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                val launchable = pm.getLaunchIntentForPackage(pkg) != null
                if (!launchable) null else InstalledApp(
                    pkg,
                    info.loadLabel(pm)?.toString()?.take(60) ?: pkg
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun resolve(category: AppCategory): InstalledApp? = discover(category).firstOrNull()
}

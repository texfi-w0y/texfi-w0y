package com.texfi.w0y.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Какой алиас иконки должен быть включён. Чистая часть — для теста: ровно
 * один включён, остальные выключены, при любом выборе.
 */
object AppIconPlan {
    fun states(selected: AppIcon): Map<AppIcon, Boolean> = AppIcon.entries.associateWith { it == selected }

    /** Иконка по набору включённых алиасов; если ни один не опознан — по умолчанию [AppIcon.W]. */
    fun current(enabledAliases: Set<String>): AppIcon = AppIcon.entries.firstOrNull { it.alias in enabledAliases } ?: AppIcon.W
}

/** Переключение иконки на рабочем столе через activity-alias, без перезапуска приложения. */
object AppIconSwitcher {
    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, "com.texfi.w0y" + icon.alias)

    /** Включённая сейчас иконка — по состоянию системы, а не по сохранённой настройке. */
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        val enabled =
            AppIcon.entries
                .filter { icon ->
                    val state = pm.getComponentEnabledSetting(component(context, icon))
                    state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                        (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == AppIcon.W)
                }.map { it.alias }
                .toSet()
        return AppIconPlan.current(enabled)
    }

    /**
     * Сначала включаем выбранную, потом гасим остальные: пока идёт
     * переключение, у приложения всегда есть хотя бы один значок.
     */
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

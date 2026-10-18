package com.texfi.w0y.desktop

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.concurrent.thread

/**
 * Значок приложения. В окне и трее он меняется сразу (ресурс `appicons/<имя>.png`). Значок в меню запуска
 * на Linux лежит в теме значков пользователя: при выборе подменяем его файлы и сбрасываем кэш, чтобы
 * лаунчер подхватил новый (может занять секунду-две). Windows: значок ярлыка берётся из установщика, он не меняется.
 */
object AppIcons {
    fun resource(icon: AppIcon) = "appicons/${icon.file}.png"

    fun applyLauncher(icon: AppIcon) {
        if (!Platform.isLinux) return
        val home = System.getProperty("user.home") ?: return
        val desktopFile = File(home, ".local/share/applications/w0y.desktop")
        if (!desktopFile.isFile) return // не установлено скриптом install: нечего подменять
        thread(isDaemon = true, name = "app-icon") {
            runCatching {
                val source = AppIcons::class.java.getResourceAsStream("/" + resource(icon))?.use { ImageIO.read(it) } ?: return@runCatching
                val themeRoot = File(home, ".local/share/icons/hicolor")
                for (size in listOf(48, 96, 192, 384)) {
                    val dir = File(themeRoot, "${size}x$size/apps")
                    if (!dir.isDirectory) continue
                    val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
                    val g = out.createGraphics()
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    g.drawImage(source, 0, 0, size, size, null)
                    g.dispose()
                    ImageIO.write(out, "png", File(dir, "w0y.png"))
                }
                if (Platform.has("gtk-update-icon-cache")) {
                    ProcessBuilder("gtk-update-icon-cache", "-f", "-t", themeRoot.absolutePath).redirectErrorStream(true).start().waitFor()
                }
            }
        }
    }
}

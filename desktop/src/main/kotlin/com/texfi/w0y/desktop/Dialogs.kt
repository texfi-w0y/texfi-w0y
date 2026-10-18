package com.texfi.w0y.desktop

/**
 * Системные диалоги выбора папки. AWT умеет выбирать только файл, поэтому спрашиваем
 * у zenity (GNOME и остальные) или kdialog (KDE) — что есть в системе; нет ни того ни другого — null.
 */
fun pickFolder(title: String): String? {
    // Windows и системы без zenity/kdialog: обычный диалог Swing, он открывает папки.
    fun swing(): String? {
        val chooser = javax.swing.JFileChooser(System.getProperty("user.home")).apply {
            dialogTitle = title
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }
    if (Platform.isWindows) return swing()
    val candidates = listOf(
        listOf("zenity", "--file-selection", "--directory", "--title=$title"),
        listOf("kdialog", "--getexistingdirectory", System.getProperty("user.home"), "--title", title),
    )
    for (command in candidates) {
        if (!Platform.has(command[0])) continue
        val result = runCatching {
            val p = ProcessBuilder(command).redirectErrorStream(false).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            if (p.waitFor() == 0 && out.isNotBlank()) out else null
        }.getOrNull()
        return result
    }
    return swing()
}

package com.texfi.w0y.desktop

/**
 * Системные диалоги выбора папки. AWT умеет выбирать только файл, поэтому спрашиваем
 * у zenity (GNOME и остальные) или kdialog (KDE) — что есть в системе; нет ни того ни другого — null.
 */
fun pickFolder(title: String): String? {
    val candidates = listOf(
        listOf("zenity", "--file-selection", "--directory", "--title=$title"),
        listOf("kdialog", "--getexistingdirectory", System.getProperty("user.home"), "--title", title),
    )
    for (command in candidates) {
        val exists = (System.getenv("PATH") ?: "").split(':').any { java.io.File(it, command[0]).canExecute() }
        if (!exists) continue
        val result = runCatching {
            val p = ProcessBuilder(command).redirectErrorStream(false).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            if (p.waitFor() == 0 && out.isNotBlank()) out else null
        }.getOrNull()
        return result
    }
    return null
}

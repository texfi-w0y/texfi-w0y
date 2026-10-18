package com.texfi.w0y.desktop

import com.texfi.w0y.data.SoundProfile
import kotlinx.coroutines.runBlocking

/** Проверка без окна: ./gradlew probe — поиск, звук, эффекты, текст, скачивание. */
fun main() = runBlocking {
    val yt = Yt()
    val found = yt.search("kai angel paris 2008")
    val song = found.songs.first()
    println("first: ${song.artist} - ${song.title} (${song.id})")
    val s = yt.stream(song.id)
    var last = 0.0
    val mpv = AudioEngine.create({ last = it }, {}, {}, { println("end: $it") }, { println("file-loaded") })
    println("mpv started: ${mpv.start()}")
    mpv.applyAudio(SoundProfile.Slowed, null, false)
    mpv.load(s.audioUrl, s.headers)
    Thread.sleep(6000)
    println("slowed time-pos=$last")
    mpv.applyAudio(SoundProfile(1.1f, 1.1f, com.texfi.w0y.data.Reverb.CAVE), null, false)
    Thread.sleep(2000)
    mpv.shutdown()
    val lyrics = LyricsRepo().lyrics(song)
    println("lyrics: synced=${lyrics?.synced?.size} plain=${lyrics?.plain?.length}")
    val tmp = java.nio.file.Files.createTempDirectory("w0y-probe").toFile()
    System.setProperty("user.home", tmp.absolutePath)
    val store = Store()
    val app = AppState(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default), yt, store)
    app.downloads.download(song)
    var n = 0
    while (app.downloadedPath(song.id) == null && n++ < 120) Thread.sleep(500)
    val path = app.downloadedPath(song.id)
    println("download: $path size=${path?.let { java.io.File(it).length() }}")
    tmp.deleteRecursively()
    System.exit(0)
}

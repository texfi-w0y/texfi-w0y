package com.texfi.w0y.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Действия меню трека: одно на всё приложение, а не по копии на каждый экран. */
@HiltViewModel
class SongMenuViewModel @Inject constructor(
    private val library: LibraryRepository,
    val player: PlayerConnection,
    private val downloads: DownloadsRepository,
) : ViewModel() {
    val playlists: StateFlow<List<PlaylistEntity>> =
        library.editablePlaylists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addToPlaylist(playlistId: Long, song: SongItem) =
        viewModelScope.launch { library.addToPlaylist(playlistId, song) }

    fun createPlaylistWith(name: String, song: SongItem) =
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            library.addToPlaylist(id, song)
        }

    fun download(song: SongItem) = downloads.download(song)

    fun playlistsWith(songId: String) = library.playlistsWith(songId)

    fun removeFromPlaylist(playlistId: Long, songId: String) =
        viewModelScope.launch { library.removeFromPlaylist(playlistId, songId) }
}

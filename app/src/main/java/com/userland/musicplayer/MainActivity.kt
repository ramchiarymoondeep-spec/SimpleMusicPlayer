package com.userland.musicplayer

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class AudioFile(val title: String, val path: String)

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MusicPlayerApp()
                }
            }
        }
    }

    @Composable
    fun MusicPlayerApp() {
        val context = LocalContext.current
        var playlist by remember { mutableStateOf<List<AudioFile>>(emptyList()) }
        var currentSongIndex by remember { mutableStateOf(-1) }
        var isPlaying by remember { mutableStateOf(false) }
        var permissionGranted by remember { mutableStateOf(false) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            permissionGranted = isGranted
            if (isGranted) {
                playlist = fetchMusicFiles(context)
            }
        }

        LaunchedEffect(Unit) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            permissionLauncher.launch(permission)
        }

        fun playSong(index: Int) {
            if (index !in playlist.indices) return
            
            mediaPlayer?.release()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(playlist[index].path)
                    prepare()
                    start()
                    
                    setOnCompletionListener {
                        val nextIndex = (index + 1) % playlist.size
                        currentSongIndex = nextIndex
                        playSong(nextIndex)
                    }
                }
                currentSongIndex = index
                isPlaying = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val currentTrackTitle = if (currentSongIndex >= 0) {
                    playlist[currentSongIndex].title
                } else {
                    "Select a song"
                }

                Text(text = currentTrackTitle, style = MaterialTheme.typography.titleLarge)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { 
                        if (playlist.isNotEmpty()) {
                            val prevIndex = if (currentSongIndex <= 0) playlist.size - 1 else currentSongIndex - 1
                            playSong(prevIndex)
                        }
                    }) { Text("Prev") }
                    
                    Button(onClick = { 
                        if (mediaPlayer != null) {
                            if (isPlaying) {
                                mediaPlayer?.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer?.start()
                                isPlaying = true
                            }
                        } else if (playlist.isNotEmpty()) {
                            playSong(0)
                        }
                    }) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }
                    
                    Button(onClick = { 
                        if (playlist.isNotEmpty()) {
                            val nextIndex = (currentSongIndex + 1) % playlist.size
                            playSong(nextIndex)
                        }
                    }) { Text("Next") }
                }
            }

            HorizontalDivider()

            if (!permissionGranted) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Permission required to load music.")
                }
            } else if (playlist.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No MP3 files found on device.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(playlist) { index, song ->
                        val isSelected = index == currentSongIndex
                        Text(
                            text = song.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { playSong(index) }
                                .padding(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    private fun fetchMusicFiles(context: Context): List<AudioFile> {
        val audioFiles = mutableListOf<AudioFile>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val title = cursor.getString(titleColumn)
                val path = cursor.getString(dataColumn)
                if (path.endsWith(".mp3", ignoreCase = true)) {
                    audioFiles.add(AudioFile(title, path))
                }
            }
        }
        return audioFiles
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

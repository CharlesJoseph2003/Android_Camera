package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import android.widget.Toast

import java.io.File

class GalleryActivity : ComponentActivity() {
    private lateinit var photoStorageManager: PhotoStorageManager
    private var refreshTrigger = mutableIntStateOf(0) // Add this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoStorageManager = PhotoStorageManager(this)

        setContent {
            GalleryScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("GalleryActivity", "onResume called - forcing refresh")
        // Force immediate refresh by recreating content
        refreshTrigger.intValue++
    }

    @Composable
    fun GalleryScreen() {
        var photos by remember { mutableStateOf<List<PhotoReference>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        // This will re-trigger when refreshTrigger changes
        LaunchedEffect(refreshTrigger.intValue) {
            Log.d("GalleryActivity", "Loading photos, trigger: ${refreshTrigger.intValue}")
            lifecycleScope.launch {
                photos = photoStorageManager.getAllPhotos()
                isLoading = false
                Log.d("GalleryActivity", "Loaded ${photos.size} photos")
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Photo Gallery (${photos.size})",
                    style = MaterialTheme.typography.headlineMedium
                )

                Button(onClick = { finish() }) {
                    Text("Back to Camera")
                }
            }

            // Photo Grid
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (photos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No photos yet. Take some photos!")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(photos) { photo ->
                        PhotoThumbnail(
                            photo = photo,
                            onClick = {
                                // Navigate to photo detail
                                val intent = Intent(this@GalleryActivity, PhotoDetailActivity::class.java)
                                intent.putExtra("photoId", photo.id)
                                startActivity(intent)
//                                Toast.makeText(this@GalleryActivity, "Photo detail coming soon", Toast.LENGTH_SHORT).show()

                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun PhotoThumbnail(photo: PhotoReference, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .aspectRatio(1f)
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(File(photo.thumbnailPath)),
                contentDescription = "Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
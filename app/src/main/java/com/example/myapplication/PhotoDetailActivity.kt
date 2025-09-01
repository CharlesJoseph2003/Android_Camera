package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PhotoDetailActivity : ComponentActivity() {
    private lateinit var photoStorageManager: PhotoStorageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoStorageManager = PhotoStorageManager(this)
        val photoId = intent.getStringExtra("photoId") ?: ""

        setContent {
            PhotoDetailScreen(photoId)
        }
    }

    @Composable
    fun PhotoDetailScreen(photoId: String) {
        var photo by remember { mutableStateOf<PhotoReference?>(null) }
        var metadata by remember { mutableStateOf<PhotoMetadataEntity?>(null) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(photoId) {
            lifecycleScope.launch {
                try {
                    // Get all photos and find the one with matching ID
                    val allPhotos = photoStorageManager.getAllPhotos()
                    photo = allPhotos.find { it.id == photoId }

                    // Get metadata for this photo
                    metadata = photoStorageManager.getPhotoMetadata(photoId)

                    isLoading = false
                } catch (e: Exception) {
                    isLoading = false
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with back button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Photo Detail",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Button(onClick = { finish() }) {
                        Text("Back")
                    }
                }

                // Full-size photo
                photo?.let { photoRef ->
                    Image(
                        painter = rememberAsyncImagePainter(File(photoRef.optimizedPath)),
                        contentDescription = "Full size photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(horizontal = 16.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                // Metadata card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Photo Information",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Divider()

                        // Timestamp
                        photo?.let {
                            MetadataRow(
                                label = "Date/Time:",
                                value = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                                    .format(Date(it.timestamp))
                            )
                        }

                        // Location info
                        metadata?.let { meta ->
                            if (meta.latitude != null && meta.longitude != null) {
                                MetadataRow(
                                    label = "Location:",
                                    value = "${meta.latitude}, ${meta.longitude}"
                                )
                            }

                            meta.address?.let { address ->
                                MetadataRow(
                                    label = "Address:",
                                    value = address
                                )
                            }

                            MetadataRow(
                                label = "Compass Direction:",
                                value = meta.compassDirection
                            )

                            MetadataRow(
                                label = "Device Orientation:",
                                value = meta.deviceOrientation
                            )
                        }

                        // Photo ID
                        MetadataRow(
                            label = "Photo ID:",
                            value = photoId
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun MetadataRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(2f)
            )
        }
    }
}
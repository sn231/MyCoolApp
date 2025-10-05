package com.example.myapplication.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.example.myapplication.data.AlbumCategory
import com.example.myapplication.data.DanmakuItem
import com.example.myapplication.data.MediaItem
import com.example.myapplication.data.MediaType
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val TAG = "PhotoAlbumScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@SuppressLint("MissingPermission")
@Composable
fun PhotoAlbumScreen(
    viewModel: PhotoAlbumViewModel = viewModel(),
    onMediaClick: (Long) -> Unit,
    onStartCollage: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val mediaItems by viewModel.allItems.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val scope = rememberCoroutineScope()

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }

    var selectedMediaItems by remember { mutableStateOf<Set<MediaItem>>(emptySet()) }
    val isInSelectionMode = selectedMediaItems.isNotEmpty()

    var selectedCategory by remember { mutableStateOf<AlbumCategory?>(null) }
    var showFavorites by remember { mutableStateOf(false) }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(categories) {
        if (selectedCategory == null && !showFavorites) {
            selectedCategory = categories.firstOrNull()
        }
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }

    var selectedTag by remember { mutableStateOf<String?>(null) }

    val currentTags = remember(mediaItems, selectedCategory, showFavorites) {
        derivedStateOf {
            mediaItems
                .filter { if (showFavorites) it.isFavorite else it.categoryId == selectedCategory?.id }
                .flatMap { it.tags }
                .distinct()
                .sorted()
        }
    }

    val imageLoader = ImageLoader.Builder(context)
        .components { add(VideoFrameDecoder.Factory()) }
        .build()

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            Log.d(TAG, "takePictureLauncher result: success=$success")
            if (success) {
                photoUri?.let { uri ->
                    scope.launch {
                        try {
                            Log.d(TAG, "Photo taken successfully, URI: $uri")
                            val categoryId = selectedCategory?.id ?: viewModel.allCategories.first().firstOrNull()?.id ?: 1L
                            val newPhoto = MediaItem(
                                uri = uri.toString(),
                                categoryId = categoryId,
                                type = MediaType.PHOTO,
                                latitude = currentLocation?.latitude,
                                longitude = currentLocation?.longitude
                            )
                            Log.d(TAG, "Preparing to insert new photo: $newPhoto")
                            viewModel.insert(newPhoto)
                            Log.d(TAG, "Successfully inserted new photo into ViewModel.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Crash occurred while inserting photo", e)
                        }
                    }
                } ?: run {
                    Log.w(TAG, "takePictureLauncher: photoUri is null after taking picture.")
                }
            } else {
                Log.e(TAG, "takePictureLauncher failed.")
            }
        }
    )

    val recordVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
        onResult = { success ->
            if (success) {
                videoUri?.let { uri ->
                    scope.launch {
                        val categoryId = selectedCategory?.id ?: viewModel.allCategories.first().firstOrNull()?.id ?: 1L

                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        retriever.release()
                        val duration = durationString?.toLongOrNull()

                        val newVideo = MediaItem(
                            uri = uri.toString(),
                            categoryId = categoryId,
                            type = MediaType.VIDEO,
                            latitude = currentLocation?.latitude,
                            longitude = currentLocation?.longitude,
                            duration = duration
                        )
                        viewModel.insert(newVideo)
                    }
                }
            }
        }
    )

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            Log.d(TAG, "requestCameraPermissionLauncher result: isGranted=$isGranted")
            if (isGranted) {
                createImageUri(context)?.let { uri ->
                    photoUri = uri
                    Log.d(TAG, "Camera permission granted, launching takePictureLauncher with uri: $uri")
                    takePictureLauncher.launch(uri)
                } ?: run {
                    Log.e(TAG, "Failed to create image URI after getting camera permission.")
                }
            } else {
                Log.w(TAG, "Camera permission denied.")
            }
        }
    )

    val requestVideoPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.getOrDefault(Manifest.permission.CAMERA, false) &&
                permissions.getOrDefault(Manifest.permission.RECORD_AUDIO, false)) {
                createVideoUri(context)?.let { uri ->
                    videoUri = uri
                    recordVideoLauncher.launch(uri)
                }
            } else { /*...*/ }
        }
    )

    val requestLocationPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            Log.d(TAG, "requestLocationPermissionsLauncher result: $permissions")
            if (permissions.values.any { it }) {
                Log.d(TAG, "Location permission granted, invoking pending action.")
                pendingAction?.invoke()
                pendingAction = null
            } else {
                Log.w(TAG, "Location permissions denied.")
            }
        }
    )

    fun takePictureWithLocation() {
        Log.d(TAG, "takePictureWithLocation called")
        val action: () -> Unit = {
            Log.d(TAG, "Executing take picture action")
            val takePictureContinuation = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera permission already granted.")
                    createImageUri(context)?.let { uri ->
                        photoUri = uri
                        Log.d(TAG, "Launching takePictureLauncher with uri: $uri")
                        takePictureLauncher.launch(uri)
                    } ?: run {
                        Log.e(TAG, "Failed to create image URI.")
                    }
                } else {
                    Log.d(TAG, "Camera permission not granted, requesting.")
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                Log.d(TAG, "Got last location: $location")
                currentLocation = location
                takePictureContinuation()
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location, proceeding without it", e)
                currentLocation = null
                takePictureContinuation()
            }
        }

        val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (locationPermissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            Log.d(TAG, "Location permission not granted, requesting.")
            pendingAction = action
            requestLocationPermissionsLauncher.launch(locationPermissions)
        } else {
            Log.d(TAG, "Location permission already granted, executing action.")
            action()
        }
    }

    fun recordVideoWithLocation() {
        val action: () -> Unit = {
            val recordVideoContinuation = {
                val videoPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                if (videoPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                    createVideoUri(context)?.let { uri ->
                        videoUri = uri
                        recordVideoLauncher.launch(uri)
                    }
                } else {
                    requestVideoPermissionsLauncher.launch(videoPermissions)
                }
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                currentLocation = location
                recordVideoContinuation()
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location for video, proceeding without it.", e)
                currentLocation = null
                recordVideoContinuation()
            }
        }

        val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (locationPermissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            pendingAction = action
            requestLocationPermissionsLauncher.launch(locationPermissions)
        } else {
            action()
        }
    }

    if (showAddCategoryDialog) {
        CategoryDialog(
            title = "添加新类别",
            confirmButtonText = "添加",
            categoryName = categoryName,
            onCategoryNameChange = { categoryName = it },
            onConfirm = {
                viewModel.insertCategory(AlbumCategory(name = categoryName))
                showAddCategoryDialog = false
                categoryName = ""
            },
            onDismiss = { showAddCategoryDialog = false; categoryName = "" }
        )
    }

    if (showEditCategoryDialog) {
        CategoryDialog(
            title = "编辑类别",
            confirmButtonText = "保存",
            categoryName = categoryName,
            onCategoryNameChange = { categoryName = it },
            onConfirm = {
                selectedCategory?.let { oldCategory ->
                    viewModel.updateCategory(oldCategory.copy(name = categoryName))
                }
                showEditCategoryDialog = false
                categoryName = ""
            },
            onDismiss = { showEditCategoryDialog = false; categoryName = "" }
        )
    }

    if (showDeleteCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCategoryDialog = false },
            title = { Text("删除类别") },
            text = { Text("你确定要删除 '${selectedCategory?.name}' 吗? 这不会删除该类别下的照片和视频。") },
            confirmButton = { Button(onClick = {
                selectedCategory?.let { categoryToDelete ->
                    viewModel.deleteCategory(categoryToDelete)
                    if (selectedCategory == categoryToDelete) {
                        selectedCategory = categories.firstOrNull()
                        showFavorites = false
                    }
                }
                showDeleteCategoryDialog = false
            }) { Text("删除") } },
            dismissButton = { Button(onClick = { showDeleteCategoryDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if(isInSelectionMode) {
                        IconButton(onClick = { onStartCollage(selectedMediaItems.map { it.uri }) }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "创建拼图")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${selectedMediaItems.size} selected")
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedMediaItems = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    } else {
                        IconButton(onClick = { showAddCategoryDialog = true }) { Icon(Icons.Default.Add, contentDescription = "添加类别") }
                        IconButton(onClick = { categoryName = selectedCategory?.name ?: ""; showEditCategoryDialog = true }, enabled = selectedCategory != null) { Icon(Icons.Default.Edit, contentDescription = "编辑类别") }
                        IconButton(onClick = { showDeleteCategoryDialog = true }, enabled = selectedCategory != null) { Icon(Icons.Default.Delete, contentDescription = "删除类别") }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { takePictureWithLocation() }) { Icon(Icons.Filled.PhotoCamera, contentDescription = "拍摄照片") }
                        IconButton(onClick = { recordVideoWithLocation() }) { Icon(Icons.Filled.Videocam, contentDescription = "录制视频") }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CategoryTabs(categories, selectedCategory, showFavorites, onCategorySelected = { category ->
                showFavorites = false
                selectedCategory = category
                selectedTag = null
            }, onFavoritesSelected = {
                showFavorites = true
                selectedCategory = null
                selectedTag = null
            })
            TagTabs(currentTags.value, selectedTag) { selectedTag = it }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f)
            ) {
                val filteredItems = mediaItems
                    .filter { if(showFavorites) it.isFavorite else it.categoryId == selectedCategory?.id }
                    .filter { selectedTag == null || it.tags.contains(selectedTag) }

                items(
                    items = filteredItems,
                    key = { item -> item.id }
                ) { item ->
                    val isSelected = selectedMediaItems.contains(item)
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = {
                                    if (isInSelectionMode) {
                                        if (isSelected) {
                                            selectedMediaItems -= item
                                        } else {
                                            if(selectedMediaItems.size < 9) selectedMediaItems += item
                                        }
                                    } else {
                                        onMediaClick(item.id)
                                    }
                                },
                                onLongClick = {
                                    if (!isInSelectionMode) {
                                        selectedMediaItems += item
                                    }
                                }
                            )
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            imageLoader = imageLoader,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (item.type == MediaType.VIDEO && item.duration != null) {
                            Text(
                                text = formatDuration(item.duration),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    mediaItem: MediaItem,
    viewModel: PhotoAlbumViewModel, // Added viewModel
    imageLoader: ImageLoader,
    categories: List<AlbumCategory>,
    onBack: () -> Unit,
    onUpdate: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit
) {
    var newTag by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showExifDialog by remember { mutableStateOf(false) }
    var newDanmaku by remember { mutableStateOf("") }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除媒体") },
            text = { Text("你确定要删除这个${if (mediaItem.type == MediaType.VIDEO) "视频" else "图片"}吗?") },
            confirmButton = {
                Button(onClick = {
                    onDelete(mediaItem)
                }) { Text("删除") }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("移动到") },
            text = {
                Column {
                    categories.filter { it.id != mediaItem.categoryId }.forEach { category ->
                        Text(
                            text = category.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdate(mediaItem.copy(categoryId = category.id))
                                    showMoveDialog = false
                                    onBack()
                                }
                                .padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = { },
            dismissButton = { Button(onClick = { showMoveDialog = false }) { Text("取消") } }
        )
    }
    if (showExifDialog) {
        ExifInfoDialog(mediaItem = mediaItem, onDismiss = { showExifDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("详情") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = {
                        onUpdate(mediaItem.copy(isFavorite = !mediaItem.isFavorite))
                    }) {
                        Icon(
                            imageVector = if (mediaItem.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "收藏"
                        )
                    }
                    IconButton(onClick = { showExifDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "信息")
                    }
                    IconButton(onClick = { showMoveDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "移动")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (mediaItem.type == MediaType.VIDEO) {
                    VideoPlayer(uri = mediaItem.uri.toUri(), modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = mediaItem.uri,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DanmakuOverlay(mediaItem.id, viewModel)
            }
            Column(modifier = Modifier.padding(16.dp)) {
                val formattedDate = remember(mediaItem.createdAt) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(mediaItem.createdAt))
                }
                Text(text = "拍摄于: $formattedDate", style = MaterialTheme.typography.bodySmall)
                mediaItem.latitude?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "GPS: %.4f, %.4f".format(mediaItem.latitude, mediaItem.longitude), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mediaItem.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text(tag) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val updatedTags = mediaItem.tags - tag
                                    onUpdate(mediaItem.copy(tags = updatedTags))
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "删除标签")
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("添加标签") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newTag.isNotBlank()) {
                            val updatedTags = mediaItem.tags + newTag
                            onUpdate(mediaItem.copy(tags = updatedTags))
                            newTag = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加标签")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newDanmaku,
                        onValueChange = { newDanmaku = it },
                        label = { Text("发送弹幕") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newDanmaku.isNotBlank()) {
                            viewModel.insertDanmaku(DanmakuItem(mediaId = mediaItem.id, text = newDanmaku))
                            newDanmaku = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送弹幕")
                    }
                }
            }
        }
    }
}

@Composable
fun ExifInfoDialog(mediaItem: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exifData = remember(mediaItem.uri) {
        try {
            context.contentResolver.openInputStream(mediaItem.uri.toUri())?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                listOf(
                    "拍摄日期" to exifInterface.getAttribute(ExifInterface.TAG_DATETIME),
                    "图片宽度" to exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH),
                    "图片高度" to exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH),
                    "相机制造商" to exifInterface.getAttribute(ExifInterface.TAG_MAKE),
                    "相机型号" to exifInterface.getAttribute(ExifInterface.TAG_MODEL),
                    "光圈值" to exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER),
                    "快门速度" to exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                    "ISO" to exifInterface.getAttribute(ExifInterface.TAG_ISO_SPEED),
                    "焦距" to exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
                    "GPS纬度" to exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
                    "GPS经度" to exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
                ).filter { it.second != null }
            }
        } catch (e: Exception) {
            // Log the error
            e.printStackTrace()
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("照片信息") },
        text = {
            if (exifData != null) {
                LazyColumn {
                    items(exifData) { (tag, value) ->
                        Text("$tag: $value")
                    }
                }
            } else {
                Text("无法加载EXIF信息。")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun DanmakuOverlay(mediaId: Long, viewModel: PhotoAlbumViewModel) {
    val danmakus by viewModel.getDanmakusForMedia(mediaId).collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        danmakus.forEach { danmaku ->
            val animatable = remember { Animatable(0f) }
            val randomY = remember { Random.nextFloat() * 0.7f + 0.1f }
            val randomDuration = remember { (Random.nextInt(5, 10) * 1000) }

            LaunchedEffect(danmaku.id) {
                while (true) {
                    animatable.snapTo(0f)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = randomDuration, easing = LinearEasing)
                    )
                    delay(Random.nextLong(1000, 2000))
                }
            }

            val xOffset = (screenWidth + 200.dp) * (1 - animatable.value) - 200.dp

            if (animatable.value < 1f) {
                Text(
                    text = danmaku.text,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = xOffset, y = (randomY * 200).dp), // Simplified y-offset logic
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


@Composable
fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = Media3MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(
        AndroidView(
            modifier = modifier,
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                }
            }
        )
    ) {
        onDispose { exoPlayer.release() }
    }
}

@Composable
fun CategoryTabs(
    categories: List<AlbumCategory>,
    selectedCategory: AlbumCategory?,
    showFavorites: Boolean,
    onCategorySelected: (AlbumCategory) -> Unit,
    onFavoritesSelected: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        item {
            Text(
                text = "我的收藏",
                modifier = Modifier.clickable { onFavoritesSelected() }.padding(16.dp),
                color = if (showFavorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        items(categories) { category ->
            Text(
                text = category.name,
                modifier = Modifier.clickable { onCategorySelected(category) }.padding(16.dp),
                color = if (category == selectedCategory && !showFavorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagTabs(allTags: List<String>, selectedTag: String?, onTagSelected: (String?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        item {
            FilterChip(
                selected = selectedTag == null,
                onClick = { onTagSelected(null) },
                label = { Text("全部") }
            )
        }
        items(allTags) { tag ->
            FilterChip(
                selected = tag == selectedTag,
                onClick = { onTagSelected(tag) },
                label = { Text(tag) }
            )
        }
    }
}

@Composable
fun CategoryDialog(
    title: String, confirmButtonText: String, categoryName: String,
    onCategoryNameChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TextField(value = categoryName, onValueChange = onCategoryNameChange, label = { Text("类别名称") }) },
        confirmButton = { Button(onClick = { if (categoryName.isNotBlank()) { onConfirm() } }) { Text(confirmButtonText) } },
        dismissButton = { Button(onClick = onDismiss) { Text("取消") } }
    )
}

private suspend fun saveBitmapToUri(context: Context, bitmap: Bitmap): Uri? {
    return withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "COLLAGE_${timeStamp}_")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MyApplication")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { out: OutputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                        throw Exception("Failed to save bitmap.")
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save bitmap to URI", e)
                // Attempt to delete the partially created entry on failure
                context.contentResolver.delete(it, null, null)
                return@withContext null
            }
        }
        uri
    }
}

private fun createImageUri(context: Context): Uri? {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "JPEG_${timeStamp}_")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MyApplication") }
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}

private fun createVideoUri(context: Context): Uri? {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "MP4_${timeStamp}_")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/MyApplication") }
    }
    return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
}

private fun formatDuration(long: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(long)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(long) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

package com.example.myapplication.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Defines the data structure for a collage template.
 * @param count The number of images for this template.
 * @param layout A function that, given the canvas width and height, returns a list of rectangles, each defining the position and size of an image in the collage.
 */
data class CollageTemplate(val count: Int, val layout: (width: Float, height: Float) -> List<Rect>)

/**
 * Entry Composable for the collage creation screen.
 *
 * @param imageUris List of Uris for the images selected by the user.
 * @param onBack Callback for when the user clicks the back button.
 * @param onSave Callback to save the generated collage bitmap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(imageUris: List<String>, onBack: () -> Unit, onSave: (Bitmap) -> Unit) {
    // Predefined collage templates for 2-9 images
    val templates = remember {
        listOf(
            CollageTemplate(2) { w, h -> listOf(Rect(0f, 0f, w / 2, h), Rect(w / 2, 0f, w, h)) },
            CollageTemplate(3) { w, h -> listOf(Rect(0f, 0f, w / 2, h), Rect(w / 2, 0f, w, h / 2), Rect(w / 2, h / 2, w, h)) },
            CollageTemplate(4) { w, h -> listOf(Rect(0f, 0f, w / 2, h / 2), Rect(w / 2, 0f, w, h / 2), Rect(0f, h / 2, w / 2, h), Rect(w / 2, h / 2, w, h)) },
            CollageTemplate(5) { w, h -> listOf(Rect(0f, 0f, w / 2, h / 3), Rect(w / 2, 0f, w, h / 3), Rect(0f, h / 3, w / 3, h), Rect(w / 3, h / 3, 2 * w / 3, h), Rect(2 * w / 3, h / 3, w, h)) },
            CollageTemplate(6) { w, h -> listOf(Rect(0f, 0f, w / 3, h / 2), Rect(w / 3, 0f, 2 * w / 3, h / 2), Rect(2 * w / 3, 0f, w, h / 2), Rect(0f, h / 2, w / 3, h), Rect(w / 3, h / 2, 2 * w / 3, h), Rect(2 * w / 3, h / 2, w, h)) },
            CollageTemplate(7) { w, h -> listOf(Rect(0f, 0f, w / 3, h / 3), Rect(w / 3, 0f, 2 * w / 3, h / 3), Rect(2 * w / 3, 0f, w, h / 3), Rect(0f, h / 3, w / 2, 2 * h / 3), Rect(w / 2, h / 3, w, 2 * h / 3), Rect(0f, 2 * h / 3, w / 2, h), Rect(w / 2, 2 * h / 3, w, h)) },
            CollageTemplate(8) { w, h -> listOf(Rect(0f, 0f, w / 2, h / 4), Rect(w / 2, 0f, w, h / 4), Rect(0f, h / 4, w / 2, 2 * h / 4), Rect(w / 2, h / 4, w, 2 * h / 4), Rect(0f, 2*h/4, w/2, 3*h/4), Rect(w/2, 2*h/4, w, 3*h/4), Rect(0f, 3*h/4, w/2, h), Rect(w/2, 3*h/4, w, h)) },
            CollageTemplate(9) { w, h -> listOf(Rect(0f, 0f, w/3, h/3), Rect(w/3, 0f, 2*w/3, h/3), Rect(2*w/3, 0f, w, h/3), Rect(0f, h/3, w/3, 2*h/3), Rect(w/3, h/3, 2*w/3, 2*h/3), Rect(2*w/3, h/3, w, 2*h/3), Rect(0f, 2*h/3, w/3, h), Rect(w/3, 2*h/3, 2*w/3, h), Rect(2*w/3, 2*h/3, w, h)) }
        )
    }

    // State: holds the selected template
    var selectedTemplate by remember { mutableStateOf<CollageTemplate?>(null) }
    // State: holds the final generated collage Bitmap
    var collageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建拼图") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                actions = {
                    // Save button, enabled only after collage is generated
                    IconButton(
                        onClick = {
                            collageBitmap?.let {
                                onSave(it)
                                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = collageBitmap != null
                    ) { Icon(Icons.Default.Save, contentDescription = "保存") }
                    // Share button, enabled only after collage is generated
                    IconButton(
                        onClick = {
                            collageBitmap?.let {
                                scope.launch {
                                    shareCollage(context, it)
                                }
                            }
                        },
                        enabled = collageBitmap != null
                    ) { Icon(Icons.Default.Share, contentDescription = "分享") }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // If a template hasn't been selected, show the template selection UI
            // Otherwise, show the collage creation UI
            if (selectedTemplate == null) {
                // Filter available templates based on the number of selected images
                val availableTemplates = templates.filter { it.count == imageUris.size }
                if (availableTemplates.isNotEmpty()) {
                    TemplateSelection(
                        templates = availableTemplates,
                        onTemplateSelected = { selectedTemplate = it }
                    )
                } else {
                    // Show a message if no suitable template is found
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有找到适合 ${imageUris.size} 张图片的模板。")
                    }
                }
            } else {
                CollageContent(
                    imageUris = imageUris,
                    template = selectedTemplate!!,
                    onCollageGenerated = { collageBitmap = it }
                )
            }
        }
    }
}

/**
 * Template selection UI.
 * Displays a horizontally scrollable list of template previews.
 *
 * @param templates List of templates.
 * @param onTemplateSelected Callback for when a template is selected.
 */
@Composable
fun TemplateSelection(templates: List<CollageTemplate>, onTemplateSelected: (CollageTemplate) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("请选择一个模板", style = MaterialTheme.typography.titleMedium)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            items(templates) { template ->
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(120.dp)
                        .border(1.dp, Color.Gray)
                        .clickable { onTemplateSelected(template) }
                        .padding(2.dp)
                ) {
                    // Draw a preview of the template layout on a Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val layout = template.layout(size.width, size.height)
                        layout.forEach { rect ->
                            drawRect(
                                color = Color.LightGray,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Core collage generation and display area.
 *
 * @param imageUris List of image Uris.
 * @param template The selected template.
 * @param onCollageGenerated Callback for when the collage Bitmap is generated.
 */
@Composable
fun CollageContent(imageUris: List<String>, template: CollageTemplate, onCollageGenerated: (Bitmap) -> Unit) {
    val context = LocalContext.current
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("图片加载失败，请重试。") } // **** 我加的改动 ****

    // 打印一下传进来的URI是什么鬼东西，这是破案第一步
    LaunchedEffect(imageUris) {
        Log.d("CollageDebug", "收到的URI列表: $imageUris")
    }

    LaunchedEffect(imageUris, template) {
        // Reset state
        generatedBitmap = null
        hasError = false
        errorMessage = "图片加载失败，请重试。"

        val finalBitmap = withContext(Dispatchers.IO) {
            val imageLoader = ImageLoader(context)
            val canvasWidth = 2160
            val canvasHeight = 2160
            val layoutRects = template.layout(canvasWidth.toFloat(), canvasHeight.toFloat())

            // **** 下面是重点改动区域 ****
            val imageBitmaps = imageUris.mapIndexedNotNull { index, uriString ->
                if (index >= layoutRects.size) return@mapIndexedNotNull null

                Log.d("CollageDebug", "开始加载第 ${index + 1} 张图片: $uriString")
                val targetRect = layoutRects[index]
                val request = ImageRequest.Builder(context)
                    .data(uriString.toUri())
                    .size(targetRect.width.toInt(), targetRect.height.toInt())
                    .allowHardware(false)
                    .build()
                try {
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        Log.d("CollageDebug", "第 ${index + 1} 张图片加载成功")
                        result.drawable.toBitmap()
                    } else {
                        val errorResult = result as ErrorResult
                        // 把Coil的失败原因打印出来！这才是关键！
                        Log.e("CollageDebug", "第 ${index + 1} 张图片加载失败: $uriString", errorResult.throwable)
                        // 把错误信息存起来，显示给用户看
                        withContext(Dispatchers.Main) {
                            errorMessage = "加载图片失败: ${errorResult.throwable.message}"
                        }
                        null
                    }
                } catch (e: Exception) {
                    // 捕获任何可能的异常，比如URI格式错误
                    Log.e("CollageDebug", "加载图片时发生未知异常: $uriString", e)
                    withContext(Dispatchers.Main) {
                        errorMessage = "加载异常: ${e.message}"
                    }
                    null
                }
            }
            // **** 改动结束 ****

            if (imageBitmaps.size != imageUris.size) {
                Log.e("CollageDebug", "图片加载数量不匹配！预期 ${imageUris.size}, 实际 ${imageBitmaps.size}")
                return@withContext null
            }

            // ... (后面的拼图逻辑完全不变) ...
            val resultBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(resultBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            imageBitmaps.forEachIndexed { index, bmp ->
                if (index >= layoutRects.size) return@forEachIndexed
                val destRect = layoutRects[index].toRectF()
                destRect.inset(16f, 16f)
                val matrix = Matrix()
                val srcWidth = bmp.width.toFloat()
                val srcHeight = bmp.height.toFloat()
                val destWidth = destRect.width()
                val destHeight = destRect.height()
                val scale = if (srcWidth * destHeight > destWidth * srcHeight) destHeight / srcHeight else destWidth / srcWidth
                val dx = (destWidth - srcWidth * scale) * 0.5f
                val dy = (destHeight - srcHeight * scale) * 0.5f
                matrix.setScale(scale, scale)
                matrix.postTranslate(destRect.left + dx, destRect.top + dy)
                canvas.save()
                canvas.clipRect(destRect)
                canvas.drawBitmap(bmp, matrix, paint)
                canvas.restore()
            }
            resultBitmap
        }

        if (finalBitmap != null) {
            generatedBitmap = finalBitmap
            onCollageGenerated(finalBitmap)
        } else {
            hasError = true
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            hasError -> {
                // 现在会显示更详细的错误信息
                Text(errorMessage)
            }
            generatedBitmap == null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在生成拼图...")
                }
            }
            else -> {
                // ... (显示拼图的Canvas逻辑不变) ...
                Canvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
                    val bmp = generatedBitmap!!
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()
                    val canvasRatio = canvasWidth / canvasHeight
                    val (destWidth, destHeight, destLeft, destTop) = if (bmpRatio > canvasRatio) {
                        val w = canvasWidth; val h = w / bmpRatio; Quad(w, h, 0f, (canvasHeight - h) / 2f)
                    } else {
                        val h = canvasHeight; val w = h * bmpRatio; Quad(w, h, (canvasWidth - w) / 2f, 0f)
                    }
                    val destRect = android.graphics.RectF(destLeft, destTop, destLeft + destWidth, destTop + destHeight)
                    drawContext.canvas.nativeCanvas.drawBitmap(bmp, null, destRect, null)
                }
            }
        }
    }
}


/**
 * Shares the collage.
 * Saves the Bitmap to the app's cache and uses FileProvider to create a secure content URI for sharing.
 *
 * @param context Context.
 * @param bitmap The Bitmap to share.
 */
private suspend fun shareCollage(context: Context, bitmap: Bitmap) {
    withContext(Dispatchers.IO) {
        try {
            // 1. Save the image to the cache directory
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val imageFile = File(cachePath, "image.png")
            FileOutputStream(imageFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            // 2. Get Uri using FileProvider
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)

            // 3. Create a share Intent and start activity on the main thread
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享拼图"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Extension function to convert a Compose Rect to an Android graphics RectF.
 */
fun androidx.compose.ui.geometry.Rect.toRectF(): android.graphics.RectF {
    return android.graphics.RectF(this.left, this.top, this.right, this.bottom)
}

data class Quad<T, U, V, W>(val first: T, val second: U, val third: V, val fourth: W)
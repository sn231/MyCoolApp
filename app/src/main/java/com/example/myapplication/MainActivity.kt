package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.ui.CollageScreen
import com.example.myapplication.ui.MediaDetailScreen
import com.example.myapplication.ui.PhotoAlbumScreen
import com.example.myapplication.ui.PhotoAlbumViewModel
import com.example.myapplication.ui.PhotoAlbumViewModelFactory
import com.example.myapplication.ui.theme.MyApplicationTheme
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoAlbumViewModel by viewModels {
        PhotoAlbumViewModelFactory((application as MyApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: PhotoAlbumViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    NavHost(navController = navController, startDestination = "photo_album") {
        composable("photo_album") {
            PhotoAlbumScreen(
                viewModel = viewModel,
                onMediaClick = { mediaId ->
                    navController.navigate("media_detail/$mediaId")
                },
                onStartCollage = { imageUris ->
                    val encodedUris = imageUris.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
                    navController.navigate("collage_screen/$encodedUris")
                }
            )
        }
        composable(
            "media_detail/{mediaId}",
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId")
            val allItems by viewModel.allItems.collectAsState()
            val allCategories by viewModel.allCategories.collectAsState()
            val mediaItem = allItems.find { it.id == mediaId }
            if (mediaItem != null) {
                MediaDetailScreen(
                    mediaItem = mediaItem,
                    viewModel = viewModel,
                    imageLoader = imageLoader,
                    categories = allCategories,
                    onBack = { navController.popBackStack() },
                    onUpdate = { updatedItem ->
                        viewModel.update(updatedItem)
                    },
                    onDelete = { itemToDelete ->
                        viewModel.delete(itemToDelete)
                        navController.popBackStack()
                    }
                )
            }
        }
        composable(
            "collage_screen/{imageUris}",
            arguments = listOf(navArgument("imageUris") { type = NavType.StringType })
        ) {
            val imageUris = it.arguments?.getString("imageUris")?.split(",")?.map { URLDecoder.decode(it, "UTF-8") } ?: emptyList()
            CollageScreen(
                imageUris = imageUris,
                onBack = { navController.popBackStack() },
                onSave = { bitmap ->
                    // viewModel.saveCollage(bitmap) // Assuming you have a method to save the collage
                }
            )
        }
    }
}

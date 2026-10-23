package com.cleanlr.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cleanlr.catalog.CatalogRepository
import com.cleanlr.catalog.MediaAsset
import com.cleanlr.catalog.MediaStoreScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LightroomWorkspace(val label: String) {
    GALLERY("Gallery"),
    LIGHTROOM("Lightroom"),
    COMMUNITY("Community")
}

@Composable
fun LightroomLibraryScreen(
    onPhotoSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imagePermission = remember { imageReadPermission() }
    var hasPhotoAccess by remember { mutableStateOf(hasImageReadPermission(context, imagePermission)) }
    var photos by remember { mutableStateOf<List<MediaAsset>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var workspace by remember { mutableStateOf(LightroomWorkspace.LIGHTROOM) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var rawOnly by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf("Imports") }
    var showCollectionMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val repository = remember { CatalogRepository() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPhotoAccess = granted
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(onPhotoSelected)
    }

    LaunchedEffect(Unit) {
        if (!hasPhotoAccess) permissionLauncher.launch(imagePermission)
    }
    LaunchedEffect(hasPhotoAccess) {
        if (!hasPhotoAccess) return@LaunchedEffect
        isScanning = true
        photos = withContext(Dispatchers.IO) {
            repository.clear()
            MediaStoreScanner.scanMediaStore(context, repository)
            repository.getPagedAssets(pageSize = 5000).items
        }
        isScanning = false
    }

    val visiblePhotos = remember(photos, query, rawOnly) {
        photos.filter { photo ->
            (query.isBlank() || photo.fileName.contains(query, ignoreCase = true)) &&
                (!rawOnly || photo.isRaw)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Color(0xFF1D1D1D),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = workspace == LightroomWorkspace.GALLERY,
                    onClick = { workspace = LightroomWorkspace.GALLERY },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    label = { Text("Gallery", fontSize = 11.sp) },
                    colors = libraryNavigationColors()
                )
                NavigationBarItem(
                    selected = workspace == LightroomWorkspace.LIGHTROOM,
                    onClick = { workspace = LightroomWorkspace.LIGHTROOM },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    label = { Text("Lightroom", fontSize = 11.sp) },
                    colors = libraryNavigationColors()
                )
                NavigationBarItem(
                    selected = workspace == LightroomWorkspace.COMMUNITY,
                    onClick = { workspace = LightroomWorkspace.COMMUNITY },
                    icon = { Icon(Icons.Default.Public, contentDescription = null) },
                    label = { Text("Community", fontSize = 11.sp) },
                    colors = libraryNavigationColors()
                )
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .background(Color(0xFF1D1D1D))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(Icons.Default.Menu, contentDescription = "Open menu", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Star, contentDescription = "Premium", tint = Color.White)
                }
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Cloud, contentDescription = "Cloud sync", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh photos") },
                            onClick = {
                                showOverflowMenu = false
                                hasPhotoAccess = hasImageReadPermission(context, imagePermission)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add photos") },
                            onClick = {
                                showOverflowMenu = false
                                importLauncher.launch("image/*")
                            }
                        )
                    }
                }
            }

            if (workspace == LightroomWorkspace.COMMUNITY) {
                CommunityWorkspace()
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (searchOpen) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Search photos", color = Color.Gray) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color(0xFF1686FF),
                                unfocusedIndicatorColor = Color.DarkGray
                            )
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showCollectionMenu = true }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (workspace == LightroomWorkspace.GALLERY) "Photos" else collection,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Choose collection",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = visiblePhotos.size.toString() + " photos",
                                color = Color(0xFF9B9B9B),
                                fontSize = 12.sp
                            )
                        }
                        DropdownMenu(
                            expanded = showCollectionMenu,
                            onDismissRequest = { showCollectionMenu = false }
                        ) {
                            listOf("Imports", "All photos", "Recently added").forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        collection = item
                                        showCollectionMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter photos", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (rawOnly) "Show all photos" else "RAW photos only") },
                                onClick = {
                                    rawOnly = !rawOnly
                                    showFilterMenu = false
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        !hasPhotoAccess -> EmptyLibrary(
                            title = "See photos on this device",
                            message = "Allow photo access to show your Lightroom library.",
                            action = "Allow access",
                            onAction = { permissionLauncher.launch(imagePermission) }
                        )
                        isScanning -> EmptyLibrary(
                            title = "Loading photos",
                            message = "Your Imports will appear here.",
                            action = null,
                            onAction = {}
                        )
                        visiblePhotos.isEmpty() -> EmptyLibrary(
                            title = "No photos found",
                            message = "Add a photo from your device to start editing.",
                            action = "Add photos",
                            onAction = { importLauncher.launch("image/*") }
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            itemsIndexed(
                                items = visiblePhotos,
                                key = { _, photo -> photo.id },
                                span = { index, _ ->
                                    if (index % 7 == 2) GridItemSpan(2) else GridItemSpan(1)
                                }
                            ) { index, photo ->
                                LibraryPhotoTile(
                                    photo = photo,
                                    wide = index % 7 == 2,
                                    onClick = { onPhotoSelected(photo.uri) }
                                )
                            }
                        }
                    }

                    if (workspace == LightroomWorkspace.LIGHTROOM) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 18.dp, bottom = 18.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0xFF1677E8)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { importLauncher.launch("image/*") }) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add photos", tint = Color.White)
                            }
                            Box(Modifier.width(1.dp).height(28.dp).background(Color(0xFF6AA9F2)))
                            IconButton(onClick = { importLauncher.launch("image/*") }) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityWorkspace() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF1686FF), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Discover the community", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Explore edits and inspiration from photographers.",
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun EmptyLibrary(
    title: String,
    message: String,
    action: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color(0xFFAAAAAA), fontSize = 13.sp)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun LibraryPhotoTile(
    photo: MediaAsset,
    wide: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = photo.uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadPhotoThumbnail(context, photo.uri) }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (wide) 1.65f else 0.92f)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF282828))
            .clickable(onClick = onClick)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = photo.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (photo.isRaw) {
            Text(
                "RAW",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )
        }
        Text(
            text = photo.fileName,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x66000000))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

private suspend fun loadPhotoThumbnail(context: Context, uri: Uri): android.graphics.Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return context.contentResolver.loadThumbnail(uri, Size(640, 640), null)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply {
        inSampleSize = 4
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun imageReadPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasImageReadPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun libraryNavigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color(0xFF1686FF),
    selectedTextColor = Color(0xFF1686FF),
    indicatorColor = Color.Transparent,
    unselectedIconColor = Color(0xFFB7B7B7),
    unselectedTextColor = Color(0xFFB7B7B7)
)

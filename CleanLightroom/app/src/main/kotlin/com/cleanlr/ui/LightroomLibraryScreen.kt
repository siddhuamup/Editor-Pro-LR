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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.painterResource
import com.cleanlr.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LightroomWorkspace(val label: String) {
    GALLERY("Gallery"),
    LIGHTROOM("Lightroom"),
    COMMUNITY("Community")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightroomLibraryScreen(
    onPhotoSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val imagePermission = remember { imageReadPermission() }
    var hasPhotoAccess by remember { mutableStateOf(hasImageReadPermission(context, imagePermission)) }
    var photos by remember { mutableStateOf<List<MediaAsset>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var workspace by remember { mutableStateOf(LightroomWorkspace.LIGHTROOM) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var rawOnly by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf("Imports") }
    var galleryCollection by remember { mutableStateOf("Photos on device") }
    var showCollectionMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showViewOptionsSheet by remember { mutableStateOf(false) }

    // View Options state (Reference Screenshot 36)
    var isJustifiedGrid by remember { mutableStateOf(true) }
    var isGridSegmented by remember { mutableStateOf(true) }
    var segmentBy by remember { mutableStateOf("Date") }

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

    // Modal Navigation Drawer (Reference Screenshot 2)
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF1E1E1E),
                drawerContentColor = Color.White,
                modifier = Modifier.width(310.dp)
            ) {
                LibraryNavigationDrawerContent(
                    onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
                )
            }
        }
    ) {
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
                        icon = { Icon(painter = painterResource(R.drawable.ic_lr_device_tab), contentDescription = null, modifier = Modifier.size(24.dp)) },
                        label = { Text("Gallery", fontSize = 11.sp) },
                        colors = libraryNavigationColors()
                    )
                    NavigationBarItem(
                        selected = workspace == LightroomWorkspace.LIGHTROOM,
                        onClick = { workspace = LightroomWorkspace.LIGHTROOM },
                        icon = { Icon(painter = painterResource(R.drawable.ic_lr_lightroom_cloud_tab), contentDescription = null, modifier = Modifier.size(24.dp)) },
                        label = { Text("Lightroom", fontSize = 11.sp) },
                        colors = libraryNavigationColors()
                    )
                    NavigationBarItem(
                        selected = workspace == LightroomWorkspace.COMMUNITY,
                        onClick = { workspace = LightroomWorkspace.COMMUNITY },
                        icon = { Icon(painter = painterResource(R.drawable.ic_lr_community_tab), contentDescription = null, modifier = Modifier.size(24.dp)) },
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
                // Top Bar with Hamburger, Premium, Search, Bell, Cloud, Overflow
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .background(Color(0xFF1D1D1D))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Navigation drawer", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    if (workspace == LightroomWorkspace.LIGHTROOM) {
                        IconButton(onClick = { searchOpen = !searchOpen }) {
                            Icon(painter = painterResource(R.drawable.ic_lr_search_bar), contentDescription = "Search", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { }) {
                            Icon(painter = painterResource(R.drawable.ic_lr_notifications_bell), contentDescription = "Notifications", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { }) {
                            Icon(painter = painterResource(R.drawable.ic_lr_cloud_uploading), contentDescription = "Cloud sync", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    IconButton(onClick = { showViewOptionsSheet = true }) {
                        Icon(painter = painterResource(R.drawable.ic_lr_more_vert), contentDescription = "View options and more", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                if (workspace == LightroomWorkspace.COMMUNITY) {
                    CommunityWorkspace()
                } else {
                    // Header Row: Dropdown, Photo Count, Filter & Sort
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
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
                                        text = if (workspace == LightroomWorkspace.GALLERY) galleryCollection else collection,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Choose collection",
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    text = "${visiblePhotos.size} photos",
                                    color = Color(0xFF9B9B9B),
                                    fontSize = 12.sp
                                )
                            }
                            DropdownMenu(
                                expanded = showCollectionMenu,
                                onDismissRequest = { showCollectionMenu = false }
                            ) {
                                val options = if (workspace == LightroomWorkspace.GALLERY) {
                                    listOf("Photos on device", "Camera", "Screenshots", "WhatsApp")
                                } else {
                                    listOf("Imports", "All photos", "Recently added", "Edited")
                                }
                                options.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = {
                                            if (workspace == LightroomWorkspace.GALLERY) {
                                                galleryCollection = item
                                            } else {
                                                collection = item
                                            }
                                            showCollectionMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Filter Button
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(painter = painterResource(R.drawable.ic_lr_filter_funnel), contentDescription = "Filter photos", tint = Color.White, modifier = Modifier.size(20.dp))
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

                        // Sort Button (Shown only in Gallery workspace: Screenshot 082951 vs 083018)
                        if (workspace == LightroomWorkspace.GALLERY) {
                            IconButton(onClick = { }) {
                                Icon(painter = painterResource(R.drawable.ic_lr_sort_order), contentDescription = "Sort photos", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Photo Grid Area
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
                                message = "Your photos will appear here shortly.",
                                action = null,
                                onAction = {}
                            )
                            visiblePhotos.isEmpty() -> EmptyLibrary(
                                title = "No photos found",
                                message = "Add a photo from your device to start editing.",
                                action = "Add photos",
                                onAction = { importLauncher.launch("image/*") }
                            )
                            else -> {
                                val isGallery = workspace == LightroomWorkspace.GALLERY
                                val columnCount = if (isGallery) 4 else if (isJustifiedGrid) 2 else 3

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columnCount),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 80.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    // Date Section Header if segmented (Screenshot 1: "October 17, 2026", "18 ▾")
                                    if (isGridSegmented && visiblePhotos.isNotEmpty()) {
                                        item(span = { GridItemSpan(columnCount) }) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val dateLabel = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())
                                                Text(
                                                    text = dateLabel,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    color = Color(0xFF2C2C2C),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${visiblePhotos.size}",
                                                            color = Color(0xFFAAAAAA),
                                                            fontSize = 11.sp
                                                        )
                                                        Icon(
                                                            Icons.Default.ArrowDropDown,
                                                            contentDescription = null,
                                                            tint = Color(0xFFAAAAAA),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    itemsIndexed(
                                        items = visiblePhotos,
                                        key = { _, photo -> photo.id },
                                        span = { index, _ ->
                                            if (!isGallery && isJustifiedGrid && index % 7 == 2) GridItemSpan(2) else GridItemSpan(1)
                                        }
                                    ) { index, photo ->
                                        LibraryPhotoTile(
                                            photo = photo,
                                            wide = !isGallery && isJustifiedGrid && index % 7 == 2,
                                            compact = isGallery,
                                            onClick = { onPhotoSelected(photo.uri) }
                                        )
                                    }
                                }
                            }
                        }

                        // Lightroom Tab Dual-Pill Blue FAB (Screenshot 3: Add Photo | Camera)
                        if (workspace == LightroomWorkspace.LIGHTROOM) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 18.dp, bottom = 18.dp),
                                shape = RoundedCornerShape(28.dp),
                                color = Color(0xFF1677E8),
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.height(52.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { importLauncher.launch("image/*") },
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Icon(painter = painterResource(R.drawable.ic_lr_import_add), contentDescription = "Add photos", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    Box(
                                        Modifier
                                            .width(1.dp)
                                            .height(26.dp)
                                            .background(Color(0xFF6AA9F2))
                                    )
                                    IconButton(
                                        onClick = { importLauncher.launch("image/*") },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Icon(painter = painterResource(R.drawable.ic_lr_camera_pro), contentDescription = "Camera", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // View Options Bottom Sheet (Reference Screenshot 36)
    if (showViewOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showViewOptionsSheet = false },
            containerColor = Color(0xFF222222),
            contentColor = Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF555555))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Section: BATCH EDIT
                Text(
                    text = "BATCH EDIT",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showViewOptionsSheet = false }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.ic_lr_select_all), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Select", color = Color.White, fontSize = 15.sp)
                }

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 4.dp))

                // Section: VIEW OPTIONS
                Text(
                    text = "VIEW OPTIONS",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isJustifiedGrid = false; showViewOptionsSheet = false }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.ic_lr_grid_level_overlay), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Square grid", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (!isJustifiedGrid) {
                        Icon(painter = painterResource(R.drawable.ic_lr_select_checkmark), contentDescription = "Active", tint = Color(0xFF1686FF), modifier = Modifier.size(18.dp))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isJustifiedGrid = true; showViewOptionsSheet = false }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.ic_lr_grid_segmentation), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Justified grid", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (isJustifiedGrid) {
                        Icon(painter = painterResource(R.drawable.ic_lr_select_checkmark), contentDescription = "Active", tint = Color(0xFF1686FF), modifier = Modifier.size(18.dp))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.ic_lr_grid_segmentation), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Grid segmentation", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = isGridSegmented,
                        onCheckedChange = { isGridSegmented = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1686FF)
                        )
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            segmentBy = if (segmentBy == "Date") "Device" else "Date"
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.ic_lr_grid_segmentation), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Segment by", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(segmentBy, color = Color(0xFF999999), fontSize = 14.sp)
                    Icon(painter = painterResource(R.drawable.ic_lr_variation_next), contentDescription = null, tint = Color(0xFF999999), modifier = Modifier.size(16.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showViewOptionsSheet = false }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.ic_lr_slideshow), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Slideshow", color = Color.White, fontSize = 15.sp)
                }

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showViewOptionsSheet = false
                            importLauncher.launch("image/*")
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.ic_lr_import_add), contentDescription = null, tint = Color(0xFF1686FF), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Add photos", color = Color(0xFF1686FF), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Slide-out Navigation Drawer content matching Reference Screenshot 2
 */
@Composable
private fun LibraryNavigationDrawerContent(
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // User Profile Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333))
                    .border(1.5.dp, Color(0xFF555555), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "User Avatar",
                    tint = Color(0xFFB0B0B0),
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "CleanLightroom",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Local Library • Device Storage",
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF2C2C2C))

        // Navigation Items (Reference Screenshot 2)
        DrawerItem(icon = Icons.Default.Person, label = "Account", onClick = onCloseDrawer)
        DrawerItem(
            painter = painterResource(R.drawable.ic_lr_star_filled),
            label = "Premium Features",
            tint = Color(0xFF1686FF),
            labelColor = Color(0xFF1686FF),
            onClick = onCloseDrawer
        )
        DrawerItem(icon = Icons.Default.Science, label = "Early access", onClick = onCloseDrawer)
        DrawerItem(icon = Icons.Default.Settings, label = "Preferences", onClick = onCloseDrawer)
        DrawerItem(icon = Icons.Default.Campaign, label = "What's New", onClick = onCloseDrawer)
        DrawerItem(painter = painterResource(R.drawable.ic_lr_mobile_badge), label = "About Lightroom", onClick = onCloseDrawer)
        DrawerItem(painter = painterResource(R.drawable.ic_lr_device_tab), label = "Local storage", onClick = onCloseDrawer)
        DrawerItem(painter = painterResource(R.drawable.ic_lr_context_help), label = "Help & Support", onClick = onCloseDrawer)
        DrawerItem(painter = painterResource(R.drawable.ic_lr_star_filled), tint = Color(0xFFFFB300), label = "Rate Lightroom", onClick = onCloseDrawer)

        HorizontalDivider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 8.dp))

        // Social Icons Row (Screenshot 2: Facebook, Instagram, X)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(R.drawable.ic_lr_social_facebook), contentDescription = "Facebook", tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
            Icon(painter = painterResource(R.drawable.ic_lr_social_instagram), contentDescription = "Instagram", tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
            Icon(painter = painterResource(R.drawable.ic_lr_social_x), contentDescription = "X", tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
        }

        Text(
            text = "CleanLightroom v10.0.1",
            color = Color(0xFF666666),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    label: String,
    tint: Color = Color(0xFFCCCCCC),
    labelColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
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
        Icon(painter = painterResource(R.drawable.ic_lr_community_tab), contentDescription = null, tint = Color(0xFF1686FF), modifier = Modifier.size(48.dp))
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
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = photo.uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadPhotoThumbnail(context, photo.uri) }.getOrNull()
        }
    }
    val aspectRatio = if (compact) 1.0f else if (wide) 1.65f else 0.92f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
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
                fontSize = if (compact) 8.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        if (!compact) {
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

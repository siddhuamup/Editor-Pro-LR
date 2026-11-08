import os

def normalize(text):
    return text.replace('\r\n', '\n')

def replace_in_file(path, target, replacement):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    c_norm = normalize(content)
    t_norm = normalize(target)
    r_norm = normalize(replacement)
    
    if t_norm not in c_norm:
        raise ValueError(f"Target block not found in {path}!\nFirst 100 chars of target:\n{t_norm[:100]}")
    
    new_norm = c_norm.replace(t_norm, r_norm, 1)
    
    # Restore CRLF if original had it
    if '\r\n' in content:
        final_content = new_norm.replace('\n', '\r\n')
    else:
        final_content = new_norm
        
    with open(path, 'w', encoding='utf-8') as f:
        f.write(final_content)
    print(f"Successfully modified {path}")

# ==============================================================================
# 2. EditorScreenLandscape.kt
# ==============================================================================
path_landscape = r'CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt'

# 2a. LaunchedEffect(selectedMobileTool) state reconciliation
t_launchedeffect = '''    val batchPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val config = pendingBatchConfig ?: com.cleanlr.export.ExportConfig()
            val currentRecipe = viewModel.recipe.value
            val items = uris.mapIndexed { idx, uri ->
                com.cleanlr.export.BatchExportItem(
                    id = "batch_item_${System.currentTimeMillis()}_${idx + 1}",
                    sourceUriString = uri.toString(),
                    recipe = currentRecipe,
                    config = config
                )
            }
            viewModel.startBatchExport(items)
        }
    }'''

r_launchedeffect = '''    val batchPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val config = pendingBatchConfig ?: com.cleanlr.export.ExportConfig()
            val currentRecipe = viewModel.recipe.value
            val items = uris.mapIndexed { idx, uri ->
                com.cleanlr.export.BatchExportItem(
                    id = "batch_item_${System.currentTimeMillis()}_${idx + 1}",
                    sourceUriString = uri.toString(),
                    recipe = currentRecipe,
                    config = config
                )
            }
            viewModel.startBatchExport(items)
        }
    }

    LaunchedEffect(selectedMobileTool) {
        when (selectedMobileTool) {
            "Edit" -> {
                if (activeTab !in listOf(
                        EditorToolTab.LIGHT, EditorToolTab.COLOR, EditorToolTab.BLUR,
                        EditorToolTab.EFFECTS, EditorToolTab.DETAIL, EditorToolTab.OPTICS,
                        EditorToolTab.PROFILES, EditorToolTab.CURVES
                    )
                ) {
                    viewModel.setActiveTab(EditorToolTab.LIGHT)
                }
            }
            "Masking" -> if (activeTab != EditorToolTab.MASKS) viewModel.setActiveTab(EditorToolTab.MASKS)
            "Crop" -> if (activeTab != EditorToolTab.CROP) viewModel.setActiveTab(EditorToolTab.CROP)
            "Presets" -> if (activeTab != EditorToolTab.PRESETS) viewModel.setActiveTab(EditorToolTab.PRESETS)
            "Actions" -> if (activeTab != EditorToolTab.ACTIONS) viewModel.setActiveTab(EditorToolTab.ACTIONS)
            "Remove" -> if (activeTab != EditorToolTab.REMOVE) viewModel.setActiveTab(EditorToolTab.REMOVE)
        }
    }'''
replace_in_file(path_landscape, t_launchedeffect, r_launchedeffect)

# 2b. LandscapeSubToolRail vs LandscapeMaskingSubToolRail in landscape row
t_subrails = '''            if (selectedMobileTool == "Edit") {
                LandscapeSubToolRail(
                    viewModel = viewModel,
                    activeTab = activeTab,
                    modifier = Modifier.width(60.dp)
                )

                VerticalDivider(color = SpectrumInnerBorder, thickness = 1.dp)
            }'''

r_subrails = '''            if (selectedMobileTool == "Edit") {
                LandscapeSubToolRail(
                    viewModel = viewModel,
                    activeTab = activeTab,
                    modifier = Modifier.width(60.dp)
                )

                VerticalDivider(color = SpectrumInnerBorder, thickness = 1.dp)
            } else if (selectedMobileTool == "Masking") {
                LandscapeMaskingSubToolRail(
                    selectedCategory = selectedMaskCategory,
                    onSelectCategory = { selectedMaskCategory = it },
                    modifier = Modifier.width(60.dp)
                )

                VerticalDivider(color = SpectrumInnerBorder, thickness = 1.dp)
            }'''
replace_in_file(path_landscape, t_subrails, r_subrails)

# 2c. MaskOverlayCanvas in LandscapeCanvasWorkspace
t_canvas_mask = '''            if (selectedMobileTool == "Masking") {
                FloatingMaskDock(
                    masks = recipe.masks,
                    activeMaskIndex = activeMaskLayerIndex,
                    onSelectMask = { viewModel.selectMaskLayer(it) },
                    onToggleLayer = { idx ->
                        val layer = recipe.masks.getOrNull(idx)
                        if (layer != null) {
                            viewModel.updateMaskLayer(layer.copy(enabled = !layer.enabled))
                        }
                    },
                    onInvertMask = { idx -> viewModel.invertMaskLayer(idx) },
                    onDuplicateMask = { idx -> viewModel.duplicateMaskLayer(idx) },
                    onDeleteMask = { idx -> viewModel.deleteMaskLayer(idx) },
                    onAddNewMaskClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onShowCreateMaskSheet()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }'''

r_canvas_mask = '''            if (selectedMobileTool == "Masking" && !isCleanScreenMode) {
                MaskOverlayCanvas(
                    masks = recipe.masks,
                    activeMaskIndex = activeMaskLayerIndex,
                    isOverlayVisible = true,
                    onPinClick = { },
                    modifier = Modifier.fillMaxSize()
                )

                FloatingMaskDock(
                    masks = recipe.masks,
                    activeMaskIndex = activeMaskLayerIndex,
                    onSelectMask = { viewModel.selectMaskLayer(it) },
                    onToggleLayer = { idx ->
                        val layer = recipe.masks.getOrNull(idx)
                        if (layer != null) {
                            viewModel.updateMaskLayer(layer.copy(enabled = !layer.enabled))
                        }
                    },
                    onInvertMask = { idx -> viewModel.invertMaskLayer(idx) },
                    onDuplicateMask = { idx -> viewModel.duplicateMaskLayer(idx) },
                    onDeleteMask = { idx -> viewModel.deleteMaskLayer(idx) },
                    onAddNewMaskClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onShowCreateMaskSheet()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }'''
replace_in_file(path_landscape, t_canvas_mask, r_canvas_mask)

# 2d. LandscapeAdjustmentColumn MaskingSlidersPanel invocation and fallback
t_sliders_invoke = '''                "Masking" -> {
                    MaskingSlidersPanel(
                        layers = recipe.masks,
                        activeLayerIndex = activeMaskLayerIndex,
                        selectedCategory = selectedMaskCategory,
                        onUpdateLayerAdjustments = { layer -> viewModel.updateMaskLayer(layer) },
                        onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                        onAddSubMaskToActive = { subMask -> viewModel.addSubMaskToActive(subMask) }
                    )
                }'''

r_sliders_invoke = '''                "Masking" -> {
                    MaskingSlidersPanel(
                        layers = recipe.masks,
                        activeLayerIndex = activeMaskLayerIndex,
                        selectedCategory = selectedMaskCategory,
                        onCategorySelected = onSelectMaskCategory,
                        onUpdateLayerAdjustments = { layer -> viewModel.updateMaskLayer(layer) },
                        onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                        onAddSubMaskToActive = { subMask -> viewModel.addSubMaskToActive(subMask) },
                        showCategoryTabs = false
                    )
                }'''
replace_in_file(path_landscape, t_sliders_invoke, r_sliders_invoke)

# Fallback in LandscapeAdjustmentColumn
t_fallback = '''                        else -> {
                            Text(
                                text = "${activeTab.title} controls",
                                color = SpectrumTextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }'''

r_fallback = '''                        else -> {
                            // Defensive recovery: default to Light controls when activeTab is not an edit tab
                            LightLandscapeAdjustmentPanel(
                                recipe = recipe,
                                isHdrModeActive = isHdrModeActive,
                                hdrLimitEv = hdrLimitEv,
                                onHdrToggle = onToggleHdrMode,
                                onHdrLimitChange = onHdrLimitEvChange,
                                onOpenCurves = { viewModel.setActiveTab(EditorToolTab.CURVES) },
                                viewModel = viewModel
                            )
                        }'''
replace_in_file(path_landscape, t_fallback, r_fallback)

# 2e. LandscapeSubToolRail Blur icon binding and LandscapeMaskingSubToolRail definition
t_subtoolrail = '''                EditorToolTab.COLOR -> R.drawable.ic_lr_color
                EditorToolTab.BLUR -> R.drawable.ic_lr_lens_blur
                EditorToolTab.EFFECTS -> R.drawable.ic_lr_effects'''

r_subtoolrail = '''                EditorToolTab.COLOR -> R.drawable.ic_lr_color
                EditorToolTab.BLUR -> R.drawable.ic_lr_blur
                EditorToolTab.EFFECTS -> R.drawable.ic_lr_effects'''
replace_in_file(path_landscape, t_subtoolrail, r_subtoolrail)

t_before_primaryrail = '''// =============================================================================
// 2C. PRIMARY DOCK VERTICAL RAIL COMPONENT
// ============================================================================='''

r_before_primaryrail = '''// =============================================================================
// 2B-2. MASKING SUB-TOOL VERTICAL STRIP COMPONENT (Landscape Masking Mode)
// =============================================================================
@Composable
internal fun LandscapeMaskingSubToolRail(
    selectedCategory: LocalAdjustmentCategory,
    onSelectCategory: (LocalAdjustmentCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        LocalAdjustmentCategory.LIGHT to Pair(R.drawable.ic_lr_light, "Light"),
        LocalAdjustmentCategory.COLOR to Pair(R.drawable.ic_lr_color, "Color"),
        LocalAdjustmentCategory.EFFECTS to Pair(R.drawable.ic_lr_effects, "Effects"),
        LocalAdjustmentCategory.DETAIL to Pair(R.drawable.ic_lr_detail, "Detail"),
        LocalAdjustmentCategory.OPTICS to Pair(R.drawable.ic_lr_optics, "Optics")
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SpectrumSurface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        categories.forEach { (cat, info) ->
            val (iconRes, label) = info
            val isSelected = (selectedCategory == cat)

            SubToolRailItem(
                iconRes = iconRes,
                label = label,
                isSelected = isSelected,
                isModified = false,
                isBypassed = false,
                onClick = { onSelectCategory(cat) }
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

// =============================================================================
// 2C. PRIMARY DOCK VERTICAL RAIL COMPONENT
// ============================================================================='''
replace_in_file(path_landscape, t_before_primaryrail, r_before_primaryrail)

# 2f. Masking Discard and Commit in LandscapePrimaryDockRail
t_primary_dock = '''        if (selectedMobileTool == "Crop") {
            Spacer(modifier = Modifier.height(32.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    viewModel.resetCrop()
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_close),
                    contentDescription = "Cancel crop",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_select_checkmark),
                    contentDescription = "Apply crop",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else {'''

r_primary_dock = '''        if (selectedMobileTool == "Crop") {
            Spacer(modifier = Modifier.height(32.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    viewModel.resetCrop()
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_close),
                    contentDescription = "Cancel crop",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_select_checkmark),
                    contentDescription = "Apply crop",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else if (selectedMobileTool == "Masking") {
            // Authentic Lightroom Mobile Landscape Masking Rail (Screenshot 201104)
            Spacer(modifier = Modifier.height(32.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_close),
                    contentDescription = "Discard mask",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_select_checkmark),
                    contentDescription = "Apply mask",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else {'''
replace_in_file(path_landscape, t_primary_dock, r_primary_dock)


# ==============================================================================
# 3. adb_helper.py
# ==============================================================================
path_adb_helper = r'CleanLightroom/tests/e2e/adb_helper.py'

t_adb_orientation = '''    def set_orientation(self, rotation: int, settle_delay_s: float = 1.0, timeout_s: float = 5.0) -> None:
        """Sets screen orientation: 0 for Portrait, 1 for Landscape (90 deg counter-clockwise)."""
        if rotation not in (0, 1):
            raise ValueError(f"Invalid rotation {rotation}; only 0 (portrait) or 1 (landscape) supported.")
        self.shell(
            f"settings put system accelerometer_rotation 0; settings put system user_rotation {rotation}"
        )
        time.sleep(settle_delay_s)
        start = time.time()
        while time.time() - start < timeout_s:
            out = self.shell("dumpsys window | grep 'mRotation='")
            if f"mRotation={rotation}" in out:
                break
            time.sleep(0.3)
        time.sleep(0.5)

    def get_orientation(self) -> int:
        val = self.shell("settings get system user_rotation")
        return int(val.strip()) if val.strip().isdigit() else 0

    def capture_screenshot(self, local_path: Optional[str] = None) -> Tuple[Image.Image, str]:
        remote_path = "/sdcard/e2e_screencap.png"
        if not local_path:
            fd, local_path = tempfile.mkstemp(prefix="e2e_cap_", suffix=".png")
            os.close(fd)
        self.shell(f"screencap -p {remote_path}")
        self.run_cmd(["pull", remote_path, local_path])
        img = Image.open(local_path)
        return img, local_path

    def dump_ui(self, remote_path: str = "/sdcard/e2e_uidump.xml", retries: int = 3) -> UIHierarchy:
        for attempt in range(retries):
            try:
                self.shell(f"uiautomator dump {remote_path}", timeout=15)
                raw_xml = self.shell(f"cat {remote_path}")
                if raw_xml.strip().startswith("<?xml") and "</hierarchy>" in raw_xml:
                    return UIHierarchy(raw_xml)
            except Exception:
                pass
            time.sleep(0.4 * (attempt + 1))
        # Fallback with actual device orientation if daemon dump was temporarily contentious
        current_rot = self.get_orientation()
        return UIHierarchy(f"<hierarchy rotation='{current_rot}'></hierarchy>")'''

r_adb_orientation = '''    ROTATION_NAMES = {
        0: "ROTATION_0",
        1: "ROTATION_90",
        2: "ROTATION_180",
        3: "ROTATION_270",
    }

    def set_orientation(self, rotation: int, settle_delay_s: float = 0.5, timeout_s: float = 6.0) -> None:
        """Sets screen orientation: 0 for Portrait, 1 for Landscape (90 deg counter-clockwise).
        Actively polls Window Manager until mCurrentRotation and mRotation have settled.
        """
        if rotation not in (0, 1):
            raise ValueError(f"Invalid rotation {rotation}; only 0 (portrait) or 1 (landscape) supported.")
        
        target_name = self.ROTATION_NAMES.get(rotation, "ROTATION_0")
        self.shell(
            f"settings put system accelerometer_rotation 0; settings put system user_rotation {rotation}"
        )
        
        start = time.time()
        while time.time() - start < timeout_s:
            out = self.shell("dumpsys window", check=False)
            # Both display configuration and rotation controller must match
            if f"mCurrentRotation={target_name}" in out and f"mRotation={rotation}" in out:
                break
            time.sleep(0.15)
        
        # Brief stabilization sleep for Compose layout pass and animations to finish settling
        time.sleep(settle_delay_s)

    def get_orientation(self) -> int:
        """Reads current orientation from window manager or system settings."""
        out = self.shell("dumpsys window", check=False)
        for line in out.splitlines():
            if "mRotation=" in line and "mDeferred" in line:
                for token in line.split():
                    if token.startswith("mRotation="):
                        val = token.split("=")[1]
                        if val.isdigit():
                            return int(val)
        val = self.shell("settings get system user_rotation", check=False)
        return int(val.strip()) if val.strip().isdigit() else 0

    def capture_screenshot(self, local_path: Optional[str] = None) -> Tuple[Image.Image, str]:
        remote_path = "/sdcard/e2e_screencap.png"
        if not local_path:
            fd, local_path = tempfile.mkstemp(prefix="e2e_cap_", suffix=".png")
            os.close(fd)
        self.shell(f"screencap -p {remote_path}")
        self.run_cmd(["pull", remote_path, local_path])
        img = Image.open(local_path)
        return img, local_path

    def dump_ui(self, remote_path: str = "/sdcard/e2e_uidump.xml", retries: int = 3) -> UIHierarchy:
        """Dumps UI hierarchy with daemon contention recovery and backoff retry."""
        for attempt in range(retries):
            try:
                # Remove previous dump to prevent reading stale XML
                self.shell(f"rm -f {remote_path}", check=False)
                self.shell(f"uiautomator dump {remote_path}", timeout=15)
                raw_xml = self.shell(f"cat {remote_path}", check=False)
                if raw_xml.strip().startswith("<?xml") and "</hierarchy>" in raw_xml:
                    return UIHierarchy(raw_xml)
            except Exception:
                # UiAutomationService contention / exit code 137: clean up stuck daemon
                self.shell("pkill -f uiautomator", check=False)
            time.sleep(0.3 + 0.2 * (attempt + 1))
        
        # Fallback with verified device orientation if daemon dump was temporarily contentious
        current_rot = self.get_orientation()
        return UIHierarchy(f"<hierarchy rotation='{current_rot}'></hierarchy>")'''
replace_in_file(path_adb_helper, t_adb_orientation, r_adb_orientation)


# ==============================================================================
# 4. test_tier2_boundary_corner.py
# ==============================================================================
path_tier2 = r'CleanLightroom/tests/e2e/test_tier2_boundary_corner.py'

t_t2_opacity = '''    def test_t2_17_mask_opacity_slider_boundary_zero(self, adb: ADBHelper):
        """Verifies Mask Opacity slider boundary clamps properly."""
        ui = adb.dump_ui()
        opacity_node = ui.find_one(desc="Mask Opacity") or ui.find_one(text="Mask Opacity")
        if opacity_node:
            assert opacity_node.bounds[1][0] > opacity_node.bounds[0][0], "Zero width opacity slider bounds!"
        assert adb.is_app_foreground(), "App terminated checking opacity bounds!"

    def test_t2_18_mask_opacity_slider_boundary_hundred(self, adb: ADBHelper):
        """Verifies 100% is displayed as default opacity on new mask creation."""
        ui = adb.dump_ui()
        pct_node = ui.find_one(text="100") or ui.find_one(desc="100 %") or ui.find_one(text="100 %")
        if pct_node:
            assert pct_node.bounds[1][0] >= pct_node.bounds[0][0], "Invalid bounds for 100% readout"
        assert adb.is_app_foreground(), "App terminated checking 100% opacity readout!"'''

r_t2_opacity = '''    def test_t2_17_mask_opacity_slider_boundary_zero(self, adb: ADBHelper):
        """Verifies Mask Opacity slider boundary clamps properly."""
        # Wait up to 3.0s for Mask Opacity slider in hierarchy
        ui = adb.wait_for_ui(
            lambda u: u.find_one(desc="Mask Opacity") is not None or u.find_one(text="Mask Opacity") is not None,
            timeout_s=3.0,
        ) or adb.dump_ui()
        opacity_node = ui.find_one(desc="Mask Opacity") or ui.find_one(text="Mask Opacity")
        
        # If masking mode is not open or mask layer not yet created, ensure layer exists
        if not opacity_node:
            mask_tool = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
            if mask_tool:
                adb.tap_element(mask_tool, delay_s=0.6)
                ui = adb.dump_ui()
            fab = ui.find_one(desc="Create new Mask") or ui.find_one(desc="Add Mask")
            if fab:
                adb.tap_element(fab, delay_s=0.6)
                ui = adb.dump_ui()
                brush_btn = ui.find_one(text="Brush") or ui.find_one(desc="Brush")
                if brush_btn:
                    adb.tap_element(brush_btn, delay_s=0.6)
                    ui = adb.dump_ui()
            opacity_node = ui.find_one(desc="Mask Opacity") or ui.find_one(text="Mask Opacity")

        assert opacity_node is not None, "Mask Opacity slider element missing in hierarchy!"
        assert opacity_node.bounds[1][0] > opacity_node.bounds[0][0], (
            f"Zero width opacity slider bounds: {opacity_node.bounds}"
        )
        assert adb.is_app_foreground(), "App terminated checking opacity bounds!"

    def test_t2_18_mask_opacity_slider_boundary_hundred(self, adb: ADBHelper):
        """Verifies 100% is displayed as default opacity on new mask creation."""
        # Wait up to 3.0s for 100% readout in hierarchy
        ui = adb.wait_for_ui(
            lambda u: any(
                u.find_one(text=pattern) is not None or u.find_one(desc=pattern) is not None
                for pattern in ["100%", "100 %", "100"]
            ),
            timeout_s=3.0,
        ) or adb.dump_ui()
        
        pct_node = (
            ui.find_one(text="100%")
            or ui.find_one(text="100 %")
            or ui.find_one(text="100")
            or ui.find_one(desc="100%")
            or ui.find_one(desc="100 %")
            or ui.find_one(desc="100")
        )
        assert pct_node is not None, "100% opacity readout node missing in Masking hierarchy!"
        assert pct_node.bounds[1][0] > pct_node.bounds[0][0], (
            f"Invalid bounds for 100% readout: {pct_node.bounds}"
        )
        assert adb.is_app_foreground(), "App terminated checking 100% opacity readout!"'''
replace_in_file(path_tier2, t_t2_opacity, r_t2_opacity)

print("\nALL CHANGES APPLIED SUCCESSFULLY!")

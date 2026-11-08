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
    
    if '\r\n' in content:
        final_content = new_norm.replace('\n', '\r\n')
    else:
        final_content = new_norm
        
    with open(path, 'w', encoding='utf-8') as f:
        f.write(final_content)
    print(f"Successfully modified {path}")

# 1. MaskingPanel.kt default arguments for onSubtract / onIntersect / onCategorySelected
path_masking = r'CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/MaskingPanel.kt'
t_masking_sig = '''@Composable
fun MaskingSlidersPanel(
    layers: List<MaskLayer>,
    activeLayerIndex: Int,
    selectedCategory: MaskAdjustmentCategory,
    onCategorySelected: (MaskAdjustmentCategory) -> Unit,
    onUpdateLayerAdjustments: (MaskLayer) -> Unit,
    onDeleteActiveLayer: () -> Unit,
    onAddSubMaskToActive: (SubMask) -> Unit,
    onSubtractSubMaskFromActive: (SubMask) -> Unit,
    onIntersectSubMaskWithActive: (SubMask) -> Unit,
    showCategoryTabs: Boolean = true,
    modifier: Modifier = Modifier
) {'''

r_masking_sig = '''@Composable
fun MaskingSlidersPanel(
    layers: List<MaskLayer>,
    activeLayerIndex: Int,
    selectedCategory: MaskAdjustmentCategory,
    onCategorySelected: (MaskAdjustmentCategory) -> Unit = {},
    onUpdateLayerAdjustments: (MaskLayer) -> Unit,
    onDeleteActiveLayer: () -> Unit,
    onAddSubMaskToActive: (SubMask) -> Unit,
    onSubtractSubMaskFromActive: (SubMask) -> Unit = onAddSubMaskToActive,
    onIntersectSubMaskWithActive: (SubMask) -> Unit = onAddSubMaskToActive,
    showCategoryTabs: Boolean = true,
    modifier: Modifier = Modifier
) {'''
replace_in_file(path_masking, t_masking_sig, r_masking_sig)

# 2. EditorScreenLandscape.kt explicit types for lambdas
path_landscape = r'CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt'
t_landscape_invoke = '''                "Masking" -> {
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

r_landscape_invoke = '''                "Masking" -> {
                    MaskingSlidersPanel(
                        layers = recipe.masks,
                        activeLayerIndex = activeMaskLayerIndex,
                        selectedCategory = selectedMaskCategory,
                        onCategorySelected = onSelectMaskCategory,
                        onUpdateLayerAdjustments = { layer: MaskLayer -> viewModel.updateMaskLayer(layer) },
                        onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                        onAddSubMaskToActive = { subMask: SubMask -> viewModel.addSubMaskToActive(subMask) },
                        onSubtractSubMaskFromActive = { subMask: SubMask -> viewModel.addSubMaskToActive(subMask) },
                        onIntersectSubMaskWithActive = { subMask: SubMask -> viewModel.addSubMaskToActive(subMask) },
                        showCategoryTabs = false
                    )
                }'''
replace_in_file(path_landscape, t_landscape_invoke, r_landscape_invoke)

# 3. EditorScreen.kt add ACTIONS and REMOVE to when (activeTab)
path_editorscreen = r'CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt'
t_active_tab = '''                        EditorToolTab.MASKS -> {
                            MaskingSlidersPanel(
                                layers = recipe.masks,
                                activeLayerIndex = activeMaskLayerIndex,
                                selectedCategory = selectedMaskCategory,
                                onUpdateLayerAdjustments = { layer -> viewModel.updateMaskLayer(layer) },
                                onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                                onAddSubMaskToActive = { subMask -> viewModel.addSubMaskToActive(subMask) }
                            )
                        }
                        }'''

r_active_tab = '''                        EditorToolTab.MASKS -> {
                            MaskingSlidersPanel(
                                layers = recipe.masks,
                                activeLayerIndex = activeMaskLayerIndex,
                                selectedCategory = selectedMaskCategory,
                                onUpdateLayerAdjustments = { layer -> viewModel.updateMaskLayer(layer) },
                                onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                                onAddSubMaskToActive = { subMask -> viewModel.addSubMaskToActive(subMask) }
                            )
                        }
                        EditorToolTab.ACTIONS -> {}
                        EditorToolTab.REMOVE -> {}
                        }'''
replace_in_file(path_editorscreen, t_active_tab, r_active_tab)

print("All fixes applied successfully!")

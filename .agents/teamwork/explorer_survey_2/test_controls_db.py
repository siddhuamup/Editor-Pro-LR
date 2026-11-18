import json
import os

base_dir = r'C:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference'
manifest_path = os.path.join(base_dir, 'PANEL_MANIFEST.json')

with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = json.load(f)

# Sort files
v_files = sorted([f for f in manifest['files'] if f['source_dir'] == 'vertical'], key=lambda x: x['filename'])
h_files = sorted([f for f in manifest['files'] if f['source_dir'] == 'horizontal'], key=lambda x: x['filename'])
all_files = v_files + h_files

# Rich control database
controls_data = {
    # Vertical
    "Screenshot_20261019-082951_Lightroom.png": (
        "Gallery", "Device Photos (Gallery tab)",
        "Top Bar (Hamburger menu, 'Photos on device' dropdown, Funnel filter, View options bars, 3-dots overflow); Date headers ('October 17, 2026 (18)', 'October 16, 2026 (1)'); Multi-column photo grid; Bottom Navigation Bar ('Gallery' [active blue], 'Lightroom', 'Community')",
        "None (Gallery root is portrait-primary in Lightroom Mobile)"
    ),
    "Screenshot_20261019-083004_Lightroom.png": (
        "Settings_and_Account", "Main Navigation Drawer",
        "Profile Header (Avatar, Name 'Siddhu Amup', Email 'siddhuamup@gmail.com'); Menu List (Account, Premium Features, Early access, Preferences, What's New, About Lightroom, Local storage, Help & Support, Rate Lightroom); Bottom Social Icons (Facebook, Instagram, X); Scrim overlay",
        "Landscape/Sub-screens: Screenshot_20261024-201248 (Account), 201254 (Prefs), 201303 (About), 201307 (Storage), 201313 (Help)"
    ),
    "Screenshot_20261019-083018_Lightroom.png": (
        "Gallery", "Lightroom Cloud Imports Tab",
        "Top Bar (Hamburger, Search glass, Notifications bell, Cloud sync, 3-dots overflow); Album selector ('Imports' dropdown, Filter funnel); Masonry thumbnail grid with filmstrip video badges; Dual FAB pill (Add Photos [+], In-App Camera); Bottom Nav ('Lightroom' active blue)",
        "Screenshot_20261019-082951 (Device tab), Screenshot_20261019-083818 (View options sheet)"
    ),
    "Screenshot_20261019-083216_Lightroom.png": (
        "Editor_and_Export", "Loupe Editor Resting State",
        "Top Navigation Bar (Back arrow, Share/Export icon, 3-dots overflow); Loupe canvas; Bottom Floating Dock with 6 tool slots: 'Actions', 'Presets', 'Crop', 'Edit', 'Masking', 'Remove'",
        "Screenshot_20261024-200525 & 200532 (Landscape Loupe editor resting state)"
    ),
    "Screenshot_20261019-083218_Lightroom.png": (
        "Editor_and_Export", "Editor Overflow Menu (3-dots)",
        "Top-right Dropdown Popup: 'Save copy to device', 'View options', 'Create Preset', 'Copy Settings', 'Paste Settings' [disabled], 'Apply from previous', 'Help', 'Your profile'; Loupe canvas and resting dock visible",
        "Screenshot_20261024-201151 (Landscape Editor 3-dots overflow menu)"
    ),
    "Screenshot_20261019-083231_Lightroom.png": (
        "Editor_and_Export", "Share / Export Modal",
        "Header (Back arrow, 'Share 1 Photo', 'Select All' pill); Horizontal carousel with blue selection frame; Quick Actions ('Share', 'Export as...'); List Items ('Save copy to device' + gear, 'Get shareable link', 'Create edit replay', 'Post to Community')",
        "Screenshot_20261024-201212 (Landscape Share modal) & 201218 (Filmstrip carousel)"
    ),
    "Screenshot_20261019-083243_Lightroom.png": (
        "Actions", "Quick Actions Bar",
        "Bottom Dock (Slot 1 'Actions' active blue); Sub-toolbar Strip: 'Auto', 'Enhance', 'Subject', 'Background', 'Sky', 'Animate'; Loupe canvas; Top Bar (Back, Share, Overflow)",
        "Screenshot_20261024-200540 (Landscape Actions bar), 200617 (Enhance sliders), 200624 (Subject sliders)"
    ),
    "Screenshot_20261019-083251_Lightroom.png": (
        "Presets", "Presets > Recommended (Analyzing)",
        "Bottom Dock (Slot 2 'Presets' active blue); Preset Tabs: 'Recommended' [active underline], 'Premium', 'Yours'; Placeholder icon with 'Analyzing your photo...' scan bar",
        "Screenshot_20261024-200557 (Landscape Recommended presets loaded)"
    ),
    "Screenshot_20261019-083253_Lightroom.png": (
        "Presets", "Presets > Premium (Adaptive Packs)",
        "Preset Tabs: 'Recommended', 'Premium' [active underline], 'Yours'; Pack items with thumbnails, Cloud icons, and count badges: 'Adaptive: Portrait (12)', 'Adaptive: Sky (9)', 'Adaptive: Subject (9)', 'Adaptive: Landscape (8)'; Chevrons",
        "Screenshot_20261024-200549 (Landscape Premium packs) & 200546 (Adaptive preset active)"
    ),
    "Screenshot_20261019-083256_Lightroom.png": (
        "Presets", "Presets > Yours (User Presets)",
        "Preset Tabs: 'Recommended', 'Premium', 'Yours' [active underline]; Custom folders: 'User Presets (2)', 'Autumn Forest (9)', 'Dark and Moody (5)', 'Northlandscapes - Astrophotography (10)'; Chevrons",
        "Screenshot_20261024-200600 (Landscape Yours preset tab)"
    ),
    "Screenshot_20261019-083306_Lightroom.png": (
        "Crop", "Crop & Expand > Aspect Tab",
        "Top Bar ('Original' pill, Help [?]); Canvas with 8-handle Crop Boundary and bottom Straighten Arc Dial ('0.00°'); Action Buttons (Free, Lock Aspect, Rotate 90°, 3-dots); Sub-tabs ('Aspect' [active], 'Geometry'); Aspect row ('Expand' toggle, 'Original' [active], 'Ratios', 'Instagram', 'Tiktok'); Bottom Bar (Cancel [X], 'Crop and expand', Accept [Checkmark])",
        "Screenshot_20261024-200520, 200710, 200714, 200720, 200728, 200731"
    ),
    "Screenshot_20261019-083314_Lightroom.png": (
        "Crop", "Crop & Expand > Geometry Tab",
        "Sub-tabs ('Aspect', 'Geometry' [active]); Upright Mode Buttons ('Auto', 'Level', 'Vertical', 'Full', 'Guided'); Toggle Switch ('Constrain Crop'); Expandable header ('Manual Transforms'); Bottom Bar (Cancel [X], 'Crop and expand', Accept [Checkmark])",
        "Screenshot_20261024-200437, 200441, 200449, 200504, 200743"
    ),
    "Screenshot_20261019-083341_Lightroom.png": (
        "Light", "Light Panel (Tone Controls & Curve)",
        "Sub-toolbar ('Auto', 'Light' [active white/blue dot], 'Color', 'Blur', 'Effects', 'Detail', 'Optics', 'Profiles'); Bottom Dock (Slot 4 'Edit' active blue); Sliders ('Exposure [0.00]', 'Contrast [0]', 'Highlights [0]'); Button ('Curve' with tone curve icon); Top Bar (Back, Undo/Redo, Share, Overflow)",
        "Screenshot_20261024-200754 (Landscape Light panel with all tone sliders and Curve button visible)"
    ),
    "Screenshot_20261019-083407_Lightroom.png": (
        "Light", "Light Panel (Blacks & HDR Mode)",
        "Light panel scrolled: Slider ('Blacks [0]'); Card: Toggle Switch ('Edit in HDR Mode' [ON blue]), Slider ('HDR Limit [+2.30]'); Canvas HDR badge indicator; Sub-toolbar (Light active); Bottom Dock (Edit active)",
        "Screenshot_20261024-200801 (Landscape Light panel HDR controls & SDR preview)"
    ),
    "Screenshot_20261019-083439_Lightroom.png": (
        "Tone_Curve", "Point Curve (White / Composite Channel)",
        "Interactive 4x4 Grid Overlay on canvas with diagonal curve line and 2 endpoint control handles (0,0) and (255,255); Bottom Panel: Title 'Curve', Action Button 'DONE'; Channel Buttons: White/Composite [active outer ring], Red, Green, Blue, Parametric TAT icon",
        "Landscape counterpart omitted in reference dataset (Tone Curve is portrait-exclusive full overlay in mobile Lightroom)"
    ),
    "Screenshot_20261019-083443_Lightroom.png": (
        "Tone_Curve", "Point Curve (Red Channel)",
        "Tone Curve 4x4 Grid with Red curve line; Channel Buttons: Red [active center red dot]; White, Green, Blue, Parametric TAT; 'DONE' button",
        "Landscape counterpart omitted in reference dataset"
    ),
    "Screenshot_20261019-083445_Lightroom.png": (
        "Tone_Curve", "Point Curve (Green Channel)",
        "Tone Curve 4x4 Grid with Green curve line; Channel Buttons: Green [active center green dot]; White, Red, Blue, Parametric TAT; 'DONE' button",
        "Landscape counterpart omitted in reference dataset"
    ),
    "Screenshot_20261019-083447_Lightroom.png": (
        "Tone_Curve", "Point Curve (Blue Channel)",
        "Tone Curve 4x4 Grid with Blue curve line; Channel Buttons: Blue [active center blue dot]; White, Red, Green, Parametric TAT; 'DONE' button",
        "Landscape counterpart omitted in reference dataset"
    ),
    "Screenshot_20261019-083452_Lightroom.png": (
        "Tone_Curve", "Parametric Curve (Targeted Split Sliders)",
        "Parametric Curve Graph on canvas; Range Split Slider Bar below graph with 3 movable split thumbs (Shadows/Darks, Darks/Lights, Lights/Highlights); Channel Buttons: Parametric S-curve icon [active]; 'DONE' button",
        "Landscape counterpart omitted in reference dataset"
    ),
    "Screenshot_20261019-083509_Lightroom.png": (
        "Color", "Edit > Color (Main Controls)",
        "Sub-toolbar ('Color' active); Buttons ('B & W' pill, 'Grading' [3-circle icon], 'Mix' [rainbow donut icon]); White Balance row ('White balance', Dropdown 'As Shot', Eyedropper tool); Sliders ('Temp [0]' blue-yellow track, 'Tint [0]' green-magenta track); Bottom Dock (Edit active)",
        "Screenshot_20261024-200806 (Landscape Color panel), 200905 (B&W active), 200911 (B&W Custom WB)"
    ),
    "Screenshot_20261019-083519_Lightroom.png": (
        "Color_Grading", "Color Grading (Shadows Wheel)",
        "Header ('Color Grading', 'DONE' button); Wheel Mode Switchers (3-Way, Shadows [active], Midtones, Highlights, Global); Chromaticity Color Wheel with center puck and outer luminance ring; Values ('Shadows', 3-dots, 'Hue 0', 'Sat 0'); Slider ('Luminance [0]')",
        "Screenshot_20261024-200814 (Landscape Color Grading Shadows wheel)"
    ),
    "Screenshot_20261019-083531_Lightroom.png": (
        "Color_Grading", "Color Grading (Shadows Wheel Interactive)",
        "Identical layout to 083519 showing Shadows wheel touch target and bottom luminance arc handle",
        "Screenshot_20261024-200814 (Landscape Color Grading Shadows wheel)"
    ),
    "Screenshot_20261019-083543_Lightroom.png": (
        "Color_Grading", "Color Grading (Global Wheel)",
        "Wheel Mode Switchers: Global Half-Circle Icon [active]; Chromaticity Wheel with center puck and outer luminance ring; Values ('Global', 3-dots, 'Hue 0', 'Sat 0'); Slider ('Luminance [0]'); 'DONE' button",
        "Screenshot_20261024-200823 (Landscape Color Grading Global wheel with HSL sliders)"
    ),
    "Screenshot_20261019-083551_Lightroom.png": (
        "Color_Mix", "Color Mix (HSL 8-Color Mixer)",
        "Header ('Color Mix', Targeted Adjustment Tool [TAT icon], 'DONE' button); 8 Color Swatch Chips (Red [active white dot], Orange, Yellow, Green, Aqua, Blue, Purple, Magenta); HSL Sliders ('Hue [0]', 'Saturation [0]', 'Luminance [0]') with color gradient tracks",
        "Screenshot_20261024-200832 (Landscape Color Mix HSL panel)"
    ),
    "Screenshot_20261019-083601_Lightroom.png": (
        "Color_Mix", "Color Mix > TAT (On-Screen Dragging)",
        "Top Pill Indicator ('Saturation Orange -45' with orange swatch circle); Canvas with TAT reticle crosshair; Bottom bar with active blue TAT button; Sub-tabs ('HUE', 'SATURATION' [active], 'LUMINANCE'); Color Chips showing deltas (Red '+46', Orange '-44')",
        "Landscape sub-state of Color Mix TAT"
    ),
    "Screenshot_20261019-083626_Lightroom.png": (
        "Lens_Blur", "Lens Blur (AI Depth Detection & Bokeh Shapes)",
        "Center Dialog ('Detecting depth using AI...' with spinner and 'Cancel' button); Slider ('Blur amount [+21]'); Bokeh Shape Chips ('Circle', 'Bubble', '5-blade', 'Ring', 'Oval', 'Focus'); Bottom Bar (Cancel [X], 'Lens blur', Done [Checkmark])",
        "Screenshot_20261024-200919 (Depth map histogram) & 200924 (Bokeh controls)"
    ),
    "Screenshot_20261019-083640_Lightroom.png": (
        "Effects", "Edit > Effects > Effects Tab",
        "Sub-toolbar ('Effects' active); Sub-tabs ('Effects' [active], 'Vignette', 'Grain'); Sliders ('Texture [0]', 'Clarity [0]', 'Dehaze [0]'); Bottom Dock (Edit active)",
        "Screenshot_20261024-200935 (Landscape Effects tab: Texture, Clarity, Dehaze)"
    ),
    "Screenshot_20261019-083652_Lightroom.png": (
        "Effects", "Edit > Effects > Vignette Tab",
        "Sub-tabs ('Effects', 'Vignette' [active], 'Grain'); Sliders ('Amount [-34]', 'Midpoint [+50]', 'Feather [+50]'); Bottom Dock (Edit active)",
        "Screenshot_20261024-200937 (Vignette resting) & 200942 (Vignette expanded sliders)"
    ),
    "Screenshot_20261019-083657_Lightroom.png": (
        "Effects", "Edit > Effects > Grain Tab",
        "Sub-tabs ('Effects', 'Vignette', 'Grain' [active]); Sliders ('Amount [+12]', 'Size [+25]', 'Roughness [+50]'); Bottom Dock (Edit active)",
        "Screenshot_20261024-200945 (Landscape Grain tab: Amount, Size, Roughness)"
    ),
    "Screenshot_20261019-083710_Lightroom.png": (
        "Detail", "Edit > Detail > Sharpening Tab",
        "Sub-toolbar ('Detail' active); Sub-tabs ('Sharpening' [active], 'Noise', 'Color noise'); Sliders ('Amount [+14]', 'Radius [+1.00]', 'Detail [+25]'); Bottom Dock (Edit active)",
        "Screenshot_20261024-200949 (Sharpening tab), 200951 & 200956 (Noise tabs), 201000 (Color noise tab)"
    ),
    "Screenshot_20261019-083714_Lightroom.png": (
        "Optics", "Edit > Optics (Lens Corrections)",
        "Sub-toolbar ('Optics' active); Toggle Switches ('Remove Chromatic Aberration' [OFF], 'Enable Lens Corrections' [OFF]); Bottom Dock (Edit active)",
        "Screenshot_20261024-201117 (Masking > Local Optics)"
    ),
    "Screenshot_20261019-083723_Lightroom.png": (
        "Profiles", "Edit > Profiles Browser",
        "Top Profile Controls: Dropdown 'Basic', Overflow 3-dots; Horizontal Card Carousel with live rendered image thumbnails ('Monochrome', 'Artistic 01', 'Artistic 02', 'Artistic 03'); Star favorite icon; Bottom Bar (Cancel [X], 'Profiles', Done [Checkmark])",
        "Screenshot_20261024-201008 & 201016 (Landscape Profiles browser)"
    ),
    "Screenshot_20261019-083730_Lightroom.png": (
        "Masking", "Create New Mask Sheet",
        "Header ('Create new Mask', Help [?]); List Options with Cloud icons: 'Select Subject', 'Select Sky', 'Select Background'; Manual Masks: 'Brush', 'Linear Gradient', 'Radial Gradient'; Range Masks: 'Color Range', 'Luminance Range'",
        "Screenshot_20261024-201045 & 201048 (Landscape Create new Mask modal)"
    ),
    "Screenshot_20261019-083741_Lightroom.png": (
        "Masking", "Masking Workspace (Empty Resting State)",
        "Bottom Dock (Slot 5 'Masking' active blue); Floating Layer Pill on right edge with blue (+) FAB button; Grayed/disabled sub-tool strip (Light, Color, Effects, Detail, Optics); Center canvas preview",
        "Screenshot_20261024-201028 (Landscape Masking workspace with active Subject mask)"
    ),
    "Screenshot_20261019-083752_Lightroom.png": (
        "Remove", "Remove Tool (Generative AI & Object Detection)",
        "Bottom Dock (Slot 6 'Remove' active blue); Left Mode Chip ('Remove' with sparkles icon); Floating Chip ('Remove extra people' with dual-person icon); Sliders ('Size [10]'); Toggle Switches ('Detect objects' [OFF], 'Generative AI' [OFF] with info [i]); Button ('Refine' [disabled pencil]); Top Bar (Back, Undo/Redo, Compare, Share, Overflow)",
        "Screenshot_20261024-201127 (Landscape Remove tool) & 201133 (Remove mode dropdown)"
    ),
    "Screenshot_20261019-083818_Lightroom.png": (
        "Gallery", "Gallery / Imports View Options & Batch Edit Sheet",
        "Bottom Modal Sheet: Section 'BATCH EDIT' ('Select' checkbox); Section 'VIEW OPTIONS' ('Square grid', 'Justified grid' [active checkmark], 'Grid segmentation' toggle, 'Segment by' chevron, 'Slideshow' play icon); Action Button ('Add photos' image+ icon); Background shows Imports grid with film video badges",
        "Portrait-exclusive bottom sheet in Gallery tab (see Screenshot_20261019-083018 for base imports grid)"
    ),

    # Horizontal (55 landscape + 11 portrait)
    "Screenshot_20261024-200437_Lightroom.png": (
        "Crop", "Crop > Geometry (Upright & Constrain Crop)",
        "Right dock with Crop active; Sub-column with Aspect & Geometry tabs; Geometry Upright Buttons ('Auto', 'Level', 'Vertical', 'Full', 'Guided'); Toggle Switch ('Constrain Crop'); Manual Transforms section header; Left canvas with crop boundary",
        "Screenshot_20261019-083314 (Portrait Crop Geometry tab)"
    ),
    "Screenshot_20261024-200441_Lightroom.png": (
        "Crop", "Crop > Geometry (Manual Distortion & Vertical)",
        "Geometry panel scrolled to Manual Transforms: Sliders ('Distortion [0]', 'Vertical [0]'); Upright buttons above; Constrain Crop switch",
        "Screenshot_20261019-083314 (Portrait Crop Geometry tab)"
    ),
    "Screenshot_20261024-200449_Lightroom.png": (
        "Crop", "Crop > Geometry (Manual Transforms Interactive State)",
        "Manual Transforms adjustment state with active slider touch targets ('Distortion', 'Vertical')",
        "Screenshot_20261019-083314 (Portrait Crop Geometry tab)"
    ),
    "Screenshot_20261024-200504_Lightroom.png": (
        "Crop", "Crop > Geometry (Manual Transforms Full Set)",
        "Manual Transforms full slider set: 'Horizontal [0]', 'Rotate [0.0°]', 'Aspect [0]', 'Scale [100%]', 'X Offset [0]', 'Y Offset [0]'; Constrain Crop switch",
        "Screenshot_20261019-083314 (Portrait Crop Geometry tab)"
    ),
    "Screenshot_20261024-200520_Lightroom.png": (
        "Crop", "Crop > Aspect Ratio Tab (Smile Straighten Dial)",
        "Crop Aspect tab: Vertical Straighten Arc Dial ('0.00°'); Aspect Ratio Mode Selector ('Expand' toggle, 'Original' [active], 'Ratios', 'Instagram', 'Tiktok'); Free/Lock aspect and rotate buttons; Left canvas with crop box",
        "Screenshot_20261019-083306 (Portrait Crop Aspect tab)"
    ),
    "Screenshot_20261024-200525_Lightroom.png": (
        "Editor_and_Export", "Loupe Editor Landscape Base View (Top Rail)",
        "Right Vertical Primary Dock (Actions, Presets, Crop, Edit, Masking, Remove); Top Bar (Back arrow, Undo/Redo, Compare, Share, Overflow); Center Left Loupe Canvas (Mountain sunset graphic)",
        "Screenshot_20261019-083216 (Portrait Loupe editor resting state)"
    ),
    "Screenshot_20261024-200532_Lightroom.png": (
        "Editor_and_Export", "Loupe Editor Landscape Base View (Bottom Rail)",
        "Right Vertical Primary Dock showing active resting state across all 6 primary tools; Full canvas preview on left",
        "Screenshot_20261019-083216 (Portrait Loupe editor resting state)"
    ),
    "Screenshot_20261024-200540_Lightroom.png": (
        "Actions", "Quick Actions Bar (Landscape)",
        "Right dock (Actions active blue); Sub-rail with Quick Action chips ('Auto', 'Enhance', 'Subject', 'Background', 'Fix angle'); Left canvas preview",
        "Screenshot_20261019-083243 (Portrait Quick Actions bar)"
    ),
    "Screenshot_20261024-200546_Lightroom.png": (
        "Presets", "Presets > Adaptive: Blur Background Active",
        "Right dock (Presets active); Preset applied state: Amount slider ('+100'); Selected preset chip 'Blur Background'; Preset categories row; Left canvas preview",
        "Screenshot_20261019-083253 (Portrait Premium presets)"
    ),
    "Screenshot_20261024-200549_Lightroom.png": (
        "Presets", "Presets > Premium (Adaptive Packs Landscape)",
        "Preset Tabs: 'Recommended', 'Premium' [active], 'Yours'; Vertical scrollable pack list: 'Adaptive: Portrait (12)', 'Adaptive: Sky (9)', 'Adaptive: Subject (9)', 'Adaptive: Landscape (8)'; Left canvas",
        "Screenshot_20261019-083253 (Portrait Premium presets)"
    ),
    "Screenshot_20261024-200557_Lightroom.png": (
        "Presets", "Presets > Recommended (Loaded Categories)",
        "Preset Tabs: 'Recommended' [active], 'Premium', 'Yours'; Category filter chips: 'All', 'Subtle', 'Strong', 'B&W'; Thumbnail cards of recommended presets; Left canvas",
        "Screenshot_20261019-083251 (Portrait Recommended presets analyzing state)"
    ),
    "Screenshot_20261024-200600_Lightroom.png": (
        "Presets", "Presets > Yours (User Presets Landscape)",
        "Preset Tabs: 'Recommended', 'Premium', 'Yours' [active]; User folders: 'User Presets (2)', 'Autumn Forest (9)', 'Dark and Moody (5)', 'Northlandscapes - Astrophotography (10)'; Left canvas",
        "Screenshot_20261019-083256 (Portrait Yours preset tab)"
    ),
    "Screenshot_20261024-200617_Lightroom.png": (
        "Actions", "Actions > Enhance Sliders",
        "Quick Actions sub-panel: Enhance active; Adjustment sliders for Subject and Background auto-enhancement; Left canvas",
        "Screenshot_20261019-083243 (Portrait Quick Actions bar)"
    ),
    "Screenshot_20261024-200624_Lightroom.png": (
        "Actions", "Actions > Subject Sliders",
        "Quick Actions sub-panel: Subject active; Presets 'Pop' and 'Warm Pop'; Sliders for 'Exposure' and 'Saturation'; Left canvas",
        "Screenshot_20261019-083243 (Portrait Quick Actions bar)"
    ),
    "Screenshot_20261024-200710_Lightroom.png": (
        "Crop", "Crop > Aspect Ratio Tab (Original Selected)",
        "Crop Aspect tab: 'Original' aspect selected; Free/Lock buttons; Straighten dial; Left canvas",
        "Screenshot_20261019-083306 (Portrait Crop Aspect tab)"
    ),
    "Screenshot_20261024-200714_Lightroom.png": (
        "Crop", "Crop > Aspect (Generative Expand Active)",
        "Crop Aspect tab: 'Expand' toggle switch turned ON (blue); Canvas shows transparent checkerboard expansion canvas border beyond original image boundary",
        "Screenshot_20261019-083306 (Portrait Crop Aspect tab with Expand toggle)"
    ),
    "Screenshot_20261024-200720_Lightroom.png": (
        "Crop", "Crop > Aspect > Ratios Dropdown Menu",
        "Crop Aspect tab with open Ratios popup dropdown: 'Original', '1:1 (Square)', '4:5', '5:7', '2:3', '16:9'; Left canvas",
        "Screenshot_20261019-083306 (Portrait Crop Aspect tab)"
    ),
    "Screenshot_20261024-200728_Lightroom.png": (
        "Crop", "Crop > Aspect > Instagram Ratios Dropdown Menu",
        "Crop Aspect tab with open Instagram ratios dropdown: 'Square (1:1)', 'Portrait (4:5)', 'Story (9:16)'; Left canvas",
        "Screenshot_20261019-083306 (Portrait Crop Aspect tab)"
    ),
    "Screenshot_20261024-200731_Lightroom.png": (
        "Crop", "Crop > Aspect Ratio Baseline",
        "Crop Aspect tab in default unedited state with Original ratio and straighten dial at 0.00°",
        "Screenshot_20261019-083306 (Portrait Crop Aspect tab)"
    ),
    "Screenshot_20261024-200743_Lightroom.png": (
        "Crop", "Crop > Geometry > Guided Upright Tool Active",
        "Geometry tab: Guided Upright selected; Left canvas displays two interactive guide lines for manual perspective convergence correction",
        "Screenshot_20261019-083314 (Portrait Crop Geometry tab)"
    ),
    "Screenshot_20261024-200754_Lightroom.png": (
        "Light", "Edit > Light Panel (Tone Controls & Curve Landscape)",
        "Right dock (Edit active); Sub-tool column (Light active); Adjustment column displaying ALL tone sliders: 'Exposure [0.00]', 'Contrast [0]', 'Highlights [0]', 'Shadows [0]', 'Whites [0]', 'Blacks [0]' + 'Curve' button; Left canvas",
        "Screenshot_20261019-083341 & 083407 (Portrait Light panel tone sliders)"
    ),
    "Screenshot_20261024-200801_Lightroom.png": (
        "Light", "Edit > Light Panel (HDR & SDR Controls Landscape)",
        "Light panel adjustment column scrolled down: 'Edit in HDR Mode' toggle, 'HDR Limit' slider, 'SDR Preview' toggle, 'Visualize HDR' toggle; Left canvas",
        "Screenshot_20261019-083407 (Portrait Light panel HDR mode)"
    ),
    "Screenshot_20261024-200806_Lightroom.png": (
        "Color", "Edit > Color Panel (Landscape Main)",
        "Right dock (Edit active); Sub-tool column (Color active); Adjustment column: 'B & W' pill, 'Grading' button, 'Mix' button; White balance row ('As Shot', Eyedropper); Sliders: 'Temp [0]', 'Tint [0]', 'Vibrance [0]', 'Saturation [0]'; Left canvas",
        "Screenshot_20261019-083509 (Portrait Color panel main)"
    ),
    "Screenshot_20261024-200814_Lightroom.png": (
        "Color_Grading", "Color Grading > Shadows Wheel (Landscape)",
        "Color Grading panel: Wheel mode icons (3-Way, Shadows [active], Midtones, Highlights, Global); Chromaticity Color Wheel with center puck and outer luminance ring; Values ('Shadows', 'Hue 0', 'Sat 0', 'Luminance 0'); 'DONE' button; Left canvas",
        "Screenshot_20261019-083519 & 083531 (Portrait Color Grading Shadows wheel)"
    ),
    "Screenshot_20261024-200823_Lightroom.png": (
        "Color_Grading", "Color Grading > Global Wheel & Sliders (Landscape)",
        "Color Grading panel: Global wheel active; Chromaticity wheel; Expanded numeric sliders below: 'Hue [0]', 'Saturation [0]', 'Luminance [0]'; 'DONE' button; Left canvas",
        "Screenshot_20261019-083543 (Portrait Color Grading Global wheel)"
    ),
    "Screenshot_20261024-200832_Lightroom.png": (
        "Color_Mix", "Color Mix > HSL Sliders (Landscape)",
        "Color Mix panel: TAT crosshair button; 8 Color Swatch Chips (Red [active], Orange, Yellow, Green, Aqua, Blue, Purple, Magenta); HSL Sliders ('Hue [0]', 'Saturation [0]', 'Luminance [0]'); 'DONE' button; Left canvas",
        "Screenshot_20261019-083551 (Portrait Color Mix HSL mixer)"
    ),
    "Screenshot_20261024-200905_Lightroom.png": (
        "Color", "Edit > Color (B&W Mode Active / As Shot)",
        "Color panel with 'B & W' mode active; White Balance dropdown 'As Shot'; Temperature and Tint sliders; Left canvas rendered in monochrome",
        "Screenshot_20261019-083509 (Portrait Color panel)"
    ),
    "Screenshot_20261024-200911_Lightroom.png": (
        "Color", "Edit > Color (B&W Mode Active / Custom WB)",
        "Color panel with 'B & W' mode active; White Balance dropdown set to 'Custom'; Modified Temperature and Tint sliders; Left canvas in monochrome",
        "Screenshot_20261019-083509 (Portrait Color panel)"
    ),
    "Screenshot_20261024-200919_Lightroom.png": (
        "Lens_Blur", "Lens Blur > Focus Range Histogram (Landscape)",
        "Lens Blur panel: Interactive Depth Map Focus Range Histogram; Focal point crosshair on canvas; Focus range selector handles; Bokeh shapes chips; Left canvas",
        "Screenshot_20261019-083626 (Portrait Lens Blur depth detection & bokeh controls)"
    ),
    "Screenshot_20261024-200924_Lightroom.png": (
        "Lens_Blur", "Lens Blur > Bokeh Shapes & Amount (Landscape)",
        "Lens Blur panel: Slider ('Blur amount [+21]'); Bokeh Shape Chips ('Circle', 'Bubble', '5-blade', 'Ring', 'Oval'); 'Boost' slider; Left canvas with blurred background and sharp foreground mountain",
        "Screenshot_20261019-083626 (Portrait Lens Blur depth detection & bokeh controls)"
    ),
    "Screenshot_20261024-200935_Lightroom.png": (
        "Effects", "Edit > Effects > Effects Tab (Landscape)",
        "Right dock (Edit active); Sub-tool column (Effects active); Sub-tabs ('Effects' [active], 'Vignette', 'Grain'); Sliders ('Texture [0]', 'Clarity [0]', 'Dehaze [0]'); Left canvas",
        "Screenshot_20261019-083640 (Portrait Effects tab)"
    ),
    "Screenshot_20261024-200937_Lightroom.png": (
        "Effects", "Edit > Effects > Vignette Tab Baseline (Landscape)",
        "Effects panel: Sub-tabs ('Effects', 'Vignette' [active], 'Grain'); Slider ('Amount [0]'); Resting state; Left canvas",
        "Screenshot_20261019-083652 (Portrait Vignette tab)"
    ),
    "Screenshot_20261024-200942_Lightroom.png": (
        "Effects", "Edit > Effects > Vignette Tab Expanded (Landscape)",
        "Effects panel: Vignette active with modified slider; Expanded full slider set: 'Amount [-34]', 'Midpoint [+50]', 'Feather [+50]', 'Roundness [0]', 'Highlights [0]'; Left canvas",
        "Screenshot_20261019-083652 (Portrait Vignette tab)"
    ),
    "Screenshot_20261024-200945_Lightroom.png": (
        "Effects", "Edit > Effects > Grain Tab (Landscape)",
        "Effects panel: Sub-tabs ('Effects', 'Vignette', 'Grain' [active]); Sliders ('Amount [+12]', 'Size [+25]', 'Roughness [+50]'); Left canvas",
        "Screenshot_20261019-083657 (Portrait Grain tab)"
    ),
    "Screenshot_20261024-200949_Lightroom.png": (
        "Detail", "Edit > Detail > Sharpening Tab (Landscape)",
        "Right dock (Edit active); Sub-tool column (Detail active); Sub-tabs ('Sharpening' [active], 'Noise', 'Color noise'); Sliders ('Amount [+14]', 'Radius [+1.00]', 'Detail [+25]', 'Masking [0]'); Left canvas",
        "Screenshot_20261019-083710 (Portrait Detail Sharpening tab)"
    ),
    "Screenshot_20261024-200951_Lightroom.png": (
        "Detail", "Edit > Detail > Noise Tab Baseline (Landscape)",
        "Detail panel: Sub-tabs ('Sharpening', 'Noise' [active], 'Color noise'); Slider ('Luminance [0]'); Resting state; Left canvas",
        "Screenshot_20261019-083710 (Portrait Detail panel)"
    ),
    "Screenshot_20261024-200956_Lightroom.png": (
        "Detail", "Edit > Detail > Noise Tab Expanded (Landscape)",
        "Detail panel: Noise active with modified slider; Expanded sliders: 'Luminance [+30]', 'Detail [+50]', 'Contrast [0]'; Left canvas",
        "Screenshot_20261019-083710 (Portrait Detail panel)"
    ),
    "Screenshot_20261024-201000_Lightroom.png": (
        "Detail", "Edit > Detail > Color Noise Tab (Landscape)",
        "Detail panel: Sub-tabs ('Sharpening', 'Noise', 'Color noise' [active]); Sliders ('Color noise [+25]', 'Detail [+50]', 'Smoothness [+50]'); Left canvas",
        "Screenshot_20261019-083710 (Portrait Detail panel)"
    ),
    "Screenshot_20261024-201008_Lightroom.png": (
        "Profiles", "Edit > Profiles Browser Favorites (Landscape)",
        "Profiles panel: Dropdown 'Favorites'; Live rendered preview card tiles ('Color', 'Monochrome', 'Portrait', 'Landscape', 'Vivid'); Left canvas",
        "Screenshot_20261019-083723 (Portrait Profiles browser)"
    ),
    "Screenshot_20261024-201016_Lightroom.png": (
        "Profiles", "Edit > Profiles Browser Scrolled (Landscape)",
        "Profiles panel: Scrolled view showing creative profile groups ('Basic', 'Artistic', 'B&W', 'Modern', 'Vintage'); Left canvas",
        "Screenshot_20261019-083723 (Portrait Profiles browser)"
    ),
    "Screenshot_20261024-201028_Lightroom.png": (
        "Masking", "Masking Workspace (Active Subject Mask)",
        "Right dock (Masking active); Mask Layer Card Stack showing 'Mask 1' (Subject icon, eye toggle, invert icon); Left canvas showing active red mask overlay (#80E02B2B) over mountain silhouettes; Local adjustment tool rail",
        "Screenshot_20261019-083741 (Portrait Masking resting state)"
    ),
    "Screenshot_20261024-201040_Lightroom.png": (
        "Masking", "Masking Layer Context Menu",
        "Mask Layer Card 3-dots popup menu: 'Add to Mask', 'Subtract from Mask', 'Intersect Mask With', 'Invert', 'Duplicate', 'Delete'; Left canvas with red mask overlay",
        "Screenshot_20261019-083741 (Portrait Masking workspace)"
    ),
    "Screenshot_20261024-201045_Lightroom.png": (
        "Masking", "Create New Mask Dialog (Top Options)",
        "Create new Mask modal dialog: AI options ('Select Subject', 'Select Sky', 'Select Background') with cloud icons; Manual options ('Brush'); Help [?]; Left canvas",
        "Screenshot_20261019-083730 (Portrait Create new Mask sheet)"
    ),
    "Screenshot_20261024-201048_Lightroom.png": (
        "Masking", "Create New Mask Dialog (Scrolled Options)",
        "Create new Mask modal dialog scrolled: 'Linear Gradient', 'Radial Gradient', 'Color Range', 'Luminance Range'; Left canvas",
        "Screenshot_20261019-083730 (Portrait Create new Mask sheet)"
    ),
    "Screenshot_20261024-201104_Lightroom.png": (
        "Masking", "Masking > Local Light Adjustments",
        "Local adjustments column for active mask: Sliders ('Exposure [0.00]', 'Contrast [0]', 'Highlights [0]', 'Shadows [0]', 'Whites [0]', 'Blacks [0]'); Mask layer card; Left canvas",
        "Screenshot_20261019-083341 (Global Light panel)"
    ),
    "Screenshot_20261024-201109_Lightroom.png": (
        "Masking", "Masking > Local Color Adjustments",
        "Local adjustments column for active mask: Sliders ('Temp [0]', 'Tint [0]', 'Saturation [0]'); 'Color' swatch picker; Mask layer card; Left canvas",
        "Screenshot_20261019-083509 (Global Color panel)"
    ),
    "Screenshot_20261024-201112_Lightroom.png": (
        "Masking", "Masking > Local Effects Adjustments",
        "Local adjustments column for active mask: Sliders ('Texture [0]', 'Clarity [0]', 'Dehaze [0]'); Mask layer card; Left canvas",
        "Screenshot_20261019-083640 (Global Effects panel)"
    ),
    "Screenshot_20261024-201114_Lightroom.png": (
        "Masking", "Masking > Local Detail Adjustments",
        "Local adjustments column for active mask: Sliders ('Sharpness [0]', 'Noise [0]'); Mask layer card; Left canvas",
        "Screenshot_20261019-083710 (Global Detail panel)"
    ),
    "Screenshot_20261024-201117_Lightroom.png": (
        "Masking", "Masking > Local Optics Adjustments",
        "Local adjustments column for active mask: Sliders ('Defringe [0]', 'Moiré [0]'); Mask layer card; Left canvas",
        "Screenshot_20261019-083714 (Global Optics panel)"
    ),
    "Screenshot_20261024-201127_Lightroom.png": (
        "Remove", "Remove Tool Workspace (Landscape)",
        "Right dock (Remove active); Sub-panel: Mode chip 'Remove'; Sliders ('Size [10]'); Toggle Switches ('Detect objects', 'Generative AI'); 'Refine' button; Left canvas",
        "Screenshot_20261019-083752 (Portrait Remove tool)"
    ),
    "Screenshot_20261024-201133_Lightroom.png": (
        "Remove", "Remove Tool Mode Dropdown",
        "Sub-panel with open tool mode popup dropdown: 'Remove' (Generative/Heal brush), 'Heal' (texture blending), 'Clone' (exact pixel stamp); Left canvas",
        "Screenshot_20261019-083752 (Portrait Remove tool)"
    ),
    "Screenshot_20261024-201151_Lightroom.png": (
        "Editor_and_Export", "Editor Overflow Menu (Landscape 3-dots)",
        "Top-right Dropdown Popup: 'Save copy to device', 'View options', 'Create Preset', 'Copy Settings', 'Paste Settings', 'Apply from previous', 'Help', 'Your profile'; Full landscape canvas and dock",
        "Screenshot_20261019-083218 (Portrait Editor overflow menu)"
    ),
    "Screenshot_20261024-201212_Lightroom.png": (
        "Editor_and_Export", "Share / Export Dialog (Landscape)",
        "Center Dialog: 'Share 1 Photo'; Quick Buttons ('Share', 'Export as...'); List Items ('Save copy to device', 'Get shareable link', 'Create edit replay', 'Post to Community'); Left canvas",
        "Screenshot_20261019-083231 (Portrait Share / Export modal)"
    ),
    "Screenshot_20261024-201218_Lightroom.png": (
        "Editor_and_Export", "Share Multi-Select Filmstrip",
        "Bottom Filmstrip Carousel with multi-photo checkboxes; Selected photo indicator; Share / Export options; Left canvas",
        "Screenshot_20261019-083231 (Portrait Share modal photo carousel)"
    ),
    "Screenshot_20261024-201225_Lightroom.png": (
        "Editor_and_Export", "Editor Undo / Redo / Reset Dropdown",
        "Top Bar Undo/Redo popup menu: 'Undo', 'Redo', 'Reset to Open', 'Reset to Original'; Left canvas; Right dock",
        "Screenshot_20261019-083216 (Top bar undo icon dropdown in portrait)"
    ),
    "Screenshot_20261024-201235_Lightroom.png": (
        "Community", "Community Discover Feed (Portrait)",
        "Top Bar (Drawer hamburger, Search glass, Notifications bell, 3-dots); Sub-tabs ('For you' [active], 'Featured', 'Remixes'); Community creator photo cards with avatar, likes, and remix buttons; Bottom Navigation ('Community' active blue)",
        "Screenshot_20261019-082951 (Bottom navigation bar Community tab)"
    ),
    "Screenshot_20261024-201248_Lightroom.png": (
        "Settings_and_Account", "Account & Generative Credits Screen",
        "Header ('Account', Close [X]); User info ('Siddhu Amup', 'siddhuamup@gmail.com'); Subscription status; Generative Credits balance counter; Cloud storage progress bar",
        "Screenshot_20261019-083004 (Main drawer 'Account' item)"
    ),
    "Screenshot_20261024-201254_Lightroom.png": (
        "Settings_and_Account", "Preferences Screen",
        "Header ('Preferences', Close [X]); Toggle switches and setting rows: Cloud cache size, Auto-add device photos, HDR editing default, GPU acceleration rendering",
        "Screenshot_20261019-083004 (Main drawer 'Preferences' item)"
    ),
    "Screenshot_20261024-201303_Lightroom.png": (
        "Settings_and_Account", "About Lightroom Screen",
        "Header ('About Lightroom', Close [X]); App Name 'Adobe Lightroom', Version 'v11.8.05', Build number, Legal terms, Privacy policy, Third-party notices",
        "Screenshot_20261019-083004 (Main drawer 'About Lightroom' item)"
    ),
    "Screenshot_20261024-201307_Lightroom.png": (
        "Settings_and_Account", "Local Storage & Cache Settings",
        "Header ('Local storage', Close [X]); Storage usage gauge (Free space, Lightroom cache, Original photos); Action buttons: 'Clear cache', 'Store originals on device'",
        "Screenshot_20261019-083004 (Main drawer 'Local storage' item)"
    ),
    "Screenshot_20261024-201313_Lightroom.png": (
        "Settings_and_Account", "Help & Support Screen",
        "Header ('Help & Support', Close [X]); Support list: 'Frequently Asked Questions', 'Guided Tutorials', 'Community Forums', 'Send Feedback', 'Contact Support'",
        "Screenshot_20261019-083004 (Main drawer 'Help & Support' item)"
    ),
    "Screenshot_20261024-201413_Lightroom.png": (
        "Camera", "In-App Camera DNG Viewfinder",
        "Live camera preview stream; Top bar: Flash mode (Auto/Off/On), Lens switcher (1x/0.6x/2x), RAW/DNG format badge, Settings; Bottom bar: Shutter button, Mode indicator ('Auto'), Gallery thumbnail preview",
        "Screenshot_20261019-083018 (Gallery cloud tab Camera FAB button)"
    ),
    "Screenshot_20261024-201419_Lightroom.png": (
        "Camera", "In-App Camera Quick Options Bar",
        "Camera viewfinder with expanded quick controls bar: Aspect ratio (4:3, 16:9, 1:1), Self-timer (2s, 5s, 10s), Composition grid toggle (Thirds, Golden), Level gauge toggle",
        "Screenshot_20261019-083018 (Camera launcher)"
    ),
    "Screenshot_20261024-201426_Lightroom.png": (
        "Camera", "In-App Camera Live Film Profile Strip",
        "Camera viewfinder with horizontal profile carousel: Live camera feed rendered through real-time creative filters ('Natural', 'Vivid', 'Warm', 'Cool', 'B&W', 'High Contrast')",
        "Screenshot_20261019-083018 (Camera launcher)"
    ),
    "Screenshot_20261024-201435_Lightroom.png": (
        "Camera", "In-App Camera Auto vs Pro Mode Selector",
        "Camera mode dropdown: 'Auto', 'Professional' (unlocks manual ISO slider [100-3200], Shutter speed [1/4000s - 1/4s], Exposure compensation [+/- 3 EV], White balance kelvin, Manual focus with green peaking)",
        "Screenshot_20261019-083018 (Camera launcher)"
    ),
    "Screenshot_20261024-201449_Lightroom.png": (
        "Camera", "In-App Camera Settings Modal",
        "Camera settings sheet: 'Save uncompressed DNG', 'Volume button action' (Shutter/Zoom), 'Geotagging' toggle, 'Lens profile correction' toggle, 'Grid overlay type'",
        "Screenshot_20261019-083018 (Camera launcher)"
    )
}

print(f"Total controls metadata entries: {len(controls_data)}")

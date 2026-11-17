import os
import json

base_dir = r'C:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference'
manifest_path = os.path.join(base_dir, 'PANEL_MANIFEST.json')

with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = json.load(f)

# Build a lookup dictionary
files_by_name = {f['filename']: f for f in manifest['files']}

# Detailed semantic database for all 102 files
metadata_db = {
    # VERTICAL SCREENSHOTS (1 to 36)
    "Screenshot_20261019-082951_Lightroom.png": {
        "panel": "Gallery",
        "subtool": "Device Photos (Gallery Tab)",
        "controls": "Top Bar (Drawer Hamburger, 'Photos on device' Dropdown, Filter Funnel, View Options Bars, Overflow 3-dots); Date Headers ('October 17, 2026 (18)', 'October 16, 2026 (1)'); Multi-column photo grid; Bottom Navigation Bar ('Gallery' [active blue], 'Lightroom', 'Community')",
        "counterpart": "None in horizontal folder (Gallery is portrait-primary; see Screenshot_20261019-083018 for Cloud tab)"
    },
    "Screenshot_20261019-083004_Lightroom.png": {
        "panel": "Settings_and_Account",
        "subtool": "Main Navigation Drawer",
        "controls": "Profile Header (Avatar, Name 'Siddhu Amup', Email 'siddhuamup@gmail.com'); Menu Items (Account, Premium Features, Early access, Preferences, What's New, About Lightroom, Local storage, Help & Support, Rate Lightroom); Social Media Icons (Facebook, Instagram, X); Dimmed scrim over gallery grid",
        "counterpart": "Screenshot_20261024-201248 (Account), Screenshot_20261024-201254 (Preferences), Screenshot_20261024-201303 (About), Screenshot_20261024-201307 (Storage), Screenshot_20261024-201313 (Help)"
    },
    "Screenshot_20261019-083018_Lightroom.png": {
        "panel": "Gallery",
        "subtool": "Lightroom Cloud Imports Tab",
        "controls": "Top Bar (Drawer Hamburger, Search Glass, Notification Bell, Cloud Sync Icon, Overflow 3-dots); Album Selector ('Imports' Dropdown, Filter Funnel); Masonry photo/video thumbnail grid with video badge indicators; Dual Floating Action Button (Add Photos [+], In-App Camera); Bottom Navigation ('Gallery', 'Lightroom' [active blue], 'Community')",
        "counterpart": "Screenshot_20261019-082951 (Device Gallery) & Screenshot_20261019-083818 (View Options Sheet)"
    },
    "Screenshot_20261019-083216_Lightroom.png": {
        "panel": "Editor_and_Export",
        "subtool": "Loupe Editor Resting State",
        "controls": "Top Navigation Bar (Back Arrow, Share/Export Icon, Overflow 3-dots); Center Loupe Canvas (Mountain sunset graphic); Bottom Floating Tool Dock ('Actions', 'Presets', 'Crop', 'Edit', 'Masking', 'Remove')",
        "counterpart": "Screenshot_20261024-200525 & Screenshot_20261024-200532 (Landscape Loupe Editor resting state)"
    },
    "Screenshot_20261019-083218_Lightroom.png": {
        "panel": "Editor_and_Export",
        "subtool": "Editor Overflow Menu (3-dots)",
        "controls": "Top-right Dropdown Popup: 'Save copy to device', 'View options', 'Create Preset', 'Copy Settings', 'Paste Settings' [disabled], 'Apply from previous', 'Help', 'Your profile'; Dimmed background canvas; Resting bottom dock visible below",
        "counterpart": "Screenshot_20261024-201151 (Landscape Editor 3-dots overflow menu)"
    },
    "Screenshot_20261019-083231_Lightroom.png": {
        "panel": "Editor_and_Export",
        "subtool": "Share / Export Modal",
        "controls": "Modal Header (Back Arrow, 'Share 1 Photo' Title, 'Select All' Pill Button); Horizontal Photo Carousel with active blue border and checkbox; Quick Action Circle Buttons ('Share', 'Export as...'); List Items ('Save copy to device' + Settings gear, 'Get shareable link', 'Create edit replay', 'Post to Community')",
        "counterpart": "Screenshot_20261024-201212 (Landscape Share 1 Photo options) & Screenshot_20261024-201218 (Multi-select filmstrip)"
    },
    "Screenshot_20261019-083243_Lightroom.png": {
        "panel": "Actions",
        "subtool": "Quick Actions Tool Bar",
        "controls": "Bottom Dock (Slot 1 'Actions' active blue); Sub-toolbar Strip ('Auto', 'Enhance', 'Subject', 'Background', 'Sky', 'Animate'); Center Canvas; Top Bar (Back, Share, Overflow)",
        "counterpart": "Screenshot_20261024-200540 (Landscape Quick Actions bar), Screenshot_20261024-200617 (Enhance sliders), Screenshot_20261024-200624 (Subject sliders)"
    },
    "Screenshot_20261019-083251_Lightroom.png": {
        "panel": "Presets",
        "subtool": "Presets > Recommended (Analyzing)",
        "controls": "Bottom Dock (Slot 2 'Presets' active blue); Preset Tabs ('Recommended' [active underline], 'Premium', 'Yours'); Loading State ('Analyzing your photo...' placeholder icon with blue scan progress bar)",
        "counterpart": "Screenshot_20261024-200557 (Landscape Recommended presets loaded with category chips)"
    },
    "Screenshot_20261019-083253_Lightroom.png": {
        "panel": "Presets",
        "subtool": "Presets > Premium (Adaptive Packs)",
        "controls": "Preset Tabs ('Recommended', 'Premium' [active underline], 'Yours'); Pack List with thumbnail previews, Cloud icons, and preset count badges ('Adaptive: Portrait (12)', 'Adaptive: Sky (9)', 'Adaptive: Subject (9)', 'Adaptive: Landscape (8)'); Chevron navigation arrows",
        "counterpart": "Screenshot_20261024-200549 (Landscape Premium preset packs) & Screenshot_20261024-200546 (Adaptive preset active)"
    },
    "Screenshot_20261019-083256_Lightroom.png": {
        "panel": "Presets",
        "subtool": "Presets > Yours (User Presets)",
        "controls": "Preset Tabs ('Recommended', 'Premium', 'Yours' [active underline]); User Preset Folders with item counts ('User Presets (2)', 'Autumn Forest (9)', 'Dark and Moody (5)', 'Northlandscapes - Astrophotography (10)'); Chevron arrows",
        "counterpart": "Screenshot_20261024-200600 (Landscape Yours preset tab)"
    },
    "Screenshot_20261019-083306_Lightroom.png": {
        "panel": "Crop",
        "subtool": "Crop & Expand > Aspect Ratio Tab",
        "controls": "Top Bar ('Original' Pill, Help [?]); Canvas with 8-handle Crop Boundary Box and bottom Straighten Arc Dial ('0.00°'); Floating Action Buttons (Free Transform, Lock Aspect, Rotate 90° Clockwise, Overflow 3-dots); Sub-tabs ('Aspect' [active underline], 'Geometry'); Aspect Mode Row ('Expand' Toggle switch, 'Original' [active], 'Ratios', 'Instagram', 'Tiktok'); Bottom Action Bar (Cancel [X], 'Crop and expand' Title, Accept [Checkmark])",
        "counterpart": "Screenshot_20261024-200520, Screenshot_20261024-200710, Screenshot_20261024-200714, Screenshot_20261024-200720, Screenshot_20261024-200728, Screenshot_20261024-200731"
    },
    "Screenshot_20261019-083314_Lightroom.png": {
        "panel": "Crop",
        "subtool": "Crop & Expand > Geometry Tab",
        "controls": "Sub-tabs ('Aspect', 'Geometry' [active underline]); Upright Mode Buttons ('Auto', 'Level', 'Vertical', 'Full', 'Guided'); Toggle Switch ('Constrain Crop'); Expandable Section ('Manual Transforms'); Bottom Action Bar (Cancel [X], 'Crop and expand', Accept [Checkmark])",
        "counterpart": "Screenshot_20261024-200437, Screenshot_20261024-200441, Screenshot_20261024-200449, Screenshot_20261024-200504, Screenshot_20261024-200743"
    },
    "Screenshot_20261019-083341_Lightroom.png": {
        "panel": "Light",
        "subtool": "Light Panel (Tone Controls & Curve Entry)",
        "controls": "Sub-toolbar ('Auto', 'Light' [active white/blue dot], 'Color', 'Blur', 'Effects', 'Detail', 'Optics', 'Profiles'); Bottom Dock (Slot 4 'Edit' active blue); Primary Sliders ('Exposure [0.00]', 'Contrast [0]', 'Highlights [0]'); Button ('Curve' with tone curve icon); Top Bar (Back, Undo/Redo, Share, Overflow)",
        "counterpart": "Screenshot_20261024-200754 (Landscape Light panel with all tone sliders and Curve button visible)"
    },
    "Screenshot_20261019-083407_Lightroom.png": {
        "panel": "Light",
        "subtool": "Light Panel (Blacks & HDR Mode)",
        "controls": "Light Panel scrolled down: Slider ('Blacks [0]'); HDR Section Card: Toggle Switch ('Edit in HDR Mode' [ON blue]), Slider ('HDR Limit [+2.30]'); Top-right Canvas HDR Badge indicator; Bottom Dock (Edit active)",
        "counterpart": "Screenshot_20261024-200801 (Landscape Light panel HDR controls & SDR preview)"
    },
    "Screenshot_20261019-083439_Lightroom.png": {
        "panel": "Tone_Curve",
        "subtool": "Point Curve (White / Composite Channel)",
        "controls": "Interactive 4x4 Grid Overlay on photo canvas with diagonal curve line and 2 endpoint handles (0,0) and (255,255); Bottom Panel: Title 'Curve', Action Button 'DONE'; Channel Selectors: White/Composite Circle [active outer ring], Red Circle, Green Circle, Blue Circle, Parametric TAT Curve Icon",
        "counterpart": "Landscape counterpart omitted in reference dataset (Tone Curve is portrait-exclusive full overlay in mobile Lightroom)"
    },
    "Screenshot_20261019-083443_Lightroom.png": {
        "panel": "Tone_Curve",
        "subtool": "Point Curve (Red Channel)",
        "controls": "Tone Curve Grid with Red curve line; Bottom Channel Selectors: Red Circle [active with center white/red dot]; White, Green, Blue, Parametric TAT; 'DONE' button",
        "counterpart": "Landscape counterpart omitted in reference dataset"
    },
    "Screenshot_20261019-083445_Lightroom.png": {
        "panel": "Tone_Curve",
        "subtool": "Point Curve (Green Channel)",
        "controls": "Tone Curve Grid with Green curve line; Bottom Channel Selectors: Green Circle [active with center white/green dot]; White, Red, Blue, Parametric TAT; 'DONE' button",
        "counterpart": "Landscape counterpart omitted in reference dataset"
    },
    "Screenshot_20261019-083447_Lightroom.png": {
        "panel": "Tone_Curve",
        "subtool": "Point Curve (Blue Channel)",
        "controls": "Tone Curve Grid with Blue curve line; Bottom Channel Selectors: Blue Circle [active with center white/blue dot]; White, Red, Green, Parametric TAT; 'DONE' button",
        "counterpart": "Landscape counterpart omitted in reference dataset"
    },
    "Screenshot_20261019-083452_Lightroom.png": {
        "panel": "Tone_Curve",
        "subtool": "Parametric Curve (Targeted Adjustment / Split Sliders)",
        "controls": "Parametric Curve Graph on canvas; Range Split Slider Bar below graph with 3 movable split thumbs (Shadows/Darks, Darks/Lights, Lights/Highlights); Bottom Channel Selectors: Parametric S-curve Icon [active]; 'DONE' button",
        "counterpart": "Landscape counterpart omitted in reference dataset"
    },
    "Screenshot_20261019-083509_Lightroom.png": {
        "panel": "Color",
        "subtool": "Edit > Color (Main Controls)",
        "controls": "Sub-toolbar ('Color' active); Action Buttons ('B & W' Pill, 'Grading' [3-circle icon], 'Mix' [rainbow donut icon]); White Balance Row ('White balance', Dropdown 'As Shot', Eyedropper Tool); Sliders ('Temp [0]' with blue-to-yellow gradient track, 'Tint [0]' with green-to-magenta track); Bottom Dock (Edit active)",
        "counterpart": "Screenshot_20261024-200806 (Landscape Color panel), Screenshot_20261024-200905 (B&W mode active), Screenshot_20261024-200911 (B&W Custom WB)"
    },
    "Screenshot_20261019-083519_Lightroom.png": {
        "panel": "Color_Grading",
        "subtool": "Color Grading (Shadows Wheel Resting)",
        "controls": "Header ('Color Grading', 'DONE' button); Wheel Mode Switchers (3-Way Wheels, Shadows [active], Midtones, Highlights, Global); Chromaticity Color Wheel with center saturation puck and outer luminance ring; Values ('Shadows', 3-dots, 'Hue 0', 'Sat 0'); Slider ('Luminance [0]')",
        "counterpart": "Screenshot_20261024-200814 (Landscape Color Grading Shadows wheel)"
    },
    "Screenshot_20261019-083531_Lightroom.png": {
        "panel": "Color_Grading",
        "subtool": "Color Grading (Shadows Wheel Interactive State)",
        "controls": "Identical layout to 083519 showing Shadows wheel touch target and luminance arc slider handle at bottom sheet boundary",
        "counterpart": "Screenshot_20261024-200814 (Landscape Color Grading Shadows wheel)"
    },
    "Screenshot_20261019-083543_Lightroom.png": {
        "panel": "Color_Grading",
        "subtool": "Color Grading (Global Wheel)",
        "controls": "Wheel Mode Switchers: Global Half-Circle Icon [active]; Chromaticity Wheel with center puck and outer luminance ring; Values ('Global', 3-dots, 'Hue 0', 'Sat 0'); Slider ('Luminance [0]'); 'DONE' button",
        "counterpart": "Screenshot_20261024-200823 (Landscape Color Grading Global wheel with expanded HSL sliders)"
    },
    "Screenshot_20261019-083551_Lightroom.png": {
        "panel": "Color_Mix",
        "subtool": "Color Mix (HSL 8-Color Mixer)",
        "controls": "Header ('Color Mix', Targeted Adjustment Tool [TAT crosshair icon], 'DONE' button); 8 Color Swatch Chips (Red [active white dot], Orange, Yellow, Green, Aqua, Blue, Purple, Magenta); HSL Sliders ('Hue [0]', 'Saturation [0]', 'Luminance [0]') with color gradient tracks",
        "counterpart": "Screenshot_20261024-200832 (Landscape Color Mix HSL panel)"
    },
    "Screenshot_20261019-083601_Lightroom.png": {
        "panel": "Color_Mix",
        "subtool": "Color Mix > Targeted Adjustment Tool (On-Screen Dragging)",
        "controls": "Top Pill Indicator ('Saturation Orange -45' with orange swatch circle); Canvas with TAT reticle crosshair; Bottom bar with active blue TAT button; Sub-tabs ('HUE', 'SATURATION' [active], 'LUMINANCE'); Color Chips showing deltas (Red '+46', Orange '-44')",
        "counterpart": "Landscape sub-state of Color Mix TAT"
    },
    "Screenshot_20261019-083551_Lightroom_dup": {},
    "Screenshot_20261019-083626_Lightroom.png": {
        "panel": "Lens_Blur",
        "subtool": "Lens Blur (AI Depth Detection & Bokeh Shapes)",
        "controls": "Center Banner Dialog ('Detecting depth using AI...' with spinner and 'Cancel' button); Slider ('Blur amount [+21]'); Bokeh Shape Selector Chips ('Circle', 'Bubble', '5-blade', 'Ring', 'Oval', 'Focus'); Bottom Action Bar (Cancel [X], 'Lens blur', Done [Checkmark])",
        "counterpart": "Screenshot_20261024-200919 (Landscape Lens Blur depth map & focal range histogram) & Screenshot_20261024-200924 (Bokeh controls)"
    },
    "Screenshot_20261019-083640_Lightroom.png": {
        "panel": "Effects",
        "subtool": "Edit > Effects > Effects Tab",
        "controls": "Sub-toolbar ('Effects' active); Sub-tabs ('Effects' [active underline], 'Vignette', 'Grain'); Sliders ('Texture [0]', 'Clarity [0]', 'Dehaze [0]'); Bottom Dock (Edit active)",
        "counterpart": "Screenshot_20261024-200935 (Landscape Effects tab: Texture, Clarity, Dehaze)"
    },
    "Screenshot_20261019-083652_Lightroom.png": {
        "panel": "Effects",
        "subtool": "Edit > Effects > Vignette Tab",
        "controls": "Sub-tabs ('Effects', 'Vignette' [active underline], 'Grain'); Sliders ('Amount [-34]', 'Midpoint [+50]', 'Feather [+50]'); Bottom Dock (Edit active)",
        "counterpart": "Screenshot_20261024-200937 (Vignette resting) & Screenshot_20261024-200942 (Vignette expanded sliders)"
    },
    "Screenshot_20261019-083657_Lightroom.png": {
        "panel": "Effects",
        "subtool": "Edit > Effects > Grain Tab",
        "controls": "Sub-tabs ('Effects', 'Vignette', 'Grain' [active underline]); Sliders ('Amount [+12]', 'Size [+25]', 'Roughness [+50]'); Bottom Dock (Edit active)",
        "counterpart": "Screenshot_20261024-200945 (Landscape Grain tab: Amount, Size, Roughness)"
    },
    "Screenshot_20261019-083710_Lightroom.png": {
        "panel": "Detail",
        "subtool": "Edit > Detail > Sharpening Tab",
        "controls": "Sub-toolbar ('Detail' active); Sub-tabs ('Sharpening' [active underline], 'Noise', 'Color noise'); Sliders ('Amount [+14]', 'Radius [+1.00]', 'Detail [+25]'); Bottom Dock (Edit active)",
        "counterpart": "Screenshot_20261024-200949 (Sharpening tab), Screenshot_20261024-200951 & 200956 (Noise tabs), Screenshot_20261024-201000 (Color noise tab)"
    },
    "Screenshot_20261019-083714_Lightroom.png": {
        "panel": "Optics",
        "subtool": "Edit > Optics (Lens Corrections)",
        "controls": "Sub-toolbar ('Optics' active); Toggle Switches ('Remove Chromatic Aberration' [OFF], 'Enable Lens Corrections' [OFF]); Bottom Dock (Edit active)",
        "counterpart": "Landscape local optics counterpart: Screenshot_20261024-201117 (Masking > Optics)"
    },
    "Screenshot_20261019-083723_Lightroom.png": {
        "panel": "Profiles",
        "subtool": "Profiles Browser",
        "controls": "Top Profile Controls: Dropdown 'Basic', Overflow 3-dots; Horizontal Card Carousel with live rendered image thumbnails ('Monochrome', 'Artistic 01', 'Artistic 02', 'Artistic 03'); Star favorite icon; Bottom Action Bar (Cancel [X], 'Profiles', Done [Checkmark])",
        "counterpart": "Screenshot_20261024-201008 & Screenshot_20261024-201016 (Landscape Profiles browser)"
    },
    "Screenshot_20261019-083730_Lightroom.png": {
        "panel": "Masking",
        "subtool": "Create New Mask Sheet",
        "controls": "Header ('Create new Mask', Help [?]); List Options with Cloud icons: 'Select Subject', 'Select Sky', 'Select Background'; Manual Masks: 'Brush', 'Linear Gradient', 'Radial Gradient'; Range Masks: 'Color Range', 'Luminance Range'",
        "counterpart": "Screenshot_20261024-201045 & Screenshot_20261024-201048 (Landscape Create new Mask modal)"
    },
    "Screenshot_20261019-083741_Lightroom.png": {
        "panel": "Masking",
        "subtool": "Masking Workspace (Empty Masks Resting State)",
        "controls": "Bottom Dock (Slot 5 'Masking' active blue); Floating Layer Pill on right edge with blue (+) FAB button; Grayed/disabled sub-tool strip (Light, Color, Effects, Detail, Optics); Center canvas preview",
        "counterpart": "Screenshot_20261024-201028 (Landscape Masking workspace with active Subject mask)"
    },
    "Screenshot_20261019-083752_Lightroom.png": {
        "panel": "Remove",
        "subtool": "Remove Tool (Generative AI & Object Detection)",
        "controls": "Bottom Dock (Slot 6 'Remove' active blue); Left Mode Chip ('Remove' with sparkles icon); Floating Chip ('Remove extra people' with dual-person icon); Sliders ('Size [10]'); Toggle Switches ('Detect objects' [OFF], 'Generative AI' [OFF] with info [i]); Button ('Refine' [disabled pencil]); Top Bar (Back, Undo/Redo, Compare, Share, Overflow)",
        "counterpart": "Screenshot_20261024-201127 (Landscape Remove tool) & Screenshot_20261024-201133 (Remove / Heal / Clone mode switcher)"
    },
    "Screenshot_20261019-083818_Lightroom.png": {
        "panel": "Gallery",
        "subtool": "Gallery / Imports View Options & Batch Edit Sheet",
        "controls": "Bottom Modal Sheet: Section 'BATCH EDIT' ('Select' checkbox); Section 'VIEW OPTIONS' ('Square grid', 'Justified grid' [active checkmark], 'Grid segmentation' toggle, 'Segment by' chevron, 'Slideshow' play icon); Action Button ('Add photos' image+ icon); Background shows Imports grid with film video badges",
        "counterpart": "Portrait-exclusive bottom sheet in Gallery tab (see Screenshot_20261019-083018 for base imports grid)"
    }
}

print(f"Configured {len(metadata_db)} vertical mappings.")

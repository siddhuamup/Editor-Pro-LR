Use parallel agent: ultra refine the CleanLightroom Android Jetpack Compose UI to achieve 100/100 visual parity with authentic Lightroom Mobile reference screenshots, replace generic icons with authentic SVGs, complete the Masking panel and sub-modes, support horizontal/landscape orientation, and complete any remaining tasks from the previous prompt.

Working directory: CleanLightroom
Integrity mode: [OPEN: Which integrity restrictions apply?]

## Requirements

### R1. UI Visual Refinement and Authentic SVG Icon Integration
Ultra refine the UI to match the reference screenshots from `CleanLightroom/design-reference` and `CleanLightroom/design-reference/horizontal screen UI(same as verticle)`. Replace remaining generic icons with authentic SVGs from `CleanLightroom/design-reference/Svg Icons/01_Lightroom_Mobile_SVG_Pack_24px` or exact matching vector assets.

### R2. Masking Panel and Sub-modes Completion
Complete the Masking panel, sub-modes, and interactive masking features. Use REA tools on the original APK for structure, behavior, and design extraction during and after building.

### R3. Screen Orientation Handling
Ensure the UI adapts accurately to horizontal/landscape screen rotation matching the horizontal reference screenshots while preserving vertical parity.

### R4. Completion of Remaining Previous Tasks
Complete any remaining incomplete tasks from previous prompt iterations [OPEN: How should remaining tasks be identified?].

## Acceptance Criteria

### Visual Parity and Icon Alignment
- [ ] UI elements match the authentic reference screenshots in layout, typography, spacing, colors, and iconography.
- [ ] Generic placeholder icons are replaced with exact authentic SVGs from the SVG pack or exact vector drawables matching the original app.

### Masking Panel Functionality
- [ ] Masking panel opens with all sub-modes and controls matching the original application.
- [ ] Mask creation, editing, and preview function without crashes.

### Orientation and Verification
- [ ] UI renders correctly in both vertical and horizontal/landscape orientations on the emulator.
- [ ] [OPEN: How should visual parity and feature completion be verified?]

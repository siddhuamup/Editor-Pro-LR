Extract and mine all SVG icons from the original APK file "C:\Users\Siddhu\Downloads\Lightroom.apk" using REA and reverse engineering tools.

Integrity mode: development

## Requirements

### R1. APK Asset Extraction
Extract all icon and vector assets from the original APK file located at `C:\Users\Siddhu\Downloads\Lightroom.apk`.

### R2. SVG Conversion and Output Organization
Convert all extracted vector icons into valid standard `.svg` files and save them in a new folder named `mined Svg`.

## Acceptance Criteria

### Verification and Asset Validity
- [ ] All icon and vector assets from `C:\Users\Siddhu\Downloads\Lightroom.apk` are extracted and stored as `.svg` files inside the `mined Svg` directory.
- [ ] Every extracted `.svg` file is non-empty (> 0 bytes) and validates as well-formed SVG/XML.
- [ ] A programmatic verification test script executes and confirms that all generated SVG files parse cleanly and are valid.

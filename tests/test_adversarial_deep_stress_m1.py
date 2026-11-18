#!/usr/bin/env python3
"""
Adversarial Deep Stress & Oracle Sensitivity Test for Milestone 1
Target: CleanLightroom/design-reference

Author: challenger_m1_2 (teamwork_challenger)
Role: critic, specialist
"""

import os
import sys
import tempfile
import struct
import zlib
import hashlib
from io import BytesIO
from PIL import Image

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DESIGN_REF_DIR = os.path.join(PROJECT_ROOT, "CleanLightroom", "design-reference")

def test_full_raster_decompression():
    """
    Stress test 1: Full raster decompression on all 102 screenshots.
    `img.verify()` only reads headers; `img.load()` actually decompresses all IDAT zlib streams.
    """
    print("\n[Stress 1] Full Raster Decompression (IDAT / zlib bitstream check) across all 102 images...")
    decompressed_count = 0
    errors = []

    for root, dirs, files in os.walk(DESIGN_REF_DIR):
        if "Svg Icons" in root:
            continue
        for file in files:
            if file.lower().endswith(".png"):
                full_path = os.path.join(root, file)
                try:
                    with Image.open(full_path) as img:
                        # Force actual decoding of entire image into memory
                        pixels = img.load()
                        w, h = img.size
                        # Touch corner pixels and center pixel to verify memory buffer
                        _ = pixels[0, 0]
                        _ = pixels[w - 1, 0]
                        _ = pixels[0, h - 1]
                        _ = pixels[w - 1, h - 1]
                        _ = pixels[w // 2, h // 2]
                        decompressed_count += 1
                except Exception as e:
                    errors.append(f"Decompression failed for {file}: {e}")

    print(f"  -> Successfully decompressed {decompressed_count}/102 images with full pixel buffer access.")
    if errors:
        for err in errors:
            print(f"  - ERROR: {err}")
        return False
    return decompressed_count == 102

def test_png_chunk_sequence_and_iend():
    """
    Stress test 2: Exhaustive PNG chunk parser.
    Validates chunk length, chunk type, CRC32 for every single chunk in every PNG file,
    and asserts terminal chunk is IEND.
    """
    print("\n[Stress 2] Exhaustive Chunk CRC32 & Terminal IEND Audit...")
    verified_files = 0
    errors = []
    chunk_types_seen = set()

    for root, dirs, files in os.walk(DESIGN_REF_DIR):
        if "Svg Icons" in root:
            continue
        for file in files:
            if file.lower().endswith(".png"):
                full_path = os.path.join(root, file)
                with open(full_path, "rb") as f:
                    data = f.read()

                offset = 8
                last_chunk = None
                while offset < len(data):
                    if offset + 8 > len(data):
                        errors.append(f"{file}: Truncated chunk header at offset {offset}")
                        break
                    length, ctype = struct.unpack(">I4s", data[offset:offset+8])
                    chunk_data_start = offset + 8
                    chunk_data_end = chunk_data_start + length
                    crc_offset = chunk_data_end
                    if crc_offset + 4 > len(data):
                        errors.append(f"{file}: Chunk {ctype} truncated payload at offset {offset}")
                        break
                    chunk_data = data[chunk_data_start:chunk_data_end]
                    expected_crc = struct.unpack(">I", data[crc_offset:crc_offset+4])[0]
                    calc_crc = zlib.crc32(ctype + chunk_data) & 0xFFFFFFFF
                    if calc_crc != expected_crc:
                        errors.append(f"{file}: CRC failure in chunk {ctype} (offset {offset})")
                        break
                    
                    last_chunk = ctype
                    chunk_types_seen.add(ctype.decode("ascii", errors="replace"))
                    offset = crc_offset + 4

                if last_chunk != b"IEND":
                    errors.append(f"{file}: File ended with chunk {last_chunk}, expected IEND")
                else:
                    verified_files += 1

    print(f"  -> Verified {verified_files}/102 PNGs through full chunk iteration.")
    print(f"  -> Distinct PNG chunk types observed: {sorted(list(chunk_types_seen))}")
    if errors:
        for err in errors:
            print(f"  - ERROR: {err}")
        return False
    return verified_files == 102

def test_oracle_sensitivity_adversarial():
    """
    Stress test 3: Sensitivity & False-Negative resistance test.
    Inject intentional 1-bit mutations into an image and ensure the verification mechanism
    positively detects and rejects corrupted files, proving the audit is not a tautology.
    """
    print("\n[Stress 3] Adversarial Oracle Sensitivity (Corrupt Byte Detection)...")
    # Sample 1 file
    sample_file = os.path.join(DESIGN_REF_DIR, "Actions", os.listdir(os.path.join(DESIGN_REF_DIR, "Actions"))[0])
    with open(sample_file, "rb") as f:
        valid_bytes = bytearray(f.read())

    # Case A: Corrupt IHDR CRC
    corrupt_ihdr_crc = bytearray(valid_bytes)
    corrupt_ihdr_crc[30] ^= 0xFF
    ihdr_rejected = False
    try:
        # Check struct CRC
        computed_crc = zlib.crc32(corrupt_ihdr_crc[12:29]) & 0xFFFFFFFF
        expected_crc = struct.unpack(">I", corrupt_ihdr_crc[29:33])[0]
        if computed_crc != expected_crc:
            ihdr_rejected = True
    except Exception:
        ihdr_rejected = True

    # Case B: Corrupt IDAT compressed stream
    corrupt_idat = bytearray(valid_bytes)
    corrupt_idat[len(corrupt_idat) // 2] ^= 0xFF
    idat_rejected = False
    try:
        with Image.open(BytesIO(corrupt_idat)) as img:
            img.load()
    except Exception:
        idat_rejected = True

    # Case C: Truncated file
    truncated_bytes = valid_bytes[:500]
    trunc_rejected = False
    try:
        with Image.open(BytesIO(truncated_bytes)) as img:
            img.load()
    except Exception:
        trunc_rejected = True

    print(f"  -> IHDR CRC corruption detected: {ihdr_rejected}")
    print(f"  -> IDAT bitstream corruption detected: {idat_rejected}")
    print(f"  -> File truncation detected: {trunc_rejected}")

    return ihdr_rejected and idat_rejected and trunc_rejected

def main():
    print("=" * 80)
    print("ADVERSARIAL DEEP STRESS & SENSITIVITY TEST SUITE")
    print("=" * 80)
    
    s1 = test_full_raster_decompression()
    s2 = test_png_chunk_sequence_and_iend()
    s3 = test_oracle_sensitivity_adversarial()

    print("\n" + "=" * 80)
    print(f"Stress Test 1 (Full Raster Decompression): {'PASS' if s1 else 'FAIL'}")
    print(f"Stress Test 2 (Exhaustive Chunk CRC & IEND): {'PASS' if s2 else 'FAIL'}")
    print(f"Stress Test 3 (Adversarial Corruption Detection): {'PASS' if s3 else 'FAIL'}")
    print("=" * 80)

    if s1 and s2 and s3:
        print("ALL ADVERSARIAL STRESS TESTS PASSED SUCCESSFULLY.")
        return 0
    else:
        print("ADVERSARIAL STRESS TESTS DETECTED FAILURES.")
        return 1

if __name__ == "__main__":
    sys.exit(main())

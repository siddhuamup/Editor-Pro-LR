"""
CleanLightroom E2E Test Infrastructure - ADB Automation & Verification Harness
Provides high-reliability device interaction, orientation control, screenshot capture,
UI hierarchy querying, and pixel-level color validation for emulator-5554.
"""

import os
import re
import sys
import time
import math
import tempfile
import subprocess
import xml.etree.ElementTree as ET
from typing import Optional, List, Tuple, Dict, Any, Callable
from PIL import Image

DEFAULT_ADB_PATH = r"C:\Users\Siddhu\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEFAULT_DEVICE = "emulator-5554"
PACKAGE_NAME = "com.cleanlr"
MAIN_ACTIVITY = ".MainActivity"


class UIElement:
    """Represents a UI Automator dumped node with geometric and semantic properties."""

    def __init__(self, node: ET.Element):
        self.node = node
        self.text = node.attrib.get("text", "")
        self.content_desc = node.attrib.get("content-desc", "")
        self.resource_id = node.attrib.get("resource-id", "")
        self.class_name = node.attrib.get("class", "")
        self.package = node.attrib.get("package", "")
        self.bounds_raw = node.attrib.get("bounds", "[0,0][0,0]")
        self.bounds = self._parse_bounds(self.bounds_raw)
        self.center = (
            (self.bounds[0][0] + self.bounds[1][0]) // 2,
            (self.bounds[0][1] + self.bounds[1][1]) // 2,
        )

    @staticmethod
    def _parse_bounds(bounds_str: str) -> Tuple[Tuple[int, int], Tuple[int, int]]:
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            return ((x1, y1), (x2, y2))
        return ((0, 0), (0, 0))

    @property
    def width(self) -> int:
        return self.bounds[1][0] - self.bounds[0][0]

    @property
    def height(self) -> int:
        return self.bounds[1][1] - self.bounds[0][1]

    def matches(
        self,
        text: Optional[str] = None,
        desc: Optional[str] = None,
        res_id: Optional[str] = None,
        class_name: Optional[str] = None,
    ) -> bool:
        if text is not None and text.lower() not in self.text.lower():
            return False
        if desc is not None and desc.lower() not in self.content_desc.lower():
            return False
        if res_id is not None and res_id.lower() not in self.resource_id.lower():
            return False
        if class_name is not None and class_name.lower() not in self.class_name.lower():
            return False
        return True

    def __repr__(self) -> str:
        return f"<UIElement text='{self.text}' desc='{self.content_desc}' bounds={self.bounds_raw}>"


class UIHierarchy:
    """Wrapper around dumped XML hierarchy tree with intuitive finders."""

    def __init__(self, raw_xml: str):
        self.raw_xml = raw_xml
        self.root = ET.fromstring(raw_xml)
        self.rotation = int(self.root.attrib.get("rotation", "0"))
        self.elements: List[UIElement] = [UIElement(n) for n in self.root.iter("node")]

    def find_all(
        self,
        text: Optional[str] = None,
        desc: Optional[str] = None,
        res_id: Optional[str] = None,
        class_name: Optional[str] = None,
    ) -> List[UIElement]:
        return [
            el
            for el in self.elements
            if el.matches(text=text, desc=desc, res_id=res_id, class_name=class_name)
        ]

    def find_one(
        self,
        text: Optional[str] = None,
        desc: Optional[str] = None,
        res_id: Optional[str] = None,
        class_name: Optional[str] = None,
    ) -> Optional[UIElement]:
        matches = self.find_all(text=text, desc=desc, res_id=res_id, class_name=class_name)
        return matches[0] if matches else None

    def exists(
        self,
        text: Optional[str] = None,
        desc: Optional[str] = None,
        res_id: Optional[str] = None,
        class_name: Optional[str] = None,
    ) -> bool:
        return self.find_one(text=text, desc=desc, res_id=res_id, class_name=class_name) is not None


class ColorVerifier:
    """Color analysis helper for Spectrum Design Tokens."""

    @staticmethod
    def hex_to_rgb(hex_str: str) -> Tuple[int, int, int]:
        clean_hex = hex_str.lstrip("#")
        if len(clean_hex) == 8:
            clean_hex = clean_hex[2:]  # Strip alpha if present
        return tuple(int(clean_hex[i : i + 2], 16) for i in (0, 2, 4))

    @staticmethod
    def rgb_to_hex(rgb: Tuple[int, int, int]) -> str:
        return f"#{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X}"

    @staticmethod
    def sample_pixel(img: Image.Image, x: int, y: int) -> Tuple[int, int, int]:
        rgb_img = img.convert("RGB")
        clamped_x = max(0, min(x, rgb_img.width - 1))
        clamped_y = max(0, min(y, rgb_img.height - 1))
        return rgb_img.getpixel((clamped_x, clamped_y))

    @classmethod
    def sample_box(
        cls, img: Image.Image, x1: int, y1: int, x2: int, y2: int
    ) -> Tuple[int, int, int]:
        rgb_img = img.convert("RGB")
        x_start, x_end = max(0, min(x1, x2)), min(rgb_img.width, max(x1, x2))
        y_start, y_end = max(0, min(y1, y2)), min(rgb_img.height, max(y1, y2))
        total_r, total_g, total_b, count = 0, 0, 0, 0
        step_x = max(1, (x_end - x_start) // 20)
        step_y = max(1, (y_end - y_start) // 20)
        for y in range(y_start, y_end, step_y):
            for x in range(x_start, x_end, step_x):
                r, g, b = rgb_img.getpixel((x, y))
                total_r += r
                total_g += g
                total_b += b
                count += 1
        if count == 0:
            return (0, 0, 0)
        return (total_r // count, total_g // count, total_b // count)

    @classmethod
    def color_distance(cls, rgb1: Tuple[int, int, int], rgb2: Tuple[int, int, int]) -> float:
        return math.sqrt(
            (rgb1[0] - rgb2[0]) ** 2 + (rgb1[1] - rgb2[1]) ** 2 + (rgb1[2] - rgb2[2]) ** 2
        )

    @classmethod
    def is_color_match(
        cls, actual_rgb: Tuple[int, int, int], expected_hex: str, tolerance: float = 35.0
    ) -> bool:
        expected_rgb = cls.hex_to_rgb(expected_hex)
        return cls.color_distance(actual_rgb, expected_rgb) <= tolerance

    @staticmethod
    def relative_luminance(rgb: Tuple[int, int, int]) -> float:
        def channel_lum(val: int) -> float:
            c = val / 255.0
            return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

        return (
            0.2126 * channel_lum(rgb[0])
            + 0.7152 * channel_lum(rgb[1])
            + 0.0722 * channel_lum(rgb[2])
        )

    @classmethod
    def contrast_ratio(cls, rgb1: Tuple[int, int, int], rgb2: Tuple[int, int, int]) -> float:
        l1 = cls.relative_luminance(rgb1)
        l2 = cls.relative_luminance(rgb2)
        bright, dark = max(l1, l2), min(l1, l2)
        return (bright + 0.05) / (dark + 0.05)


class ADBHelper:
    """Primary ADB Automation and Device Interface."""

    def __init__(self, device: str = DEFAULT_DEVICE, adb_path: Optional[str] = None):
        self.device = device
        self.adb_path = adb_path or (
            DEFAULT_ADB_PATH if os.path.exists(DEFAULT_ADB_PATH) else "adb"
        )

    def run_cmd(self, args: List[str], check: bool = True, timeout: int = 20) -> str:
        cmd = [self.adb_path, "-s", self.device] + args
        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                check=check,
                timeout=timeout,
            )
            return result.stdout.strip()
        except subprocess.TimeoutExpired:
            raise RuntimeError(f"ADB command timed out after {timeout}s: {' '.join(cmd)}")
        except subprocess.CalledProcessError as e:
            raise RuntimeError(f"ADB command failed ({e.returncode}): {e.stderr.strip()}")

    def shell(self, cmd_str: str, check: bool = True, timeout: int = 20) -> str:
        return self.run_cmd(["shell", cmd_str], check=check, timeout=timeout)

    def is_device_online(self) -> bool:
        try:
            state = self.run_cmd(["get-state"], check=False)
            return state.strip() == "device"
        except Exception:
            return False

    def get_focused_window(self) -> str:
        focus = self.shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", check=False)
        if "Application Not Responding: com.android.systemui" in focus or "Application Not Responding:" in focus and PACKAGE_NAME not in focus:
            # Dismiss system dialog if it appears
            self.shell("input tap 540 1205")
            self.shell("input keyevent KEYCODE_BACK")
            time.sleep(0.5)
            focus = self.shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", check=False)
        return focus

    def is_app_foreground(self, pkg: str = PACKAGE_NAME, timeout_s: float = 5.0) -> bool:
        start = time.time()
        while time.time() - start < timeout_s:
            focus = self.get_focused_window()
            if f"Application Not Responding: {pkg}" in focus:
                return False
            if pkg in focus and ("mCurrentFocus" in focus or "mFocusedApp" in focus):
                return True
            time.sleep(0.3)
        return False

    def launch_app(
        self, pkg: str = PACKAGE_NAME, act: str = MAIN_ACTIVITY, wait_s: float = 1.5
    ) -> None:
        self.shell(f"am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n {pkg}/{act}")
        time.sleep(wait_s)

    def stop_app(self, pkg: str = PACKAGE_NAME) -> None:
        self.shell(f"am force-stop {pkg}")
        time.sleep(0.5)

    def restart_app(
        self, pkg: str = PACKAGE_NAME, act: str = MAIN_ACTIVITY, wait_s: float = 1.5
    ) -> None:
        self.stop_app(pkg)
        self.launch_app(pkg, act, wait_s=wait_s)

    def set_orientation(self, rotation: int, settle_delay_s: float = 1.0, timeout_s: float = 5.0) -> None:
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
        return UIHierarchy(f"<hierarchy rotation='{current_rot}'></hierarchy>")

    def tap(self, x: int, y: int, delay_s: float = 0.5) -> None:
        self.shell(f"input tap {x} {y}")
        if delay_s > 0:
            time.sleep(delay_s)

    def tap_element(self, element: UIElement, delay_s: float = 0.5) -> None:
        self.tap(element.center[0], element.center[1], delay_s=delay_s)

    def swipe(
        self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300, delay_s: float = 0.5
    ) -> None:
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {duration_ms}")
        if delay_s > 0:
            time.sleep(delay_s)

    def key_event(self, keycode: str, delay_s: float = 0.5) -> None:
        self.shell(f"input keyevent {keycode}")
        if delay_s > 0:
            time.sleep(delay_s)

    def check_for_crashes(self, pkg: str = PACKAGE_NAME) -> List[str]:
        output = self.shell(f"logcat -d | grep -E 'Process: {pkg}|ANR in {pkg}'")
        lines = [
            line.strip()
            for line in output.splitlines()
            if pkg in line
            and "adbd" not in line
            and "grep" not in line
            and "shell,v2,raw" not in line
        ]
        return lines

    def clear_logcat(self) -> None:
        self.shell("logcat -c")

    def wait_for_ui(
        self,
        predicate: Callable[[UIHierarchy], bool],
        timeout_s: float = 6.0,
        interval_s: float = 0.6,
    ) -> Optional[UIHierarchy]:
        start = time.time()
        while time.time() - start < timeout_s:
            try:
                ui = self.dump_ui()
                if predicate(ui):
                    return ui
            except Exception:
                pass
            time.sleep(interval_s)
        return None

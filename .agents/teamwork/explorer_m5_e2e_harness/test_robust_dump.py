import sys, time
sys.path.append(r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\tests\e2e')
from adb_helper import ADBHelper, UIHierarchy

adb = ADBHelper()

def robust_dump_ui(adb, remote_path="/sdcard/e2e_uidump.xml", retries=3):
    last_err = None
    for attempt in range(retries):
        try:
            adb.shell(f"rm -f {remote_path}", check=False)
            adb.shell(f"uiautomator dump {remote_path}", timeout=15)
            raw_xml = adb.shell(f"cat {remote_path}", check=False)
            if raw_xml.strip().startswith("<?xml") and "</hierarchy>" in raw_xml:
                return UIHierarchy(raw_xml)
        except Exception as e:
            last_err = e
            adb.shell("pkill -f uiautomator", check=False)
        time.sleep(0.3 + 0.2 * (attempt + 1))
    
    current_rot = adb.get_orientation()
    return UIHierarchy(f"<hierarchy rotation='{current_rot}'></hierarchy>")

# Rapid test
success_count = 0
for i in range(5):
    t0 = time.time()
    ui = robust_dump_ui(adb)
    dt = time.time() - t0
    has_nodes = len(ui.find_all()) > 0
    print(f'Dump {i}: {dt:.3f}s, nodes={len(ui.find_all())}, rotation={ui.rotation}')
    if has_nodes:
        success_count += 1

print(f'Total successful: {success_count} / 5')

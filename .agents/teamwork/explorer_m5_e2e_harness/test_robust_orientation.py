import sys, time
sys.path.append(r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\tests\e2e')
from adb_helper import ADBHelper

adb = ADBHelper()

def robust_set_orientation(adb, rotation, settle_delay_s=0.5, timeout_s=6.0):
    ROTATION_NAMES = {0: "ROTATION_0", 1: "ROTATION_90"}
    target_name = ROTATION_NAMES[rotation]
    adb.shell(f"settings put system accelerometer_rotation 0; settings put system user_rotation {rotation}")
    start = time.time()
    settled = False
    while time.time() - start < timeout_s:
        out = adb.shell("dumpsys window", check=False)
        if f"mCurrentRotation={target_name}" in out and f"mRotation={rotation}" in out:
            settled = True
            break
        time.sleep(0.15)
    time.sleep(settle_delay_s)
    return settled, time.time() - start

print('Testing 0 -> 1:')
s1, dt1 = robust_set_orientation(adb, 1)
ui1 = adb.dump_ui()
print(f'Rotated to 1 in {dt1:.3f}s: settled={s1}, ui.rotation={ui1.rotation}')

print('Testing 1 -> 0:')
s0, dt0 = robust_set_orientation(adb, 0)
ui0 = adb.dump_ui()
print(f'Rotated to 0 in {dt0:.3f}s: settled={s0}, ui.rotation={ui0.rotation}')

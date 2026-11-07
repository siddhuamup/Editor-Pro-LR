import sys, time
sys.path.append(r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\tests\e2e')
from adb_helper import ADBHelper

adb = ADBHelper()
adb.shell('settings put system accelerometer_rotation 0; settings put system user_rotation 0')
time.sleep(1.0)

print('Triggering rotation to 1...')
t0 = time.time()
adb.shell('settings put system user_rotation 1')

for i in range(20):
    elapsed = time.time() - t0
    rot_lines = []
    out = adb.shell('dumpsys window')
    for line in out.splitlines():
        if 'mCurrentRotation=' in line or ('mRotation=' in line and 'mDeferred' in line):
            rot_lines.append(line.strip())
    print(f't={elapsed:.3f}s: {rot_lines}')
    time.sleep(0.1)

adb.shell('settings put system user_rotation 0')

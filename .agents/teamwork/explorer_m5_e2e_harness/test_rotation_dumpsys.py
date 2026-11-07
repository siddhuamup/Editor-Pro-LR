import sys, time
sys.path.append(r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\tests\e2e')
from adb_helper import ADBHelper

adb = ADBHelper()
print('Setting rotation 1...')
adb.shell('settings put system accelerometer_rotation 0; settings put system user_rotation 1')
time.sleep(0.5)

out = adb.shell('dumpsys window')
for line in out.splitlines():
    if any(k in line for k in ['mCurrentRotation', 'mRotation=', 'mDisplayRotation']):
        print('  ', line.strip())

print('Setting rotation 0...')
adb.shell('settings put system accelerometer_rotation 0; settings put system user_rotation 0')
time.sleep(0.5)

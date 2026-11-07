import sys
sys.path.append(r'c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\tests\e2e')
from adb_helper import ADBHelper

adb = ADBHelper()
out = adb.shell("dumpsys window")
for line in out.splitlines():
    if any(k in line for k in ["mCurrentRotation", "mRotation", "mDisplayRotation", "rotation="]):
        print(line.strip())

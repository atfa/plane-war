#!/data/data/com.termux/files/usr/bin/bash
# Install + launch on this phone. Root (KernelSU) -> fully unattended, no tapping.
set -euo pipefail
APK=$(readlink -f "${1:-plane.apk}")
PKG=dev.termux.planewar
su -c "pm install -r -g '$APK'"
su -c "am start -n $PKG/.MainActivity"
echo "installed and launched $PKG"

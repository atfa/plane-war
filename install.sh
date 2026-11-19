#!/data/data/com.termux/files/usr/bin/bash
# Install + launch on this phone. Root (KernelSU) -> fully unattended, no tapping.
set -euo pipefail
APK=$(readlink -f "${1:-plane.apk}")
PKG=dev.xduo.plane
if [ ! -f "$APK" ]; then
  echo "missing $APK - 先跑 ./build.sh 生成 plane.apk（APK 不随 git 发布，也可以从 Releases 下载）"
  exit 1
fi
su -c "pm install -r -g '$APK'"
su -c "am start -n $PKG/.MainActivity"
echo "installed and launched $PKG"

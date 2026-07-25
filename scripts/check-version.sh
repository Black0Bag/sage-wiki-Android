#!/bin/bash
# check-version.sh — CI 版本号宪法校验（sage-wiki-Android）
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=$(grep 'versionName' app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
VERSION_CODE=$(grep 'versionCode' app/build.gradle.kts | head -1 | sed -E 's/.*=[[:space:]]*([0-9]+).*/\1/')
echo "📋 当前版本号: $VERSION (versionCode: $VERSION_CODE)"
if ! echo "$VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "❌ 版本号格式错误：$VERSION（应为 MAJOR.MINOR.PATCH）"; exit 1
fi
MAJOR=$(echo "$VERSION" | cut -d. -f1); MINOR=$(echo "$VERSION" | cut -d. -f2); PATCH=$(echo "$VERSION" | cut -d. -f3)
EXPECTED_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))
if [ "$VERSION_CODE" -ne "$EXPECTED_CODE" ]; then
    echo "❌ versionCode 不匹配：当前 $VERSION_CODE，期望 $EXPECTED_CODE"; exit 1
fi
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -z "$LAST_TAG" ]; then echo "✅ 首次构建，跳过比较"; exit 0; fi
LAST_VERSION="${LAST_TAG#v}"
if [ "$VERSION" = "$LAST_VERSION" ]; then
    echo "⚠️  版本号未变化（$VERSION），代码有改动。请确认是否需要升级版本号。"; exit 0
fi
LAST_MAJOR=$(echo "$LAST_VERSION" | cut -d. -f1); LAST_MINOR=$(echo "$LAST_VERSION" | cut -d. -f2); LAST_PATCH=$(echo "$LAST_VERSION" | cut -d. -f3)
NEW_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH)); OLD_CODE=$((LAST_MAJOR * 10000 + LAST_MINOR * 100 + LAST_PATCH))
if [ "$NEW_CODE" -le "$OLD_CODE" ]; then
    echo "❌ 版本号不能回退：$LAST_VERSION → $VERSION"; exit 1
fi
echo "✅ 版本号校验通过：$LAST_VERSION → $VERSION"

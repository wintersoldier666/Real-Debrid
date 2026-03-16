#!/bin/bash
set -e

# ---- Paths ----
ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
BUILD_TOOLS="/usr/lib/android-sdk/build-tools/29.0.3"
AAPT="$BUILD_TOOLS/aapt"
DX="/usr/lib/android-sdk/build-tools/debian/dx"
APKSIGNER="$BUILD_TOOLS/apksigner"

PROJECT="/home/user/Real-Debrid"
SRC="$PROJECT/app/src/main"
BUILD="$PROJECT/build"
GEN="$BUILD/gen"
OBJ="$BUILD/obj"
DEX="$BUILD/dex"
APK_UNSIGNED="$BUILD/app-unsigned.apk"
APK_ALIGNED="$BUILD/app-aligned.apk"
APK_SIGNED="$BUILD/facebook-lite.apk"

KOTLIN_STDLIB=$(find /usr/share/kotlin -name "kotlin-stdlib*.jar" 2>/dev/null | head -1)
if [ -z "$KOTLIN_STDLIB" ]; then
  KOTLIN_STDLIB=$(find /usr -name "kotlin-stdlib.jar" 2>/dev/null | head -1)
fi
echo "Kotlin stdlib: $KOTLIN_STDLIB"

# ---- Clean ----
rm -rf "$BUILD"
mkdir -p "$GEN" "$OBJ" "$DEX"

echo "=== Step 1: Package resources with aapt ==="
"$AAPT" package -f -m \
  -S "$SRC/res" \
  -J "$GEN" \
  -M "$SRC/AndroidManifest.xml" \
  -I "$ANDROID_JAR"

echo "=== Step 2: Compile Kotlin sources ==="
KT_FILES=$(find "$SRC/java/com/mktplace" -name "*.kt")
R_JAVA=$(find "$GEN" -name "R.java")
kotlinc $KT_FILES $R_JAVA \
  -classpath "$ANDROID_JAR:$KOTLIN_STDLIB" \
  -d "$OBJ" \
  -jvm-target 1.6

echo "=== Step 3: Convert to Dalvik (dx) ==="
"$DX" --dex --output="$DEX/classes.dex" "$OBJ" "$KOTLIN_STDLIB"

echo "=== Step 4: Build unsigned APK ==="
"$AAPT" package -f \
  -S "$SRC/res" \
  -M "$SRC/AndroidManifest.xml" \
  -I "$ANDROID_JAR" \
  -F "$APK_UNSIGNED"

# Add dex file
cd "$DEX"
"$AAPT" add "$APK_UNSIGNED" classes.dex
cd "$PROJECT"

echo "=== Step 5: Zipalign ==="
"$BUILD_TOOLS/zipalign" -f 4 "$APK_UNSIGNED" "$APK_ALIGNED"

echo "=== Step 6: Generate debug keystore ==="
KEYSTORE="$BUILD/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias androidkey \
    -keyalg RSA -keysize 2048 \
    -validity 10000 \
    -storepass android \
    -keypass android \
    -dname "CN=Facebook Lite, OU=Dev, O=Dev, L=City, S=State, C=US" 2>/dev/null
fi

echo "=== Step 7: Sign APK ==="
java -jar "$BUILD_TOOLS/apksigner.jar" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias androidkey \
  --out "$APK_SIGNED" \
  "$APK_ALIGNED"

echo ""
echo "✓ APK built: $APK_SIGNED"
ls -lh "$APK_SIGNED"

#!/bin/bash
set -e

# ---- Paths ----
ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
BUILD_TOOLS="/usr/lib/android-sdk/build-tools/29.0.3"
AAPT="$BUILD_TOOLS/aapt"
DX="/usr/lib/android-sdk/build-tools/debian/dx"

PROJECT="/home/user/Real-Debrid"
SRC="$PROJECT/app/src/main"
BUILD="$PROJECT/build"
GEN="$BUILD/gen"
OBJ="$BUILD/obj"
DEX="$BUILD/dex"
APK_UNSIGNED="$BUILD/app-unsigned.apk"
APK_ALIGNED="$BUILD/app-aligned.apk"
APK_SIGNED="$BUILD/marketplace-lite.apk"

# ---- Clean ----
rm -rf "$BUILD"
mkdir -p "$GEN" "$OBJ" "$DEX"

echo "=== Step 1: Package resources with aapt ==="
"$AAPT" package -f -m \
  -S "$SRC/res" \
  -J "$GEN" \
  -M "$SRC/AndroidManifest.xml" \
  -I "$ANDROID_JAR"

echo "=== Step 2: Compile Java sources ==="
JAVA_FILES=$(find "$SRC/java" -name "*.java")
R_JAVA=$(find "$GEN" -name "R.java")
javac -source 1.8 -target 1.8 \
  -classpath "$ANDROID_JAR" \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OBJ" \
  $JAVA_FILES $R_JAVA

echo "=== Step 3: Convert to Dalvik (dx) ==="
"$DX" --dex --output="$DEX/classes.dex" "$OBJ"

echo "=== Step 4: Build unsigned APK ==="
"$AAPT" package -f \
  -S "$SRC/res" \
  -M "$SRC/AndroidManifest.xml" \
  -I "$ANDROID_JAR" \
  -F "$APK_UNSIGNED"

cd "$DEX"
"$AAPT" add "$APK_UNSIGNED" classes.dex
cd "$PROJECT"

echo "=== Step 5: Zipalign ==="
"$BUILD_TOOLS/zipalign" -f 4 "$APK_UNSIGNED" "$APK_ALIGNED"

echo "=== Step 6: Generate keystore ==="
KEYSTORE="$PROJECT/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias androidkey \
    -keyalg RSA -keysize 2048 \
    -validity 10000 \
    -storepass android \
    -keypass android \
    -dname "CN=Dev, OU=Dev, O=Dev, L=City, S=State, C=US" 2>/dev/null
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

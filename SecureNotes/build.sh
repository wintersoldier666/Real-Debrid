#!/bin/bash
set -e

# ============================================================
# Ghost Notes - Manual Android APK Build Script
# Build tools: aapt, javac, dx, zipalign, apksigner
# Target: Android API 23 (6.0 Marshmallow) and above
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

ANDROID_SDK="/usr/lib/android-sdk"
BUILD_TOOLS="$ANDROID_SDK/build-tools/debian"
ANDROID_JAR="$ANDROID_SDK/platforms/android-23/android.jar"
AAPT="$BUILD_TOOLS/aapt"
DX="$BUILD_TOOLS/dx"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="apksigner"

# Build directories
BIN="$PROJECT_DIR/bin"
CLASSES="$BIN/classes"
GEN="$BIN/gen"
APK_UNSIGNED="$BIN/app-unsigned.apk"
APK_ALIGNED="$BIN/app-aligned.apk"
APK_SIGNED="$BIN/GhostNotes.apk"
KEYSTORE="$BIN/debug.keystore"

# Source directories
MANIFEST="$PROJECT_DIR/AndroidManifest.xml"
RES="$PROJECT_DIR/res"
SRC="$PROJECT_DIR/src"

echo "=============================================="
echo "  Building Ghost Notes APK"
echo "  Ultimate Encrypted Notes App"
echo "=============================================="

# Create directories
mkdir -p "$CLASSES" "$GEN"

# ============================================================
# Step 1: Generate R.java from resources
# ============================================================
echo "[1/6] Compiling resources..."
"$AAPT" package -f -m \
    -J "$GEN" \
    -M "$MANIFEST" \
    -S "$RES" \
    -I "$ANDROID_JAR" \
    -F "$BIN/resources.ap_" \
    --error-on-failed-insert

echo "      Resources compiled"

# ============================================================
# Step 2: Compile Java sources
# ============================================================
echo "[2/6] Compiling Java sources..."

# Find all Java source files
JAVA_FILES=$(find "$SRC" -name "*.java" | tr '\n' ' ')
JAVA_FILES="$JAVA_FILES $GEN/com/ghostnotes/R.java"

javac \
    -source 8 \
    -target 8 \
    -bootclasspath "$ANDROID_JAR" \
    -classpath "$ANDROID_JAR" \
    -d "$CLASSES" \
    -encoding UTF-8 \
    $JAVA_FILES

echo "      Java compiled"

# ============================================================
# Step 3: Convert to DEX (Dalvik Executable)
# ============================================================
echo "[3/6] Converting to DEX..."
"$DX" --dex \
    --output="$BIN/classes.dex" \
    "$CLASSES"

echo "      DEX created"

# ============================================================
# Step 4: Package APK
# ============================================================
echo "[4/6] Packaging APK..."
# Add classes.dex to the resources package
cp "$BIN/resources.ap_" "$APK_UNSIGNED"
cd "$BIN" && zip -j "$APK_UNSIGNED" "classes.dex"
cd "$PROJECT_DIR"

echo "      APK packaged"

# ============================================================
# Step 5: Zipalign
# ============================================================
echo "[5/6] Aligning APK..."
rm -f "$APK_ALIGNED"
"$ZIPALIGN" -v 4 "$APK_UNSIGNED" "$APK_ALIGNED" > /dev/null

echo "      APK aligned"

# ============================================================
# Step 6: Sign APK
# ============================================================
echo "[6/6] Signing APK..."

# Generate a debug keystore if it doesn't exist
if [ ! -f "$KEYSTORE" ]; then
    echo "      Generating debug keystore..."
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -storepass ghostnotes \
        -alias ghostnotes \
        -keypass ghostnotes \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Ghost Notes, OU=Security, O=Private, L=Unknown, ST=Unknown, C=US" \
        2>/dev/null
fi

"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:ghostnotes \
    --key-pass pass:ghostnotes \
    --ks-key-alias ghostnotes \
    --out "$APK_SIGNED" \
    "$APK_ALIGNED"

# Verify
"$APKSIGNER" verify --verbose "$APK_SIGNED" 2>&1 | grep -E "Verified|v[12] scheme"

echo ""
echo "=============================================="
echo "  SUCCESS!"
echo "  APK: $APK_SIGNED"
echo "  Size: $(du -sh "$APK_SIGNED" | cut -f1)"
echo "=============================================="
echo ""
echo "Security Features:"
echo "  ✓ AES-256-GCM encryption (all note content)"
echo "  ✓ PBKDF2-HMAC-SHA256 (210,000 iterations)"
echo "  ✓ Unique IV per note (authenticated encryption)"
echo "  ✓ FLAG_SECURE (no screenshots/screen recording)"
echo "  ✓ Auto-lock on background"
echo "  ✓ No internet permission"
echo "  ✓ No backup (android:allowBackup=false)"
echo "  ✓ Private internal storage"
echo "  ✓ Network security config blocks all connections"
echo "=============================================="

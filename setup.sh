#!/bin/bash
# Setup script for Desmond PTT

set -e

echo "🔷 Desmond PTT Setup"
echo ""

# Check for Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    # Try common locations
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "/usr/local/share/android-sdk" ]; then
        export ANDROID_HOME="/usr/local/share/android-sdk"
    else
        echo "⚠️  Android SDK not found!"
        echo "   Install Android Studio or set ANDROID_HOME"
        exit 1
    fi
fi

ANDROID_HOME=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
echo "📱 Android SDK: $ANDROID_HOME"

# Download gradle wrapper if not present
if [ ! -f "gradlew" ]; then
    echo "📥 Downloading Gradle wrapper..."
    
    # Use gradle from Android Studio if available
    STUDIO_GRADLE="$HOME/Library/Application Support/Google/AndroidStudio*/gradle/gradle-*/bin/gradle"
    STUDIO_GRADLE=$(ls -d $STUDIO_GRADLE 2>/dev/null | tail -1)
    
    if [ -n "$STUDIO_GRADLE" ] && [ -f "$STUDIO_GRADLE" ]; then
        echo "   Using gradle from Android Studio"
        "$STUDIO_GRADLE" wrapper --gradle-version 8.5
    else
        # Download wrapper jar directly
        echo "   Downloading gradle wrapper jar..."
        mkdir -p gradle/wrapper
        curl -sL "https://github.com/AkihiroSuda/gradle-wrapper/raw/master/gradle-wrapper.jar" -o gradle/wrapper/gradle-wrapper.jar
        
        # Create gradlew script
        cat > gradlew << 'EOF'
#!/bin/sh
exec java -jar "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" "$@"
EOF
        chmod +x gradlew
        
        # Create gradlew.bat for Windows
        cat > gradlew.bat << 'EOF'
@echo off
java -jar "%~dp0gradle\wrapper\gradle-wrapper.jar" %*
EOF
    fi
fi

echo ""
echo "🔨 Building APK..."
./gradlew assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "✅ Build successful!"
    echo "📦 APK: $APK_PATH"
    echo ""
    echo "Install with:"
    echo "  adb install $APK_PATH"
    echo ""
    echo "Or transfer to your phone and install manually."
else
    echo "❌ Build failed. Check logs above."
    exit 1
fi

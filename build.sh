#!/bin/bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_NAME="Ongoma"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  $PROJECT_NAME - Build Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}➜ $1${NC}"
}

# gradlew exists?
if [ ! -f "$PROJECT_DIR/gradlew" ]; then
    print_error "gradlew not found! Please ensure you're in the project root directory."
    exit 1
fi

chmod +x "$PROJECT_DIR/gradlew"

# Parse command line arguments
BUILD_TYPE="${1:-debug}"

case "$BUILD_TYPE" in
    debug)
        print_info "Building DEBUG APK..."
        "$PROJECT_DIR/gradlew" clean assembleDebug
        APK_PATH="$PROJECT_DIR/build/outputs/apk/debug/ongoma-v2-debug.apk"
        ;;
    release)
        print_info "Building RELEASE APK..."
        "$PROJECT_DIR/gradlew" clean assembleRelease
        APK_PATH="$PROJECT_DIR/build/outputs/apk/release/ongoma-v2-release.apk"
        ;;
    both)
        print_info "Building both DEBUG and RELEASE APKs..."
        "$PROJECT_DIR/gradlew" clean assembleDebug assembleRelease
        DEBUG_APK="$PROJECT_DIR/build/outputs/apk/debug/ongoma-v2-debug.apk"
        RELEASE_APK="$PROJECT_DIR/build/outputs/apk/release/ongoma-v2-release.apk"
        ;;
    clean)
        print_info "Cleaning build artifacts..."
        "$PROJECT_DIR/gradlew" clean
        print_success "Clean complete!"
        exit 0
        ;;
    *)
        print_error "Invalid build type: $BUILD_TYPE"
        echo "Usage: $0 [debug|release|both|clean]"
        echo "  debug   - Build debug APK (default)"
        echo "  release - Build release APK"
        echo "  both    - Build both debug and release APKs"
        echo "  clean   - Clean build artifacts"
        exit 1
        ;;
esac

echo ""
print_success "BUILD SUCCESSFUL!"

echo ""

if [ "$BUILD_TYPE" = "both" ]; then
    if [ -f "$DEBUG_APK" ]; then
        print_info "Debug APK: $DEBUG_APK"
        print_info "Size: $(du -h "$DEBUG_APK" | cut -f1)"
    fi
    if [ -f "$RELEASE_APK" ]; then
        print_info "Release APK: $RELEASE_APK"
        print_info "Size: $(du -h "$RELEASE_APK" | cut -f1)"
    fi
else
    if [ -f "$APK_PATH" ]; then
        print_info "APK Location: $APK_PATH"
        print_info "Size: $(du -h "$APK_PATH" | cut -f1)"
        echo ""
        cp $APK_PATH ~/Downloads/ongoma.apk
    else
        print_error "APK not found at expected location!"
        exit 1
    fi
fi

#@/* bring to root *./
cp $APK_PATH ongoma.apk


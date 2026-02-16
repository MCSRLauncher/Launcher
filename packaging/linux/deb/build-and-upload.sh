#!/bin/bash
set -e

VERSION="0.7.2~beta"
RELEASE_TAG="0.7.2-beta"
GPG_KEY="3408D2D89725BDD9"
PPA="ppa:flammablebunny/mcsrlauncher"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
WORK_DIR="/tmp/mcsrlauncher-${VERSION}"
PKG="mcsrlauncher"

export GPG_TTY=$(tty)

echo "==> Cleaning up old build..."
rm -rf "$WORK_DIR"
rm -f "/tmp/${PKG}_${VERSION}"*

echo "==> Setting up source package directory..."
mkdir -p "$WORK_DIR/debian/source"

# Copy debian files
cp "$SCRIPT_DIR/control" "$WORK_DIR/debian/"
cp "$SCRIPT_DIR/changelog" "$WORK_DIR/debian/"
cp "$SCRIPT_DIR/copyright" "$WORK_DIR/debian/"
echo "13" > "$WORK_DIR/debian/compat"
echo "3.0 (quilt)" > "$WORK_DIR/debian/source/format"

# Create debian/rules
cat > "$WORK_DIR/debian/rules" << 'RULES'
#!/usr/bin/make -f
%:
	dh $@

override_dh_auto_install:
	mkdir -p debian/mcsrlauncher/usr/share/java/mcsrlauncher
	mkdir -p debian/mcsrlauncher/usr/bin
	mkdir -p debian/mcsrlauncher/usr/share/applications
	mkdir -p debian/mcsrlauncher/usr/share/icons/hicolor/128x128/apps
	cp MCSRLauncher.jar debian/mcsrlauncher/usr/share/java/mcsrlauncher/
	cp mcsrlauncher.desktop debian/mcsrlauncher/usr/share/applications/
	cp mcsrlauncher.png debian/mcsrlauncher/usr/share/icons/hicolor/128x128/apps/
	cp mcsrlauncher.sh debian/mcsrlauncher/usr/bin/mcsrlauncher
	chmod +x debian/mcsrlauncher/usr/bin/mcsrlauncher
RULES
chmod +x "$WORK_DIR/debian/rules"

echo "==> Downloading jar..."
curl -sL "https://github.com/MCSRLauncher/Launcher/releases/download/${RELEASE_TAG}/MCSRLauncher.jar" \
  -o "$WORK_DIR/MCSRLauncher.jar"

echo "==> Copying assets..."
cp "$PROJECT_DIR/packaging/linux/mcsrlauncher.desktop" "$WORK_DIR/"
cp "$PROJECT_DIR/packaging/linux/mcsrlauncher.sh" "$WORK_DIR/"

echo "==> Extracting icon from jar..."
cd "$WORK_DIR"
jar xf MCSRLauncher.jar icons/launcher/icon.png
cp icons/launcher/icon.png mcsrlauncher.png
rm -rf icons META-INF

echo "==> Creating orig tarball..."
cd /tmp
tar czf "${PKG}_${VERSION}.orig.tar.gz" "${PKG}-${VERSION}"

echo "==> Creating debian tarball..."
cd "$WORK_DIR"
tar czf "/tmp/${PKG}_${VERSION}-1.debian.tar.gz" debian/

echo "==> Generating .dsc file..."
# Calculate checksums
ORIG_SHA256=$(sha256sum "/tmp/${PKG}_${VERSION}.orig.tar.gz" | cut -d' ' -f1)
ORIG_SIZE=$(stat -c%s "/tmp/${PKG}_${VERSION}.orig.tar.gz")
DEB_SHA256=$(sha256sum "/tmp/${PKG}_${VERSION}-1.debian.tar.gz" | cut -d' ' -f1)
DEB_SIZE=$(stat -c%s "/tmp/${PKG}_${VERSION}-1.debian.tar.gz")
ORIG_MD5=$(md5sum "/tmp/${PKG}_${VERSION}.orig.tar.gz" | cut -d' ' -f1)
DEB_MD5=$(md5sum "/tmp/${PKG}_${VERSION}-1.debian.tar.gz" | cut -d' ' -f1)

cat > "/tmp/${PKG}_${VERSION}-1.dsc" << DSC
Format: 3.0 (quilt)
Source: ${PKG}
Binary: ${PKG}
Architecture: all
Version: ${VERSION}-1
Maintainer: flammablebunny <theflammablebunny@gmail.com>
Homepage: https://github.com/MCSRLauncher/Launcher
Standards-Version: 4.6.0
Build-Depends: debhelper-compat (= 13)
Checksums-Sha256:
 ${ORIG_SHA256} ${ORIG_SIZE} ${PKG}_${VERSION}.orig.tar.gz
 ${DEB_SHA256} ${DEB_SIZE} ${PKG}_${VERSION}-1.debian.tar.gz
Files:
 ${ORIG_MD5} ${ORIG_SIZE} ${PKG}_${VERSION}.orig.tar.gz
 ${DEB_MD5} ${DEB_SIZE} ${PKG}_${VERSION}-1.debian.tar.gz
DSC

echo "==> Signing .dsc file..."
gpg --default-key "$GPG_KEY" --clearsign "/tmp/${PKG}_${VERSION}-1.dsc"
mv "/tmp/${PKG}_${VERSION}-1.dsc.asc" "/tmp/${PKG}_${VERSION}-1.dsc"

echo "==> Generating .changes file..."
DSC_SHA256=$(sha256sum "/tmp/${PKG}_${VERSION}-1.dsc" | cut -d' ' -f1)
DSC_SIZE=$(stat -c%s "/tmp/${PKG}_${VERSION}-1.dsc")
DSC_MD5=$(md5sum "/tmp/${PKG}_${VERSION}-1.dsc" | cut -d' ' -f1)

DIST=$(head -1 "$SCRIPT_DIR/changelog" | sed 's/.*) \(.*\);.*/\1/')
DATE=$(date -R)

cat > "/tmp/${PKG}_${VERSION}-1_source.changes" << CHANGES
Format: 1.8
Date: ${DATE}
Source: ${PKG}
Binary: ${PKG}
Architecture: source
Version: ${VERSION}-1
Distribution: ${DIST}
Urgency: medium
Maintainer: flammablebunny <theflammablebunny@gmail.com>
Changed-By: flammablebunny <theflammablebunny@gmail.com>
Description:
 ${PKG} - MCSR Launcher
Changes:
 ${PKG} (${VERSION}-1) ${DIST}; urgency=medium
 .
   * Initial release
Checksums-Sha256:
 ${DSC_SHA256} ${DSC_SIZE} ${PKG}_${VERSION}-1.dsc
 ${ORIG_SHA256} ${ORIG_SIZE} ${PKG}_${VERSION}.orig.tar.gz
 ${DEB_SHA256} ${DEB_SIZE} ${PKG}_${VERSION}-1.debian.tar.gz
Files:
 ${DSC_MD5} ${DSC_SIZE} games optional ${PKG}_${VERSION}-1.dsc
 ${ORIG_MD5} ${ORIG_SIZE} games optional ${PKG}_${VERSION}.orig.tar.gz
 ${DEB_MD5} ${DEB_SIZE} games optional ${PKG}_${VERSION}-1.debian.tar.gz
CHANGES

echo "==> Signing .changes file..."
gpg --default-key "$GPG_KEY" --clearsign "/tmp/${PKG}_${VERSION}-1_source.changes"
mv "/tmp/${PKG}_${VERSION}-1_source.changes.asc" "/tmp/${PKG}_${VERSION}-1_source.changes"

echo "==> Uploading to PPA..."
dput --unchecked "$PPA" "/tmp/${PKG}_${VERSION}-1_source.changes"

echo "==> Done! Check your email for build status from Launchpad."

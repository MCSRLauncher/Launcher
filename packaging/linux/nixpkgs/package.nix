{ lib
, stdenv
, fetchurl
, makeWrapper
, copyDesktopItems
, makeDesktopItem
, jdk17
, addDriverRunpath

# Minecraft native deps
, libGL
, libX11
, libXcursor
, libXext
, libXrandr
, libXxf86vm
, glfw-wayland-minecraft
}:

let
  runtimeLibs = [
    libGL
    libX11
    libXcursor
    libXext
    libXrandr
    libXxf86vm
    glfw-wayland-minecraft
  ];
in

stdenv.mkDerivation rec {
  pname = "mcsrlauncher";
  version = "0.7.2-beta";

  src = fetchurl {
    url = "https://github.com/MCSRLauncher/Launcher/releases/download/${version}/MCSRLauncher.jar";
    sha256 = "sha256-NIaBbUW5X7Jsl/tnLXDFoJSBt0zpgkiT8UY7vhRaeKE=";
  };

  dontUnpack = true;

  nativeBuildInputs = [
    makeWrapper
    copyDesktopItems
  ];

  desktopItems = [
    (makeDesktopItem {
      name = "mcsrlauncher";
      desktopName = "MCSR Launcher";
      comment = "Minecraft Launcher written in Kotlin. Made for MCSR Community and MCSR Ranked";
      exec = "mcsrlauncher %U";
      icon = "mcsrlauncher";
      terminal = false;
      categories = [ "Game" ];
      keywords = [ "minecraft" "speedrun" "mcsr" "launcher" ];
      startupWMClass = "MCSR Launcher";
    })
  ];

  installPhase = ''
    runHook preInstall

    mkdir -p $out/share/java
    mkdir -p $out/bin
    mkdir -p $out/share/icons/hicolor/128x128/apps

    cp $src $out/share/java/MCSRLauncher.jar

    # Extract icon from JAR
    ${jdk17}/bin/jar xf $src icons/launcher/icon.png
    cp icons/launcher/icon.png $out/share/icons/hicolor/128x128/apps/mcsrlauncher.png

    # Create launcher script with runtime Wayland/tiling WM detection
    cat > $out/bin/mcsrlauncher << 'SCRIPT'
#!/bin/bash

# Auto apply _JAVA_AWT_WM_NONREPARENTING for Wayland and tiling WM sessions
if [ -z "$_JAVA_AWT_WM_NONREPARENTING" ]; then
    if [ "$XDG_SESSION_TYPE" = "wayland" ]; then
        export _JAVA_AWT_WM_NONREPARENTING=1
    elif [ -n "$XDG_CURRENT_DESKTOP" ]; then
        case "$XDG_CURRENT_DESKTOP" in
            sway|Sway|hyprland|Hyprland|i3|bspwm|dwm|awesome|qtile|river|niri|wayfire|wlroots)
                export _JAVA_AWT_WM_NONREPARENTING=1
                ;;
        esac
    fi
fi

SCRIPT
    echo "exec ${jdk17}/bin/java -jar $out/share/java/MCSRLauncher.jar \"\$@\"" >> $out/bin/mcsrlauncher
    chmod +x $out/bin/mcsrlauncher

    wrapProgram $out/bin/mcsrlauncher \
      --set LD_LIBRARY_PATH "${addDriverRunpath.driverLink}/lib:${lib.makeLibraryPath runtimeLibs}"

    runHook postInstall
  '';

  meta = with lib; {
    description = "Minecraft Launcher written in Kotlin. Made for MCSR Community and MCSR Ranked";
    homepage = "https://github.com/MCSRLauncher/Launcher";
    license = licenses.gpl3Plus;
    maintainers = [{
      name = "flammablebunny";
      email = "theflammablebunny@gmail.com";
      github = "flammablebunny";
    }];
    platforms = platforms.linux ++ platforms.darwin;
    mainProgram = "mcsrlauncher";
  };
}

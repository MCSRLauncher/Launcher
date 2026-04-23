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

exec java -jar /usr/share/java/mcsrlauncher/MCSRLauncher.jar "$@"

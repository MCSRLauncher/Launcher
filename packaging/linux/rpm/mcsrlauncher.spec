Name:           mcsrlauncher
Version:        0.7.1~beta
Release:        1%{?dist}
Summary:        Minecraft Launcher written in Kotlin. Made for MCSR Community and MCSR Ranked

License:        GPL-3.0-or-later
URL:            https://github.com/MCSRLauncher/Launcher
Source0:        MCSRLauncher.jar
Source1:        mcsrlauncher.desktop
Source2:        mcsrlauncher.png

BuildArch:      noarch
Requires:       java-21-openjdk
Requires:       hicolor-icon-theme

%description
Minecraft Launcher written in Kotlin. Made for MCSR Community and MCSR Ranked

%install
mkdir -p %{buildroot}%{_datadir}/java/%{name}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}%{_datadir}/applications
mkdir -p %{buildroot}%{_datadir}/icons/hicolor/128x128/apps

install -m 644 %{SOURCE0} %{buildroot}%{_datadir}/java/%{name}/MCSRLauncher.jar
install -m 644 %{SOURCE1} %{buildroot}%{_datadir}/applications/%{name}.desktop
install -m 644 %{SOURCE2} %{buildroot}%{_datadir}/icons/hicolor/128x128/apps/%{name}.png

cat > %{buildroot}%{_bindir}/%{name} << 'EOF'
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
EOF
chmod 755 %{buildroot}%{_bindir}/%{name}

%files
%{_datadir}/java/%{name}/MCSRLauncher.jar
%{_bindir}/%{name}
%{_datadir}/applications/%{name}.desktop
%{_datadir}/icons/hicolor/128x128/apps/%{name}.png

%changelog
* Mon Feb 16 2026 flammablebunny <theflammablebunny@gmail.com> - 0.7.1~beta-1
- Initial RPM release
package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.data.device.DeviceOSType
import mslinks.ShellLink
import mslinks.ShellLinkHelper
import org.apache.commons.io.FileUtils
import java.awt.Desktop
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import javax.swing.JOptionPane
import kotlin.io.path.setPosixFilePermissions

object ShortcutUtils {

    data class ShortcutConfig(
        val instanceId: String,
        val account: String
    )

    private val osType = OSUtils.getOSType()

    fun getShortcutExtension(): String {
        return when (osType) {
            DeviceOSType.WINDOWS -> ".lnk"
            DeviceOSType.MACOS -> ".app"
            DeviceOSType.LINUX -> ".desktop"
        }
    }

    // assumes user has the correct java PATH set to run MCSRLauncher, should probably find whatever is running current inst of launcher
    // TODO: Add icon for shortcut when instances gain icons in the launcher
    fun createShortcut(shortcutPathString: String, instanceId: String, account: String) {
        val shortcutConfig = ShortcutConfig(instanceId, account)

        when (osType) {
            DeviceOSType.WINDOWS -> createWindowsShortcut(shortcutPathString, shortcutConfig)
            DeviceOSType.MACOS -> createMacShortcut(shortcutPathString, shortcutConfig)
            DeviceOSType.LINUX -> createLinuxShortcut(shortcutPathString, shortcutConfig)
        }
    }

    private fun createWindowsShortcut(shortcutPathString: String, shortcutConfig: ShortcutConfig) {
        var cmdArgs = "--launch ${shortcutConfig.instanceId}"
        if (shortcutConfig.account != "null") cmdArgs = cmdArgs.plus(" --account ${shortcutConfig.account}")

        val shellLink = ShellLink()
            .setWorkingDir(Paths.get("").toAbsolutePath().normalize().toString())
            .setCMDArgs(cmdArgs)

        val targetPath = Paths.get("").resolve("MCSRLauncher.jar").toAbsolutePath().normalize()
        val root = targetPath.root.toString()
        val path = targetPath.subpath(0, targetPath.nameCount).toString()

        ShellLinkHelper(shellLink)
            .setLocalTarget(root, path, ShellLinkHelper.Options.ForceTypeFile)
            .saveTo(shortcutPathString)

        Desktop.getDesktop().open(File(shortcutPathString).parentFile)
    }

    private fun createMacShortcut(shortcutPathString: String, shortcutConfig: ShortcutConfig) {
        var cmdArgs = "--launch ${shortcutConfig.instanceId}"
        if (shortcutConfig.account != "null") cmdArgs = cmdArgs.plus(" --account ${shortcutConfig.account}")

        val shortcutPath = Path.of(shortcutPathString)
        val targetPath = Paths.get("").resolve("MCSRLauncher.jar").toAbsolutePath().normalize()

        val contentsPath = shortcutPath.resolve("Contents")
//        val resourcesPath = contentsPath.resolve("Resources") // Used for icon file
        val macOSPath = contentsPath.resolve("MacOS")

        val runCommand = macOSPath.resolve("Run.command")
        val commandString = buildMacCommandString(targetPath, cmdArgs)

        FileUtils.writeStringToFile(runCommand.toFile(), commandString, Charsets.UTF_8)
        runCommand.setPosixFilePermissions(
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE,
                PosixFilePermission.OTHERS_WRITE
                )
        )

        val infoPlist = contentsPath.resolve("Info.plist")
        val infoString = buildMacInfoString(shortcutConfig.instanceId)

        FileUtils.writeStringToFile(infoPlist.toFile(), infoString, Charsets.UTF_8)

        // better checking should be done to see where the launcher jarfile cannot be accessed, e.g. Desktop folder
        JOptionPane.showMessageDialog(
            null,
            I18n.translate("text.shortcut.mac_security"),
            I18n.translate("text.warning"),
            JOptionPane.OK_OPTION
        )
    }

    // GNOME envs can't double click .desktop files, should probably fix (see #1038 in PrismLauncher)
    private fun createLinuxShortcut(shortcutPathString: String, shortcutConfig: ShortcutConfig) {
        var cmdArgs = "--launch ${shortcutConfig.instanceId}"
        if (shortcutConfig.account != "null") cmdArgs = cmdArgs.plus(" --account ${shortcutConfig.account}")

        val targetPath = Paths.get("").resolve("MCSRLauncher.jar").toAbsolutePath().normalize()

        val desktopString = buildLinuxDesktopEntry(targetPath, cmdArgs, shortcutConfig.instanceId)
        val shortcutPath = Path.of(shortcutPathString)

        FileUtils.writeStringToFile(shortcutPath.toFile(), desktopString, Charsets.UTF_8)
        shortcutPath.setPosixFilePermissions(
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE,
                PosixFilePermission.OTHERS_WRITE
            )
        )
    }

    private fun buildMacCommandString(targetPath: Path, cmdArgs: String): String {
        val sb = StringBuilder()
        sb.appendLine("#!/bin/bash")
        sb.appendLine("cd \"${targetPath.parent}\"")
        sb.appendLine("java -jar \"$targetPath\" $cmdArgs")

        return sb.toString()
    }

    private fun buildMacInfoString(instanceId: String): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ")
        sb.appendLine("<!DOCTYPE plist PUBLIC \"-//Apple Computer//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
        sb.appendLine("<plist version=\"1.0\">")
        sb.appendLine("<dict>")
        sb.appendLine("\t<key>CFBundleExecutable</key>")
        sb.appendLine("\t<string>Run.command</string>")
//        sb.appendLine("\t<key>CFBundleIconFile</key>") // These two lines become relevant once instance icons materialise
//        sb.appendLine("\t<string>Icon.icns</string>")
        sb.appendLine("\t<key>CFBundleName</key>")
        sb.appendLine("\t<string>$instanceId</string>")
        sb.appendLine("\t<key>CFBundlePackageType</key>")
        sb.appendLine("\t<string>APPL</string>")
        sb.appendLine("\t<key>CFBundleShortVersionString</key>")
        sb.appendLine("\t<string>1.0</string>")
        sb.appendLine("\t<key>CFBundleVersion</key>")
        sb.appendLine("\t<string>1.0</string>")
        sb.appendLine("</dict>")
        sb.appendLine("</plist>")

        return sb.toString()
    }

    private fun buildLinuxDesktopEntry(targetPath: Path, cmdArgs: String, instanceId: String): String {
        val sb = StringBuilder()
        sb.appendLine("[Desktop Entry]")
        sb.appendLine("Type=Application")
        sb.appendLine("Categories=Game;ActionGame;AdventureGame;Simulation")
        sb.appendLine("Exec=java -jar \"$targetPath\" $cmdArgs")
        sb.appendLine("Path=${Paths.get("").toAbsolutePath().normalize()}")
        sb.appendLine("Name=$instanceId")
        // sb.appendLine("Icon=")

        return sb.toString()
    }
}
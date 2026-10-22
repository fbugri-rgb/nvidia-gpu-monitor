package nvidiamonitor.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Laat de app automatisch starten bij het aanmelden:
 * <ul>
 *     <li>Windows: registersleutel HKCU\Software\Microsoft\Windows\CurrentVersion\Run</li>
 *     <li>Linux: ~/.config/autostart/nvidia-gpu-monitor.desktop</li>
 *     <li>macOS: ~/Library/LaunchAgents/be.kdg.nvidiamonitor.plist</li>
 * </ul>
 * Geen van deze manieren heeft administratorrechten nodig.
 */
public final class AutoStart {
    private static final String WIN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String WIN_VALUE = "NvidiaGpuMonitor";
    private static final String MAC_LABEL = "be.kdg.nvidiamonitor";

    private AutoStart() {
    }

    public static String label() {
        return Platform.current() == Platform.WINDOWS ? "Start with Windows" : "Start at login";
    }

    public static boolean isEnabled() {
        return switch (Platform.current()) {
            case WINDOWS -> run("reg", "query", WIN_KEY, "/v", WIN_VALUE) == 0;
            case LINUX -> Files.exists(linuxFile());
            case MAC -> Files.exists(macFile());
        };
    }

    public static void setEnabled(boolean enabled) throws IOException {
        switch (Platform.current()) {
            case WINDOWS -> {
                if (enabled) {
                    registerWindows();
                } else if (isEnabled() && run("reg", "delete", WIN_KEY, "/v", WIN_VALUE, "/f") != 0) {
                    throw new IOException("Could not remove the startup entry");
                }
            }
            case LINUX -> writeOrDelete(linuxFile(), enabled, linuxDesktopEntry());
            case MAC -> writeOrDelete(macFile(), enabled, macLaunchAgent());
        }
    }

    /**
     * Commando waarmee het systeem de app start, in widget-modus.
     * Een met jpackage gebouwde app heeft een eigen launcher; anders starten we Java zelf.
     */
    private static List<String> command() {
        List<String> command = new ArrayList<>();
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher != null) {
            command.add(launcher);
        } else {
            String java = Platform.current() == Platform.WINDOWS ? "javaw.exe" : "java";
            command.add(Path.of(System.getProperty("java.home"), "bin", java).toString());
            command.add("-cp");
            command.add(Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
                    .map(entry -> new File(entry).getAbsolutePath())
                    .collect(Collectors.joining(File.pathSeparator)));
            command.add("nvidiamonitor.Main");
        }
        command.add("--widget");
        return command;
    }

    private static String quoted(List<String> command) {
        return command.stream().map(arg -> "\"" + arg + "\"").collect(Collectors.joining(" "));
    }

    // ---------- Windows ----------

    /**
     * Schrijft de waarde via een .reg-bestand: zo moeten de aanhalingstekens in het commando
     * niet door de Windows command line ontsnapt worden.
     */
    private static void registerWindows() throws IOException {
        String escaped = quoted(command()).replace("\\", "\\\\").replace("\"", "\\\"");
        String reg = "﻿Windows Registry Editor Version 5.00\r\n\r\n"
                + "[" + WIN_KEY.replace("HKCU", "HKEY_CURRENT_USER") + "]\r\n"
                + "\"" + WIN_VALUE + "\"=\"" + escaped + "\"\r\n";
        Path file = Files.createTempFile("nvidia-monitor", ".reg");
        try {
            Files.writeString(file, reg, StandardCharsets.UTF_16LE);
            if (run("reg", "import", file.toString()) != 0) {
                throw new IOException("Could not add the startup entry");
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // ---------- Linux ----------

    private static Path linuxFile() {
        String config = System.getenv("XDG_CONFIG_HOME");
        Path base = config != null && !config.isBlank()
                ? Path.of(config) : Path.of(System.getProperty("user.home"), ".config");
        return base.resolve("autostart").resolve("nvidia-gpu-monitor.desktop");
    }

    private static String linuxDesktopEntry() {
        return """
                [Desktop Entry]
                Type=Application
                Name=NVIDIA GPU Monitor
                Comment=Monitor NVIDIA GPU usage, memory and temperature
                Exec=%s
                Terminal=false
                X-GNOME-Autostart-enabled=true
                """.formatted(quoted(command()));
    }

    // ---------- macOS ----------

    private static Path macFile() {
        return Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", MAC_LABEL + ".plist");
    }

    private static String macLaunchAgent() {
        String arguments = command().stream()
                .map(arg -> "        <string>" + arg.replace("&", "&amp;").replace("<", "&lt;") + "</string>")
                .collect(Collectors.joining("\n"));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Label</key>
                    <string>%s</string>
                    <key>ProgramArguments</key>
                    <array>
                %s
                    </array>
                    <key>RunAtLoad</key>
                    <true/>
                </dict>
                </plist>
                """.formatted(MAC_LABEL, arguments);
    }

    // ---------- hulpmethodes ----------

    private static void writeOrDelete(Path file, boolean enabled, String content) throws IOException {
        if (enabled) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(file);
        }
    }

    private static int run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}

# NVIDIA GPU Monitor

A small desktop app that shows what your NVIDIA graphics card is doing, like the GPU tab in Windows Task Manager. It also has a floating widget and adjustable transparency.

Built in Java Swing as part of the Java 2 groeiproject.

## Features

- **Live graphs** of the last 60 seconds for GPU usage, video memory, temperature and power draw. It also shows the core clock, memory clock and fan speed.
- **Multiple graphics cards:** show all cards or pick one under *Hardware*.
- **Compact view:** shrink the window to just the graphs. Double-click to switch between compact and full view.
- **Floating widget:** a small window that stays on top of other windows and has no taskbar button.
- **Transparency** for each window:
  - *Window opacity* makes the whole window see-through, text included.
  - *Background* makes only the background see-through, so the text and graphs stay sharp.
- **Always on top**, **show/hide individual meters**, **start at login**.
- Remembers your settings: transparency, hidden meters, chosen GPU, compact view and widget position.

### Controls

| Action | Main window | Widget |
|---|---|---|
| Right-click | Menu | Menu |
| Drag | Move window | Move widget |
| Double-click | Compact ⇄ full view | Open the main window |
| Mouse wheel | – | Change background transparency |
| ☰ button (title bar) | Menu | – |
| Bottom-right corner | Resize | – |

### Menu

- Compact view (graphs only)
- Show widget / Switch to widget only (main window)
- Open monitor / Hide widget (widget)
- Show / hide meters: GPU usage, Dedicated memory, Temperature, Power draw
- Hardware: All GPUs, GPU 0, GPU 1, …
- Window opacity and Background sliders
- Always on top
- Start with Windows (*Start at login* on Linux and macOS)
- About
- Exit

## Requirements

- An **NVIDIA graphics card** with the official NVIDIA driver. The app reads its data from `nvidia-smi`, which comes with the driver.
- **Java 21 or newer**, but only if you use the `.jar`. The native app includes its own Java.

| OS | Status |
|---|---|
| Windows 10/11 | Fully supported (developed and tested here) |
| Linux | Supported with the proprietary NVIDIA driver (`nvidia-smi` must work in a terminal). Transparency needs a compositing desktop such as GNOME or KDE. |
| macOS | The app runs, but NVIDIA has no drivers for current macOS versions. On a Mac the app will usually show *"nvidia-smi not found"*. |

## Download and run

### Option 1: the jar (any OS with Java 21+)

```bash
java -jar nvidia-gpu-monitor-1.1.0.jar            # main window
java -jar nvidia-gpu-monitor-1.1.0.jar --widget   # widget only
```

On Windows you can also double-click the `.jar`.

### Option 2: the native app (no Java needed)

| OS | File | How to start |
|---|---|---|
| Windows | `NvidiaGpuMonitor-1.1.0-windows.zip` | Unzip and run `NvidiaGpuMonitor\NvidiaGpuMonitor.exe` |
| Linux | `NvidiaGpuMonitor-1.1.0-linux.tar.gz` or `.deb` | `tar xzf …` then `NvidiaGpuMonitor/bin/NvidiaGpuMonitor`, or `sudo apt install ./nvidiagpumonitor_*.deb` |
| macOS | `NvidiaGpuMonitor-1.1.0-macos.tar.gz` or `.dmg` | Open `NvidiaGpuMonitor.app` |

The macOS app is not signed. The first time, right-click the app and choose **Open**, or run
`xattr -dr com.apple.quarantine NvidiaGpuMonitor.app`.

## Building

The project uses Gradle. The wrapper is included, so you don't need to install Gradle.

```bash
./gradlew dist          # jar + native app for the current OS   (Windows: gradlew.bat dist)
./gradlew run           # start the app from source
```

| Task | Output |
|---|---|
| `./gradlew jar` | `build/libs/nvidia-gpu-monitor-1.1.0.jar` (runs on every OS) |
| `./gradlew packageNative` | `build/dist/NvidiaGpuMonitor-1.1.0-<os>.zip` or `.tar.gz` (app with its own Java runtime) |
| `./gradlew jpackageInstaller` | `build/jpackage/installer/` (installer for the current OS) |
| `./gradlew dist` | `jar` + `packageNative` |

Installer types: `-Pinstaller=msi|exe` (Windows, needs the [WiX Toolset](https://wixtoolset.org/)), `deb|rpm` (Linux) and `dmg|pkg` (macOS).

A JDK 21 or newer is needed to build; it must include `jpackage`, which every standard JDK does.

### Windows, Linux and macOS builds

`jpackage` can only build an app for the OS it runs on. To get all three, push the project to GitHub. The workflow in [`.github/workflows/build.yml`](.github/workflows/build.yml) then builds on Windows, Linux and macOS at the same time. The results appear under **Actions → Build → Artifacts**:

- **Windows:** jar + `.zip`
- **Linux:** jar + `.tar.gz` + `.deb`
- **macOS:** jar + `.tar.gz` + `.dmg` (Apple Silicon)

### IntelliJ

You can open the project as before and run `nvidiamonitor.Main`, or import `build.gradle` as a Gradle project. When you start it from IntelliJ, the *About* screen shows version `dev`.

## Project structure

```
src/nvidiamonitor/
├── Main.java                  start; --widget opens only the widget
├── model/
│   ├── GpuStats.java          one reading of one graphics card (record)
│   └── Meter.java             the meters you can show or hide
├── service/
│   ├── NvidiaSmiReader.java   runs nvidia-smi and parses its CSV output
│   ├── GpuMonitor.java        polls every second on a background thread
│   ├── AutoStart.java         start at login (registry / .desktop / LaunchAgent)
│   └── Platform.java          Windows, Linux or macOS
└── ui/
    ├── AppController.java     connects data and windows, builds the right-click menu
    ├── MainWindow.java        main window (full and compact view)
    ├── GpuSection.java        all meters of one graphics card
    ├── MetricCard.java        one card: title, value and graph
    ├── HistoryGraph.java      line graph of the last 60 readings
    ├── WidgetWindow.java      floating widget
    ├── TitleBar.java          custom title bar (needed for transparency)
    ├── WindowStyle.java       opacity, background transparency, always on top
    ├── WindowMouse.java       drag, right-click and double-click
    ├── Settings.java          saved settings (java.util.prefs)
    └── Theme.java             colours and fonts
```

### How it works

Every second, `GpuMonitor` runs this command on a background thread:

```
nvidia-smi --query-gpu=index,name,driver_version,utilization.gpu,... --format=csv,noheader,nounits
```

Each line of output becomes a `GpuStats` record. The listeners in the UI then receive the new values on the Swing thread.

Windows only allow transparency when they have no regular title bar and border. That's why both windows draw their own title bar and background.

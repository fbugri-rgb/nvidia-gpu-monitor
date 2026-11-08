# NVIDIA GPU Monitor

A small desktop app that shows what your NVIDIA graphics card is doing, like the GPU tab in Windows Task Manager. It also has a floating widget and adjustable transparency.

Built in Java Swing as part of the Java 2 groeiproject.

## ⬇️ Download

| Platform | Download | Then |
|---|---|---|
| **Windows** | [NvidiaGpuMonitor-1.3.2-windows.zip](https://github.com/fbugri-rgb/nvidia-gpu-monitor/releases/latest/download/NvidiaGpuMonitor-1.3.2-windows.zip) | Unzip and run `NvidiaGpuMonitor.exe`. No Java needed. |
| **Linux** (Debian/Ubuntu) | [nvidiagpumonitor_1.3.2_amd64.deb](https://github.com/fbugri-rgb/nvidia-gpu-monitor/releases/latest/download/nvidiagpumonitor_1.3.2_amd64.deb) | `sudo apt install ./nvidiagpumonitor_1.3.2_amd64.deb` |
| **Linux** (other) | [NvidiaGpuMonitor-1.3.2-linux.tar.gz](https://github.com/fbugri-rgb/nvidia-gpu-monitor/releases/latest/download/NvidiaGpuMonitor-1.3.2-linux.tar.gz) | Extract and run `NvidiaGpuMonitor/bin/NvidiaGpuMonitor` |
| **macOS** (Apple Silicon) | [NvidiaGpuMonitor-1.3.2-macos-arm64.dmg](https://github.com/fbugri-rgb/nvidia-gpu-monitor/releases/latest/download/NvidiaGpuMonitor-1.3.2-macos-arm64.dmg) | Open it, drag the app to Applications, then right-click it and choose **Open** the first time |
| **Any OS with Java 21+** | [nvidia-gpu-monitor-1.3.2.jar](https://github.com/fbugri-rgb/nvidia-gpu-monitor/releases/latest/download/nvidia-gpu-monitor-1.3.2.jar) | Double-click it, or run `java -jar nvidia-gpu-monitor-1.3.2.jar` |

All files and release notes: [**Releases**](https://github.com/fbugri-rgb/nvidia-gpu-monitor/releases/latest).
You need an NVIDIA graphics card with the NVIDIA driver installed (see [Requirements](#requirements)).

## Features

- **Live graphs** of the last 60 seconds for GPU usage, video memory, temperature and power draw. It also shows the core clock, memory clock and fan speed.
- **Multiple graphics cards:** show all cards or pick one under *Hardware*.
- **Compact view:** shrink the window to just the graphs. Double-click to switch between compact and full view.
- **Floating widget:** a small window that stays on top of other windows and has no taskbar button.
- **Transparency** for each window:
  - *Window opacity* makes the whole window see-through, text included.
  - *Background* makes only the background see-through, so the text and graphs stay sharp. This needs *Transparent background* to be on. It's on by default on Windows and macOS, and off on Linux, where see-through windows flicker each time the values update. To set it at startup instead, run the jar with `java -Dnvidiamonitor.transparent=true` or `=false`.
- **Always on top**, **show/hide individual meters**, **start at login**.
- Remembers your settings: transparency, hidden meters, chosen GPU, compact view and widget position.
- **Lets Optimus laptops sleep:** when no app uses the NVIDIA GPU, the monitor stops querying it, so the GPU can power off (0 W) instead of being kept awake at 25–30 W. It resumes automatically as soon as another app wakes the GPU.

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
- Transparent background (on/off for both windows)
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
| Linux | Supported with the proprietary NVIDIA driver (`nvidia-smi` must work in a terminal). Transparency needs a compositing desktop such as GNOME or KDE. *Transparent background* is off by default, because it makes the windows flicker. |
| macOS | The app runs, but NVIDIA has no drivers for current macOS versions. On a Mac the app will usually show *"nvidia-smi not found"*. |

## Download and run

### Option 1: the jar (any OS with Java 21+)

```bash
java -jar nvidia-gpu-monitor-1.3.2.jar            # main window
java -jar nvidia-gpu-monitor-1.3.2.jar --widget   # widget only
```

On Windows you can also double-click the `.jar`.

### Option 2: the native app (no Java needed)

| OS | File | How to start |
|---|---|---|
| Windows | `NvidiaGpuMonitor-1.3.2-windows.zip` | Unzip and run `NvidiaGpuMonitor\NvidiaGpuMonitor.exe` |
| Linux | `NvidiaGpuMonitor-1.3.2-linux.tar.gz` or `.deb` | `tar xzf …` then `NvidiaGpuMonitor/bin/NvidiaGpuMonitor`, or `sudo apt install ./nvidiagpumonitor_*.deb` |
| macOS | `NvidiaGpuMonitor-1.3.2-macos.tar.gz` or `.dmg` | Open `NvidiaGpuMonitor.app` |

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
| `./gradlew jar` | `build/libs/nvidia-gpu-monitor-1.3.2.jar` (runs on every OS) |
| `./gradlew packageNative` | `build/dist/NvidiaGpuMonitor-1.3.2-<os>.zip` or `.tar.gz` (app with its own Java runtime) |
| `./gradlew jpackageInstaller` | `build/jpackage/installer/` (installer for the current OS) |
| `./gradlew dist` | `jar` + `packageNative` |

Installer types: `-Pinstaller=msi|exe` (Windows, needs the [WiX Toolset](https://wixtoolset.org/)), `deb|rpm` (Linux) and `dmg|pkg` (macOS).

A JDK 22 or newer is needed to build, because the classes in `src-java22/` are compiled for Java 22. The jar it produces still runs on Java 21. The JDK must include `jpackage`, which every standard JDK does.

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
│   ├── NvidiaSmiReader.java   one long-running nvidia-smi (--loop-ms) and parses its CSV output
│   ├── GpuMonitor.java        active/sleep logic on a background thread
│   ├── GpuPowerState.java     reads the GPU's PCIe power state (D0/D3) without waking it
│   ├── LinuxPowerState.java   … via /sys/bus/pci/devices/*/power_state
│   ├── WindowsPowerState.java … Java 21 fallback (no sleep mode)
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

src-java22/nvidiamonitor/service/
└── WindowsPowerState.java     … via CfgMgr32.dll (Foreign Function & Memory API, Java 22+)
```

### How it works

`GpuMonitor` keeps one `nvidia-smi` process running, which prints a line per card every second:

```
nvidia-smi --query-gpu=index,name,driver_version,utilization.gpu,... --format=csv,noheader,nounits --loop-ms=1000
```

Each line of output becomes a `GpuStats` record. The listeners in the UI then receive the new values on the Swing thread.

**Sleep mode (Optimus laptops).** Every `nvidia-smi` query wakes a sleeping GPU, and it stays awake in its high-performance state for a few seconds. Polling every second would therefore keep it awake forever. When all cards report 0 % load and 0 MB used memory for 5 readings in a row, `GpuMonitor` stops `nvidia-smi` and the OS powers the GPU off (D3). After that it only reads the power state the OS keeps for the device: `DEVPKEY_Device_PowerData` on Windows, `power_state` in sysfs on Linux. That read never wakes the card. Meanwhile the UI shows 0 W and the last temperature, like Task Manager does. When another app wakes the GPU (D0), `nvidia-smi` starts again. The first power reading after a wake-up is discarded, because the sensor often reports nonsense at that moment (e.g. 751 W).

On Windows, the power state is read through the Foreign Function & Memory API, which became final in Java 22. The jar is a multi-release jar: on Java 22+ (and in the native app) it uses `src-java22/`, while on Java 21 the app runs without sleep mode. Desktop GPUs that drive a display always have memory in use, so they never enter sleep mode.

Windows only allow transparency when they have no regular title bar and border. That's why both windows draw their own title bar and background.

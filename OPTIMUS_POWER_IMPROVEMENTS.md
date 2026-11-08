# GPU Power & Efficiency Improvements (Preventing `D3` Wakeups on Optimus Laptops)

## 1. Problem Summary: "The Observer Effect"

On NVIDIA Optimus laptops (like the MSI Creator Z17 with an Intel iGPU + NVIDIA RTX dGPU), Windows completely cuts power to the dedicated NVIDIA GPU (`D3cold` state, **`0W`**) whenever no 3D or compute application is using it.

Currently, `NvidiaGpuMonitor` polls the GPU by spawning a new `nvidia-smi` process every second in `NvidiaSmiReader.read()`:

```java
process = new ProcessBuilder(executable,
        "--query-gpu=" + FIELDS, "--format=csv,noheader,nounits")
        .redirectErrorStream(true)
        .start();
```

### Why This Heats Up the GPU at Idle (65–66°C instead of ~47°C)
1. **Forces PCIe Wake-Up (`D3` → `D0`):** `nvidia-smi` queries the GPU's onboard hardware registers directly. If the GPU is asleep in `D3` (`0W`), the Windows PCIe bus driver must physically power the GPU back on (`D0`) to answer the query.
2. **Boots into `P0` High-Performance Clocks:** Every time an NVIDIA GPU wakes from `D3` sleep (or initializes a new NVML session), its firmware boots into the **`P0` performance state** (`1,125 MHz` Core / `7,001 MHz` VRAM), drawing **25W – 32W** for ~3–5 seconds before stepping down to `P8` or returning to sleep.
3. **Infinite Wake Loop:** Because `GpuMonitor` polls every 1–2 seconds, the GPU is hit again *before* the 3–5 second wake window expires—trapping the card at **25–32W** indefinitely and heating the laptop to **65–66°C** with `0%` GPU load.
4. **Process-Spawning CPU Overhead:** Starting a new `nvidia-smi.exe` OS process every second spawns **3,600 processes per hour**, consuming significant background CPU time.

---

## 2. How Task Manager & HWiNFO64 Avoid Waking the GPU

- **Windows Task Manager** queries the Windows Display Driver Model (`dxgkrnl.sys`) kernel counters in RAM. When the GPU is in `D3` sleep, the kernel driver returns `0%` usage, `0 MB` VRAM, and the **last cached temperature reading** recorded right before the GPU powered off—without touching the PCIe bus.
- **HWiNFO64** checks the Windows PnP device power state (`DEVPKEY_Device_PowerData`) before polling. If the GPU is in `D3` (`PD_MostRecentPowerState == 4`), it skips querying the NVIDIA driver.

---

## 3. Solution Architecture: 4 Key Improvements

```
                   ┌──────────────────────────────────────┐
                   │             ACTIVE MODE              │
                   │  Persistent `nvidia-smi --loop-ms`   │
                   │  Reads live CSV stream from stdout   │
                   └──────────────────┬───────────────────┘
                                      │
             `gpuUsage == 0` AND      │  External app wakes GPU
             `memoryUsedMb == 0`      │  (`D-State == 1` / `D0`)
             for 5 consecutive ticks  ▼  
                   ┌──────────────────────────────────────┐
                   │             SLEEP MODE               │
                   │  Destroy `nvidia-smi` process (`0W`) │
                   │  Check OS PCIe D-State passively     │
                   │  Emit cached temp + 0W / 0% / 0 MHz  │
                   └──────────────────────────────────────┘
```

---

### Improvement 1: Use a Persistent `nvidia-smi --loop-ms` Stream in Active Mode

Instead of spawning a new `nvidia-smi` process on every poll tick, keep a **single** `nvidia-smi` process open while the GPU is actively being used:

```java
Process process = new ProcessBuilder(
        executable,
        "--query-gpu=" + FIELDS,
        "--format=csv,noheader,nounits",
        "--loop-ms=" + intervalMs
).redirectErrorStream(true).start();
```

- Read lines continuously from `process.getInputStream()` on a background thread.
- **Benefit:** Eliminates 3,600 process creations per hour and stops re-initializing the driver on every tick.

---

### Improvement 2: Detect Idle State and Release `nvidia-smi` (`Active` → `Sleep`)

As long as *any* `nvidia-smi` process is running, the GPU cannot enter `D3` (`0W`) deep sleep.
However, `nvidia-smi` itself tells us whether any *other* application is using the GPU:

- When **no application** is using the dedicated GPU, `nvidia-smi` reports:
  - `utilization.gpu == 0` (`gpuUsage == 0`)
  - `memory.used == 0` (`memoryUsedMb == 0`)
- When **any** game, browser, or CUDA app is on the dGPU, `memoryUsedMb > 0`.

**Idle Transition Rule:**
1. Count consecutive samples where `stats.gpuUsage() == 0 && stats.memoryUsedMb() == 0`.
2. Once this reaches **5 consecutive seconds**, stop the `nvidia-smi` process (`process.destroy()`) and switch `GpuMonitor` into **Sleep Mode**.
3. Within ~3 seconds of closing `nvidia-smi`, Windows automatically powers the GPU down into **`D3` (`0W`)**.

---

### Improvement 3: Passive `D-State` Check While Sleeping (`Sleep` → `Active`)

While in **Sleep Mode**, do **not** call `nvidia-smi`. Instead:
1. Emit a synthetic `GpuStats` snapshot using the last known static info (`name`, `driverVersion`, `memoryTotalMb`, `powerLimit`) and the **last known `temperature`**, with `gpuUsage = 0`, `powerDraw = 0`, `coreClock = 0`, `memoryClock = 0`.
2. Check the OS PCIe hardware power state (`D-state`) passively—which **never wakes the GPU**:

#### On Windows: Check `DEVPKEY_Device_PowerData`
In Windows `CfgMgr32.dll` / PnP, every PCIe device exposes `DEVPKEY_Device_PowerData` (`{3464F7A4-2444-40B1-980A-E0903CB6D912}, 1`).
Byte offset `4` of the returned `CM_POWER_DATA` struct is `PD_MostRecentPowerState` (`DEVICE_POWER_STATE` enum):
- `1` = `PowerDeviceD0` (**Awake** — an application is using the GPU!)
- `4` = `PowerDeviceD3` (**Asleep / 0W** — stay in Sleep Mode)

##### Option A: Zero-Dependency Native Call via Java 21 Foreign Function & Memory (FFM) API or JNA
Because the project already targets **Java 21** (`options.release = 21` in `build.gradle`), you can call `CfgMgr32.dll` (`CM_Locate_DevNodeW` + `CM_Get_DevNode_PropertyW`) directly via `java.lang.foreign` in **< 0.05 ms** with zero external libraries and zero process spawns.

*(Note: If using FFM with `jpackage` in `build.gradle`, add `--enable-native-access=ALL-UNNAMED` to `DefaultJVMOptions` or `jpackage` arguments.)*

##### Option B: Lightweight Fallback on Linux (`/sys/bus/pci/devices/.../power_state`)
On Linux, read `/sys/class/drm/card*/device/power_state` as a regular text file (does not wake the GPU):
- Returns `"D0"` when awake, or `"D3cold"` / `"D3hot"` when asleep.

##### Option C: PowerShell Helper Process (Single Persistent Pipe on Windows)
If you want to avoid native FFM code initially, you can keep a single lightweight background PowerShell session open or check `Get-PnpDeviceProperty`:
```powershell
$p = Get-PnpDevice -FriendlyName "*NVIDIA*" | Get-PnpDeviceProperty -KeyName "DEVPKEY_Device_PowerData"
$p.Data[4] # Returns 1 (D0 Awake) or 4 (D3 Asleep)
```
When it returns `1` (`D0`), transition `GpuMonitor` back to **Active Mode** and restart the `nvidia-smi --loop-ms` stream!

---

### Improvement 4: Filter Wake-Up Power Sensor Spikes (`751W` Artifact)

When an NVIDIA laptop GPU wakes up from `D3cold` (`0W`), the onboard power ADC sensor sometimes returns an uninitialized garbage value on the very first read (e.g. `751.87 W` on an `80 W` limit GPU).

In `NvidiaSmiReader.parse(String line)`:

```java
static GpuStats parse(String line) throws IOException {
    String[] p = line.split(",\\s*");
    if (p.length < 13) {
        throw new IOException("Onverwachte uitvoer van nvidia-smi: " + line);
    }
    double powerDraw = num(p[8]);
    double powerLimit = num(p[9]);

    // Filter transient D3cold wake-up ADC spikes (e.g. 751.87 W on an 80 W GPU)
    if (powerLimit > 0 && powerDraw > powerLimit * 1.5) {
        powerDraw = -1; // or 0
    }

    return new GpuStats(
            (int) num(p[0]),
            p[1].trim(),
            p[2].trim(),
            num(p[3]),
            num(p[4]),
            num(p[5]),
            num(p[6]),
            num(p[7]),
            powerDraw,
            powerLimit,
            num(p[10]),
            num(p[11]),
            num(p[12])
    );
}
```

---

## 4. Reference Implementation Sketch for `GpuStats` (Sleep Snapshot)

Add a helper method to `src/nvidiamonitor/model/GpuStats.java` to create a zero-power sleep snapshot while preserving the GPU's name, total VRAM, power limit, and last cached temperature:

```java
public GpuStats asSleeping() {
    return new GpuStats(
            index,
            name,
            driverVersion,
            0.0,            // gpuUsage: 0%
            0.0,            // memoryUsage: 0%
            0.0,            // memoryUsedMb: 0 MB
            memoryTotalMb,
            temperature,    // keep last known temperature (like Task Manager)
            0.0,            // powerDraw: 0.0 W (D3 Sleep)
            powerLimit,
            -1.0,           // fanSpeed
            0.0,            // coreClock: 0 MHz
            0.0             // memoryClock: 0 MHz
    );
}
```

---

## 5. Summary Checklist for Implementation

- [ ] **Step 1:** Add `asSleeping()` helper to `GpuStats.java`.
- [ ] **Step 2:** Add the `powerDraw > powerLimit * 1.5` wake-up spike filter in `NvidiaSmiReader.parse()`.
- [ ] **Step 3:** Convert `NvidiaSmiReader` to support streaming via `--loop-ms` instead of spawning a new `nvidia-smi` process every second.
- [ ] **Step 4:** In `GpuMonitor`, track consecutive idle ticks (`gpuUsage == 0 && memoryUsedMb == 0`). After 5 idle ticks, stop the `nvidia-smi` stream so the GPU can enter `D3` (`0W`).
- [ ] **Step 5:** While in Sleep Mode, check the PCIe `D-state` (`DEVPKEY_Device_PowerData` on Windows / `power_state` sysfs on Linux) and emit `lastStats.asSleeping()` until another application wakes the GPU into `D0`.

package nvidiamonitor.service;

import nvidiamonitor.model.GpuStats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Leest GPU-gegevens uit via de nvidia-smi command line tool (wordt meegeleverd met de NVIDIA driver).
 * <p>
 * Er draait één blijvend nvidia-smi-proces met {@code --loop-ms}, dat elke interval een regel per kaart
 * schrijft. Zo wordt niet elke seconde een nieuw proces gestart en de driver opnieuw geïnitialiseerd.
 * Zolang het proces loopt kan de kaart niet in diepe slaap (D3) gaan; daarom stopt {@link GpuMonitor}
 * het wanneer de kaart niet gebruikt wordt.
 */
public class NvidiaSmiReader {
    private static final String FIELDS = "index,name,driver_version,utilization.gpu,utilization.memory,"
            + "memory.used,memory.total,temperature.gpu,power.draw,power.limit,fan.speed,clocks.gr,clocks.mem";

    /** Bovengrens voor het vermogen wanneer de driver geen limiet meldt (laptopkaarten). */
    private static final double MAX_PLAUSIBLE_WATT = 400;

    private final String executable;
    private Process process;

    public NvidiaSmiReader() {
        this.executable = findExecutable();
    }

    /**
     * Start het nvidia-smi-proces. {@code onSample} krijgt elke interval de gegevens van alle kaarten,
     * {@code onError} een melding bij onverwachte uitvoer of wanneer het proces onverwacht stopt.
     * Beide worden aangeroepen op de leesthread.
     */
    public synchronized void start(long intervalMs, Consumer<List<GpuStats>> onSample,
                                   Consumer<String> onError) throws IOException {
        stop();
        Process started;
        try {
            started = new ProcessBuilder(executable,
                    "--query-gpu=" + FIELDS, "--format=csv,noheader,nounits", "--loop-ms=" + intervalMs)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new IOException("nvidia-smi not found – is the NVIDIA driver installed?", e);
        }
        process = started;
        Thread thread = new Thread(() -> readLoop(started, onSample, onError), "nvidia-smi-reader");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stopt het proces, zodat de kaart weer in slaap kan gaan. */
    public synchronized void stop() {
        if (process != null) {
            Process p = process;
            process = null;
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
        }
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    private synchronized boolean isCurrent(Process p) {
        return process == p;
    }

    private void readLoop(Process p, Consumer<List<GpuStats>> onSample, Consumer<String> onError) {
        Map<Integer, GpuStats> batch = new LinkedHashMap<>();
        Set<Integer> seen = new HashSet<>();
        int expected = 0; // aantal kaarten per interval, gekend na het eerste volledige interval
        int lastIndex = -1;
        boolean delivered = false;
        String lastOutput = "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                lastOutput = line;
                GpuStats stats;
                try {
                    // de eerste meting na het ontwaken geeft soms onzin voor het vermogen (bv. 751 W)
                    stats = parse(line, seen.add(parseIndex(line)));
                } catch (IOException e) {
                    if (isCurrent(p)) {
                        onError.accept(e.getMessage());
                    }
                    continue;
                }
                // een kaart die al in de reeks zit (of een lager nummer) betekent: volgend interval
                if (!batch.isEmpty() && (batch.containsKey(stats.index()) || stats.index() < lastIndex)) {
                    expected = Math.max(expected, batch.size());
                    if (!delivered) {
                        deliver(p, batch, onSample);
                    }
                    batch.clear();
                }
                batch.put(stats.index(), stats);
                lastIndex = stats.index();
                delivered = false;
                // zolang het aantal kaarten niet gekend is: doorsturen zodra nvidia-smi even niets meer schrijft
                if (expected > 0 ? batch.size() >= expected : !reader.ready()) {
                    deliver(p, batch, onSample);
                    delivered = true;
                    if (expected > 0) {
                        batch.clear();
                    }
                }
            }
        } catch (IOException e) {
            lastOutput = e.getMessage();
        }
        if (isCurrent(p)) {
            onError.accept("nvidia-smi stopped unexpectedly: " + lastOutput);
        }
    }

    private void deliver(Process p, Map<Integer, GpuStats> batch, Consumer<List<GpuStats>> onSample) {
        if (isCurrent(p)) {
            onSample.accept(List.copyOf(batch.values()));
        }
    }

    private static int parseIndex(String line) {
        try {
            return Integer.parseInt(line.split(",", 2)[0].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static GpuStats parse(String line) throws IOException {
        return parse(line, false);
    }

    /** @param firstSample de eerste meting van deze kaart sinds het proces gestart is */
    static GpuStats parse(String line, boolean firstSample) throws IOException {
        String[] p = line.split(",\\s*");
        if (p.length < 13) {
            throw new IOException("Onverwachte uitvoer van nvidia-smi: " + line);
        }
        double powerDraw = num(p[8]);
        double powerLimit = num(p[9]);
        // Na het ontwaken uit D3cold geeft de vermogenssensor eerst een willekeurige waarde
        double maxPlausible = powerLimit > 0 ? powerLimit * 1.5 : MAX_PLAUSIBLE_WATT;
        if (firstSample || powerDraw > maxPlausible) {
            powerDraw = -1;
        }
        return new GpuStats((int) num(p[0]), p[1].trim(), p[2].trim(),
                num(p[3]), num(p[4]), num(p[5]), num(p[6]), num(p[7]),
                powerDraw, powerLimit, num(p[10]), num(p[11]), num(p[12]), false);
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return -1; // "[N/A]", "[Not Supported]" of "[Unknown Error]"
        }
    }

    private static String findExecutable() {
        // vaste plaatsen (een app gestart via snelkoppeling of autostart heeft soms een beperkt PATH)
        String[] candidates = {
                "C:\\Windows\\System32\\nvidia-smi.exe",
                "C:\\Program Files\\NVIDIA Corporation\\NVSMI\\nvidia-smi.exe",
                "/usr/bin/nvidia-smi",
                "/usr/local/bin/nvidia-smi"
        };
        for (String candidate : candidates) {
            if (new java.io.File(candidate).isFile()) {
                return candidate;
            }
        }
        return "nvidia-smi"; // via PATH
    }
}

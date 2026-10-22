package nvidiamonitor.service;

import nvidiamonitor.model.GpuStats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Leest GPU-gegevens uit via de nvidia-smi command line tool (wordt meegeleverd met de NVIDIA driver).
 */
public class NvidiaSmiReader {
    private static final String FIELDS = "index,name,driver_version,utilization.gpu,utilization.memory,"
            + "memory.used,memory.total,temperature.gpu,power.draw,power.limit,fan.speed,clocks.gr,clocks.mem";

    private final String executable;

    public NvidiaSmiReader() {
        this.executable = findExecutable();
    }

    /** Leest alle NVIDIA-kaarten in het systeem (één regel per kaart). */
    public List<GpuStats> read() throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(executable,
                    "--query-gpu=" + FIELDS, "--format=csv,noheader,nounits")
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new IOException("nvidia-smi not found – is the NVIDIA driver installed?", e);
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("nvidia-smi reageert niet");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Onderbroken", e);
        }
        if (lines.isEmpty() || process.exitValue() != 0) {
            throw new IOException("nvidia-smi gaf een fout: " + String.join(" ", lines));
        }
        List<GpuStats> result = new ArrayList<>();
        for (String line : lines) {
            result.add(parse(line));
        }
        return result;
    }

    static GpuStats parse(String line) throws IOException {
        String[] p = line.split(",\\s*");
        if (p.length < 13) {
            throw new IOException("Onverwachte uitvoer van nvidia-smi: " + line);
        }
        return new GpuStats((int) num(p[0]), p[1].trim(), p[2].trim(),
                num(p[3]), num(p[4]), num(p[5]), num(p[6]), num(p[7]),
                num(p[8]), num(p[9]), num(p[10]), num(p[11]), num(p[12]));
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return -1; // "[N/A]" of "[Not Supported]"
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

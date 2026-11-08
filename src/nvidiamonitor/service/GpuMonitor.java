package nvidiamonitor.service;

import nvidiamonitor.model.GpuStats;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Volgt alle GPU's op een achtergrondthread en stuurt elke meting naar de listeners op de Swing-thread.
 * <p>
 * Op een Optimus-laptop schakelt Windows/Linux de NVIDIA-kaart helemaal uit (D3, 0 W) wanneer geen app
 * ze gebruikt. Elke nvidia-smi-opvraging maakt de kaart echter wakker (in P0, 25–30 W). Daarom twee modi:
 * <ul>
 *     <li><b>Actief</b>: één blijvend nvidia-smi-proces levert elke seconde een meting.</li>
 *     <li><b>Slaap</b>: na {@value #IDLE_SAMPLES_BEFORE_SLEEP} metingen zonder belasting en zonder bezet
 *     geheugen stopt nvidia-smi, zodat de kaart kan gaan slapen. Daarna wordt enkel de D-state bij het
 *     besturingssysteem nagekeken (wekt de kaart niet). Maakt een andere app de kaart wakker (D0),
 *     dan start nvidia-smi opnieuw.</li>
 * </ul>
 * Kan de D-state niet gelezen worden (Java 21, macOS, oudere Linux-kernel), dan blijft de monitor actief.
 */
public class GpuMonitor {
    private static final int IDLE_SAMPLES_BEFORE_SLEEP = 5;
    /** Zo lang wachten we tot de kaart effectief in D3 gaat; lukt dat niet, dan houdt iets anders ze wakker. */
    private static final long D3_TIMEOUT_MS = 20_000;
    /** Na zo'n mislukte poging pas na deze tijd opnieuw proberen. */
    private static final long SLEEP_RETRY_MS = 5 * 60_000;
    private static final long RESTART_DELAY_MS = 5_000;

    private final NvidiaSmiReader reader = new NvidiaSmiReader();
    private final GpuPowerState powerState = GpuPowerState.forCurrentPlatform();
    private final List<Consumer<List<GpuStats>>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> errorListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gpu-monitor");
        t.setDaemon(true);
        return t;
    });

    // enkel gebruikt op de scheduler-thread
    private long intervalMs;
    private List<GpuStats> lastStats = List.of();
    private boolean sleeping;
    private boolean reachedD3;
    private int idleSamples;
    private long sleepingSince;
    private long sleepAllowedAt;
    private long restartAt;

    public void addListener(Consumer<List<GpuStats>> listener) {
        listeners.add(listener);
    }

    public void addErrorListener(Consumer<String> listener) {
        errorListeners.add(listener);
    }

    public void start(long intervalMs) {
        this.intervalMs = intervalMs;
        scheduler.execute(this::startReader);
        scheduler.scheduleWithFixedDelay(this::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
        reader.stop();
    }

    private void startReader() {
        idleSamples = 0;
        try {
            reader.start(intervalMs,
                    stats -> scheduler.execute(() -> onSample(stats)),
                    message -> scheduler.execute(() -> onReaderError(message)));
        } catch (IOException e) {
            onReaderError(e.getMessage());
        }
    }

    private void onReaderError(String message) {
        restartAt = System.currentTimeMillis() + RESTART_DELAY_MS;
        publishError(message);
    }

    private void onSample(List<GpuStats> stats) {
        if (sleeping) {
            return; // nog een meting van net voor het stoppen
        }
        lastStats = stats;
        publish(stats);
        boolean idle = !stats.isEmpty() && stats.stream().allMatch(GpuStats::idle);
        idleSamples = idle ? idleSamples + 1 : 0;
        if (idleSamples >= IDLE_SAMPLES_BEFORE_SLEEP
                && System.currentTimeMillis() >= sleepAllowedAt
                && powerState.read() != GpuPowerState.State.UNKNOWN) {
            enterSleep();
        }
    }

    private void enterSleep() {
        reader.stop();
        sleeping = true;
        reachedD3 = false;
        sleepingSince = System.currentTimeMillis();
        publishSleeping();
    }

    private void wake() {
        sleeping = false;
        startReader();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (!sleeping) {
            if (!reader.isRunning() && now >= restartAt) {
                startReader();
            }
            return;
        }
        publishSleeping();
        switch (powerState.read()) {
            case ASLEEP -> reachedD3 = true;
            case AWAKE -> {
                if (reachedD3) {
                    wake(); // een andere app gebruikt de kaart
                } else if (now - sleepingSince > D3_TIMEOUT_MS) {
                    sleepAllowedAt = now + SLEEP_RETRY_MS;
                    wake();
                }
            }
            case UNKNOWN -> wake();
        }
    }

    private void publishSleeping() {
        publish(lastStats.stream().map(GpuStats::asSleeping).toList());
    }

    private void publish(List<GpuStats> stats) {
        SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.accept(stats)));
    }

    private void publishError(String message) {
        SwingUtilities.invokeLater(() -> errorListeners.forEach(l -> l.accept(message)));
    }
}

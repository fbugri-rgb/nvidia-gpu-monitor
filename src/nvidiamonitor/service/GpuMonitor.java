package nvidiamonitor.service;

import nvidiamonitor.model.GpuStats;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Vraagt elke seconde de gegevens van alle GPU's op op een achtergrondthread
 * en stuurt het resultaat naar de listeners op de Swing-thread.
 */
public class GpuMonitor {
    private final NvidiaSmiReader reader = new NvidiaSmiReader();
    private final List<Consumer<List<GpuStats>>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> errorListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gpu-monitor");
        t.setDaemon(true);
        return t;
    });

    public void addListener(Consumer<List<GpuStats>> listener) {
        listeners.add(listener);
    }

    public void addErrorListener(Consumer<String> listener) {
        errorListeners.add(listener);
    }

    public void start(long intervalMs) {
        scheduler.scheduleWithFixedDelay(this::poll, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void poll() {
        try {
            List<GpuStats> stats = reader.read();
            SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.accept(stats)));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> errorListeners.forEach(l -> l.accept(e.getMessage())));
        }
    }
}

package nvidiamonitor.model;

/**
 * Eén meting van de GPU. Waarden die de driver niet kent (bv. fan op een laptop) zijn -1.
 * {@code asleep} is true wanneer de kaart in diepe slaap (D3, 0 W) staat en niet uitgelezen wordt.
 */
public record GpuStats(
        int index,            // nummer van de kaart volgens nvidia-smi
        String name,
        String driverVersion,
        double gpuUsage,      // %
        double memoryUsage,   // % (geheugencontroller)
        double memoryUsedMb,
        double memoryTotalMb,
        double temperature,   // °C
        double powerDraw,     // W
        double powerLimit,    // W
        double fanSpeed,      // %
        double coreClock,     // MHz
        double memoryClock,   // MHz
        boolean asleep
) {
    public double memoryPercent() {
        return memoryTotalMb > 0 ? memoryUsedMb / memoryTotalMb * 100 : 0;
    }

    /** Geen andere app gebruikt de kaart: geen belasting en geen bezet geheugen. */
    public boolean idle() {
        return gpuUsage <= 0 && memoryUsedMb == 0;
    }

    /**
     * Momentopname voor een slapende kaart: vaste gegevens en de laatst gemeten temperatuur blijven
     * behouden (zoals Taakbeheer), belasting, vermogen en kloksnelheden zijn 0.
     */
    public GpuStats asSleeping() {
        return new GpuStats(index, name, driverVersion, 0, 0, 0, memoryTotalMb, temperature,
                0, powerLimit, -1, 0, 0, true);
    }

    public GpuStats withPowerDraw(double watt) {
        return new GpuStats(index, name, driverVersion, gpuUsage, memoryUsage, memoryUsedMb, memoryTotalMb,
                temperature, watt, powerLimit, fanSpeed, coreClock, memoryClock, asleep);
    }

    public static boolean known(double value) {
        return value >= 0;
    }
}

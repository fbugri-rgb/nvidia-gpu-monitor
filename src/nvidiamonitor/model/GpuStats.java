package nvidiamonitor.model;

/**
 * Eén meting van de GPU. Waarden die de driver niet kent (bv. fan op een laptop) zijn -1.
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
        double memoryClock    // MHz
) {
    public double memoryPercent() {
        return memoryTotalMb > 0 ? memoryUsedMb / memoryTotalMb * 100 : 0;
    }

    public static boolean known(double value) {
        return value >= 0;
    }
}

package nvidiamonitor.service;

/**
 * Windows: de echte versie staat in src-java22/ en gebruikt de Foreign Function &amp; Memory API
 * (pas definitief sinds Java 22). De jar is een multi-release jar: op Java 22+ wordt die versie geladen,
 * op Java 21 deze, en dan werkt de app gewoon zonder slaapstand.
 */
final class WindowsPowerState {
    private WindowsPowerState() {
    }

    static GpuPowerState create() {
        return GpuPowerState.NONE;
    }
}

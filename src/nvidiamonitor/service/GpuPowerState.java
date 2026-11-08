package nvidiamonitor.service;

/**
 * Leest de PCIe-energietoestand (D-state) van de NVIDIA-kaart(en) bij het besturingssysteem.
 * Dat gebeurt zonder de kaart zelf aan te spreken, dus een slapende kaart (D3, 0 W) blijft slapen.
 * Hiermee merkt {@link GpuMonitor} dat een andere app de kaart wakker gemaakt heeft.
 */
public interface GpuPowerState {
    enum State {
        /** minstens één NVIDIA-kaart staat aan (D0) */
        AWAKE,
        /** alle NVIDIA-kaarten slapen (D1–D3) */
        ASLEEP,
        /** niet uit te lezen op dit systeem */
        UNKNOWN
    }

    State read();

    /** Werkt niet op dit systeem: de kaart wordt dan nooit losgelaten. */
    GpuPowerState NONE = () -> State.UNKNOWN;

    static GpuPowerState forCurrentPlatform() {
        try {
            return switch (Platform.current()) {
                case WINDOWS -> WindowsPowerState.create();
                case LINUX -> LinuxPowerState.create();
                case MAC -> NONE;
            };
        } catch (Throwable e) {
            return NONE; // bv. geen native toegang: dan gewoon zonder slaapstand
        }
    }
}

package nvidiamonitor.model;

/**
 * De meters die de gebruiker kan tonen of verbergen.
 */
public enum Meter {
    GPU("GPU usage", "GPU"),
    MEMORY("Dedicated memory", "MEM"),
    TEMPERATURE("Temperature", "TEMP"),
    POWER("Power draw", "PWR");

    private final String label;
    private final String shortLabel;

    Meter(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }
}

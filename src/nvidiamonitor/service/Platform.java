package nvidiamonitor.service;

/**
 * Het besturingssysteem waarop de app draait.
 */
public enum Platform {
    WINDOWS, MAC, LINUX;

    public static Platform current() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return WINDOWS;
        if (os.contains("mac")) return MAC;
        return LINUX;
    }
}

package nvidiamonitor.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Linux: leest {@code /sys/bus/pci/devices/<kaart>/power_state} ("D0", "D3hot", "D3cold").
 * Dat bestand lezen maakt de kaart niet wakker.
 */
final class LinuxPowerState implements GpuPowerState {
    private static final Path PCI_DEVICES = Path.of("/sys/bus/pci/devices");
    private final List<Path> powerStateFiles;

    private LinuxPowerState(List<Path> powerStateFiles) {
        this.powerStateFiles = powerStateFiles;
    }

    static GpuPowerState create() {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> devices = Files.list(PCI_DEVICES)) {
            for (Path device : devices.toList()) {
                Path powerState = device.resolve("power_state");
                // NVIDIA (0x10de) en een beeldscherm-controller (klasse 0x03xxxx)
                if (read(device.resolve("vendor")).equals("0x10de")
                        && read(device.resolve("class")).startsWith("0x03")
                        && Files.isReadable(powerState)) {
                    files.add(powerState);
                }
            }
        } catch (IOException e) {
            return NONE;
        }
        return files.isEmpty() ? NONE : new LinuxPowerState(List.copyOf(files));
    }

    @Override
    public State read() {
        boolean any = false;
        for (Path file : powerStateFiles) {
            String state = read(file);
            if (state.equals("D0")) {
                return State.AWAKE;
            }
            any |= state.startsWith("D");
        }
        return any ? State.ASLEEP : State.UNKNOWN;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file).trim();
        } catch (IOException e) {
            return "";
        }
    }
}

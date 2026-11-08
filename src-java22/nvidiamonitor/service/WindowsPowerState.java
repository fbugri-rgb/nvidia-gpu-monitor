package nvidiamonitor.service;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Windows (Java 22+): leest {@code DEVPKEY_Device_PowerData} van de NVIDIA-kaart(en) via CfgMgr32.dll,
 * net als HWiNFO. Dat is de toestand die Windows zelf bijhoudt: de kaart wordt er niet voor gewekt.
 * Byte 4 van de {@code CM_POWER_DATA}-struct is {@code PD_MostRecentPowerState}: 1 = D0 … 4 = D3.
 */
final class WindowsPowerState implements GpuPowerState {
    private static final String DISPLAY_CLASS = "{4d36e968-e325-11ce-bfc1-08002be10318}";
    private static final int CM_GETIDLIST_FILTER_PRESENT = 0x100;
    private static final int CM_GETIDLIST_FILTER_CLASS = 0x200;
    private static final int CR_SUCCESS = 0;
    private static final int CR_BUFFER_SMALL = 0x1A;
    private static final long POWER_DATA_SIZE = 128; // CM_POWER_DATA is 56 bytes

    private final Arena arena = Arena.ofAuto();
    private final MethodHandle listSize;
    private final MethodHandle list;
    private final MethodHandle locate;
    private final MethodHandle getProperty;
    private final MemorySegment powerDataKey;
    private final MemorySegment propertyType = arena.allocate(JAVA_INT);
    private final MemorySegment bufferSize = arena.allocate(JAVA_INT);
    private final MemorySegment buffer = arena.allocate(POWER_DATA_SIZE);
    private List<Integer> devices = List.of();

    private WindowsPowerState() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup cfgmgr = SymbolLookup.libraryLookup("CfgMgr32.dll", arena);
        // CONFIGRET CM_Get_Device_ID_List_SizeW(PULONG pulLen, PCWSTR pszFilter, ULONG ulFlags)
        listSize = linker.downcallHandle(cfgmgr.find("CM_Get_Device_ID_List_SizeW").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        // CONFIGRET CM_Get_Device_ID_ListW(PCWSTR pszFilter, PZZWSTR Buffer, ULONG BufferLen, ULONG ulFlags)
        list = linker.downcallHandle(cfgmgr.find("CM_Get_Device_ID_ListW").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
        // CONFIGRET CM_Locate_DevNodeW(PDEVINST pdnDevInst, DEVINSTID_W pDeviceID, ULONG ulFlags)
        locate = linker.downcallHandle(cfgmgr.find("CM_Locate_DevNodeW").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        // CONFIGRET CM_Get_DevNode_PropertyW(DEVINST, const DEVPROPKEY*, DEVPROPTYPE*, PBYTE, PULONG, ULONG)
        getProperty = linker.downcallHandle(cfgmgr.find("CM_Get_DevNode_PropertyW").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        // DEVPKEY_Device_PowerData = {A45C254E-DF1C-4EFD-8020-67D146A850E0}, 32 (devpkey.h)
        powerDataKey = arena.allocate(20);
        powerDataKey.set(ValueLayout.JAVA_INT_UNALIGNED, 0, 0xA45C254E);
        powerDataKey.set(ValueLayout.JAVA_SHORT_UNALIGNED, 4, (short) 0xDF1C);
        powerDataKey.set(ValueLayout.JAVA_SHORT_UNALIGNED, 6, (short) 0x4EFD);
        byte[] data4 = {(byte) 0x80, 0x20, 0x67, (byte) 0xD1, 0x46, (byte) 0xA8, 0x50, (byte) 0xE0};
        MemorySegment.copy(data4, 0, powerDataKey, ValueLayout.JAVA_BYTE, 8, data4.length);
        powerDataKey.set(ValueLayout.JAVA_INT_UNALIGNED, 16, 32);
    }

    static GpuPowerState create() throws Throwable {
        WindowsPowerState state = new WindowsPowerState();
        state.findDevices();
        return state.devices.isEmpty() ? NONE : state;
    }

    @Override
    public State read() {
        try {
            State state = readDevices();
            if (state == State.UNKNOWN) {
                findDevices(); // bv. na een driverupdate krijgt de kaart een nieuwe devnode
                state = readDevices();
            }
            return state;
        } catch (Throwable e) {
            return State.UNKNOWN;
        }
    }

    private State readDevices() throws Throwable {
        boolean any = false;
        for (int device : devices) {
            bufferSize.set(JAVA_INT, 0, (int) POWER_DATA_SIZE);
            int result = (int) getProperty.invokeExact(device, powerDataKey, propertyType, buffer, bufferSize, 0);
            if (result != CR_SUCCESS) {
                continue;
            }
            int dState = buffer.get(ValueLayout.JAVA_INT_UNALIGNED, 4); // PD_MostRecentPowerState
            if (dState == 1) {
                return State.AWAKE;
            }
            any |= dState >= 2 && dState <= 4;
        }
        return any ? State.ASLEEP : State.UNKNOWN;
    }

    /** Zoekt alle aanwezige NVIDIA-beeldschermadapters (PCI\VEN_10DE…). */
    private void findDevices() throws Throwable {
        List<Integer> found = new ArrayList<>();
        try (Arena call = Arena.ofConfined()) {
            MemorySegment filter = call.allocateFrom(DISPLAY_CLASS, StandardCharsets.UTF_16LE);
            int flags = CM_GETIDLIST_FILTER_CLASS | CM_GETIDLIST_FILTER_PRESENT;
            MemorySegment ids;
            int result;
            do {
                MemorySegment length = call.allocate(JAVA_INT);
                result = (int) listSize.invokeExact(length, filter, flags);
                if (result != CR_SUCCESS) {
                    return;
                }
                int chars = length.get(JAVA_INT, 0);
                ids = call.allocate((long) chars * 2);
                result = (int) list.invokeExact(filter, ids, chars, flags);
            } while (result == CR_BUFFER_SMALL); // er kwam net een apparaat bij
            if (result != CR_SUCCESS) {
                return;
            }
            // lijst van strings gescheiden door \0, afgesloten met een lege string
            long offset = 0;
            while (offset < ids.byteSize()) {
                String id = ids.getString(offset, StandardCharsets.UTF_16LE);
                if (id.isEmpty()) {
                    break;
                }
                offset += (id.length() + 1) * 2L;
                if (id.toUpperCase().startsWith("PCI\\VEN_10DE")) {
                    MemorySegment devInst = call.allocate(JAVA_INT);
                    MemorySegment idSegment = call.allocateFrom(id, StandardCharsets.UTF_16LE);
                    if ((int) locate.invokeExact(devInst, idSegment, 0) == CR_SUCCESS) {
                        found.add(devInst.get(JAVA_INT, 0));
                    }
                }
            }
        }
        devices = List.copyOf(found);
    }
}

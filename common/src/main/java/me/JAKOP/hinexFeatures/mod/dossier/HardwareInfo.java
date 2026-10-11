package me.JAKOP.hinexFeatures.mod.dossier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

public final class HardwareInfo {

    private static final CompletableFuture<JsonObject> INFO = new CompletableFuture<>();
    private static volatile String hwid;

    private HardwareInfo() {
    }

    public static void start() {
        Thread thread = new Thread(() -> {
            try {
                INFO.complete(collect());
            } catch (Throwable t) {
                HinexFeaturesClient.LOGGER.warn("Не удалось опросить железо", t);
                INFO.complete(new JsonObject());
            }
        }, "HinexFeatures-Hardware");
        thread.setDaemon(true);
        thread.start();
    }

    public static JsonObject now() {
        return INFO.getNow(new JsonObject()).deepCopy();
    }

    public static String hwid() {
        return hwid;
    }

    public static void whenReady(Runnable task) {
        INFO.whenComplete((info, error) -> task.run());
    }

    private static JsonObject collect() throws Exception {
        SystemInfo system = new SystemInfo();
        HardwareAbstractionLayer hardware = system.getHardware();
        JsonObject info = new JsonObject();

        CentralProcessor processor = hardware.getProcessor();
        JsonObject cpu = new JsonObject();
        cpu.addProperty("model", processor.getProcessorIdentifier().getName().trim());
        cpu.addProperty("vendor", processor.getProcessorIdentifier().getVendor());
        cpu.addProperty("cores", processor.getPhysicalProcessorCount());
        cpu.addProperty("threads", processor.getLogicalProcessorCount());
        cpu.addProperty("maxFreqMhz", processor.getMaxFreq() / 1_000_000L);
        info.add("cpu", cpu);

        info.addProperty("totalMemoryMb", hardware.getMemory().getTotal() / 1_048_576L);

        JsonArray cards = new JsonArray();
        for (GraphicsCard card : hardware.getGraphicsCards()) {
            JsonObject gpu = new JsonObject();
            gpu.addProperty("name", card.getName());
            gpu.addProperty("vendor", card.getVendor());
            gpu.addProperty("vramMb", card.getVRam() / 1_048_576L);
            gpu.addProperty("driver", card.getVersionInfo());
            cards.add(gpu);
        }
        info.add("gpuCards", cards);

        ComputerSystem computer = hardware.getComputerSystem();
        JsonObject pc = new JsonObject();
        pc.addProperty("manufacturer", computer.getManufacturer());
        pc.addProperty("model", computer.getModel());
        pc.addProperty("baseboard", computer.getBaseboard().getManufacturer() + " " + computer.getBaseboard().getModel());
        info.add("computer", pc);

        OperatingSystem os = system.getOperatingSystem();
        JsonObject osInfo = new JsonObject();
        osInfo.addProperty("family", os.getFamily());
        osInfo.addProperty("build", os.getVersionInfo().toString());
        osInfo.addProperty("bitness", os.getBitness());
        osInfo.addProperty("elevated", os.isElevated());
        info.add("osDetails", osInfo);

        hwid = sha256(processor.getProcessorIdentifier().getProcessorID()
                + "|" + computer.getHardwareUUID()
                + "|" + computer.getBaseboard().getSerialNumber()
                + "|" + computer.getSerialNumber());
        return info;
    }

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash, 0, 16);
    }
}

package services;

import com.google.gson.*;
import managers.HardwareSettingsManager;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.print.*;

/** Desktop USB host polls the authenticated store API; no inbound workstation port is required. */
public final class SharedLabelPrinting {
    private SharedLabelPrinting() { }
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "smartstock-label-host"); t.setDaemon(true); return t;
    });
    private static boolean started;
    private static JsonObject acknowledgement;
    public static synchronized void start() {
        if (started) return;
        started = true;
        WORKER.scheduleWithFixedDelay(SharedLabelPrinting::tick, 2, 3, TimeUnit.SECONDS);
    }
    private static void tick() {
        try {
            if (acknowledgement != null) {
                try { LanApiClient.sharedLabelPrinter(acknowledgement, false); }
                catch (LanApiClient.LanApiException ex) {
                    if (!"INVALID_LABEL_REQUEST".equals(ex.code())) throw ex;
                    // An expired job must not block the host's new jobs forever.
                }
                acknowledgement = null;
            }
            if (!HardwareSettingsManager.hostsSharedLabelPrinter()) return;
            JsonObject response = LanApiClient.sharedLabelPrinter(request("POLL"), false);
            if (!response.has("queue")) { HardwareSettingsManager.saveHostsSharedLabelPrinter(false); return; }
            if (!response.has("job")) return;
            JsonObject job = response.getAsJsonObject("job");
            JsonObject done = request("FINISH"); done.addProperty("id", job.get("id").getAsString());
            try {
                printLocal(response.get("queue").getAsString(), job.getAsJsonObject("payload"));
                done.addProperty("success", true); done.addProperty("message", "Accepted by the host print spooler; check physical output.");
            } catch (Exception ex) {
                done.addProperty("success", false); done.addProperty("message", "Print result may be uncertain. Check the printer before reprinting. " + ex.getClass().getSimpleName());
            }
            acknowledgement = done;
        } catch (Exception ex) {
            // Offline, logged out or not enrolled yet. Never replay a claimed print job.
        }
    }
    public static JsonObject request(String action) {
        JsonObject r = new JsonObject(); r.addProperty("action", action); return r;
    }
    public static boolean usesSharedPrinter() throws IOException { return HardwareSettingsManager.useSharedLabelPrinter(); }
    public static void submitOrder(BufferedImage image, int count) throws Exception {
        JsonObject payload = request("ORDER"); payload.addProperty("count", count);
        payload.add("images", encode(List.of(image))); submit(payload);
    }
    public static void submitPriceTags(List<BufferedImage> rows, double width, double height, double gap) throws Exception {
        JsonObject payload = request("PRICE"); payload.addProperty("count", 1);
        payload.addProperty("width", width); payload.addProperty("height", height); payload.addProperty("gap", gap);
        payload.add("images", encode(rows)); submit(payload);
    }
    private static void submit(JsonObject payload) throws Exception {
        validatePayload(payload);
        JsonObject request = request("SUBMIT"); request.addProperty("id", UUID.randomUUID().toString()); request.add("payload", payload);
        try { LanApiClient.sharedLabelPrinter(request, true); }
        catch (Exception ex) {
            throw new Exception("Label submission was not confirmed. Check Shared CT221B Setup / Status for job "
                    + request.get("id").getAsString().substring(0, 8) + " and check physical output before submitting again. " + ex.getMessage(), ex);
        }
    }
    private static JsonArray encode(List<BufferedImage> images) throws IOException {
        JsonArray array = new JsonArray();
        for (BufferedImage image : images) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out);
            array.add(Base64.getEncoder().encodeToString(out.toByteArray()));
        }
        return array;
    }
    static List<BufferedImage> validatePayload(JsonObject payload) throws Exception {
        String kind = payload.get("action").getAsString();
        if (!Set.of("ORDER", "PRICE").contains(kind)) throw new IllegalArgumentException("Unsupported label job.");
        int count = payload.get("count").getAsInt();
        if (count < 1 || count > 100) throw new IllegalArgumentException("Print 1 to 100 labels at a time.");
        if ("PRICE".equals(kind)) {
            double width = payload.get("width").getAsDouble(), height = payload.get("height").getAsDouble(), gap = payload.get("gap").getAsDouble();
            if (!Double.isFinite(width + height + gap) || width < 0.9 || width > 2.15 || height < 0.1 || height > 10 || gap < 0 || gap > 0.5 || count != 1)
                throw new IllegalArgumentException("Invalid CT221B label dimensions.");
        }
        JsonArray images = payload.getAsJsonArray("images");
        if (images == null || images.isEmpty() || images.size() > 100 || ("ORDER".equals(kind) && images.size() != 1)
                || payload.toString().length() > 1500000) throw new IllegalArgumentException("Label job is too large. Print a smaller batch.");
        List<BufferedImage> decoded = new ArrayList<>(); long pixels = 0;
        for (JsonElement element : images) {
            byte[] bytes = Base64.getDecoder().decode(element.getAsString());
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("Invalid label image.");
                var reader = readers.next();
                try {
                    reader.setInput(input); int w = reader.getWidth(0), h = reader.getHeight(0); pixels += (long) w * h;
                    if (!"png".equalsIgnoreCase(reader.getFormatName()) || w < 1 || h < 1 || w > 1200 || h > 2400 || pixels > 10000000)
                        throw new IllegalArgumentException("Label images exceed the print limit.");
                    decoded.add(reader.read(0));
                } finally { reader.dispose(); }
            }
        }
        return decoded;
    }
    private static void printLocal(String queue, JsonObject payload) throws Exception {
        PrintService service = HardwareSettingsManager.findPrintService(queue);
        if (service == null) throw new PrintException("The shared CT221B USB queue is unavailable.");
        List<BufferedImage> images = validatePayload(payload);
        if ("ORDER".equals(payload.get("action").getAsString())) {
            Receipt.CustomOrderLabelPrinter.printSharedImage(images.get(0), payload.get("count").getAsInt(), service);
        } else {
            byte[] content = PriceTagPrintService.formatCt221bGapJob(images, payload.get("width").getAsDouble(),
                    payload.get("height").getAsDouble(), payload.get("gap").getAsDouble());
            service.createPrintJob().print(new SimpleDoc(content, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
        }
    }
}

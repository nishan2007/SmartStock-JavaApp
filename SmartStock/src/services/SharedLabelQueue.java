package services;

import com.google.gson.*;
import data.EnvironmentProfile;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import utils.SecureFilePermissions;

/** Store-local spool. Claimed jobs are never automatically replayed after an uncertain print. */
final class SharedLabelQueue {
    private static final class Holder {
        static final SharedLabelQueue INSTANCE = new SharedLabelQueue(
                EnvironmentProfile.active().file("shared-label-printer"), Clock.systemUTC());
    }
    static SharedLabelQueue current() { return Holder.INSTANCE; }
    private final Path root;
    private final Clock clock;
    private final Map<Integer, Long> heartbeats = new HashMap<>();
    SharedLabelQueue(Path root, Clock clock) { this.root = root; this.clock = clock; }
    private Path folder(int store) throws Exception {
        if (store < 1) throw new IllegalArgumentException("A store is required.");
        Path p = root.resolve("store-" + store);
        Files.createDirectories(p);
        SecureFilePermissions.restrictDirectoryToOwner(root);
        SecureFilePermissions.restrictDirectoryToOwner(p);
        return p;
    }
    private JsonObject read(Path path) throws Exception {
        return Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : new JsonObject();
    }
    private void write(Path path, JsonObject value) throws Exception {
        Path temp = Files.createTempFile(path.getParent(), ".label-", ".tmp");
        try {
            Files.writeString(temp, value.toString());
            SecureFilePermissions.restrictFileToOwner(temp);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }
    synchronized JsonObject status(int store) throws Exception {
        JsonObject result = read(folder(store).resolve("host.json"));
        result.addProperty("online", clock.millis() - heartbeats.getOrDefault(store, 0L) < 15000);
        JsonArray jobs = new JsonArray();
        for (Path path : jobs(store)) {
            JsonObject job = read(path);
            job.remove("payload");
            jobs.add(job);
        }
        result.add("jobs", jobs);
        return result;
    }
    private List<Path> jobs(int store) throws Exception {
        try (var paths = Files.list(folder(store))) {
            List<Path> result = new ArrayList<>();
            for (Path p : paths.filter(f -> f.getFileName().toString().startsWith("job-")).toList()) {
                JsonObject job = read(p); long age = clock.millis() - job.get("createdAt").getAsLong();
                if (age > 86400000L) { Files.deleteIfExists(p); continue; }
                String state = job.get("state").getAsString();
                if (age > 600000L && ("QUEUED".equals(state) || "SENDING".equals(state))) {
                    job.addProperty("state", "QUEUED".equals(state) ? "EXPIRED" : "UNKNOWN");
                    job.addProperty("message", "Job expired. Check physical output before submitting again.");
                    job.remove("payload"); write(p, job);
                }
                result.add(p);
            }
            result.sort(Comparator.comparing(Path::toString)); return result;
        }
    }
    synchronized JsonObject configure(int store, String device, String name, String queue, boolean enabled) throws Exception {
        if (enabled && (name == null || name.isBlank() || name.length() > 80 || queue == null || queue.length() > 250
                || !Receipt.CustomOrderLabelPrinter.isCt221b(queue)))
            throw new IllegalArgumentException("Choose the local CT221B queue and enter a host name.");
        JsonObject previous = read(folder(store).resolve("host.json"));
        if (previous.has("device") && (!enabled || !previous.get("device").getAsString().equals(device)
                || !previous.get("queue").getAsString().equals(queue))) {
            for (Path p : jobs(store)) {
                JsonObject job = read(p);
                if ("QUEUED".equals(job.get("state").getAsString())) {
                    job.addProperty("state", "CANCELLED"); job.remove("payload"); write(p, job);
                }
            }
        }
        JsonObject host = new JsonObject();
        if (enabled) {
            if (name == null || name.isBlank() || name.length() > 80 || queue == null || queue.isBlank() || queue.length() > 250)
                throw new IllegalArgumentException("Choose the local CT221B queue and enter a host name.");
            host.addProperty("device", device); host.addProperty("name", name); host.addProperty("queue", queue);
        }
        write(folder(store).resolve("host.json"), host);
        heartbeats.remove(store);
        return status(store);
    }
    synchronized JsonObject enqueue(int store, String device, String id, JsonObject payload) throws Exception {
        UUID.fromString(id);
        Path path = folder(store).resolve("job-" + id + ".json");
        if (Files.exists(path)) {
            JsonObject prior = read(path);
            if (!device.equals(prior.get("submittedBy").getAsString())) throw new IllegalArgumentException("Job ID already used.");
            prior.remove("payload"); return prior;
        }
        JsonObject host = status(store);
        if (!host.has("device") || !host.get("online").getAsBoolean())
            throw new IllegalStateException("The shared label printer host is offline. Open SmartStock on that computer and check the printer.");
        try { SharedLabelPrinting.validatePayload(payload); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid label images or print dimensions. Print a smaller valid batch.", ex); }
        List<Path> files = jobs(store);
        long pending = 0;
        for (Path p : files) {
            JsonObject job = read(p);
            if ("QUEUED".equals(job.get("state").getAsString())) pending++;
            else if (clock.millis() - job.get("createdAt").getAsLong() > 86400000L) Files.deleteIfExists(p);
        }
        if (pending >= 50) throw new IllegalStateException("The label queue is full. Check the host before submitting more labels.");
        JsonObject job = new JsonObject(); job.addProperty("id", id); job.addProperty("submittedBy", device);
        job.addProperty("createdAt", clock.millis()); job.addProperty("state", "QUEUED"); job.add("payload", payload.deepCopy());
        write(path, job); job.remove("payload"); return job;
    }
    synchronized JsonObject poll(int store, String device) throws Exception {
        JsonObject host = read(folder(store).resolve("host.json"));
        if (!host.has("device") || !device.equals(host.get("device").getAsString())) return new JsonObject();
        heartbeats.put(store, clock.millis());
        JsonObject result = new JsonObject(); result.addProperty("queue", host.get("queue").getAsString());
        List<JsonObject> pending = new ArrayList<>();
        for (Path p : jobs(store)) {
            JsonObject job = read(p);
            if ("QUEUED".equals(job.get("state").getAsString())) pending.add(job);
        }
        pending.sort(Comparator.comparingLong(j -> j.get("createdAt").getAsLong()));
        if (!pending.isEmpty()) {
            JsonObject job = pending.get(0); job.addProperty("state", "SENDING");
            job.addProperty("claimedBy", device);
            write(folder(store).resolve("job-" + job.get("id").getAsString() + ".json"), job);
            result.add("job", job);
        }
        return result;
    }
    synchronized JsonObject finish(int store, String device, String id, boolean success, String message) throws Exception {
        UUID.fromString(id);
        Path path = folder(store).resolve("job-" + id + ".json"); JsonObject job = read(path);
        if (!job.has("claimedBy") || !device.equals(job.get("claimedBy").getAsString()))
            throw new IllegalArgumentException("This job was not assigned to this printer host.");
        if ("SENDING".equals(job.get("state").getAsString())) {
            job.addProperty("state", success ? "SPOOLED" : "FAILED");
            job.addProperty("message", message == null ? "" : message.substring(0, Math.min(message.length(), 300)));
            job.remove("payload"); write(path, job);
        }
        job.remove("payload"); return job;
    }
}

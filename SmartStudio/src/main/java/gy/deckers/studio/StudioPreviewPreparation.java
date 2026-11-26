package gy.deckers.studio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import publishing.Publication;
import publishing.PublicationLargeFiles;
import publishing.PublicationPreview;
import publishing.StudioDriveIdentity;

/** Offline migration utility. Adds previews without reserializing design models. */
public final class StudioPreviewPreparation {
    private StudioPreviewPreparation() {}

    public static void main(String[] args) {
        try {
            if (args.length != 3) throw new IOException("Usage: StudioPreviewPreparation <design> <private-recovery-folder> <verified-original-sha256>");
            System.out.println(Publication.JSON.toJson(prepare(Path.of(args[0]), Path.of(args[1]), args[2])));
        } catch (Exception error) { System.err.println(error.getMessage());System.exit(1); }
    }

    record Result(String status, String beforeSha256, String afterSha256, String backup) {}

    static Result prepare(Path source, Path recovery, String expected) throws Exception {
        source = source.toAbsolutePath().normalize();recovery = recovery.toAbsolutePath().normalize();
        if (!expected.matches("[a-f0-9]{64}")) throw new IOException("Supply the verified migration checksum.");
        plain(source, false);plain(recovery, true);
        if (source.startsWith(recovery)) throw new IOException("Recovery must be separate from the design.");
        boolean badge = source.toString().toLowerCase(Locale.ROOT).endsWith(".sstbadge");
        if (!badge && !source.toString().toLowerCase(Locale.ROOT).endsWith(".sspub")) throw new IOException("Unsupported design type.");
        String before = BadgeTemplateLibrary.fingerprint(source);
        JsonObject document = metadata(source, badge);
        if (validPreview(document)) return new Result("already-prepared", before, before, null);
        if (!before.equals(expected)) throw new IOException("Design changed since verified migration. Keep writes frozen and review it.");
        Path target = source, backup = recovery.resolve(UUID.randomUUID() + (badge ? ".sstbadge" : ".sspub"));
        Path cache = Files.createTempDirectory(recovery, "preview-assets-");
        try {
            StudioDocumentSave.write(target, expected, file -> {
                Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
                try (var channel = java.nio.channels.FileChannel.open(backup, StandardOpenOption.WRITE)) { channel.force(true); }
                if (!BadgeTemplateLibrary.fingerprint(backup).equals(expected)) throw new IOException("Recovery copy failed verification.");
                StudioDriveIdentity identity = StudioDriveIdentity.fromFile(file);
                if (identity != null) document.add("smartDrive", Publication.JSON.toJsonTree(identity));
                String encoded;
                if (badge) {
                    var image = BadgeTemplate.load(file).render(Map.of(), null, 72);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();ImageIO.write(image, "png", bytes);
                    if (bytes.size() > 1024 * 1024) throw new IOException("Badge preview exceeds 1 MiB.");
                    encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());
                } else {
                    Publication artwork = PublicationLargeFiles.read(file, cache);
                    encoded = JsonParser.parseString(new String(PublicationPreview.documentJson(artwork, artwork), StandardCharsets.UTF_8))
                            .getAsJsonObject().get("previewPng").getAsString();
                }
                document.addProperty("previewPng", encoded);
                byte[] json = Publication.JSON.toJson(document).getBytes(StandardCharsets.UTF_8);
                if (json.length > (badge ? BadgeTemplate.MAX_BYTES : PublicationLargeFiles.MAX_JSON_BYTES)) throw new IOException("Prepared metadata exceeds the format limit.");
                Path staged = Files.createTempFile(file.getParent(), ".studio-preview-", ".partial");
                try {
                    if (badge) Files.write(staged, json);
                    else archive(file, staged, json);
                    try (var channel = java.nio.channels.FileChannel.open(staged, StandardOpenOption.WRITE)) { channel.force(true); }
                    if (!BadgeTemplateLibrary.fingerprint(file).equals(expected)) throw new IOException("Design changed while preparing its preview.");
                    Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(staged); }
            });
            return new Result("prepared", before, BadgeTemplateLibrary.fingerprint(target), backup.toString());
        } finally {
            // Only this invocation's generated asset cache; never staff files or recovery copies.
            try (var paths = Files.walk(cache)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void plain(Path path, boolean directory) throws IOException {
        if (directory ? !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) : !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Expected a plain design file or recovery directory.");
        for (Path part = path; part != null; part = part.getParent()) if (Files.isSymbolicLink(part)) throw new IOException("Linked preparation path.");
    }

    private static JsonObject metadata(Path source, boolean badge) throws IOException {
        int limit = badge ? BadgeTemplate.MAX_BYTES : PublicationLargeFiles.MAX_JSON_BYTES;
        if (Files.size(source) > (badge ? BadgeTemplate.MAX_BYTES : PublicationLargeFiles.MAX_BYTES)) throw new IOException("Design exceeds its format limit.");
        try {
            if (badge) try (InputStream input = Files.newInputStream(source)) { return json(input, limit); }
            try (ZipFile zip = new ZipFile(source.toFile())) {
                ZipEntry metadata = null;int entries = 0;
                for (var all = zip.entries(); all.hasMoreElements();) {
                    ZipEntry entry = all.nextElement();
                    if (++entries > 10001) throw new IOException("Too many archive entries.");
                    if (entry.getName().equals("publication.json")) {
                        if (metadata != null) throw new IOException("Duplicate publication metadata.");metadata = entry;
                    }
                }
                if (metadata == null) throw new IOException("Missing publication metadata.");
                try (InputStream input = zip.getInputStream(metadata)) { return json(input, limit); }
            }
        } catch (RuntimeException invalid) { throw new IOException("Invalid design metadata.", invalid); }
    }

    private static JsonObject json(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("Design metadata exceeds its limit.");
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static boolean validPreview(JsonObject document) {
        try {
            String value = document.get("previewPng").getAsString();
            if (value.length() > 1400000) return false;
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length < 24 || !HexFormat.of().formatHex(Arrays.copyOf(bytes, 8)).equals("89504e470d0a1a0a")) return false;
            var header = java.nio.ByteBuffer.wrap(bytes);
            if (header.getInt(16) < 1 || header.getInt(16) > 2048 || header.getInt(20) < 1 || header.getInt(20) > 2048) return false;
            return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
        } catch (RuntimeException | IOException invalid) { return false; }
    }

    private static void archive(Path source, Path staged, byte[] json) throws IOException {
        long total = 0;
        try (ZipFile zip = new ZipFile(source.toFile()); ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(staged))) {
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();output.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals("publication.json")) { output.write(json);total += json.length; }
                else try (InputStream input = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[65536];long size = 0;int count;
                    while ((count = input.read(buffer)) != -1) {
                        size += count;total += count;
                        if (size > PublicationLargeFiles.MAX_IMAGE_BYTES || total > PublicationLargeFiles.MAX_BYTES) throw new IOException("Archive expansion exceeds its limit.");
                        output.write(buffer, 0, count);
                    }
                }
                output.closeEntry();
            }
        }
        if (Files.size(staged) > PublicationLargeFiles.MAX_BYTES) throw new IOException("Prepared design exceeds 5 GiB.");
    }
}

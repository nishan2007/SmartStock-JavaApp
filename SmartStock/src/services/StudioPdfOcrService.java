package services;

import data.EnvironmentProfile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

final class StudioPdfOcrService {
    static final String PATH = "/v1/studio/pdf-ocr";
    static final String STATUS_PATH = PATH + "/status";
    static final int MAX_PNG_BYTES = 18 * 1024 * 1024;
    static final int MAX_BASE64_CHARS = 4 * ((MAX_PNG_BYTES + 2) / 3);
    private static final int MAX_PIXELS = 16_000_000;
    private static final int MAX_TSV_BYTES = 2 * 1024 * 1024;
    private static final Semaphore SLOT = new Semaphore(1);

    private StudioPdfOcrService() { }

    record Settings(String executable, String dataDirectory, String language) {
        static Settings load(Path config, StudioPdfOcrRuntime runtime) throws IOException {
            Properties properties = new Properties();
            if (Files.exists(config)) {
                try (var reader = Files.newBufferedReader(config)) {
                    properties.load(reader);
                }
            }
            String language = properties.getProperty("language", "eng").strip();
            if (runtime != null && StudioPdfOcrRuntime.supported() && language.equals("eng")
                    && !properties.containsKey("executable") && !properties.containsKey("dataDirectory")) {
                Settings managed = runtime.managedSettings();
                if (managed != null) return managed;
            }
            String executable = properties.getProperty("executable", defaultExecutable()).strip();
            String dataDirectory = properties.getProperty("dataDirectory", "").strip();
            if (executable.isBlank() || (!executable.equals("tesseract") && !Path.of(executable).isAbsolute())
                    || (!dataDirectory.isBlank() && !Path.of(dataDirectory).isAbsolute())
                    || !language.matches("[A-Za-z0-9_]+(?:\\+[A-Za-z0-9_]+)*")) {
                throw new IOException("Invalid server OCR settings.");
            }
            return new Settings(executable, dataDirectory, language);
        }

        private static String defaultExecutable() {
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null && !programFiles.isBlank()) {
                Path installed = Path.of(programFiles, "Tesseract-OCR", "tesseract.exe");
                if (Files.isRegularFile(installed)) return installed.toString();
            }
            return "tesseract";
        }
    }

    static Settings settings() throws IOException {
        try {
            return Settings.load(EnvironmentProfile.active().file("studio-ocr.properties"), StudioPdfOcrRuntime.current());
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid server OCR settings.", ex);
        }
    }

    static boolean tryStart() { return SLOT.tryAcquire(); }

    static void finish() { SLOT.release(); }

    static void check(Settings settings) throws IOException, InterruptedException {
        recognizeSample(settings);
    }

    static void checkEnglish(Settings settings) throws IOException, InterruptedException {
        if (recognizeSample(settings).lines().noneMatch(line -> line.startsWith("5\t") && line.endsWith("\tINVOICE")))
            throw new IOException("The installed OCR engine could not recognize the English test page.");
    }

    private static String recognizeSample(Settings settings) throws IOException, InterruptedException {
        BufferedImage sample = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        var graphics = sample.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, sample.getWidth(), sample.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(graphics.getFont().deriveFont(32f));
            graphics.drawString("INVOICE TOTAL", 12, 64);
        } finally {
            graphics.dispose();
        }
        return recognize(settings, sample, Duration.ofSeconds(15));
    }

    static BufferedImage decodePage(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length > MAX_PNG_BYTES) throw new IllegalArgumentException();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var reader = ImageIO.getImageReadersByFormatName("png").next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width > 32_767 || height > 32_767 || (long) width * height > MAX_PIXELS
                            || reader.getRawImageType(0).getColorModel().getComponentSize(0) > 8)
                        throw new IllegalArgumentException();
                    return reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("OCR requires a PNG page image of at most 16 megapixels and 18 MiB.");
        }
    }

    static String recognize(Settings settings, BufferedImage image, Duration timeout)
            throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("smartstock-ocr-page-");
        try {
            Path page = directory.resolve("page.png");
            Path output = directory.resolve("result");
            ImageIO.write(image, "png", page.toFile());
            List<String> args = new ArrayList<>(List.of(
                    settings.executable(), page.toString(), output.toString(), "-l", settings.language()));
            if (!settings.dataDirectory().isBlank())
                args.addAll(List.of("--tessdata-dir", settings.dataDirectory()));
            args.addAll(List.of("-c", "tessedit_create_tsv=1"));
            run(args, directory, timeout);
            try (var input = Files.newInputStream(directory.resolve("result.tsv"))) {
                byte[] tsv = input.readNBytes(MAX_TSV_BYTES + 1);
                if (tsv.length > MAX_TSV_BYTES) throw new IOException("OCR result is too large.");
                return new String(tsv, StandardCharsets.UTF_8);
            }
        } finally {
            List<Path> temporaryFiles;
            try (var files = Files.list(directory)) {
                temporaryFiles = files.toList();
            }
            for (Path path : temporaryFiles) deleteTemporary(path);
            deleteTemporary(directory);
        }
    }

    static void run(List<String> args, Path directory, Duration timeout)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(args).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("OMP_THREAD_LIMIT", "2");
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS))
                throw new IOException("Tesseract timed out.");
            if (process.exitValue() != 0)
                throw new IOException("Tesseract could not read the page with the configured language data.");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static void deleteTemporary(Path path) throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            for (int attempt = 0; ; attempt++) {
                try {
                    Files.deleteIfExists(path);
                    return;
                } catch (FileSystemException ex) {
                    if (attempt >= 20) throw ex;
                    // Windows can briefly hold a file open after process exit.
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException cancel) {
                        interrupted = true;
                    }
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}

package gy.deckers.studio;

import org.w3c.dom.Node;
import publishing.Publication;
import publishing.PublicationRenderer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

/** PNG pixels remain usable by existing badge renderers and template servers. */
final class BadgeEditableArtwork {
    private static final String PNG_METADATA = "javax_imageio_png_1.0";
    private static final String KEYWORD = "SmartStudio Badge Artwork";
    private static final int MAX_SOURCE = 2_000_000;

    enum Kind { CODE, WORD_ART, TABLE }

    record Source(Kind kind, List<Publication.Element> objects, StudioTables.Grid grid) {
        Source(Kind kind, List<Publication.Element> objects) { this(kind, objects, null); }
    }

    private BadgeEditableArtwork() {}

    static StudioImageFile.Loaded code(Publication.Element code) throws Exception {
        var artwork = BadgeCodeArtwork.create(code);
        return write(artwork.image(), artwork.name(), new Source(Kind.CODE, List.of(code)));
    }

    static StudioImageFile.Loaded wordArt(Publication.Element wordArt) throws IOException {
        Publication.Element object = StudioTables.copy(wordArt);
        object.x = 0;
        object.y = 0;
        return render("WordArt", object.width, object.height, List.of(object), new Source(Kind.WORD_ART, List.of(object)), 0);
    }

    static StudioImageFile.Loaded table(StudioTables.Grid grid) throws IOException {
        var objects = grid.elements();
        for (Publication.Element object : objects) {
            object.x -= grid.x;
            object.y -= grid.y;
        }
        double pad = grid.stroke / 2 + .05;
        var source = new Source(Kind.TABLE, List.of(), grid);
        return render("Table", grid.width() + 2 * pad, grid.height() + 2 * pad, objects, source, pad);
    }

    static StudioTables.Grid table(Source source) {
        source.grid().elements(); // Validate dimensions and cells before opening the editor.
        return source.grid();
    }

    private static StudioImageFile.Loaded render(String label, double width, double height,
            List<Publication.Element> objects, Source source, double pad)
            throws IOException {
        if (!Double.isFinite(width + height) || width <= 0 || height <= 0)
            throw new IOException("Artwork needs positive dimensions.");
        Publication publication = new Publication();
        publication.width = width;
        publication.height = height;
        for (Publication.Element original : objects) {
            var element = StudioTables.copy(original);
            element.x += pad;
            element.y += pad;
            publication.pages.get(0).objects.add(element);
        }
        publication.validate();
        double scale = Math.min(600 / 25.4, 2400 / Math.max(width, height));
        var image = new BufferedImage(Math.max(1, (int)Math.ceil(width * scale)),
                Math.max(1, (int)Math.ceil(height * scale)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(scale, scale);
            new PublicationRenderer(publication).artwork(graphics, 0);
        } finally {
            graphics.dispose();
        }
        return write(image, label, source);
    }

    private static StudioImageFile.Loaded write(BufferedImage image, String name, Source source)
            throws IOException {
        String json = Publication.JSON.toJson(source);
        if (json.length() > MAX_SOURCE) throw new IOException("This artwork is too large for a badge. Simplify it and try again.");
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            var parameters = writer.getDefaultWriteParam();
            var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), parameters);
            var root = new IIOMetadataNode(PNG_METADATA);
            var text = new IIOMetadataNode("iTXt");
            var entry = new IIOMetadataNode("iTXtEntry");
            entry.setAttribute("keyword", KEYWORD);
            entry.setAttribute("compressionFlag", "FALSE");
            entry.setAttribute("compressionMethod", "0");
            entry.setAttribute("languageTag", "");
            entry.setAttribute("translatedKeyword", "");
            entry.setAttribute("text", json);
            text.appendChild(entry);
            root.appendChild(text);
            metadata.mergeTree(PNG_METADATA, root);
            writer.write(null, new IIOImage(image, null, metadata), parameters);
        } finally {
            writer.dispose();
        }
        return new StudioImageFile.Loaded(bytes.toByteArray(), image, name);
    }

    static Source read(BadgeTemplate.Element layer) throws IOException {
        if (!"IMAGE".equals(layer.type)) return null;
        byte[] bytes = Base64.getDecoder().decode(layer.image);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) return null;
                reader.setInput(input, true, false);
                Node root = reader.getImageMetadata(0).getAsTree(PNG_METADATA);
                for (Node chunk = root.getFirstChild(); chunk != null; chunk = chunk.getNextSibling()) {
                    if (!"iTXt".equals(chunk.getNodeName())) continue;
                    for (Node child = chunk.getFirstChild(); child != null; child = child.getNextSibling()) {
                        var entry = (IIOMetadataNode)child;
                        if (!KEYWORD.equals(entry.getAttribute("keyword"))) continue;
                        String json = entry.getAttribute("text");
                        if (json.length() > MAX_SOURCE) throw new IOException("Editable artwork data exceeds the badge limit.");
                        Source source = Publication.JSON.fromJson(json, Source.class);
                        if (source == null || source.kind() == null || source.objects() == null
                                || (source.kind() == Kind.TABLE ? source.grid() == null : source.objects().size() != 1))
                            throw new IOException("This badge artwork could not be opened for editing.");
                        return source;
                    }
                }
                return null;
            } finally {
                reader.dispose();
            }
        }
    }
}

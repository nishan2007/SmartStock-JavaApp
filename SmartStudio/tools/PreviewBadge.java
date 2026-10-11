import gy.deckers.studio.BadgeTemplate;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.Map;

/** Run with SmartStudio/target/classes and target/lib/* on the classpath. */
public class PreviewBadge {
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]);Files.createDirectories(output);
        BadgeTemplate template=BadgeTemplate.builtin();
        template.save(output.resolve("skeldon-primary.sstbadge"));
        ImageIO.write(template.render(Map.of(),null,300),"png",output.resolve("skeldon-primary-preview.png").toFile());
        System.out.println("Created portable template and 300 DPI preview in "+output.toAbsolutePath());
    }
}

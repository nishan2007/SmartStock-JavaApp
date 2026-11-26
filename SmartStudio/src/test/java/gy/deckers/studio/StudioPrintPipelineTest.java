package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StudioPrintPipelineTest {
    @Test void startupChoosesRasterBeforeNativePrintingInitializes()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var process=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                "--add-exports=java.desktop/sun.print=ALL-UNNAMED","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                Probe.class.getName()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS));String output=new String(process.getInputStream().readAllBytes());
        assertEquals(0,process.exitValue(),output);assertTrue(output.contains("RASTER=true"),output);
    }
    public static class Probe {
        public static void main(String[] args)throws Exception{
            PublicationPrinting.configureNativePipeline();
            System.out.println("RASTER="+Class.forName("sun.print.RasterPrinterJob").getField("forceRaster").getBoolean(null));
        }
    }
}

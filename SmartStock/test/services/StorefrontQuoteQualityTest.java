package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontQuoteQualityTest {
    @Test void qualityPreferenceReachesStaffDescriptionWithoutChangingOtherRequests(){
        assertEquals("My model",StorefrontQuoteRequests.withPrintQuality("My model",""));
        assertEquals("My model\nPrint quality preference: Prioritize fine detail",
            StorefrontQuoteRequests.withPrintQuality("My model","DETAIL"));
        assertEquals("My model\nPrint quality preference: Standard",
            StorefrontQuoteRequests.withPrintQuality("My model","STANDARD"));
    }
    @Test void rejectsUnknownQualityAndOversizedDescription(){
        assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.withPrintQuality("Model","FAST"));
        assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.withPrintQuality("a".repeat(1990),"DETAIL"));
    }
    @Test void modelUnitsAreExplicitAndBounded(){
        assertEquals("Model",StorefrontQuoteRequests.withModelUnits("Model",""));
        assertEquals("Model\nModel units: millimeters",StorefrontQuoteRequests.withModelUnits("Model","MM"));
        assertEquals("Model\nModel units: inches",StorefrontQuoteRequests.withModelUnits("Model","IN"));
        assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.withModelUnits("Model","METERS"));
        assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.withModelUnits("a".repeat(1990),"CM"));
    }
    @Test void threeMfArchiveHasBoundedContents(){
        byte[] valid=zip3mf("3D/3dmodel.model","<model/>");
        assertDoesNotThrow(()->StorefrontQuoteRequests.validate3mf(valid));
        assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.validate3mf(zip3mf("3D/../escape.model","<model/>")));
        assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.validate3mf(zip3mf("3D/3dmodel.model","x".repeat(33*1024*1024))));
        assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.validate3mf(new byte[]{80,75,0,0}));
    }
    private static byte[] zip3mf(String modelPath,String model){
        try(var data=new java.io.ByteArrayOutputStream();var zip=new java.util.zip.ZipOutputStream(data)){
            zip.putNextEntry(new java.util.zip.ZipEntry("_rels/.rels"));zip.write("<Relationships/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry(modelPath));zip.write(model.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            zip.finish();return data.toByteArray();
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}

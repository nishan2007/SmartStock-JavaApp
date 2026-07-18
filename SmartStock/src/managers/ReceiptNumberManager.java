package managers;

import java.nio.file.Path;

/** Register-side workstation code preview helpers; receipt allocation is server-only. */
public final class ReceiptNumberManager {
    private static final Path CONFIG_PATH=Path.of(System.getProperty("user.home"),".smartstock","device.properties");
    private ReceiptNumberManager(){}
    public static Path getConfigPath(){return CONFIG_PATH;}
    public static String previewSanitizedDeviceId(String value){
        if(value==null)return "";String digits=value.replaceAll("\\D+","");if(digits.isBlank())return "";
        int number;try{number=Integer.parseInt(digits);}catch(Exception e){number=1;}number=Math.max(1,Math.min(9999,number));return String.format("%04d",number);
    }
}

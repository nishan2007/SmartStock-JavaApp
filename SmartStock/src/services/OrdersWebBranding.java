package services;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.Objects;

/** Uses the same company and location settings as SmartStock receipts. */
final class OrdersWebBranding {
    private OrdersWebBranding() { }
    static Map<String,Object> load(int location)throws Exception {
        var settings=managers.ServerCompanyCustomizationRepository.loadReceiptSettingsForLocation(location);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("name",join(settings.companyName()));
        result.put("motto",join(settings.mottoLine1(),settings.mottoLine2()));
        result.put("address",join(settings.addressLine1(),settings.addressLine2(),settings.addressLine3()));
        result.put("phone",join(settings.phoneLine1(),settings.phoneLine2()));
        result.put("email",join(settings.emailLine1(),settings.emailLine2()));
        if(settings.logoPath()!=null&&!settings.logoPath().isBlank()) {
            try {
                var logo=ServerImageAssetService.load(settings.logoPath());
                if(logo.bytes().length<=2*1024*1024&&("image/png".equals(logo.contentType())||"image/jpeg".equals(logo.contentType())))
                    result.put("logo","data:"+logo.contentType()+";base64,"+Base64.getEncoder().encodeToString(logo.bytes()));
            }catch(Exception ignored){ /* Company text remains usable when the logo is unavailable. */ }
        }
        return result;
    }
    private static String join(String... values) {
        return String.join(" · ",Arrays.stream(values).filter(Objects::nonNull).map(String::trim).filter(v->!v.isEmpty()).toList());
    }
}

package services;

import managers.CompanyCustomizationManager;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Function;

/** Copies company preferences into an editable Wallet draft; never saves implicitly. */
public final class WalletCompanyBranding {
    private WalletCompanyBranding() { }
    public record Info(String name, String logoPath, String address, String phone, String email, String motto) { }
    public static Info from(CompanyCustomizationManager.ReceiptSettings s) {
        return new Info(s.companyName(),s.logoPath(),join(s.addressLine1(),s.addressLine2(),s.addressLine3()),
                join(s.phoneLine1(),s.phoneLine2()),join(s.emailLine1(),s.emailLine2()),join(s.mottoLine1(),s.mottoLine2()));
    }
    private static String join(String... values) {
        return String.join(" · ",Arrays.stream(values).filter(Objects::nonNull).map(String::trim).filter(v->!v.isEmpty()).toList());
    }
    public static WalletBadgeTemplate initial(WalletBadgeTemplate saved, Info info, Function<String,BufferedImage> images) throws Exception {
        return saved.equals(WalletBadgeTemplate.defaults()) ? apply(saved,info,images) : saved;
    }
    public static WalletBadgeTemplate apply(WalletBadgeTemplate t, Info info, Function<String,BufferedImage> images) throws Exception {
        String logo=t.logoPng();
        if(info.logoPath()!=null&&!info.logoPath().isBlank()) {
            BufferedImage image=images.apply(info.logoPath());
            if(image!=null)logo=Base64.getEncoder().encodeToString(WalletBadgeTemplate.image(image,480,150,100));
        }
        var fields=new ArrayList<>(t.fields());
        add(fields,"COMPANY ADDRESS",info.address()); add(fields,"COMPANY PHONE",info.phone());
        add(fields,"COMPANY EMAIL",info.email()); add(fields,"COMPANY MOTTO",info.motto());
        String name=info.name()==null||info.name().isBlank()?t.company():info.name();
        return new WalletBadgeTemplate(name,t.title(),t.background(),t.foreground(),t.labelColor(),fields,
                logo,t.thumbnailPng(),t.logoScale(),t.thumbnailScale(),t.employeePhoto(),t.poster());
    }
    private static void add(List<WalletBadgeTemplate.Field> fields,String label,String text) {
        if(text==null||text.isBlank()||fields.size()>=24||fields.stream().anyMatch(f->f.label().equals(label)))return;
        // Contact information is opt-in, so loading company preferences never exposes it by default.
        fields.add(new WalletBadgeTemplate.Field(false,"backFields","TEXT",label,text));
    }
}

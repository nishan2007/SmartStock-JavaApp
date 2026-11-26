package Receipt;

import services.LanApiClient.CustomerOpenBalance;
import utils.CurrencyFormatter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** The preview and printer paint the same paginated US Letter document. */
public final class AccountStatementLetter implements Printable {
    private static final int WIDTH=540, HEIGHT=720;
    private static final Color INK=new Color(30,42,55), MUTED=new Color(89,102,115);
    private static final Font BODY=new Font(Font.SANS_SERIF,Font.PLAIN,10);
    private static final Font SMALL=BODY.deriveFont(9f), BOLD=BODY.deriveFont(Font.BOLD);
    private final String company, customer, account;
    private final Instant asOf;
    private final ZoneId zone;
    private final List<CustomerOpenBalance> balances;
    private final List<List<Row>> pages=new ArrayList<>();
    private final BigDecimal totalDue;
    private final int tableTop;
    private record Row(CustomerOpenBalance balance,List<String> label,int height) { }

    public AccountStatementLetter(String company,String customer,String account,
                                  List<CustomerOpenBalance> balances,Instant asOf,ZoneId zone) {
        this.company=clean(company);this.customer=clean(customer);this.account=clean(account);
        this.asOf=asOf;this.zone=zone;
        this.balances=balances.stream().filter(b->b.balanceDue()!=null&&b.balanceDue().signum()>0).toList();
        totalDue=this.balances.stream().map(CustomerOpenBalance::balanceDue).reduce(BigDecimal.ZERO,BigDecimal::add);
        Graphics2D g=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            tableTop=header(g,false);
            List<Row> page=new ArrayList<>();
            int y=tableTop+26;
            for(var b:this.balances){
                List<String> label=wrap(g,b.documentNumber(),BOLD,198);
                // Very long references remain intact and are never split across pages.
                int height=Math.max(40,24+label.size()*13);
                if(height>HEIGHT-tableTop-128)throw new IllegalArgumentException("A document reference is too long to fit on a statement page.");
                if(y+height>HEIGHT-102&&!page.isEmpty()){pages.add(List.copyOf(page));page.clear();y=tableTop+26;}
                page.add(new Row(b,label,height));y+=height;
            }
            pages.add(List.copyOf(page));
        }finally{g.dispose();}
    }

    public int pageCount(){return pages.size();}
    public BigDecimal totalDue(){return totalDue;}

    public static PageFormat pageFormat(){
        Paper paper=new Paper();paper.setSize(612,792);paper.setImageableArea(36,36,540,720);
        PageFormat page=new PageFormat();page.setPaper(paper);return page;
    }

    public BufferedImage renderPage(int pageIndex,double scale){
        if(pageIndex<0||pageIndex>=pageCount())throw new IndexOutOfBoundsException(pageIndex);
        BufferedImage image=new BufferedImage((int)Math.ceil(612*scale),(int)Math.ceil(792*scale),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try{g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());g.scale(scale,scale);g.translate(36,36);paint(g,pageIndex);}finally{g.dispose();}
        return image;
    }

    @Override public int print(Graphics graphics,PageFormat format,int pageIndex){
        if(pageIndex<0||pageIndex>=pageCount())return NO_SUCH_PAGE;
        Graphics2D g=(Graphics2D)graphics.create();
        try{
            double scale=Math.min(format.getImageableWidth()/WIDTH,format.getImageableHeight()/HEIGHT);
            g.translate(format.getImageableX(),format.getImageableY());g.scale(scale,scale);paint(g,pageIndex);
        }finally{g.dispose();}
        return PAGE_EXISTS;
    }

    private void paint(Graphics2D g,int pageIndex){
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        header(g,true);
        g.setColor(new Color(233,238,242));g.fillRect(0,tableTop,WIDTH,25);
        g.setFont(BOLD);g.setColor(INK);
        g.drawString("Purchase / document",8,tableTop+16);g.drawString("Date",214,tableTop+16);
        right(g,"Total",368,tableTop+16);right(g,"Paid",450,tableTop+16);right(g,"Balance due",532,tableTop+16);
        int y=tableTop+26;
        for(var row:pages.get(pageIndex)){
            var b=row.balance();
            g.setColor(MUTED);g.setFont(SMALL);g.drawString(type(b.documentType()),8,y+13);
            g.setColor(INK);g.setFont(BOLD);
            int labelY=y+27;for(String line:row.label()){g.drawString(line,8,labelY);labelY+=13;}
            g.setFont(SMALL);
            String date=b.createdAtEpochMillis()>0?DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(zone).format(Instant.ofEpochMilli(b.createdAtEpochMillis())):"-";
            g.drawString(date,214,y+25);
            g.setFont(BODY);right(g,money(b.total()),368,y+25);right(g,money(b.paid()),450,y+25);
            g.setFont(BOLD);right(g,money(b.balanceDue()),532,y+25);
            y+=row.height();g.setColor(new Color(220,225,229));g.drawLine(0,y,WIDTH,y);
        }
        if(balances.isEmpty()){g.setFont(BODY);g.setColor(MUTED);g.drawString("No unpaid purchases on this account at this store.",8,y+25);y+=40;}
        if(pageIndex==pageCount()-1){
            y+=18;g.setColor(INK);g.setFont(BODY);g.drawString("Open entries: "+balances.size(),8,y+14);
            g.setColor(new Color(233,238,242));g.fillRect(287,y-3,WIDTH-287,40);
            g.setColor(INK);g.setFont(BOLD);g.drawString("TOTAL DUE",297,y+13);
            g.setFont(BODY.deriveFont(Font.BOLD,15f));right(g,money(totalDue),530,y+25);
        }else{
            g.setColor(MUTED);g.setFont(SMALL);g.drawString("Continued on next page. Total due appears on the final page.",8,y+22);
        }
        g.setColor(new Color(220,225,229));g.drawLine(0,HEIGHT-30,WIDTH,HEIGHT-30);
        g.setColor(MUTED);g.setFont(SMALL);g.drawString("Statement only. No payment recorded.",0,HEIGHT-13);
        right(g,"Page "+(pageIndex+1)+" of "+pageCount(),WIDTH,HEIGHT-13);
    }

    private int header(Graphics2D g,boolean draw){
        int y=19;
        for(String line:wrap(g,company,BODY.deriveFont(Font.BOLD,18f),WIDTH)){
            if(draw){g.setColor(INK);g.setFont(BODY.deriveFont(Font.BOLD,18f));g.drawString(line,0,y);}y+=23;
        }
        y+=7;if(draw){g.setFont(BODY.deriveFont(Font.BOLD,15f));g.drawString("UNPAID PURCHASES STATEMENT",0,y);}y+=26;
        for(String line:wrap(g,customer,BODY.deriveFont(Font.BOLD,12f),WIDTH)){
            if(draw){g.setFont(BODY.deriveFont(Font.BOLD,12f));g.drawString(line,0,y);}y+=16;
        }
        for(String line:wrap(g,"Account: "+account,BODY,WIDTH)){
            if(draw){g.setFont(BODY);g.drawString(line,0,y);}y+=15;
        }
        if(draw){
            g.setColor(MUTED);g.setFont(SMALL);
            g.drawString("As of "+DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a").withZone(zone).format(asOf)+"  |  Amounts in GYD",0,y);
            g.drawString("Connected store balances only. Other stores may have additional amounts due.",0,y+15);
        }
        return y+30;
    }

    private static String type(String value){return switch(value==null?"":value){case "MINUTES"->"Phone minutes";case "SALE"->"Purchase";case "INVOICE"->"Account invoice";case "CUSTOM_ORDER"->"Custom order";case "ACCOUNT"->"Additional account charges";default->clean(value).replace('_',' ');};}
    private static String money(BigDecimal value){return CurrencyFormatter.format(value==null?BigDecimal.ZERO:value);}
    private static void right(Graphics2D g,String text,int x,int y){g.drawString(text,x-g.getFontMetrics().stringWidth(text),y);}
    private static String clean(String text){return text==null?"":text.replaceAll("[\\p{Cntrl}]"," ").trim();}

    private static List<String> wrap(Graphics2D g,String text,Font font,int width){
        FontMetrics metrics=g.getFontMetrics(font);List<String> lines=new ArrayList<>();String rest=clean(text);
        while(!rest.isEmpty()){
            int n=rest.length();while(n>1&&metrics.stringWidth(rest.substring(0,n))>width)n--;
            if(n<rest.length()){int space=rest.lastIndexOf(' ',n);if(space>0)n=space;}
            lines.add(rest.substring(0,n));rest=rest.substring(n).stripLeading();
        }
        if(lines.isEmpty())lines.add("");return lines;
    }
}

package services;

import org.junit.jupiter.api.Test;
import javax.print.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptPrinterDiscoveryTest {
    @Test void recoversAfterTemporarilyEmptyDiscovery()throws Exception {
        PrintService printer=printer("Receipt");AtomicInteger calls=new AtomicInteger(),waits=new AtomicInteger();
        assertSame(printer,ReceiptPrinterDiscovery.resolve("Receipt",()->calls.incrementAndGet()<3?new PrintService[0]:new PrintService[]{printer},()->null,ms->waits.incrementAndGet()));
        assertEquals(3,calls.get());assertEquals(2,waits.get());
    }
    @Test void acceptsDefaultOnlyWhenItIsTheConfiguredQueue()throws Exception {
        PrintService receipt=printer("Receipt");
        assertSame(receipt,ReceiptPrinterDiscovery.resolve("Receipt",()->new PrintService[0],()->receipt,ms->{}));
        AtomicInteger calls=new AtomicInteger();
        assertThrows(PrintException.class,()->ReceiptPrinterDiscovery.resolve("Receipt",()->{calls.incrementAndGet();return new PrintService[]{printer("Office")};},()->printer("Office"),ms->{}));
        assertEquals(4,calls.get());
    }
    @Test void retriesDiscoveryExceptionsWithoutSubmittingJobs()throws Exception {
        AtomicInteger calls=new AtomicInteger();PrintService receipt=printer("Receipt");
        assertSame(receipt,ReceiptPrinterDiscovery.resolve("Receipt",()->{if(calls.incrementAndGet()==1)throw new IllegalStateException("Spooler restarting");return new PrintService[]{receipt};},()->null,ms->{}));
    }
    private static PrintService printer(String name){return (PrintService)Proxy.newProxyInstance(PrintService.class.getClassLoader(),new Class<?>[]{PrintService.class},(proxy,method,args)->{if(method.getName().equals("getName"))return name;throw new AssertionError("Discovery must not print: "+method.getName());});}
}

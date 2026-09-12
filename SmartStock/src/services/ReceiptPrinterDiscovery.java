package services;

import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.util.function.Supplier;

/** Retries discovery only. Never retries submission of a possibly accepted print job. */
final class ReceiptPrinterDiscovery {
    private ReceiptPrinterDiscovery() { }
    static PrintService resolve(String name)throws PrintException {
        return resolve(name,()->PrintServiceLookup.lookupPrintServices(null,null),PrintServiceLookup::lookupDefaultPrintService,Thread::sleep);
    }
    static PrintService resolve(String name,Supplier<PrintService[]> lookup,Supplier<PrintService> defaultLookup,Pause pause)throws PrintException {
        if(name==null||name.isBlank())throw new PrintException("The receipt printer has no configured Windows queue name.");
        RuntimeException lastFailure=null;
        for(int attempt=0;attempt<4;attempt++) {
            if(attempt>0)try{pause.sleep(400L*attempt);}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new PrintException("Receipt printer discovery was interrupted.");}
            try {
                PrintService[] services=lookup.get();
                if(services!=null)for(PrintService service:services)if(service!=null&&name.equals(service.getName()))return service;
                // Windows can expose its default queue before the complete queue list is refreshed.
                PrintService defaultService=defaultLookup.get();
                if(defaultService!=null&&name.equals(defaultService.getName()))return defaultService;
            }catch(RuntimeException ex){lastFailure=ex;}
        }
        String message="Receipt printer queue '"+name+"' is unavailable on the computer running SmartStock's New Item web app. Check that the printer is on, the Windows Print Spooler is running, and this queue is installed for the account running SmartStock. Verify Hardware Settings on that computer.";
        if(lastFailure!=null)throw new PrintException(message,lastFailure);
        throw new PrintException(message);
    }
    interface Pause { void sleep(long millis)throws InterruptedException; }
}

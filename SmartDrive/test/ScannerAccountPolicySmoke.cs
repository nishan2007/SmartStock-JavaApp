using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;

internal static class ScannerAccountPolicySmoke {
    private static void Reject(SmartDriveAccounts service,Dictionary<string,object> request,string method="Execute") {
        try { typeof(SmartDriveAccounts).GetMethod(method,BindingFlags.Instance|BindingFlags.NonPublic).Invoke(service,new object[]{request}); }
        catch(TargetInvocationException failure) { if(failure.InnerException is InvalidDataException)return;throw; }
        throw new Exception("Account policy accepted an invalid request.");
    }
    private static void Main() {
        using(var service=new SmartDriveAccounts()) {
            typeof(SmartDriveAccounts).GetMethod("Execute",BindingFlags.Instance|BindingFlags.NonPublic).Invoke(service,new object[]{new Dictionary<string,object>{{"action","health"}}});
            Reject(service,new Dictionary<string,object>{{"action","health"},{"account","Administrator"}});
            Reject(service,new Dictionary<string,object>{{"action","workstation-activity"},{"accounts",new object[]{"Administrator"}}},"ReadActivity");
            Reject(service,new Dictionary<string,object>{{"action","workstation-activity"},{"accounts",new object[0]}},"ReadActivity");
            Reject(service,new Dictionary<string,object>{{"action","workstation-activity"},{"accounts",new object[]{"sdpc_012345abcdef","sdpc_012345abcdef"}}},"ReadActivity");
            typeof(SmartDriveAccounts).GetField("stores",BindingFlags.Instance|BindingFlags.NonPublic).SetValue(service,new HashSet<string>{"1"});
            typeof(SmartDriveAccounts).GetField("scannerAccounts",BindingFlags.Instance|BindingFlags.NonPublic).SetValue(service,new HashSet<string>{"sdscan_c5040"});
            foreach(string action in new[]{"scanner-disable","scanner-enable","scanner-rotate"}) {
                foreach(string account in new[]{"Administrator","sdscan_unknown","sdpc_012345abcdef"})
                    Reject(service,new Dictionary<string,object>{{"action",action},{"account",account}});
            }
            Reject(service,new Dictionary<string,object>{{"action","scanner-rotate"},{"account","sdscan_c5040"},{"password","invalid"},{"disabled",false}});
            Reject(service,new Dictionary<string,object>{{"action","scanner-rotate"},{"account","sdscan_c5040"},{"password",new string('a',36)},{"disabled","false"}});
            Reject(service,new Dictionary<string,object>{{"action","execute"},{"account","sdscan_c5040"}});
            Reject(service,new Dictionary<string,object>{{"action","create"},{"account","sdpc_012345abcdef"},{"password",new string('a',36)},{"stores",new object[]{"2"}}});
            typeof(SmartDriveAccounts).GetField("scannerProvisioning",BindingFlags.Instance|BindingFlags.NonPublic).SetValue(service,true);
            Reject(service,new Dictionary<string,object>{{"action","scanner-create"},{"account","Administrator"},{"id","Canon"},{"store","1"},{"password",new string('a',36)}});
            Reject(service,new Dictionary<string,object>{{"action","scanner-create"},{"account","sdscan_new"},{"id","../Canon"},{"store","1"},{"password",new string('a',36)}});
            Reject(service,new Dictionary<string,object>{{"action","scanner-create"},{"account","sdscan_new"},{"id","Canon"},{"store","2"},{"password",new string('a',36)}});
            Reject(service,new Dictionary<string,object>{{"action","scanner-create"},{"account","sdscan_new"},{"id","Canon"},{"store","1"},{"password","invalid"}});
        }
        Console.WriteLine("Account policy rejected all invalid requests before native account operations.");
    }
}

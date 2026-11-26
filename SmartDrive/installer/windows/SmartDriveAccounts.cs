using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Principal;
using System.ServiceProcess;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;

internal sealed class SmartDriveAccounts : ServiceBase
{
    private NamedPipeServerStream pipe;
    private volatile bool stopping;
    private HashSet<string> stores;
    private HashSet<string> scannerAccounts;
    private HashSet<string> configuredScannerAccounts;
    private bool scannerProvisioning;
    private readonly JavaScriptSerializer json = new JavaScriptSerializer { MaxJsonLength = 1024*1024 };
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct UserInfo
    {
        public string Name, Password;
        public uint PasswordAge, Privilege;
        public string HomeDirectory, Comment;
        public uint Flags;
        public string ScriptPath;
    }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct GroupMember { public string Name; }
    [StructLayout(LayoutKind.Sequential)]
    private struct UserFlags { public uint Flags; }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct UserPassword { public string Password; }
    [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
    private struct SessionInfo { public string Client,User;public uint Seconds,Idle; }
    [DllImport("Netapi32.dll",CharSet=CharSet.Unicode)]
    private static extern uint NetSessionEnum(string server,string client,string user,uint level,out IntPtr buffer,uint maximum,out uint read,out uint total,ref uint resume);
    [DllImport("Netapi32.dll", CharSet = CharSet.Unicode)]
    private static extern uint NetUserAdd(string server, uint level, ref UserInfo info, out uint parameter);
    [DllImport("Netapi32.dll", CharSet = CharSet.Unicode)]
    private static extern uint NetLocalGroupAddMembers(string server, string group, uint level, ref GroupMember member, uint count);
    [DllImport("Netapi32.dll", CharSet = CharSet.Unicode)]
    private static extern uint NetUserSetInfo(string server, string user, uint level, ref UserFlags flags, out uint parameter);
    [DllImport("Netapi32.dll", CharSet = CharSet.Unicode, EntryPoint="NetUserSetInfo")]
    private static extern uint SetPassword(string server, string user, uint level, ref UserPassword password, out uint parameter);
    [DllImport("Netapi32.dll", CharSet = CharSet.Unicode)]
    private static extern uint NetSessionDel(string server, string client, string user);
    [DllImport("Netapi32.dll", CharSet=CharSet.Unicode)]
    private static extern uint NetUserGetInfo(string server,string user,uint level,out IntPtr buffer);
    [DllImport("Netapi32.dll")]
    private static extern uint NetApiBufferFree(IntPtr buffer);
    internal SmartDriveAccounts() { ServiceName = "SmartDriveAccounts"; CanStop = true; }

    protected override void OnStart(string[] args)
    {
        var settings = json.Deserialize<Dictionary<string, object>>(File.ReadAllText(
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "account-stores.json")));
        stores = new HashSet<string>();
        foreach (object value in (object[])settings["storeIds"])
        {
            string id = (string)value;
            if (!Regex.IsMatch(id, "^[1-9][0-9]{0,8}$")) throw new InvalidDataException();
            stores.Add(id);
        }
        scannerAccounts = new HashSet<string>();
        if(settings.ContainsKey("scanners"))foreach(object value in (object[])settings["scanners"]) {
            var scanner=(Dictionary<string,object>)value;
            string account=(string)scanner["account"],store=(string)scanner["storeId"];
            if(!Regex.IsMatch(account,"^sdscan_[a-z0-9_]{1,12}$")||!stores.Contains(store)||!scannerAccounts.Add(account))throw new InvalidDataException();
        }
        configuredScannerAccounts=new HashSet<string>(scannerAccounts);
        scannerProvisioning=settings.ContainsKey("dataRoot")&&settings["dataRoot"] is string&&Regex.IsMatch((string)settings["dataRoot"],"^[A-Za-z]:\\\\");
        LoadScannerAccounts();
        var security = new PipeSecurity();
        security.AddAccessRule(new PipeAccessRule(new SecurityIdentifier(WellKnownSidType.NetworkSid, null),
            PipeAccessRights.FullControl, AccessControlType.Deny));
        foreach (var sid in new[] { WellKnownSidType.LocalServiceSid, WellKnownSidType.LocalSystemSid, WellKnownSidType.BuiltinAdministratorsSid })
            security.AddAccessRule(new PipeAccessRule(new SecurityIdentifier(sid, null), PipeAccessRights.FullControl, AccessControlType.Allow));
        pipe = new NamedPipeServerStream("SmartDriveAccounts.v1", PipeDirection.InOut, 1,
            PipeTransmissionMode.Byte, PipeOptions.Asynchronous, 8192, 8192, security);
        new Thread(Serve) { IsBackground = true }.Start();
    }
    private static byte[] ReadBytes(Stream input, int size)
    {
        byte[] bytes = new byte[size];
        int offset = 0;
        while (offset < size)
        {
            int count = input.Read(bytes, offset, size - offset);
            if (count == 0) throw new EndOfStreamException();
            offset += count;
        }
        return bytes;
    }
    private void Serve()
    {
        while (!stopping)
        {
            try
            {
                pipe.WaitForConnection();
                var length = BitConverter.ToInt32(ReadBytes(pipe, 4), 0);
                if (length < 1 || length > 4096) throw new InvalidDataException();
                var request = json.Deserialize<Dictionary<string, object>>(Encoding.UTF8.GetString(ReadBytes(pipe, length)));
                bool succeeded = false;
                object data=null;
                try {
                    if(request.ContainsKey("action")&&(string)request["action"]=="workstation-activity")data=ReadActivity(request);
                    else Execute(request);
                    succeeded = true;
                }
                catch { /* Return a bounded result without account secrets or native error details. */ }
                byte[] response = Encoding.UTF8.GetBytes(json.Serialize(new { ok = succeeded, data = data }) + "\n");
                pipe.Write(response, 0, response.Length);
                pipe.Flush();
            }
            catch { if (stopping) return; }
            finally
            {
                if (!stopping && pipe.IsConnected) pipe.Disconnect();
            }
        }
    }
    private object ReadActivity(Dictionary<string,object> request) {
        if(request.Count!=2||!request.ContainsKey("action")||(string)request["action"]!="workstation-activity"||!request.ContainsKey("accounts")||!(request["accounts"] is object[]))throw new InvalidDataException();
        object[] input=(object[])request["accounts"];
        if(input.Length<1||input.Length>128)throw new InvalidDataException();
        var accounts=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach(object value in input){
            if(!(value is string)||!Regex.IsMatch((string)value,"^sdpc_[a-f0-9]{12}$")||!accounts.Add((string)value))throw new InvalidDataException();
        }
        var observed=new Dictionary<string,DateTime>(StringComparer.OrdinalIgnoreCase);
        uint resume=0,visited=0;
        for(int page=0;page<100;page++){
            uint previous=resume,read,total;IntPtr buffer;
            uint status=NetSessionEnum(null,null,null,10,out buffer,65536,out read,out total,ref resume);
            try{
                if(status!=0&&status!=234)throw new InvalidOperationException();
                if(read>10000||visited+read>10000||(read>0&&buffer==IntPtr.Zero))throw new InvalidDataException();visited+=read;
                for(uint index=0;index<read;index++){
                    var session=(SessionInfo)Marshal.PtrToStructure(IntPtr.Add(buffer,checked((int)index*Marshal.SizeOf(typeof(SessionInfo)))),typeof(SessionInfo));
                    if(session.User==null||!accounts.Contains(session.User))continue;
                    DateTime active=DateTime.UtcNow.AddSeconds(-(double)session.Idle);
                    if(!observed.ContainsKey(session.User)||observed[session.User]<active)observed[session.User]=active;
                }
                if(status==0){
                    var result=new List<object>();
                    foreach(var item in observed)result.Add(new {account=item.Key,lastActiveAt=item.Value.ToString("o")});
                    return result;
                }
                if(resume==previous)throw new InvalidDataException();
            }finally{if(buffer!=IntPtr.Zero)NetApiBufferFree(buffer);}
        }
        throw new InvalidDataException();
    }
    private void Execute(Dictionary<string, object> request)
    {
        string action = (string)request["action"];
        if(action=="health") {
            if(request.Count!=1)throw new InvalidDataException();
            return;
        }
        string account = (string)request["account"];
        if(action=="scanner-create") {
            string id=(string)request["id"],store=(string)request["store"],scannerPassword=(string)request["password"];
            if(!scannerProvisioning||!Regex.IsMatch(account,"^sdscan_[a-z0-9_]{1,12}$")||
                !Regex.IsMatch(id,"^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$")||!stores.Contains(store)||
                !Regex.IsMatch(scannerPassword,"^[A-Za-z0-9!_-]{32,100}$"))throw new InvalidDataException();
            ProvisionScanner(new Dictionary<string,object>{{"id",id},{"store",store},{"account",account},{"password",scannerPassword}});
            return;
        }
        if(action=="scanner-disable"||action=="scanner-enable"||action=="scanner-rotate") {
            if(!scannerAccounts.Contains(account))throw new InvalidDataException();
            if(action=="scanner-disable"){Revoke(account);return;}
            if(action=="scanner-enable") {
                uint enabledParameter;var enabledFlags=new UserFlags{Flags=0x0001u|0x0200u|0x10000u};
                if(NetUserSetInfo(null,account,1008,ref enabledFlags,out enabledParameter)!=0)throw new InvalidOperationException();
                return;
            }
            string newPassword=(string)request["password"];
            if(!Regex.IsMatch(newPassword,"^[A-Za-z0-9!_-]{32,100}$")||!(request["disabled"] is bool))throw new InvalidDataException();
            uint scannerParameter;var passwordInfo=new UserPassword{Password=newPassword};
            if(SetPassword(null,account,1003,ref passwordInfo,out scannerParameter)!=0)throw new InvalidOperationException();
            var scannerFlags=new UserFlags{Flags=0x0001u|0x0200u|0x10000u|((bool)request["disabled"]?0x0002u:0u)};
            if(NetUserSetInfo(null,account,1008,ref scannerFlags,out scannerParameter)!=0)throw new InvalidOperationException();
            uint closed=NetSessionDel(null,null,account);if(closed!=0&&closed!=2312)throw new InvalidOperationException();
            return;
        }
        if (!Regex.IsMatch(account, "^sdpc_[a-f0-9]{12}$")) throw new InvalidDataException();
        if (action == "revoke") { Revoke(account); return; }
        if (action != "create") throw new InvalidDataException();
        string password = (string)request["password"];
        if (!Regex.IsMatch(password, "^[A-Za-z0-9!_-]{32,100}$")) throw new InvalidDataException();
        var groups = new HashSet<string>();
        foreach (object value in (object[])request["stores"])
        {
            string id = (string)value;
            if (!stores.Contains(id)) throw new InvalidDataException();
            groups.Add(id);
        }
        if (groups.Count == 0) throw new InvalidDataException();
        var user = new UserInfo { Name = account, Password = password, Privilege = 1,
            Comment = "SmartDrive enrolled workstation", Flags = 0x0001 | 0x0200 | 0x10000 };
        uint parameter;
        if (NetUserAdd(null, 1, ref user, out parameter) != 0) throw new InvalidOperationException();
        try
        {
            foreach (string id in groups)
            {
                var member = new GroupMember { Name = Environment.MachineName + "\\" + account };
                if (NetLocalGroupAddMembers(null, "SDStore" + id, 3, ref member, 1) != 0) throw new InvalidOperationException();
            }
        }
        catch { Revoke(account); throw; }
    }
    private bool OwnsScanner(string account,string tag) {
        IntPtr buffer;uint result=NetUserGetInfo(null,account,1,out buffer);
        if(result==2221)return false;if(result!=0)throw new InvalidOperationException();
        try{return ((UserInfo)Marshal.PtrToStructure(buffer,typeof(UserInfo))).Comment=="SmartDrive scan "+tag;}
        finally{NetApiBufferFree(buffer);}
    }
    private void LoadScannerAccounts() {
        scannerAccounts=new HashSet<string>(configuredScannerAccounts);
        string path=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"scanner-accounts.json");
        if(!File.Exists(path))return;
        var registry=json.Deserialize<Dictionary<string,object>>(File.ReadAllText(path));
        if(Convert.ToInt32(registry["version"])!=1)throw new InvalidDataException();
        object[] devices=(object[])registry["devices"];if(devices.Length>1000)throw new InvalidDataException();
        foreach(object value in devices) {
            var row=(Dictionary<string,object>)value;
            string account=(string)row["account"],store=(string)row["storeId"],id=(string)row["id"],tag=(string)row["ownerTag"],phase=(string)row["phase"];
            Guid parsed;
            if(!Regex.IsMatch(account,"^sdscan_[a-z0-9_]{1,12}$")||!Regex.IsMatch(id,"^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$")||
                !stores.Contains(store)||!Guid.TryParse(tag,out parsed)||!new HashSet<string>{"active","provisioning","requires-review"}.Contains(phase))throw new InvalidDataException();
            if(!OwnsScanner(account,tag))continue;
            if(phase=="active"){if(!scannerAccounts.Add(account))throw new InvalidDataException();}
            else Revoke(account);
        }
    }
    private void ProvisionScanner(Dictionary<string,object> request) {
        string script=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"installer","windows","Provision-Scanner.ps1");
        string system=Environment.GetFolderPath(Environment.SpecialFolder.System);
        var start=new ProcessStartInfo(Path.Combine(system,"WindowsPowerShell","v1.0","powershell.exe"),
            "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File \""+script+"\"") {
            UseShellExecute=false,CreateNoWindow=true,RedirectStandardInput=true,RedirectStandardOutput=true,RedirectStandardError=true };
        bool succeeded=false;
        using(var process=new Process{StartInfo=start}) {
            process.OutputDataReceived+=(sender,args)=>{};process.ErrorDataReceived+=(sender,args)=>{};
            process.Start();process.BeginOutputReadLine();process.BeginErrorReadLine();
            process.StandardInput.Write(json.Serialize(request));process.StandardInput.Close();
            if(!process.WaitForExit(45000)) {
                using(var kill=Process.Start(new ProcessStartInfo(Path.Combine(system,"taskkill.exe"),"/PID "+process.Id+" /T /F"){UseShellExecute=false,CreateNoWindow=true})) {kill.WaitForExit(5000);}
            } else succeeded=process.ExitCode==0;
        }
        LoadScannerAccounts();
        if(!succeeded||!scannerAccounts.Contains((string)request["account"]))throw new InvalidOperationException();
    }
    private static void Revoke(string account)
    {
        uint parameter;
        var flags = new UserFlags { Flags = 0x0001 | 0x0002 | 0x0200 | 0x10000 };
        uint disabled = NetUserSetInfo(null, account, 1008, ref flags, out parameter);
        if (disabled == 2221) return;
        if (disabled != 0) throw new InvalidOperationException();
        uint result = NetSessionDel(null, null, account);
        if (result != 0 && result != 2312) throw new InvalidOperationException();
    }
    protected override void OnStop() { stopping = true; if (pipe != null) pipe.Dispose(); }
    private static void Main() { ServiceBase.Run(new SmartDriveAccounts()); }
}

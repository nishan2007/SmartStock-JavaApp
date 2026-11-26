using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Security.AccessControl;
using System.Security.Principal;

internal static class OfficePreview
{
    [StructLayout(LayoutKind.Sequential)]
    private struct BasicLimits
    {
        public long ProcessTime, JobTime;
        public uint Flags;
        public UIntPtr MinimumWorkingSet, MaximumWorkingSet;
        public uint ActiveProcesses;
        public UIntPtr Affinity;
        public uint Priority, Scheduling;
    }
    [StructLayout(LayoutKind.Sequential)]
    private struct IoCounters { public ulong ReadOperations, WriteOperations, OtherOperations, ReadBytes, WriteBytes, OtherBytes; }
    [StructLayout(LayoutKind.Sequential)]
    private struct ExtendedLimits
    {
        public BasicLimits Basic;
        public IoCounters Io;
        public UIntPtr ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory;
    }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct Startup
    {
        public uint Size;
        public string Reserved, Desktop, Title;
        public uint X, Y, Width, Height, Columns, Rows, Fill, Flags;
        public ushort Show, ReservedSize;
        public IntPtr ReservedData, Input, Output, Error;
    }
    [StructLayout(LayoutKind.Sequential)]
    private struct ProcessInfo { public IntPtr Process, Thread; public uint ProcessId, ThreadId; }
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr CreateJobObject(IntPtr attributes, string name);
    [DllImport("kernel32.dll")]
    private static extern bool SetInformationJobObject(IntPtr job, int information, IntPtr data, uint size);
    [DllImport("kernel32.dll")]
    private static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CreateProcessWithLogonW(string username, string domain, string password, uint logonFlags,
        string app, StringBuilder command, uint flags, IntPtr environment, string directory, ref Startup startup, out ProcessInfo process);
    [DllImport("kernel32.dll")] private static extern uint ResumeThread(IntPtr thread);
    [DllImport("kernel32.dll")] private static extern uint WaitForSingleObject(IntPtr handle, uint timeout);
    [DllImport("kernel32.dll")] private static extern bool GetExitCodeProcess(IntPtr process, out uint code);
    [DllImport("kernel32.dll")] private static extern bool TerminateJobObject(IntPtr job, uint code);
    [DllImport("kernel32.dll")] private static extern bool TerminateProcess(IntPtr process, uint code);
    [DllImport("kernel32.dll")] private static extern bool CloseHandle(IntPtr handle);

    private static string Quote(string value)
    {
        if (!Path.IsPathRooted(value) || value.IndexOf('"') >= 0) throw new InvalidDataException();
        return "\"" + value + "\"";
    }
    private static int Main(string[] args)
    {
        IntPtr job = IntPtr.Zero, environment = IntPtr.Zero;
        var process = new ProcessInfo();
        string stage = "configuration";
        try
        {
            if (args.Length != 4 || !Path.GetFileName(args[0]).Equals("soffice.bin", StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException();
            string converter = Path.GetFullPath(args[0]), input = Path.GetFullPath(args[1]);
            string output = Path.GetFullPath(args[2]), profile = Path.GetFullPath(args[3]);
            string workspace = Path.GetDirectoryName(input);
            if (Path.GetDirectoryName(output) != workspace || Path.GetDirectoryName(profile) != workspace) throw new InvalidDataException();
            string extension = Path.GetExtension(input).ToLowerInvariant();
            if (extension != ".docx" && extension != ".pptx") throw new InvalidDataException();
            Directory.CreateDirectory(Path.Combine(profile, "user"));
            string username = Environment.GetEnvironmentVariable("SMARTDRIVE_PREVIEW_USER");
            string password = Environment.GetEnvironmentVariable("SMARTDRIVE_PREVIEW_PASSWORD");
            if (username != "sdpreview" || String.IsNullOrEmpty(password)) throw new InvalidDataException();
            var jobIdentity = (SecurityIdentifier)new NTAccount(Environment.MachineName, username).Translate(typeof(SecurityIdentifier));
            foreach (string directory in new[] { workspace, output, profile, Path.Combine(profile, "user") })
            {
                var security = Directory.GetAccessControl(directory);
                security.AddAccessRule(new FileSystemAccessRule(jobIdentity, FileSystemRights.Modify,
                    InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
                Directory.SetAccessControl(directory, security);
            }
            var inputSecurity = File.GetAccessControl(input);
            inputSecurity.AddAccessRule(new FileSystemAccessRule(jobIdentity, FileSystemRights.ReadAndExecute, AccessControlType.Allow));
            File.SetAccessControl(input, inputSecurity);
            File.WriteAllText(Path.Combine(profile, "user", "registrymodifications.xcu"),
                "<?xml version=\"1.0\"?><oor:items xmlns:oor=\"http://openoffice.org/2001/registry\">" +
                "<item oor:path=\"/org.openoffice.Office.Common/Security/Scripting\"><prop oor:name=\"MacroSecurityLevel\" oor:op=\"fuse\"><value>3</value></prop></item></oor:items>");
            stage = "create job";
            job = CreateJobObject(IntPtr.Zero, null);
            if (job == IntPtr.Zero) throw new InvalidOperationException();
            var limits = new ExtendedLimits {
                Basic = new BasicLimits { Flags = 0x2000 | 0x0200 | 0x0008, ActiveProcesses = 1 },
                JobMemory = new UIntPtr(512u * 1024u * 1024u)
            };
            IntPtr buffer = Marshal.AllocHGlobal(Marshal.SizeOf(limits));
            try
            {
                Marshal.StructureToPtr(limits, buffer, false);
                stage = "set job limits";
                if (!SetInformationJobObject(job, 9, buffer, (uint)Marshal.SizeOf(limits))) throw new InvalidOperationException();
            }
            finally { Marshal.FreeHGlobal(buffer); }
            string systemRoot = Environment.GetFolderPath(Environment.SpecialFolder.Windows);
            environment = Marshal.StringToHGlobalUni("APPDATA=" + profile + "\0LOCALAPPDATA=" + profile +
                "\0PATH=" + Path.Combine(systemRoot, "System32") + "\0SystemRoot=" + systemRoot +
                "\0TEMP=" + profile + "\0TMP=" + profile + "\0USERPROFILE=" + profile + "\0\0");
            var startup = new Startup { Size = (uint)Marshal.SizeOf(typeof(Startup)) };
            string command = Quote(converter) + " --headless --nologo --nodefault --norestore --nolockcheck " +
                "\"-env:UserInstallation=" + new Uri(profile + Path.DirectorySeparatorChar).AbsoluteUri + "\" --convert-to pdf --outdir " +
                Quote(output) + " " + Quote(input);
            DateTime deadline = DateTime.UtcNow.AddSeconds(60);
            for (int attempt = 0; attempt < 2; attempt++)
            {
                stage = "start converter";
                if (!CreateProcessWithLogonW(username, Environment.MachineName, password, 1, converter, new StringBuilder(command),
                    0x08000000 | 0x00000400 | 0x00000004, environment, Path.GetDirectoryName(input), ref startup, out process))
                    throw new InvalidOperationException();
                stage = "isolate converter";
                if (!AssignProcessToJobObject(job, process.Process))
                {
                    TerminateProcess(process.Process, 2);
                    throw new InvalidOperationException();
                }
                stage = "resume converter";
                if (ResumeThread(process.Thread) == 0xffffffff) throw new InvalidOperationException();
                stage = "wait for converter";
                double remaining = (deadline - DateTime.UtcNow).TotalMilliseconds;
                if (remaining <= 0 || WaitForSingleObject(process.Process, (uint)remaining) != 0)
                {
                    TerminateJobObject(job, 2);
                    throw new InvalidOperationException();
                }
                uint exit;
                if (!GetExitCodeProcess(process.Process, out exit)) throw new InvalidOperationException();
                // LibreOffice requests one restart after creating a fresh user profile.
                if (exit == 81 && attempt == 0)
                {
                    CloseHandle(process.Thread);
                    CloseHandle(process.Process);
                    process = new ProcessInfo();
                    continue;
                }
                if (exit != 0) { Console.Error.WriteLine("Office converter exit code " + exit); throw new InvalidOperationException(); }
                return 0;
            }
            return 2;
        }
        catch { Console.Error.WriteLine("Office preview failed during " + stage + "."); return 2; }
        finally
        {
            if (process.Thread != IntPtr.Zero) CloseHandle(process.Thread);
            if (process.Process != IntPtr.Zero) CloseHandle(process.Process);
            if (job != IntPtr.Zero) CloseHandle(job);
            if (environment != IntPtr.Zero) Marshal.FreeHGlobal(environment);
        }
    }
}

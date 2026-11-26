using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Security;

public static class WindowsDriveCredential
{
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct Credential
    {
        public uint Flags;
        public uint Type;
        public string TargetName;
        public string Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }

    [DllImport("advapi32.dll", EntryPoint = "CredWriteW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CredWrite(ref Credential credential, uint flags);

    [DllImport("advapi32.dll", EntryPoint = "CredReadW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CredRead(string target, uint type, uint flags, out IntPtr credential);

    [DllImport("advapi32.dll")]
    private static extern void CredFree(IntPtr credential);

    public static bool Exists(string host)
    {
        IntPtr existing;
        if (CredRead(host, 2, 0, out existing)) { CredFree(existing); return true; }
        int error = Marshal.GetLastWin32Error();
        if (error == 1168) return false;
        throw new Win32Exception(error);
    }

    public static void Save(string host, string user, SecureString password)
    {
        IntPtr blob = Marshal.SecureStringToCoTaskMemUnicode(password);
        try
        {
            var credential = new Credential {
                Type = 2, TargetName = host, UserName = user,
                CredentialBlob = blob, CredentialBlobSize = checked((uint)password.Length * 2),
                Persist = 2, Comment = "SmartDrive workstation access"
            };
            if (!CredWrite(ref credential, 0)) throw new Win32Exception(Marshal.GetLastWin32Error());
        }
        finally { Marshal.ZeroFreeCoTaskMemUnicode(blob); }
    }
}

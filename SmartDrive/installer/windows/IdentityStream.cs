using System;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

public static class SmartDriveImportIdentityStreamV1 {
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    private static extern SafeFileHandle CreateFileW(string name,uint access,uint share,IntPtr security,uint creation,uint flags,IntPtr template);
    private static FileStream Open(string path,bool write) {
        SafeFileHandle handle=CreateFileW(path+":SmartDriveIdentity",write?0x40000000u:0x80000000u,write?0u:7u,IntPtr.Zero,write?2u:3u,0,IntPtr.Zero);
        if(handle.IsInvalid){int error=Marshal.GetLastWin32Error();handle.Dispose();throw new Win32Exception(error);}
        try{return new FileStream(handle,write?FileAccess.Write:FileAccess.Read);}catch{handle.Dispose();throw;}
    }
    public static void Write(string path,byte[] bytes) {
        if(bytes==null||bytes.Length<1||bytes.Length>4096)throw new ArgumentException("Invalid identity metadata size.");
        using(FileStream stream=Open(path,true)){stream.Write(bytes,0,bytes.Length);stream.Flush(true);}
    }
    public static byte[] Read(string path) {
        using(FileStream stream=Open(path,false)) {
            if(stream.Length<1||stream.Length>4096)throw new IOException("Invalid identity metadata size.");
            byte[] bytes=new byte[(int)stream.Length];int offset=0;
            while(offset<bytes.Length){int count=stream.Read(bytes,offset,bytes.Length-offset);if(count==0)throw new EndOfStreamException();offset+=count;}
            return bytes;
        }
    }
}

using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
using System.Text;
using System.Web.Script.Serialization;
using System.Windows.Forms;
[assembly: System.Reflection.AssemblyVersion("1.0.0.0")]

[ComVisible(true), Guid("B824B49D-22AC-4161-AC8A-9916E8FA3F7F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IInitializeWithStream { void Initialize(IStream stream, uint mode); }
[ComVisible(true), Guid("E357FCCD-A995-4576-B01F-234630154E96"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IThumbnailProvider { void GetThumbnail(uint size, out IntPtr bitmap, out uint alpha); }
[StructLayout(LayoutKind.Sequential)]
public struct PreviewRect { public int Left, Top, Right, Bottom; }
[ComVisible(true), Guid("8895B1C6-B41F-4C1C-A562-0D564250836F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IPreviewHandler
{
    void SetWindow(IntPtr parent, ref PreviewRect rect);
    void SetRect(ref PreviewRect rect);
    void DoPreview();
    void Unload();
    void SetFocus();
    void QueryFocus(out IntPtr window);
    [PreserveSig] int TranslateAccelerator(IntPtr message);
}
[ComVisible(true), Guid("00000114-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IOleWindow
{
    void GetWindow(out IntPtr window);
    void ContextSensitiveHelp(bool enter);
}

internal sealed class ShellStream : Stream
{
    private readonly IStream source;
    internal ShellStream(IStream source) { this.source = source; }
    public override bool CanRead { get { return true; } }
    public override bool CanSeek { get { return true; } }
    public override bool CanWrite { get { return false; } }
    public override long Length { get { System.Runtime.InteropServices.ComTypes.STATSTG info; source.Stat(out info, 1); return info.cbSize; } }
    public override long Position { get { return Seek(0, SeekOrigin.Current); } set { Seek(value, SeekOrigin.Begin); } }
    public override int Read(byte[] buffer, int offset, int count)
    {
        byte[] target = offset == 0 ? buffer : new byte[count];
        IntPtr result = Marshal.AllocCoTaskMem(4);
        try
        {
            source.Read(target, count, result);
            int read = Marshal.ReadInt32(result);
            if (read < 0 || read > count) throw new InvalidDataException();
            if (offset != 0) Buffer.BlockCopy(target, 0, buffer, offset, read);
            return read;
        }
        finally { Marshal.FreeCoTaskMem(result); }
    }
    public override long Seek(long offset, SeekOrigin origin)
    {
        IntPtr result = Marshal.AllocCoTaskMem(8);
        try { source.Seek(offset, (int)origin, result); return Marshal.ReadInt64(result); }
        finally { Marshal.FreeCoTaskMem(result); }
    }
    public override void Flush() {}
    public override void SetLength(long value) { throw new NotSupportedException(); }
    public override void Write(byte[] buffer, int offset, int count) { throw new NotSupportedException(); }
}

internal static class EmbeddedPreview
{
    internal static Bitmap Read(Stream source)
    {
        if (source.Length > 5L * 1024 * 1024 * 1024) throw new InvalidDataException();
        source.Position = 0;
        int first = source.ReadByte();
        source.Position = 0;
        string json;
        if (first == 'P')
        {
            using (var zip = new ZipArchive(source, ZipArchiveMode.Read, true))
            {
                if (zip.Entries.Count > 10001) throw new InvalidDataException();
                ZipArchiveEntry document = null;
                foreach (var entry in zip.Entries)
                {
                    if (entry.FullName != "publication.json") continue;
                    if (document != null || entry.Length > 16 * 1024 * 1024 ||
                        entry.CompressedLength > 16 * 1024 * 1024) throw new InvalidDataException();
                    document = entry;
                }
                if (document == null) throw new InvalidDataException();
                using (var input = document.Open()) json = ReadJson(input, 16 * 1024 * 1024);
            }
        }
        else
        {
            if (source.Length > 8 * 1024 * 1024) throw new InvalidDataException();
            json = ReadJson(source, 8 * 1024 * 1024);
        }
        var parser = new JavaScriptSerializer { MaxJsonLength = 16 * 1024 * 1024, RecursionLimit = 100 };
        var data = parser.Deserialize<Dictionary<string, object>>(json);
        object value;
        if (!data.TryGetValue("previewPng", out value) || !(value is string) ||
            ((string)value).Length > 1400000) throw new InvalidDataException("Save the design in updated SmartStudio.");
        byte[] png = Convert.FromBase64String((string)value);
        if (png.Length < 24 || BitConverter.ToString(png, 0, 8) != "89-50-4E-47-0D-0A-1A-0A" ||
            Dimension(png, 16) < 1 || Dimension(png, 16) > 2048 ||
            Dimension(png, 20) < 1 || Dimension(png, 20) > 2048) throw new InvalidDataException();
        using (var input = new MemoryStream(png))
        using (var image = Image.FromStream(input, true, true)) return new Bitmap(image);
    }
    private static uint Dimension(byte[] bytes, int offset)
    {
        return ((uint)bytes[offset] << 24) | ((uint)bytes[offset + 1] << 16) |
            ((uint)bytes[offset + 2] << 8) | bytes[offset + 3];
    }
    private static string ReadJson(Stream source, int limit)
    {
        using (var output = new MemoryStream())
        {
            byte[] block = new byte[8192];
            int count;
            while ((count = source.Read(block, 0, block.Length)) > 0)
            {
                if (output.Length + count > limit) throw new InvalidDataException();
                output.Write(block, 0, count);
            }
            return Encoding.UTF8.GetString(output.ToArray());
        }
    }
}

[ComVisible(true), Guid("A93BB9B4-B568-494A-85F0-5627163CA83B"), ClassInterface(ClassInterfaceType.None)]
public sealed class StudioThumbnail : IInitializeWithStream, IThumbnailProvider
{
    private IStream stream;
    public void Initialize(IStream source, uint mode)
    {
        if (stream != null) throw new InvalidOperationException();
        stream = source;
    }
    public void GetThumbnail(uint size, out IntPtr bitmap, out uint alpha)
    {
        if (stream == null || size < 1 || size > 2048) throw new InvalidDataException();
        using (var image = EmbeddedPreview.Read(new ShellStream(stream)))
        {
            double scale = Math.Min((double)size / image.Width, (double)size / image.Height);
            using (var thumbnail = new Bitmap(Math.Max(1, (int)(image.Width * scale)), Math.Max(1, (int)(image.Height * scale))))
            {
                using (var graphics = Graphics.FromImage(thumbnail))
                {
                    graphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                    graphics.DrawImage(image, 0, 0, thumbnail.Width, thumbnail.Height);
                }
                bitmap = thumbnail.GetHbitmap(Color.FromArgb(0));
                alpha = 2;
            }
        }
    }
}

[ComVisible(true), Guid("66BBA6C8-7598-43D5-96AF-A20F62CC7DB1"), ClassInterface(ClassInterfaceType.None)]
public sealed class StudioPreviewHandler : IInitializeWithStream, IPreviewHandler, IOleWindow
{
    private IStream stream;
    private IntPtr parent;
    private PreviewRect bounds;
    private PictureBox view;
    [DllImport("user32.dll")] private static extern IntPtr SetParent(IntPtr child, IntPtr parent);
    [DllImport("user32.dll")] private static extern IntPtr GetFocus();
    public void Initialize(IStream source, uint mode)
    {
        if (stream != null) throw new InvalidOperationException();
        stream = source;
    }
    public void SetWindow(IntPtr window, ref PreviewRect rect)
    {
        parent = window;
        SetRect(ref rect);
        if (view != null) SetParent(view.Handle, parent);
    }
    public void SetRect(ref PreviewRect rect)
    {
        bounds = rect;
        if (view != null) view.SetBounds(rect.Left, rect.Top, Math.Max(0, rect.Right - rect.Left), Math.Max(0, rect.Bottom - rect.Top));
    }
    public void DoPreview()
    {
        if (stream == null || view != null) throw new InvalidOperationException();
        var image = EmbeddedPreview.Read(new ShellStream(stream));
        view = new PictureBox { Image = image, SizeMode = PictureBoxSizeMode.Zoom, BackColor = Color.White };
        SetParent(view.Handle, parent);
        SetRect(ref bounds);
        view.Show();
    }
    public void Unload()
    {
        if (view != null) { var image = view.Image; view.Dispose(); if (image != null) image.Dispose(); view = null; }
        stream = null;
    }
    public void SetFocus() { if (view != null) view.Focus(); }
    public void QueryFocus(out IntPtr window) { window = GetFocus(); }
    public int TranslateAccelerator(IntPtr message) { return 1; }
    public void GetWindow(out IntPtr window) { window = view == null ? IntPtr.Zero : view.Handle; }
    public void ContextSensitiveHelp(bool enter) { throw new COMException("Unsupported.", unchecked((int)0x80004001)); }
}

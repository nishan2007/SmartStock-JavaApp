using System;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Text;
using System.Web.Script.Serialization;

internal static class PreviewSmokeTest
{
    private static void Main()
    {
        byte[] png;
        using (var image = new Bitmap(20, 10))
        using (var output = new MemoryStream())
        {
            image.Save(output, System.Drawing.Imaging.ImageFormat.Png);
            png = output.ToArray();
        }
        var serializer = new JavaScriptSerializer();
        string json = serializer.Serialize(new { previewPng = Convert.ToBase64String(png) });
        using (var source = new MemoryStream(Encoding.UTF8.GetBytes(json)))
        using (var preview = EmbeddedPreview.Read(source))
        {
            if (preview.Width != 20 || preview.Height != 10) throw new Exception("Badge preview dimensions changed.");
        }
        using (var archive = new MemoryStream())
        {
            using (var zip = new ZipArchive(archive, ZipArchiveMode.Create, true))
            using (var writer = new StreamWriter(zip.CreateEntry("publication.json").Open()))
                writer.Write(json);
            archive.Position = 0;
            using (var preview = EmbeddedPreview.Read(archive))
                if (preview.Width != 20) throw new Exception("Publication preview is missing.");
        }
        bool rejected = false;
        using (var input = new MemoryStream(Encoding.UTF8.GetBytes("{}")))
        {
            try { using (var preview = EmbeddedPreview.Read(input)) {} }
            catch (InvalidDataException) { rejected = true; }
        }
        if (!rejected) throw new Exception("A file without a preview was accepted.");
        Console.WriteLine("SmartStudio shell preview extraction passed.");
    }
}

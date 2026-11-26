using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.ServiceProcess;
using System.Web.Script.Serialization;

internal sealed class SmartDriveService : ServiceBase
{
    private Process worker;
    private StreamWriter log;
    private string logPath;
    private readonly object logLock = new object();
    private volatile bool stopping;

    internal SmartDriveService() { ServiceName = "SmartDrive"; CanStop = true; AutoLog = true; }

    private void WriteLog(string message)
    {
        if (message == null) return;
        lock (logLock)
        {
            if (log != null)
            {
                if (log.BaseStream.Length > 10 * 1024 * 1024)
                {
                    log.Dispose();
                    string previous = logPath + ".previous";
                    if (File.Exists(previous)) File.Delete(previous);
                    File.Move(logPath, previous);
                    log = new StreamWriter(new FileStream(logPath, FileMode.CreateNew, FileAccess.Write, FileShare.Read));
                }
                log.WriteLine(DateTime.UtcNow.ToString("o") + " " + message);
                log.Flush();
            }
        }
    }

    private static string Quote(string path)
    {
        if (path.IndexOf('"') >= 0 || !Path.IsPathRooted(path)) throw new InvalidDataException("Invalid service path.");
        return "\"" + path + "\"";
    }

    protected override void OnStart(string[] args)
    {
        var serializer = new JavaScriptSerializer();
        string settingsPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "service.json");
        var settings = serializer.Deserialize<Dictionary<string, object>>(File.ReadAllText(settingsPath));
        string app = (string)settings["appRoot"];
        string node = (string)settings["nodePath"];
        string config = (string)settings["configPath"];
        logPath = (string)settings["logPath"];
        if (File.Exists(logPath) && new FileInfo(logPath).Length > 10 * 1024 * 1024)
        {
            string previous = logPath + ".previous";
            if (File.Exists(previous)) File.Delete(previous);
            File.Move(logPath, previous);
        }
        log = new StreamWriter(new FileStream(logPath, FileMode.Append, FileAccess.Write, FileShare.Read));
        var start = new ProcessStartInfo(node, Quote(Path.Combine(app, "src", "main.js")) + " " + Quote(config));
        start.WorkingDirectory = app;
        start.UseShellExecute = false;
        start.CreateNoWindow = true;
        start.RedirectStandardOutput = true;
        start.RedirectStandardError = true;
        var environment = (Dictionary<string, object>)settings["environment"];
        foreach (var pair in environment) start.EnvironmentVariables[pair.Key] = (string)pair.Value;
        worker = new Process { StartInfo = start, EnableRaisingEvents = true };
        worker.OutputDataReceived += (sender, eventArgs) => WriteLog(eventArgs.Data);
        worker.ErrorDataReceived += (sender, eventArgs) => WriteLog(eventArgs.Data);
        worker.Exited += (sender, eventArgs) => { if (!stopping) Environment.Exit(1); };
        if (!worker.Start()) throw new InvalidOperationException("SmartDrive could not start.");
        worker.BeginOutputReadLine();
        worker.BeginErrorReadLine();
    }

    protected override void OnStop()
    {
        stopping = true;
        if (worker != null && !worker.HasExited)
        {
            var stop = new ProcessStartInfo(Path.Combine(Environment.SystemDirectory, "taskkill.exe"),
                "/PID " + worker.Id + " /T /F");
            stop.UseShellExecute = false;
            stop.CreateNoWindow = true;
            using (var terminator = Process.Start(stop)) terminator.WaitForExit(15000);
            worker.WaitForExit(10000);
        }
        if (worker != null) worker.Dispose();
        lock (logLock) { if (log != null) { log.Dispose(); log = null; } }
    }

    private static void Main() { ServiceBase.Run(new SmartDriveService()); }
}

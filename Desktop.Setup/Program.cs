using System.Diagnostics;
using System.Reflection;
using System.Runtime.InteropServices;

try
{
    KillRunningApp();

    var dest = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "AETDesktop");
    var cfg = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "AETDesktop");

    RemoveOldInstall(dest);
    RemoveDesktopShortcut();

    Directory.CreateDirectory(dest);
    Directory.CreateDirectory(cfg);

    var exe = Path.Combine(dest, "AETDesktop.exe");
    Extract("AETDesktop.exe", exe);
    Extract("default_settings.json", Path.Combine(dest, "default_settings.json"));
    Extract("desktop-settings.json", Path.Combine(cfg, "desktop-settings.json"));
    CreateShortcut(exe, dest);

    Process.Start(new ProcessStartInfo(exe)
    {
        WorkingDirectory = dest,
        UseShellExecute = true,
    });
    MessageBox(
        "Старая версия удалена.\nНовая версия AET Desktop установлена.\n\n" +
        "Программа: " + dest + "\nНастройки: " + cfg,
        "AET Desktop",
        0x40);
}
catch (Exception ex)
{
    MessageBox(ex.Message, "AET Desktop", 0x10);
}

static void KillRunningApp()
{
    foreach (var process in Process.GetProcessesByName("AETDesktop"))
    {
        try { process.Kill(entireProcessTree: true); } catch { /* already gone */ }
    }

    var deadline = DateTime.UtcNow.AddSeconds(8);
    while (DateTime.UtcNow < deadline && Process.GetProcessesByName("AETDesktop").Length > 0)
    {
        Thread.Sleep(200);
    }
}

static void RemoveOldInstall(string dest)
{
    if (!Directory.Exists(dest)) return;

    // Retry a few times in case antivirus briefly locks the exe after Kill.
    Exception? last = null;
    for (var attempt = 0; attempt < 6; attempt++)
    {
        try
        {
            Directory.Delete(dest, recursive: true);
            return;
        }
        catch (Exception ex)
        {
            last = ex;
            Thread.Sleep(300);
            KillRunningApp();
        }
    }

    throw new InvalidOperationException(
        "Не удалось удалить старую версию:\n" + dest + "\n\n" + last?.Message);
}

static void RemoveDesktopShortcut()
{
    var desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
    var link = Path.Combine(desktop, "AET Desktop.lnk");
    if (File.Exists(link))
    {
        try { File.Delete(link); } catch { /* recreate below */ }
    }
}

static void Extract(string fileName, string dest)
{
    var assembly = Assembly.GetExecutingAssembly();
    var name = assembly.GetManifestResourceNames()
        .FirstOrDefault(item => item.EndsWith(fileName, StringComparison.OrdinalIgnoreCase))
        ?? throw new InvalidOperationException("В установщике нет файла " + fileName);
    using var source = assembly.GetManifestResourceStream(name)
        ?? throw new InvalidOperationException("Не удалось прочитать " + fileName);
    using var target = File.Create(dest);
    source.CopyTo(target);
}

static void CreateShortcut(string exe, string workDir)
{
    var desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
    var link = Path.Combine(desktop, "AET Desktop.lnk");
    var shellType = Type.GetTypeFromProgID("WScript.Shell")
        ?? throw new InvalidOperationException("WScript.Shell");
    dynamic shell = Activator.CreateInstance(shellType)!;
    dynamic shortcut = shell.CreateShortcut(link);
    shortcut.TargetPath = exe;
    shortcut.WorkingDirectory = workDir;
    shortcut.Description = "AET Desktop";
    shortcut.Save();
}

[DllImport("user32.dll", CharSet = CharSet.Unicode)]
static extern int MessageBoxW(IntPtr hWnd, string text, string caption, uint type);

static void MessageBox(string text, string caption, uint type) =>
    MessageBoxW(IntPtr.Zero, text, caption, type);

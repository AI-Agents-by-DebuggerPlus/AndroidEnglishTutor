using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Desktop.Wpf;

public sealed class MirrorSettings
{
    [JsonPropertyName("supabaseUrl")]
    public string SupabaseUrl { get; set; } = "";

    [JsonPropertyName("supabaseAnonKey")]
    public string SupabaseAnonKey { get; set; } = "";

    [JsonPropertyName("senderName")]
    public string SenderName { get; set; } = "AndroidEnglishTutor";

    [JsonPropertyName("useAnonymousAuth")]
    public bool UseAnonymousAuth { get; set; } = true;
}

public sealed class StudyDisplaySnapshot
{
    public bool Active { get; set; }
    public bool Blanked { get; set; }
    public string TopicTitle { get; set; } = "";
    public string StageLabel { get; set; } = "";
    public int CardIndex { get; set; }
    public int CardCount { get; set; }
    public string English { get; set; } = "";
    public string Russian { get; set; } = "";
    public string StatusMessage { get; set; } = "";
    public string EnglishColor { get; set; } = "#FFE8F4F3";
    public string RussianColor { get; set; } = "#FF8AA8A6";
    public string BackgroundColor { get; set; } = "#FF000000";
    public long Epoch { get; set; }
    public long CountdownEndsAtEpochMs { get; set; }
    public long CountdownTotalMs { get; set; }
    public bool Hidden { get; set; }
}

public sealed class DesktopLocalSettings
{
    public double Zoom { get; set; } = 1.0;
    public int PauseSeconds { get; set; } = 30;
    public int Brightness { get; set; } = 100;
    public bool FadeBrightness { get; set; }
    public int TopPadding { get; set; }
    public string EnglishColor { get; set; } = "#FFE8F4F3";
    public string RussianColor { get; set; } = "#FF8AA8A6";
}

public static class DesktopLocalSettingsStore
{
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public static string FilePath =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "AETDesktop", "desktop-settings.json");

    public static DesktopLocalSettings Load()
    {
        try
        {
            if (!File.Exists(FilePath)) return new DesktopLocalSettings();
            return JsonSerializer.Deserialize<DesktopLocalSettings>(File.ReadAllText(FilePath), JsonOptions)
                ?? new DesktopLocalSettings();
        }
        catch
        {
            return new DesktopLocalSettings();
        }
    }

    public static void Save(DesktopLocalSettings settings)
    {
        var dir = Path.GetDirectoryName(FilePath);
        if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
        File.WriteAllText(FilePath, JsonSerializer.Serialize(settings, JsonOptions));
    }
}

public static class SettingsLoader
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
    };

    public static MirrorSettings? Load()
    {
        foreach (var path in Candidates())
        {
            if (!File.Exists(path)) continue;
            try
            {
                var text = File.ReadAllText(path);
                return JsonSerializer.Deserialize<MirrorSettings>(text, JsonOptions);
            }
            catch
            {
                // try the next location
            }
        }
        return null;
    }

    private static IEnumerable<string> Candidates()
    {
        var baseDir = AppContext.BaseDirectory;
        yield return Path.Combine(baseDir, "default_settings.json");
        yield return Path.Combine(Environment.CurrentDirectory, "default_settings.json");
        yield return Path.Combine(Environment.CurrentDirectory, "app", "src", "main", "assets", "default_settings.json");
        yield return Path.GetFullPath(Path.Combine(baseDir, "..", "..", "..", "..", "app", "src", "main", "assets", "default_settings.json"));
        yield return Path.GetFullPath(Path.Combine(Environment.CurrentDirectory, "..", "app", "src", "main", "assets", "default_settings.json"));
    }
}

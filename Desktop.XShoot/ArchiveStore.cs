using System.IO;
using System.Reflection;
using System.Text.Json;

namespace Desktop.XShoot;

public static class ArchiveStore
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public static string AppDir =>
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "AETXShoot");

    public static string ArchivePath => Path.Combine(AppDir, "guessed_archive.json");

    public static string SettingsPath => Path.Combine(AppDir, "settings.json");

    public static GuessedArchive Load()
    {
        Directory.CreateDirectory(AppDir);
        if (!File.Exists(ArchivePath))
            return new GuessedArchive();
        return JsonSerializer.Deserialize<GuessedArchive>(File.ReadAllText(ArchivePath), JsonOptions)
            ?? new GuessedArchive();
    }

    public static HashSet<string> LoadKeys() =>
        Load().Words
            .Select(Normalize)
            .Where(static w => w.Length > 0)
            .ToHashSet(StringComparer.Ordinal);

    public static void Save(IEnumerable<string> words)
    {
        Directory.CreateDirectory(AppDir);
        var archive = new GuessedArchive
        {
            Version = 1,
            UpdatedUtc = DateTime.UtcNow.ToString("o"),
            Words = words
                .Select(Normalize)
                .Where(static w => w.Length > 0)
                .Distinct(StringComparer.Ordinal)
                .OrderBy(static w => w, StringComparer.Ordinal)
                .ToList(),
        };
        File.WriteAllText(ArchivePath, JsonSerializer.Serialize(archive, JsonOptions));
        TrySyncToAetRepo(archive);
    }

    public static void Add(string english)
    {
        var key = Normalize(english);
        if (key.Length == 0) return;
        var keys = LoadKeys();
        if (!keys.Add(key)) return;
        Save(keys);
    }

    public static XShootSettings LoadSettings()
    {
        Directory.CreateDirectory(AppDir);
        if (!File.Exists(SettingsPath))
            return new XShootSettings();
        return JsonSerializer.Deserialize<XShootSettings>(File.ReadAllText(SettingsPath), JsonOptions)
            ?? new XShootSettings();
    }

    public static void SaveSettings(XShootSettings settings)
    {
        Directory.CreateDirectory(AppDir);
        File.WriteAllText(SettingsPath, JsonSerializer.Serialize(settings, JsonOptions));
    }

    public static List<DictWord> LoadDictionary()
    {
        var assembly = Assembly.GetExecutingAssembly();
        using var stream = assembly.GetManifestResourceStream("dictionary.json")
            ?? throw new InvalidOperationException("Словарь dictionary.json не встроен в приложение.");
        using var reader = new StreamReader(stream);
        var file = JsonSerializer.Deserialize<DictionaryFile>(reader.ReadToEnd(), new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true,
        }) ?? new DictionaryFile();
        return file.Words
            .Where(w => !string.IsNullOrWhiteSpace(w.En) && !string.IsNullOrWhiteSpace(w.Ru))
            .GroupBy(w => w.Key)
            .Select(g => g.First())
            .OrderBy(w => w.En, StringComparer.OrdinalIgnoreCase)
            .ToList();
    }

    /// <summary>
    /// Keep a copy next to AET lesson scripts/assets when running from a developer machine.
    /// </summary>
    private static void TrySyncToAetRepo(GuessedArchive archive)
    {
        var candidates = new[]
        {
            FindRepoRootFrom(AppContext.BaseDirectory),
            FindRepoRootFrom(Environment.CurrentDirectory),
        };
        foreach (var root in candidates.Where(static r => r != null).Distinct(StringComparer.OrdinalIgnoreCase))
        {
            try
            {
                var filesDir = Path.Combine(root!, "Files");
                Directory.CreateDirectory(filesDir);
                File.WriteAllText(
                    Path.Combine(filesDir, "xshoot_archive.json"),
                    JsonSerializer.Serialize(archive, JsonOptions));

                var assets = Path.Combine(root!, "app", "src", "main", "assets");
                if (Directory.Exists(assets))
                {
                    File.WriteAllText(
                        Path.Combine(assets, "xshoot_archive.json"),
                        JsonSerializer.Serialize(archive, JsonOptions));
                }
            }
            catch
            {
                // Optional sync only.
            }
        }
    }

    private static string? FindRepoRootFrom(string start)
    {
        try
        {
            var dir = new DirectoryInfo(start);
            while (dir != null)
            {
                if (File.Exists(Path.Combine(dir.FullName, "settings.gradle.kts")) &&
                    Directory.Exists(Path.Combine(dir.FullName, "app", "src", "main", "assets", "topics")))
                {
                    return dir.FullName;
                }
                dir = dir.Parent;
            }
        }
        catch
        {
            // ignore
        }
        return null;
    }

    public static string Normalize(string value) =>
        value.Trim().ToLowerInvariant();
}

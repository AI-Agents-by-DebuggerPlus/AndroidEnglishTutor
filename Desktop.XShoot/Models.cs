using System.Text.Json.Serialization;

namespace Desktop.XShoot;

public sealed class DictWord
{
    public string En { get; set; } = "";
    public string Ru { get; set; } = "";
    public bool Concise { get; set; } = true;

    public string Key => En.Trim().ToLowerInvariant();

    public string PrimaryRu =>
        Ru.Split('|', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .FirstOrDefault()
        ?? Ru;
}

public sealed class DictionaryFile
{
    public List<DictWord> Words { get; set; } = [];
}

public sealed class GuessedArchive
{
    public int Version { get; set; } = 1;
    public string UpdatedUtc { get; set; } = "";
    public List<string> Words { get; set; } = [];
}

public sealed class XShootSettings
{
    public bool LearningMode { get; set; } = true;
    public bool ConciseOnly { get; set; }
    public int BatchSize { get; set; } = 12;
    public int TargetSeconds { get; set; } = 12;
}

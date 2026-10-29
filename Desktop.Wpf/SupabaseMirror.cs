using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace Desktop.Wpf;

public sealed class SupabaseMirror : IDisposable
{
    private const string Prefix = "[STUDY:Display]";
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
    };

    private HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(15) };
    private readonly MirrorSettings _settings;
    private string? _accessToken;

    public SupabaseMirror(MirrorSettings settings)
    {
        _settings = settings;
    }

    public void ResetConnection()
    {
        _accessToken = null;
        var previous = _http;
        _http = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        previous.Dispose();
    }

    public async Task<StudyDisplaySnapshot?> FetchLatestAsync(CancellationToken cancellationToken)
    {
        try
        {
            return await FetchOnceAsync(cancellationToken);
        }
        catch (SupabaseStatusException ex) when (ex.Status is 401 or 403)
        {
            ResetConnection();
            return await FetchOnceAsync(cancellationToken);
        }
    }

    private async Task<StudyDisplaySnapshot?> FetchOnceAsync(CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(_accessToken) && _settings.UseAnonymousAuth)
        {
            await TryAnonymousSignInAsync(cancellationToken);
        }

        var sender = string.IsNullOrWhiteSpace(_settings.SenderName)
            ? "AndroidEnglishTutor"
            : _settings.SenderName.Trim();
        var url = _settings.SupabaseUrl.TrimEnd('/')
            + "/rest/v1/messages?select=content,created_at"
            + "&sender_name=eq." + Uri.EscapeDataString(sender)
            + "&content=like." + Uri.EscapeDataString(Prefix + "*")
            + "&order=created_at.desc&limit=1";

        using var request = Authorized(HttpMethod.Get, url);
        using var response = await _http.SendAsync(request, cancellationToken);
        var body = await response.Content.ReadAsStringAsync(cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            throw new SupabaseStatusException((int)response.StatusCode, Trim(body));
        }

        using var doc = JsonDocument.Parse(body);
        if (doc.RootElement.ValueKind != JsonValueKind.Array || doc.RootElement.GetArrayLength() == 0)
        {
            return null;
        }

        var content = doc.RootElement[0].GetProperty("content").GetString() ?? "";
        var payload = content.StartsWith(Prefix, StringComparison.Ordinal)
            ? content[Prefix.Length..].Trim()
            : content.Trim();
        return JsonSerializer.Deserialize<StudyDisplaySnapshot>(payload, JsonOptions);
    }

    private async Task TryAnonymousSignInAsync(CancellationToken cancellationToken)
    {
        var url = _settings.SupabaseUrl.TrimEnd('/') + "/auth/v1/signup";
        using var request = new HttpRequestMessage(HttpMethod.Post, url);
        AddKey(request);
        request.Content = new StringContent("{}", Encoding.UTF8, "application/json");
        using var response = await _http.SendAsync(request, cancellationToken);
        if (!response.IsSuccessStatusCode) return;
        var body = await response.Content.ReadAsStringAsync(cancellationToken);
        using var doc = JsonDocument.Parse(body);
        if (doc.RootElement.TryGetProperty("access_token", out var token))
        {
            _accessToken = token.GetString();
        }
    }

    private HttpRequestMessage Authorized(HttpMethod method, string url)
    {
        var request = new HttpRequestMessage(method, url);
        AddKey(request);
        if (!string.IsNullOrWhiteSpace(_accessToken))
        {
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _accessToken);
        }
        return request;
    }

    private void AddKey(HttpRequestMessage request)
    {
        var key = _settings.SupabaseAnonKey.Trim();
        request.Headers.TryAddWithoutValidation("apikey", key);
        request.Headers.Authorization ??= new AuthenticationHeaderValue("Bearer", key);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
    }

    private static string Trim(string body) =>
        body.Length <= 240 ? body : body[..240];

    public void Dispose() => _http.Dispose();

    private sealed class SupabaseStatusException : Exception
    {
        public int Status { get; }

        public SupabaseStatusException(int status, string body)
            : base($"Supabase {status}: {body}")
        {
            Status = status;
        }
    }
}

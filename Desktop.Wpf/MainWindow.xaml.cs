using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;

namespace Desktop.Wpf;

public partial class MainWindow : Window
{
    private readonly DispatcherTimer _pollTimer = new() { Interval = TimeSpan.FromMilliseconds(700) };
    private readonly DispatcherTimer _tickTimer = new() { Interval = TimeSpan.FromMilliseconds(50) };
    private readonly DispatcherTimer _chromeTimer = new() { Interval = TimeSpan.FromMilliseconds(2500) };
    private readonly DispatcherTimer _linkTimer = new() { Interval = TimeSpan.FromMinutes(1) };
    private readonly DispatcherTimer _wakeTimer = new() { Interval = TimeSpan.FromMinutes(5) };
    private readonly DesktopLocalSettings _local;
    private SupabaseMirror? _mirror;
    private StudyDisplaySnapshot? _snapshot;
    private LocalSettingsWindow? _settingsWindow;
    private CancellationTokenSource? _pollCts;
    private int _pollBusy;
    private double _zoom = 1.0;
    private bool _covered;
    private bool _heldOpen;
    private bool _linkOk;
    private bool _inputActive = true;
    private DateTime _shownAtUtc = DateTime.UtcNow;
    private DateTime _lastSuccessUtc = DateTime.MinValue;
    private DateTime _nextResetUtc = DateTime.MinValue;
    private DateTime _ignoreMouseUntilUtc = DateTime.MinValue;
    private DateTime _lastInputUtc = DateTime.UtcNow;
    private Point _lastMouse;
    private bool _hasLastMouse;
    private const double ZoomMin = 0.5;
    private const double ZoomMax = 3.0;
    private const double ZoomStep = 0.1;

    public MainWindow()
    {
        InitializeComponent();
        _local = DesktopLocalSettingsStore.Load();
        _local.PauseSeconds = Math.Clamp(_local.PauseSeconds, 0, 300);
        _local.Brightness = Math.Clamp(_local.Brightness, 20, 100);
        _local.TopPadding = Math.Clamp(_local.TopPadding, 0, 400);
        _zoom = Math.Round(Math.Clamp(_local.Zoom, ZoomMin, ZoomMax), 1);
        _pollTimer.Tick += async (_, _) => await PollAsync();
        _tickTimer.Tick += (_, _) => OnTick();
        _linkTimer.Tick += async (_, _) => await CheckLinkAsync();
        _wakeTimer.Tick += (_, _) => StayAwake();
        _chromeTimer.Tick += (_, _) =>
        {
            _chromeTimer.Stop();
            Chrome.Visibility = Visibility.Collapsed;
        };
        Loaded += async (_, _) => await StartAsync();
        Closed += (_, _) =>
        {
            _pollCts?.Cancel();
            _settingsWindow?.Close();
            _mirror?.Dispose();
            AllowSleep();
        };
        StayAwake();
        _wakeTimer.Start();
        ShowChrome();
    }

    private async Task StartAsync()
    {
        var settings = SettingsLoader.Load();
        if (settings == null ||
            string.IsNullOrWhiteSpace(settings.SupabaseUrl) ||
            string.IsNullOrWhiteSpace(settings.SupabaseAnonKey))
        {
            IdleStatus.Text = "Нет supabaseUrl / supabaseAnonKey (default_settings.json)";
            return;
        }

        _mirror = new SupabaseMirror(settings);
        IdleStatus.Text = "Ожидание урока с телефона";
        _tickTimer.Start();
        _linkTimer.Start();
        await PollAsync();
        _pollTimer.Start();
    }

    private async Task CheckLinkAsync()
    {
        if (_mirror == null) return;
        if (_linkOk && DateTime.UtcNow - _lastSuccessUtc < TimeSpan.FromSeconds(20)) return;
        _nextResetUtc = DateTime.UtcNow.AddMinutes(1);
        _pollCts?.Cancel();
        var waitUntil = DateTime.UtcNow.AddSeconds(2);
        while (Volatile.Read(ref _pollBusy) == 1 && DateTime.UtcNow < waitUntil)
        {
            await Task.Delay(50);
        }

        _mirror.ResetConnection();
        await PollAsync();
    }

    private async Task PollAsync()
    {
        if (_mirror == null || Interlocked.Exchange(ref _pollBusy, 1) == 1) return;
        _pollCts?.Dispose();
        _pollCts = new CancellationTokenSource();
        var token = _pollCts.Token;
        try
        {
            var previous = _snapshot;
            var next = await _mirror.FetchLatestAsync(token);
            _linkOk = true;
            _lastSuccessUtc = DateTime.UtcNow;
            if (next == null)
            {
                _snapshot = null;
                _covered = false;
                Mouse.OverrideCursor = null;
                ShowIdle();
                return;
            }

            var epochChanged = next.Epoch != previous?.Epoch;
            var wasPhoneBlank = previous is { Blanked: true };
            if (epochChanged)
            {
                _heldOpen = false;
                _shownAtUtc = DateTime.UtcNow;
                _inputActive = false;
                _lastInputUtc = DateTime.MinValue;
                _hasLastMouse = false;
                _ignoreMouseUntilUtc = DateTime.UtcNow.AddMilliseconds(500);
                Chrome.Visibility = Visibility.Collapsed;
                _chromeTimer.Stop();
                // Hidden mode blacks only the phone. Desktop keeps the card.
                _covered = HasPicture(next) && next.Blanked;
            }
            else if (!_heldOpen && HasPicture(next) && next.Blanked)
            {
                _covered = true;
            }
            else if (!_heldOpen && _covered && wasPhoneBlank && HasPicture(next) && !next.Blanked)
            {
                _covered = false;
                _shownAtUtc = DateTime.UtcNow;
            }

            _snapshot = next;
            RenderCard();
        }
        catch (OperationCanceledException)
        {
            return;
        }
        catch (Exception ex)
        {
            _linkOk = false;
            IdleStatus.Text = "Supabase: " + ex.Message;
            if (_snapshot is not { Active: true })
            {
                ShowIdle();
            }

            if (_mirror != null && DateTime.UtcNow >= _nextResetUtc)
            {
                _nextResetUtc = DateTime.UtcNow.AddMinutes(1);
                _mirror.ResetConnection();
            }
        }
        finally
        {
            Interlocked.Exchange(ref _pollBusy, 0);
        }
    }

    private static bool HasPicture(StudyDisplaySnapshot? snap) =>
        snap is { Active: true } &&
        (!string.IsNullOrWhiteSpace(snap.English) || !string.IsNullOrWhiteSpace(snap.Russian));

    private void OnTick()
    {
        if (!_covered && HasPicture(_snapshot) && _local.PauseSeconds > 0)
        {
            var elapsed = DateTime.UtcNow - _shownAtUtc;
            if (elapsed >= TimeSpan.FromSeconds(_local.PauseSeconds))
            {
                Cover();
            }
        }

        ApplyCursor();

        RenderCountdown();
        ApplyBrightness();
    }

    private void Cover()
    {
        _covered = true;
        _heldOpen = false;
        HideCursor();
        Chrome.Visibility = Visibility.Collapsed;
        _chromeTimer.Stop();
        RenderCard();
    }

    private void Reveal()
    {
        if (!HasPicture(_snapshot)) return;
        _covered = false;
        _heldOpen = true;
        _inputActive = true;
        _shownAtUtc = DateTime.UtcNow;
        Mouse.OverrideCursor = null;
        BottomBar.Visibility = Visibility.Visible;
        RenderCard();
        ShowChrome();
    }

    private static void HideCursor() => Mouse.OverrideCursor = Cursors.None;

    private void RenderCard()
    {
        var snap = _snapshot;
        if (snap == null || !snap.Active)
        {
            _covered = false;
            Mouse.OverrideCursor = null;
            Background = Brushes.Black;
            DimCover.Visibility = Visibility.Collapsed;
            ShowIdle();
            CountdownPanel.Visibility = Visibility.Collapsed;
            TopicStage.Visibility = Visibility.Collapsed;
            ApplyZoom();
            ApplyPresence();
            ApplyBrightness();
            return;
        }

        TopicText.Text = string.IsNullOrWhiteSpace(snap.TopicTitle) ? "Урок" : snap.TopicTitle;
        var count = Math.Max(snap.CardCount, 1);
        StageText.Text = $"{snap.StageLabel} · {snap.CardIndex}/{count}";
        EnglishText.Text = string.IsNullOrWhiteSpace(snap.English)
            ? (string.IsNullOrWhiteSpace(snap.StatusMessage) ? "Next — показать следующее слово" : snap.StatusMessage)
            : snap.English;
        EnglishText.Foreground = BrushFrom(_local.EnglishColor, Brushes.White);
        EnglishText.FontSize = EnglishSize(EnglishText.Text.Length) * _zoom;
        RussianText.Text = snap.Russian;
        RussianText.Foreground = BrushFrom(_local.RussianColor, new SolidColorBrush(Color.FromRgb(0x8A, 0xA8, 0xA6)));
        RussianText.FontSize = Math.Max(16 * _zoom, EnglishText.FontSize * 0.62);
        RussianText.Visibility = string.IsNullOrWhiteSpace(snap.Russian) ? Visibility.Collapsed : Visibility.Visible;
        ApplyZoom();

        if (_covered || !HasPicture(snap))
        {
            Background = Brushes.Black;
            IdlePanel.Visibility = HasPicture(snap) ? Visibility.Collapsed : Visibility.Visible;
            CardPanel.Visibility = Visibility.Collapsed;
            CountdownPanel.Visibility = Visibility.Collapsed;
            TopicStage.Visibility = Visibility.Collapsed;
            DimCover.Visibility = _covered ? Visibility.Visible : Visibility.Collapsed;
            ApplyPresence();
            ApplyBrightness();
            return;
        }

        Background = BrushFrom(snap.BackgroundColor, Brushes.Black);
        IdlePanel.Visibility = Visibility.Collapsed;
        CardPanel.Visibility = Visibility.Visible;
        TopicStage.Visibility = Visibility.Visible;
        DimCover.Visibility = Visibility.Collapsed;
        RenderCountdown();
        ApplyPresence();
        ApplyBrightness();
    }

    private void ApplyBrightness()
    {
        var level = _local.Brightness;
        if (_local.FadeBrightness && !_covered && HasPicture(_snapshot) && _local.PauseSeconds > 0)
        {
            var totalMs = _local.PauseSeconds * 1000.0;
            var left = totalMs - (DateTime.UtcNow - _shownAtUtc).TotalMilliseconds;
            var remain = Math.Clamp(left / totalMs, 0, 1);
            level = (int)Math.Round(level * remain);
        }

        BrightnessCover.Opacity = (100 - Math.Clamp(level, 0, 100)) / 100.0;
    }

    private void ApplyPresence()
    {
        if (!HasPicture(_snapshot))
        {
            BottomBar.Visibility = Visibility.Visible;
            ApplyCursor();
            return;
        }

        var showNav = _inputActive && !_covered;
        BottomBar.Visibility = showNav ? Visibility.Visible : Visibility.Collapsed;
        ApplyCursor();
    }

    private void ApplyCursor()
    {
        var idle = DateTime.UtcNow - _lastInputUtc >= TimeSpan.FromSeconds(5);
        if (_covered || idle || (HasPicture(_snapshot) && !_inputActive)) HideCursor();
        else Mouse.OverrideCursor = null;
    }

    private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
    {
        NoteUserInput();
        if (e.Key == Key.Space && Keyboard.Modifiers == ModifierKeys.None)
        {
            Reveal();
            e.Handled = true;
            return;
        }

        if (Keyboard.Modifiers == ModifierKeys.None && e.Key is Key.F or Key.Escape)
        {
            ToggleFullscreen();
            e.Handled = true;
            return;
        }

        if ((Keyboard.Modifiers & ModifierKeys.Control) == 0) return;
        var zoomIn = e.Key is Key.OemPlus or Key.Add;
        var zoomOut = e.Key is Key.OemMinus or Key.Subtract;
        if (!zoomIn && !zoomOut) return;
        _zoom = Math.Round(Math.Clamp(_zoom + (zoomIn ? ZoomStep : -ZoomStep), ZoomMin, ZoomMax), 1);
        _local.Zoom = _zoom;
        DesktopLocalSettingsStore.Save(_local);
        ApplyZoom();
        RenderCard();
        e.Handled = true;
    }

    private void ApplyZoom()
    {
        IdleTitle.FontSize = 28 * _zoom;
        IdleStatus.FontSize = 16 * _zoom;
        IdleHint.FontSize = 14 * _zoom;
        IdleHint.Margin = new Thickness(0, 28 * _zoom, 0, 0);
        TopicText.FontSize = 16 * _zoom;
        StageText.FontSize = 14 * _zoom;
        StageText.Margin = new Thickness(12, 0, 0, 0);
        RussianText.Margin = new Thickness(0, 20 * _zoom, 0, 0);
        CountdownText.FontSize = 13 * _zoom;
        CountdownBar.Width = 96 * _zoom;
        CountdownBar.Height = Math.Max(4, 4 * _zoom);
        CardPanel.Margin = new Thickness(28, _local.TopPadding, 28, 72);
    }

    private void ShowIdle()
    {
        IdlePanel.Visibility = Visibility.Visible;
        CardPanel.Visibility = Visibility.Collapsed;
        DimCover.Visibility = Visibility.Collapsed;
    }

    private void RenderCountdown()
    {
        if (_covered || !HasPicture(_snapshot) || _local.PauseSeconds <= 0)
        {
            CountdownPanel.Visibility = Visibility.Collapsed;
            return;
        }

        var totalMs = _local.PauseSeconds * 1000.0;
        var left = totalMs - (DateTime.UtcNow - _shownAtUtc).TotalMilliseconds;
        if (left <= 0)
        {
            CountdownPanel.Visibility = Visibility.Collapsed;
            return;
        }

        CountdownPanel.Visibility = Visibility.Visible;
        CountdownBar.Value = Math.Clamp(left / totalMs, 0, 1);
        CountdownText.Text = ((int)((left + 999) / 1000)).ToString();
    }

    private void Window_MouseMove(object sender, MouseEventArgs e)
    {
        var pos = e.GetPosition(this);
        var moved = _hasLastMouse &&
            Math.Abs(pos.X - _lastMouse.X) + Math.Abs(pos.Y - _lastMouse.Y) > 6;
        _lastMouse = pos;
        _hasLastMouse = true;
        if (!moved || DateTime.UtcNow < _ignoreMouseUntilUtc)
        {
            if (HasPicture(_snapshot) && !_inputActive) HideCursor();
            return;
        }

        NoteUserInput();
    }

    private void Window_PreviewMouseDown(object sender, MouseButtonEventArgs e) => NoteUserInput();

    private void Window_PreviewMouseWheel(object sender, MouseWheelEventArgs e) => NoteUserInput();

    private void NoteUserInput()
    {
        _lastInputUtc = DateTime.UtcNow;
        _inputActive = true;
        if (HasPicture(_snapshot))
        {
            if (_covered)
            {
                Reveal();
                return;
            }

            _shownAtUtc = DateTime.UtcNow;
            _heldOpen = true;
            BottomBar.Visibility = Visibility.Visible;
            Mouse.OverrideCursor = null;
            ShowChrome();
            RenderCountdown();
            ApplyBrightness();
            return;
        }

        Mouse.OverrideCursor = null;
        ShowChrome();
    }

    private void ShowChrome()
    {
        if (_covered) return;
        Chrome.Visibility = Visibility.Visible;
        FullScreenButton.Content = WindowState == WindowState.Maximized ? "❐" : "⛶";
        _chromeTimer.Stop();
        _chromeTimer.Start();
    }

    private void Minimize_Click(object sender, RoutedEventArgs e) =>
        WindowState = WindowState.Minimized;

    private void Fullscreen_Click(object sender, RoutedEventArgs e) => ToggleFullscreen();

    private void ToggleFullscreen()
    {
        if (WindowState == WindowState.Maximized)
        {
            WindowState = WindowState.Normal;
            Width = 440;
            Height = 900;
            Left = Math.Max(0, (SystemParameters.PrimaryScreenWidth - Width) / 2);
            Top = Math.Max(0, (SystemParameters.PrimaryScreenHeight - Height) / 2);
        }
        else
        {
            WindowState = WindowState.Maximized;
        }
        ShowChrome();
    }

    private void Settings_Click(object sender, RoutedEventArgs e)
    {
        if (_settingsWindow == null)
        {
            _settingsWindow = new LocalSettingsWindow(_local, ApplyLocalSettings);
            _settingsWindow.Owner = this;
            _settingsWindow.Closed += (_, _) => _settingsWindow = null;
        }

        _settingsWindow.Show();
        _settingsWindow.Activate();
        ShowChrome();
    }

    private void ApplyLocalSettings()
    {
        _local.PauseSeconds = Math.Clamp(_local.PauseSeconds, 0, 300);
        _local.Brightness = Math.Clamp(_local.Brightness, 20, 100);
        _local.TopPadding = Math.Clamp(_local.TopPadding, 0, 400);
        _shownAtUtc = DateTime.UtcNow;
        DesktopLocalSettingsStore.Save(_local);
        RenderCard();
    }

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    private const uint EsContinuous = 0x80000000;
    private const uint EsSystemRequired = 0x00000001;
    private const uint EsDisplayRequired = 0x00000002;

    [DllImport("kernel32.dll")]
    private static extern uint SetThreadExecutionState(uint esFlags);

    private static void StayAwake() =>
        SetThreadExecutionState(EsContinuous | EsSystemRequired | EsDisplayRequired);

    private static void AllowSleep() => SetThreadExecutionState(EsContinuous);

    private static int EnglishSize(int length) => length switch
    {
        > 160 => 26,
        > 80 => 36,
        > 40 => 48,
        _ => 64,
    };

    private static Brush BrushFrom(string hex, Brush fallback)
    {
        var raw = hex.Trim().TrimStart('#');
        if (!uint.TryParse(raw, System.Globalization.NumberStyles.HexNumber, null, out var value))
        {
            return fallback;
        }
        if (raw.Length < 8) value |= 0xFF000000;
        var color = Color.FromArgb(
            (byte)((value >> 24) & 0xFF),
            (byte)((value >> 16) & 0xFF),
            (byte)((value >> 8) & 0xFF),
            (byte)(value & 0xFF));
        return new SolidColorBrush(color);
    }
}

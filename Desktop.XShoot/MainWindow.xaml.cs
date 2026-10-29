using System.Diagnostics;
using System.IO;
using System.Speech.Synthesis;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Threading;

namespace Desktop.XShoot;

public partial class MainWindow : Window
{
    private readonly List<DictWord> _dictionary;
    private readonly HashSet<string> _guessed;
    private readonly XShootSettings _settings;
    private readonly Random _random = new();
    private readonly DispatcherTimer _timer;
    private readonly SpeechSynthesizer? _tts;

    private List<DictWord> _batch = [];
    private int _batchIndex;
    private DictWord? _current;
    private bool _roundOpen;
    private bool _paused;
    private DateTime _roundStartedUtc;
    private TimeSpan _remaining = TimeSpan.FromSeconds(12);
    private int _sessionCorrect;
    private int _sessionMiss;
    private int _exercises;

    public MainWindow()
    {
        InitializeComponent();
        _dictionary = ArchiveStore.LoadDictionary();
        _guessed = ArchiveStore.LoadKeys();
        _settings = ArchiveStore.LoadSettings();
        ChkConcise.IsChecked = _settings.ConciseOnly;
        ModeLearning.IsChecked = _settings.LearningMode;
        ModeOrdinary.IsChecked = !_settings.LearningMode;

        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(50) };
        _timer.Tick += OnTimerTick;

        try { _tts = new SpeechSynthesizer(); }
        catch { _tts = null; }

        TxtArchivePath.Text = "Архив: " + ArchiveStore.ArchivePath;
        RefreshStats();
        NewBatch();
        ShowPanel(PanelSetup);
        UpdateFooter();
    }

    private IEnumerable<DictWord> ActiveDictionary()
    {
        var pool = _dictionary.AsEnumerable();
        if (ChkConcise.IsChecked == true)
            pool = pool.Where(w => w.Concise);
        return pool;
    }

    private void NewBatch()
    {
        StopRound(resetUi: true);
        var size = Math.Clamp(_settings.BatchSize, 6, 20);
        var unguessed = ActiveDictionary()
            .Where(w => !_guessed.Contains(w.Key))
            .OrderBy(_ => _random.Next())
            .Take(size)
            .OrderBy(w => w.En, StringComparer.OrdinalIgnoreCase)
            .ToList();
        if (unguessed.Count == 0)
        {
            unguessed = ActiveDictionary()
                .OrderBy(_ => _random.Next())
                .Take(size)
                .OrderBy(w => w.En, StringComparer.OrdinalIgnoreCase)
                .ToList();
        }
        _batch = unguessed;
        _batchIndex = 0;
        ListWords.ItemsSource = _batch.Select(w => w.En).ToList();
        if (_batch.Count > 0)
        {
            ListWords.SelectedIndex = 0;
            ShowLearningWord(_batch[0]);
        }
        else
        {
            TxtLearnEn.Text = "";
            TxtLearnRu.Text = "Нет слов в словаре.";
        }
        RefreshStats();
        UpdateFooter();
    }

    private void ShowLearningWord(DictWord word)
    {
        TxtLearnEn.Text = word.En;
        TxtLearnRu.Text = word.PrimaryRu;
        Speak(word.En);
    }

    private void StartRound()
    {
        if (_batch.Count == 0)
        {
            NewBatch();
            if (_batch.Count == 0) return;
        }
        if (_batchIndex >= _batch.Count)
            _batchIndex = 0;

        _current = _batch[_batchIndex];
        _roundOpen = true;
        _paused = false;
        BtnPause.Content = "Пауза";
        _remaining = TimeSpan.FromSeconds(Math.Clamp(_settings.TargetSeconds, 5, 30));
        _roundStartedUtc = DateTime.UtcNow;
        TxtBoard.Text = _current.PrimaryRu;
        TxtAnswer.Text = "";
        TxtShootFeedback.Text = "";
        TxtShootFeedback.Foreground = System.Windows.Media.Brushes.LightGray;
        BarTimer.Value = 1;
        TxtShootStatus.Text = $"Слово {_batchIndex + 1} из {_batch.Count} · верно {_sessionCorrect} · мимо {_sessionMiss}";
        ShowPanel(PanelShoot);
        TabShoot.IsChecked = true;
        TxtAnswer.Focus();
        _timer.Start();
    }

    private void StopRound(bool resetUi)
    {
        _timer.Stop();
        _roundOpen = false;
        _paused = false;
        if (resetUi)
        {
            BarTimer.Value = 1;
            TxtShootFeedback.Text = "";
        }
    }

    private void OnTimerTick(object? sender, EventArgs e)
    {
        if (!_roundOpen || _paused || _current is null) return;
        var elapsed = DateTime.UtcNow - _roundStartedUtc;
        var total = TimeSpan.FromSeconds(Math.Clamp(_settings.TargetSeconds, 5, 30));
        var left = total - elapsed;
        if (left <= TimeSpan.Zero)
        {
            FailCurrent("Время вышло.");
            return;
        }
        _remaining = left;
        BarTimer.Value = left.TotalSeconds / total.TotalSeconds;
    }

    private void SubmitAnswer()
    {
        if (!_roundOpen || _current is null || _paused) return;
        var answer = ArchiveStore.Normalize(TxtAnswer.Text);
        var expected = _current.Key;
        if (answer.Length == 0) return;

        if (answer == expected)
        {
            _sessionCorrect++;
            _exercises++;
            if (_guessed.Add(_current.Key))
                ArchiveStore.Save(_guessed);
            TxtShootFeedback.Foreground = System.Windows.Media.Brushes.LightGreen;
            TxtShootFeedback.Text = "Верно: " + _current.En;
            Speak(_current.En);
            AdvanceAfterDelay();
        }
        else
        {
            FailCurrent("Нужно: " + _current.En);
        }
    }

    private void FailCurrent(string message)
    {
        if (!_roundOpen || _current is null) return;
        _sessionMiss++;
        _exercises++;
        TxtShootFeedback.Foreground = System.Windows.Media.Brushes.Salmon;
        TxtShootFeedback.Text = message;
        Speak(_current.En);
        AdvanceAfterDelay();
    }

    private void SkipCurrent()
    {
        if (!_roundOpen || _current is null) return;
        _sessionMiss++;
        TxtShootFeedback.Foreground = System.Windows.Media.Brushes.Khaki;
        TxtShootFeedback.Text = "Пропуск: " + _current.En;
        AdvanceAfterDelay();
    }

    private async void AdvanceAfterDelay()
    {
        StopRound(resetUi: false);
        RefreshStats();
        await Task.Delay(900);
        _batchIndex++;
        if (_batchIndex >= _batch.Count)
        {
            TxtShootFeedback.Text = "Партия закончена. Новая партия на экране «Упражнения».";
            TxtShootStatus.Text = $"Верно {_sessionCorrect} · мимо {_sessionMiss}";
            NewBatch();
            if (ModeLearning.IsChecked == true)
            {
                ShowPanel(PanelLearning);
                TabLearning.IsChecked = true;
            }
            else
            {
                ShowPanel(PanelSetup);
                TabSetup.IsChecked = true;
            }
            return;
        }
        StartRound();
    }

    private void RefreshStats()
    {
        var total = ActiveDictionary().Count();
        var guessed = ActiveDictionary().Count(w => _guessed.Contains(w.Key));
        var left = Math.Max(0, total - guessed);
        TxtStats.Text =
            $"Слов в словаре: {total}\n" +
            $"В архиве (знаю): {guessed}\n" +
            $"Ещё не отгадано: {left}\n" +
            $"Упражнений в этой сессии: {_exercises}\n" +
            $"Верно / мимо: {_sessionCorrect} / {_sessionMiss}";
        UpdateFooter();
    }

    private void UpdateFooter()
    {
        TxtFooter.Text = $"Партия: {_batch.Count} · архив: {_guessed.Count} · словарь: {_dictionary.Count}";
    }

    private void ShowPanel(UIElement panel)
    {
        PanelSetup.Visibility = panel == PanelSetup ? Visibility.Visible : Visibility.Collapsed;
        PanelLearning.Visibility = panel == PanelLearning ? Visibility.Visible : Visibility.Collapsed;
        PanelShoot.Visibility = panel == PanelShoot ? Visibility.Visible : Visibility.Collapsed;
    }

    private void Speak(string text)
    {
        if (_tts is null || string.IsNullOrWhiteSpace(text)) return;
        try
        {
            _tts.SpeakAsyncCancelAll();
            _tts.SpeakAsync(text);
        }
        catch
        {
            // TTS optional
        }
    }

    private void PersistOptions()
    {
        _settings.ConciseOnly = ChkConcise.IsChecked == true;
        _settings.LearningMode = ModeLearning.IsChecked == true;
        ArchiveStore.SaveSettings(_settings);
    }

    private void OnTabChanged(object sender, RoutedEventArgs e)
    {
        if (!IsLoaded) return;
        if (TabSetup.IsChecked == true) ShowPanel(PanelSetup);
        else if (TabLearning.IsChecked == true)
        {
            ShowPanel(PanelLearning);
            if (_batch.Count == 0) NewBatch();
        }
        else if (TabShoot.IsChecked == true) StartRound();
    }

    private void OnOptionsChanged(object sender, RoutedEventArgs e)
    {
        if (!IsLoaded) return;
        PersistOptions();
        RefreshStats();
    }

    private void OnNewBatch(object sender, RoutedEventArgs e) => NewBatch();

    private void OnOpenShoot(object sender, RoutedEventArgs e)
    {
        if (ModeLearning.IsChecked == true && _batch.Count > 0 && !_roundOpen)
        {
            // Start from selected word if any.
            if (ListWords.SelectedIndex >= 0)
                _batchIndex = ListWords.SelectedIndex;
        }
        StartRound();
    }

    private void OnOpenArchiveFolder(object sender, RoutedEventArgs e)
    {
        Directory.CreateDirectory(ArchiveStore.AppDir);
        Process.Start(new ProcessStartInfo
        {
            FileName = ArchiveStore.AppDir,
            UseShellExecute = true,
        });
    }

    private void OnWordSelected(object sender, SelectionChangedEventArgs e)
    {
        if (ListWords.SelectedIndex < 0 || ListWords.SelectedIndex >= _batch.Count) return;
        ShowLearningWord(_batch[ListWords.SelectedIndex]);
    }

    private void OnSubmitAnswer(object sender, RoutedEventArgs e) => SubmitAnswer();

    private void OnSkip(object sender, RoutedEventArgs e) => SkipCurrent();

    private void OnPause(object sender, RoutedEventArgs e)
    {
        if (!_roundOpen) return;
        _paused = !_paused;
        BtnPause.Content = _paused ? "Далее" : "Пауза";
        if (!_paused)
            _roundStartedUtc = DateTime.UtcNow - (TimeSpan.FromSeconds(_settings.TargetSeconds) - _remaining);
    }

    private void OnAnswerKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            e.Handled = true;
            SubmitAnswer();
        }
    }

    private void OnWindowKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.F5)
        {
            NewBatch();
            e.Handled = true;
        }
    }

    private void OnExit(object sender, RoutedEventArgs e) => Close();

    protected override void OnClosed(EventArgs e)
    {
        PersistOptions();
        ArchiveStore.Save(_guessed);
        _timer.Stop();
        try { _tts?.Dispose(); } catch { /* ignore */ }
        base.OnClosed(e);
    }
}

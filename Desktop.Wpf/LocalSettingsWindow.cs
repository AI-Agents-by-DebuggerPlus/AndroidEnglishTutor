using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace Desktop.Wpf;

public sealed class LocalSettingsWindow : Window
{
    private static readonly string[] EnglishPresets = ["#FFE8F4F3", "#FFFFFFFF", "#FF7DD3FC", "#FFFBBF24"];
    private static readonly string[] RussianPresets = ["#FF8AA8A6", "#FFE8F4F3", "#FFA7F3D0", "#FFF9A8D4"];

    public LocalSettingsWindow(DesktopLocalSettings settings, Action onChange)
    {
        Title = "Настройки AET Desktop";
        Width = 440;
        Height = 640;
        WindowStartupLocation = WindowStartupLocation.CenterOwner;
        ResizeMode = ResizeMode.NoResize;
        Background = new SolidColorBrush(Color.FromRgb(0x12, 0x3D, 0x45));
        Foreground = Brushes.White;

        var root = new StackPanel { Margin = new Thickness(20) };
        root.Children.Add(Label("Пауза до затемнения, секунды (0 — не затемнять)"));
        var pause = new Slider
        {
            Minimum = 0,
            Maximum = 300,
            TickFrequency = 5,
            IsSnapToTickEnabled = true,
            Value = settings.PauseSeconds,
        };
        var pauseText = ValueText($"{settings.PauseSeconds} с");
        pause.ValueChanged += (_, _) =>
        {
            settings.PauseSeconds = (int)pause.Value;
            pauseText.Text = $"{settings.PauseSeconds} с";
            onChange();
        };
        root.Children.Add(pause);
        root.Children.Add(pauseText);

        root.Children.Add(Label("Яркость"));
        var brightness = new Slider
        {
            Minimum = 20,
            Maximum = 100,
            TickFrequency = 5,
            IsSnapToTickEnabled = true,
            Value = settings.Brightness,
        };
        var brightnessText = ValueText($"{settings.Brightness}%");
        brightness.ValueChanged += (_, _) =>
        {
            settings.Brightness = (int)brightness.Value;
            brightnessText.Text = $"{settings.Brightness}%";
            onChange();
        };
        root.Children.Add(brightness);
        root.Children.Add(brightnessText);

        var fade = new CheckBox
        {
            Content = "Уменьшать яркость до нуля вместе с таймером",
            IsChecked = settings.FadeBrightness,
            Foreground = Brushes.White,
            Margin = new Thickness(0, 8, 0, 0),
        };
        fade.Checked += (_, _) =>
        {
            settings.FadeBrightness = true;
            onChange();
        };
        fade.Unchecked += (_, _) =>
        {
            settings.FadeBrightness = false;
            onChange();
        };
        root.Children.Add(fade);

        root.Children.Add(Label("Отступ сверху"));
        var topPad = new Slider
        {
            Minimum = 0,
            Maximum = 400,
            TickFrequency = 10,
            IsSnapToTickEnabled = true,
            Value = settings.TopPadding,
        };
        var topPadText = ValueText($"{settings.TopPadding}");
        topPad.ValueChanged += (_, _) =>
        {
            settings.TopPadding = (int)topPad.Value;
            topPadText.Text = settings.TopPadding.ToString();
            onChange();
        };
        root.Children.Add(topPad);
        root.Children.Add(topPadText);

        var englishBox = ColorField(root, "Цвет английского текста", settings.EnglishColor, EnglishPresets, value =>
        {
            settings.EnglishColor = value;
            onChange();
        });
        var russianBox = ColorField(root, "Цвет русского текста", settings.RussianColor, RussianPresets, value =>
        {
            settings.RussianColor = value;
            onChange();
        });

        var close = new Button
        {
            Content = "Закрыть",
            Width = 120,
            Height = 32,
            Margin = new Thickness(0, 18, 0, 0),
            HorizontalAlignment = HorizontalAlignment.Right,
        };
        close.Click += (_, _) => Close();
        root.Children.Add(close);
        Content = new ScrollViewer { Content = root, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };

        // Keep the compiler from dropping the boxes if a future edit stops reading them.
        englishBox.Tag = russianBox;
    }

    private static TextBlock Label(string text) => new()
    {
        Text = text,
        Foreground = Brushes.White,
        TextWrapping = TextWrapping.Wrap,
        Margin = new Thickness(0, 14, 0, 6),
    };

    private static TextBlock ValueText(string text) => new()
    {
        Text = text,
        Foreground = new SolidColorBrush(Color.FromRgb(0x8A, 0xA8, 0xA6)),
    };

    private static TextBox ColorField(
        StackPanel root,
        string title,
        string current,
        string[] presets,
        Action<string> apply)
    {
        root.Children.Add(Label(title));
        var box = new TextBox
        {
            Text = current,
            Margin = new Thickness(0, 0, 0, 8),
            Padding = new Thickness(6),
        };
        box.LostFocus += (_, _) =>
        {
            var text = box.Text.Trim();
            if (!text.StartsWith('#')) text = "#" + text;
            box.Text = text;
            apply(text);
        };
        root.Children.Add(box);

        var row = new WrapPanel();
        foreach (var preset in presets)
        {
            var chip = new Button
            {
                Width = 36,
                Height = 28,
                Margin = new Thickness(0, 0, 8, 8),
                Background = BrushOf(preset),
                BorderBrush = Brushes.White,
            };
            chip.Click += (_, _) =>
            {
                box.Text = preset;
                apply(preset);
            };
            row.Children.Add(chip);
        }
        root.Children.Add(row);
        return box;
    }

    private static Brush BrushOf(string hex)
    {
        var raw = hex.Trim().TrimStart('#');
        if (!uint.TryParse(raw, System.Globalization.NumberStyles.HexNumber, null, out var value))
        {
            return Brushes.Gray;
        }
        if (raw.Length < 8) value |= 0xFF000000;
        return new SolidColorBrush(Color.FromArgb(
            (byte)((value >> 24) & 0xFF),
            (byte)((value >> 16) & 0xFF),
            (byte)((value >> 8) & 0xFF),
            (byte)(value & 0xFF)));
    }
}

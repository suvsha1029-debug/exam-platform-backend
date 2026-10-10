using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Web.WebView2.Core;

namespace QSStudio.Desktop;

internal static class DesktopDialogService
{
    internal static void Info(Window owner, string title, string message)
    {
        ShowOwned(owner, () => MessageBox.Show(owner, Clean(message), title, MessageBoxButton.OK, MessageBoxImage.Information));
    }

    internal static void Warning(Window owner, string title, string message)
    {
        ShowOwned(owner, () => MessageBox.Show(owner, Clean(message), title, MessageBoxButton.OK, MessageBoxImage.Warning));
    }

    internal static bool Confirm(Window owner, string title, string message)
    {
        MessageBoxResult result = MessageBoxResult.No;
        ShowOwned(owner, () => result = MessageBox.Show(owner, Clean(message), title, MessageBoxButton.YesNo, MessageBoxImage.Question));
        return result == MessageBoxResult.Yes;
    }

    internal static string? Prompt(Window owner, string title, string message, string defaultText = "")
    {
        Window dialog = new()
        {
            Title = title,
            Owner = owner,
            Topmost = owner.Topmost,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            SizeToContent = SizeToContent.WidthAndHeight,
            ResizeMode = ResizeMode.NoResize,
            Background = System.Windows.Media.Brushes.White
        };

        TextBox input = new()
        {
            Text = defaultText,
            MinWidth = 420,
            Margin = new Thickness(0, 8, 0, 0),
            Padding = new Thickness(8)
        };
        StackPanel buttons = new() { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 14, 0, 0) };
        Button ok = new() { Content = "OK", MinWidth = 82, Margin = new Thickness(0, 0, 8, 0), IsDefault = true };
        Button cancel = new() { Content = "Cancel", MinWidth = 82, IsCancel = true };
        ok.Click += (_, _) => dialog.DialogResult = true;
        buttons.Children.Add(ok);
        buttons.Children.Add(cancel);

        StackPanel root = new() { Margin = new Thickness(18) };
        root.Children.Add(new TextBlock { Text = Clean(message), TextWrapping = TextWrapping.Wrap, MaxWidth = 520 });
        root.Children.Add(input);
        root.Children.Add(buttons);
        dialog.Content = root;
        input.SelectAll();
        input.Focus();

        string? result = null;
        ShowOwned(owner, () => result = dialog.ShowDialog() == true ? input.Text : null);
        return result;
    }

    internal static void HandleScriptDialog(Window owner, CoreWebView2ScriptDialogOpeningEventArgs args, string title)
    {
        string message = string.IsNullOrWhiteSpace(args.Message) ? "Please confirm this action." : args.Message;
        switch (args.Kind)
        {
            case CoreWebView2ScriptDialogKind.Alert:
                Info(owner, title, message);
                args.Accept();
                break;
            case CoreWebView2ScriptDialogKind.Confirm:
            case CoreWebView2ScriptDialogKind.Beforeunload:
                if (Confirm(owner, title, message)) args.Accept();
                break;
            case CoreWebView2ScriptDialogKind.Prompt:
                string? value = Prompt(owner, title, message, args.DefaultText);
                if (value != null)
                {
                    args.ResultText = value;
                    args.Accept();
                }
                break;
        }
    }

    internal static string Clean(string message)
    {
        if (string.IsNullOrWhiteSpace(message)) return "Operation could not be completed.";
        string cleaned = Regex.Replace(message, @"https?://(localhost|127\.0\.0\.1)(:\d+)?[^\s]*", "secure desktop service", RegexOptions.IgnoreCase);
        cleaned = cleaned.Replace("backend", "secure service", StringComparison.OrdinalIgnoreCase);
        return cleaned.Trim();
    }

    private static void ShowOwned(Window owner, Action show)
    {
        bool oldTopmost = owner.Topmost;
        try
        {
            owner.Topmost = true;
            owner.Activate();
            show();
        }
        finally
        {
            owner.Topmost = oldTopmost;
            owner.Activate();
        }
    }
}

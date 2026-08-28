Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$secondsLeft = 10
$cancelled = $false

$form = New-Object System.Windows.Forms.Form
$form.Text = "Выключение компьютера"
$form.Size = New-Object System.Drawing.Size(420, 200)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.TopMost = $true

$label = New-Object System.Windows.Forms.Label
$label.AutoSize = $false
$label.Size = New-Object System.Drawing.Size(380, 70)
$label.Location = New-Object System.Drawing.Point(20, 20)
$label.Font = New-Object System.Drawing.Font("Segoe UI", 12)
$label.TextAlign = "MiddleCenter"
$label.Text = "Компьютер выключится через $secondsLeft сек.`r`nНажмите «Отменить», чтобы прервать."

$cancelButton = New-Object System.Windows.Forms.Button
$cancelButton.Text = "Отменить"
$cancelButton.Size = New-Object System.Drawing.Size(140, 36)
$cancelButton.Location = New-Object System.Drawing.Point(135, 105)
$cancelButton.Font = New-Object System.Drawing.Font("Segoe UI", 11)

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 1000

$timer.Add_Tick({
    $script:secondsLeft--
    if ($script:secondsLeft -le 0) {
        $timer.Stop()
        $form.Close()
        return
    }
    $label.Text = "Компьютер выключится через $script:secondsLeft сек.`r`nНажмите «Отменить», чтобы прервать."
})

$cancelButton.Add_Click({
    $script:cancelled = $true
    $timer.Stop()
    $form.Close()
})

$form.Add_FormClosing({
    $timer.Stop()
})

$form.Controls.Add($label)
$form.Controls.Add($cancelButton)
$form.AcceptButton = $cancelButton
$form.CancelButton = $cancelButton

$timer.Start()
[void]$form.ShowDialog()
$timer.Dispose()
$form.Dispose()

if (-not $cancelled) {
    Start-Process -FilePath "shutdown.exe" -ArgumentList "/s","/t","0" -WindowStyle Hidden
}

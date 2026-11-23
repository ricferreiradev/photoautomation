# Pacote Android

[Baixar PhotoAutomation 0.26.0](photoautomation-0.26.0.apk) · [SHA-256](photoautomation-0.26.0.apk.sha256)

O APK usa o pacote `com.ricferreira.photoautomation`, código de versão 26 e Android mínimo 8.0 (API 26). Foi compilado na variante release e assinado com o certificado Android Debug existente do projeto para manter a identidade usada pelo OAuth do Google. A instalação como atualização exige que o APK já instalado tenha a mesma assinatura.

Para conferir o download no PowerShell, compare o resultado com o arquivo de checksum.

```powershell
Get-FileHash .\photoautomation-0.26.0.apk -Algorithm SHA256
```

Para conferir assinatura e certificado com o Android SDK Build Tools:

```text
apksigner verify --print-certs photoautomation-0.26.0.apk
```

SHA-1 do certificado para OAuth Android — `BB:56:EC:84:43:26:CE:2A:D8:82:CF:06:0B:F1:2A:D7:E9:F9:48:34`

O [guia de acesso ao Google](../docs/guia.md#acesso-google) explica como autorizar a conta de teste ou configurar OAuth para uma compilação própria.

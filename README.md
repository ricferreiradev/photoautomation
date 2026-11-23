# PhotoAutomation

PhotoAutomation é um app Android para controlar a captura de fotos em um ou dois celulares. Um arquivo JSON no Google Drive compartilha os comandos de captura e a configuração. As fotos são enviadas para a pasta escolhida no Drive.

## Download

[Baixar PhotoAutomation 0.26.0 para Android](resources/photoautomation-0.26.0.apk) · [Checksum SHA-256](resources/photoautomation-0.26.0.apk.sha256)

Requer Android 8.0 ou superior, Google Play Services, uma conta Google autorizada e câmera traseira no aparelho que fotografa. Abra o APK no Android e permita a instalação pela fonte usada para abri-lo, se o sistema solicitar.

**Acesso ao Drive:** com a configuração OAuth em modo de teste, a conta Google precisa estar cadastrada como usuária de teste no projeto Google Cloud. Se o acesso ainda não estiver disponível para sua conta, siga o [guia de configuração OAuth](docs/guia.md#acesso-google), que inclui o caminho para compilar sua própria cópia.

## Comece a fotografar

1. Para usar um aparelho, selecione **Servidor + Cliente**. Para usar dois, escolha **Servidor** no aparelho controlador e **Cliente** no aparelho que fotografa.
2. Autorize o Google Drive. No servidor, crie o JSON pela tela **Configuração**. Se usar dois aparelhos, selecione o mesmo arquivo no cliente. Com contas diferentes, compartilhe o JSON e a pasta de destino com as permissões adequadas.
3. No servidor, ajuste a pasta das fotos, o intervalo e o zoom. Toque em **Salvar configuração no Drive**. Para tirar só uma foto por comando, marque a opção de captura única antes de salvar.
4. Abra o cliente e conceda as permissões solicitadas. No servidor, use **Play** para iniciar e **Stop** para pausar. A aba **Monitoramento** mostra o estado da captura, as pendências locais e a prévia disponível.

## Análise opcional

No servidor, a aba **Análise** permite descrever e transcrever a última foto ou as imagens de uma pasta. A análise da pasta processa as imagens em lotes e consolida as respostas. O app envia as imagens selecionadas e os prompts à API OpenAI, usando uma chave configurada no aparelho e um modelo disponível para a conta. As chamadas podem gerar cobrança. As respostas e o histórico de notificações ficam no aparelho que executou a análise.

## Documentação

- [Guia de uso, configuração, compilação e diagnóstico](docs/guia.md)
- [Exemplo do JSON de controle](examples/photoautomation-config.json)
- [Pacote Android e verificação de integridade](resources/README.md)
- [Descrição do repositório e notas de publicação da versão 0.26.0](docs/publicacao.md)

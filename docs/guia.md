# Guia do PhotoAutomation 0.26.0

[Voltar ao README](../README.md) · [Baixar o APK](../resources/photoautomation-0.26.0.apk)

App Android pessoal com controle compartilhado por um arquivo JSON no Google Drive. Um celular pode controlar e outro fotografar; o mesmo celular também pode executar as duas funções. Captura pela câmera traseira, fila local de envio, contador por sessão e prévia da última foto.

## Navegação e Play/Stop

A barra inferior tem **Configuração**, o botão central **Play/Stop** e **Monitoramento**. As duas telas ficam montadas na memória: alternar entre elas não recria views, não consulta o Drive por causa da troca e preserva os campos, a rolagem e a imagem. O serviço continua independente da aba selecionada.

- **Configuração:** função do dispositivo, seleção/criação do JSON, pasta das fotos, intervalo e zoom. **Salvar configuração no Drive** publica os parâmetros do formulário.
- **Monitoramento:** estado compartilhado do JSON e, nos modos que capturam, estado local do cliente, fotos enviadas/pendentes e última foto.
- **Última foto no Google Drive:** nos modos Servidor e Servidor + Cliente, o monitoramento tem o botão **Ver última foto do Drive**. O toque consulta a foto mais recente (data de criação no Drive) diretamente na pasta salva no JSON e baixa uma prévia reduzida, quando disponível. Com captura única ligada, a confirmação do envio no JSON também baixa e exibe automaticamente a foto correspondente na aba Monitoramento, independentemente de a análise automática estar ligada. A prévia usa o ID confirmado pelo cliente, sem baixar a mesma foto novamente. O servidor precisa estar com o app aberto para atualizar a tela; falhas de rede são tentadas novamente após 30 segundos. No modo de captura contínua, a consulta continua manual. São exibidos nome, data e KB transferidos. Se o arquivo e sua versão não mudaram, usa a cópia local sem transferir a imagem novamente. O cache também pode ser exibido ao reabrir a tela, identificado como a última foto baixada; isso não confirma que continue sendo a mais recente. A pasta vem do JSON remoto, não de alterações ainda não salvas no formulário. Na ausência de miniatura, permite o original até 5 MB; não baixa originais maiores silenciosamente. A prévia local da câmera do Cliente continua separada.
- **Play/Stop no Servidor:** ativa/pausa `enabled` no JSON para os clientes, sem acessar a câmera local.
- **Indicador no Cliente:** não aceita cliques. O cliente se prepara automaticamente ao abrir o app, escolher o modo Cliente e selecionar um JSON, após conceder permissão de câmera. Mostra Gravando apenas depois de uma captura real; enquanto `enabled=false`, aguarda comandos. Somente o servidor controla o início/pausa pelo JSON. O sistema Android ainda pode encerrar o app.
- **Play/Stop em Servidor + Cliente:** ativa o JSON e inicia o cliente local; Stop para o cliente local e publica `enabled=false` no Drive. Se o Drive falhar, a parada local acontece e o erro de publicação é informado.

Play/Stop altera somente a ativação: não publica mudanças ainda não salvas de pasta, intervalo ou zoom. O texto acima da barra explica a ação para o modo atual. Uma alteração no Drive depende de conexão; a troca de telas é imediata.

## Usar em dois celulares

1. Instale o mesmo APK nos dois celulares. Autorize o Google Drive em cada um. Pode usar a mesma conta; se forem contas diferentes, compartilhe o JSON como Editor com a conta controladora e dê acesso ao arquivo e à pasta de fotos à conta receptora.
2. No primeiro celular, escolha **Servidor** em **Função deste celular**. Toque em **Criar JSON em uma pasta**, navegue pelas pastas, toque em **Criar JSON aqui** e informe o nome do arquivo. O JSON nasce com `enabled=false`. A pasta do JSON e a pasta das fotos são escolhas independentes.
3. No segundo celular, escolha **Cliente**, toque em **Selecionar JSON em uma pasta**, navegue até a pasta e toque no mesmo JSON. Os seletores permitem voltar entre pastas e abrir **Compartilhados comigo**. Como alternativa, use **Copiar link do JSON para outro celular** no servidor e **Usar link ou ID de um JSON** no cliente. Ambos precisam apontar para o mesmo ID, não apenas para arquivos com o mesmo nome.
4. Abra o cliente com o app visível. Ele se conecta automaticamente; conceda permissão de câmera e notificações quando solicitado. Não há botão de parada local no modo Cliente.
5. No servidor, ajuste pasta das fotos, intervalo e zoom na tela Configuração; toque em **Salvar configuração no Drive**. Toque em **Play** para ativar ou **Stop** para pausar os clientes pelo JSON. A alteração só vale depois de salva com sucesso no Drive. O serviço de câmera recusa iniciar no modo Servidor. Mudar de outro modo para Servidor encerra o cliente local.
6. O receptor lê o JSON aproximadamente a cada 3 segundos mais o tempo de rede. `enabled=true` inicia fotos, `false` pausa novas capturas. Uma captura já em andamento pode terminar e ser enviada. Os envios pendentes continuam após pausar/desarmar.

Em um único celular, escolha **Servidor + Cliente**. Você pode criar/selecionar o JSON, editar os controles e iniciar o cliente no mesmo aparelho. A função é salva localmente em cada dispositivo; não altera o papel do outro celular. O modo Cliente exibe o estado compartilhado, mas não permite editar nem criar configurações.

## Formato do JSON

```json
{
  "enabled": false,
  "folderId": "root",
  "intervalSeconds": 0.5,
  "zoom": 1.0,
  "captureSingleShot": false,
  "analysisLatestOnly": false,
  "analysisAutoEnabled": false,
  "analysisModel": "gpt-6.2-astra",
  "analysisEffort": "medium",
  "analysisPrompt": "Descreva o conteúdo visível na imagem. Transcreva o texto legível e indique os trechos que não conseguiu ler.",
  "batchPrompt": "Descreva as imagens do lote em ordem e preserve as transcrições legíveis.",
  "consolidationPrompt": "Reúna as descrições e transcrições dos relatórios, preservando os detalhes e sinalizando trechos ilegíveis."
}
```

- `enabled`: booleano real `true` ou `false`, sem aspas.
- `folderId`: ID da pasta de destino, não o link. `root` representa o Meu Drive da conta que envia as fotos; para um destino comum entre contas diferentes, use o ID real de uma pasta compartilhada. O seletor do app preenche esse campo.
- `intervalSeconds`: número entre 0.25 e 86400. Aceita frações; no JSON use ponto decimal. O formulário aceita vírgula ou ponto.
- `zoom`: multiplicador entre 0.1 e 100. O receptor limita ao mínimo/máximo suportado pela câmera e mostra o zoom efetivo no status. O valor solicitado no JSON permanece igual.
- `analysisPrompt`: texto usado na análise. JSONs antigos sem esse campo recebem o prompt padrão ao serem lidos, sem interromper a captura. Ao salvar, o campo passa a constar no arquivo.
- `batchPrompt` e `consolidationPrompt`: instruções adicionais da análise de toda a pasta. O primeiro é enviado com as imagens de cada lote. O segundo é usado nas consolidações intermediária e final. Quando esses campos estão ausentes do JSON, o app usa instruções genéricas para analisar as imagens e reunir os relatórios.
- `analysisLatestOnly`: `true` analisa somente a última imagem criada no Drive na pasta do JSON, em uma chamada com essa imagem e o prompt. `false` analisa toda a pasta com lotes e consolidação. Vale para análise manual e automática.
- `analysisAutoEnabled`: liga a rotina automática no servidor, usando `intervalSeconds`. É independente de `analysisLatestOnly`. Campos ausentes em JSONs antigos equivalem a `false`.
- `captureSingleShot`: liga o modo de uma foto por comando Play. Ausente em JSON antigo equivale a `false`.
- `analysisModel` e `analysisEffort` são selecionáveis na aba Análise. A versão oferece `gpt-6.2-astra` e `gpt-6.2-sol`, com os níveis `low`, `medium`, `high`, `xhigh` e `max`. Use um modelo disponível para a chave configurada.

## Capturar somente uma foto

Na tela Configuração do servidor, marque **Capturar somente uma imagem e parar** e salve. Cada toque em Play gera um `captureRequestId` novo no JSON. O cliente tira uma única foto com zoom e pasta configurados, coloca-a na fila e pausa localmente. Um registro persistente do comando evita uma segunda captura ao reler o JSON, perder a conexão ou reiniciar o serviço. A captura não depende de terminar o upload: a fila continua enviando normalmente. O cliente atualiza `enabled=false` no JSON; se essa atualização falhar, continua pausado e tenta novamente. O serviço de câmera fica preparado para receber outro Play.

A versão 0.17 serializa a pausa e a confirmação no cliente para evitar que uma sobrescreva a outra. Se a confirmação faltar, o cliente procura no Drive o identificador exato da captura concluída e recupera o recibo sem reenviar a foto nem escolher outra pelo horário. Depois que a foto chega ao Drive, o cliente registra no JSON `singleShotUploadedFileId` e `singleShotUploadedRequestId`, preservando o restante da configuração. Com **Somente última foto** marcada, a análise manual e automática consulta a imagem não excluída mais recente diretamente na pasta, por data de criação no Drive. Não depende do ID da confirmação da captura única; fotos novas continuam sendo detectadas mesmo no mesmo comando. A rotina não repete uma foto já respondida com o mesmo prompt. Com a opção desmarcada, a rotina de captura única aguarda a confirmação e analisa toda a pasta uma vez por comando. O prompt, notificações e opções de análise continuam valendo. A análise automática só é executada se `analysisAutoEnabled=true`; se desligada, pode ser iniciada manualmente após o envio. O modo de análise de toda a pasta executa uma vez por comando de captura única. Analisar imagens nunca as move para a lixeira; somente a limpeza explícita e confirmada da pasta faz isso.

Para outra foto, toque novamente em Play após a pausa. A geração do identificador é feita pelo app; não é necessário editar esses campos técnicos. Em um único celular, use Servidor + Cliente. Com dois celulares, ambos precisam da versão 0.15 ou superior. Com mais de um cliente, cada cliente pode tirar uma foto por comando; o fluxo principal considera um servidor e um cliente.

## Rotina automática de análise

Na aba **Análise**, marque as opções desejadas e toque em **Salvar prompt e opções no JSON**. Elas também são incluídas ao salvar a configuração geral. A rotina roda apenas em Servidor ou Servidor + Cliente e precisa da chave OpenAI local. Ligada com o app aberto, inicia um serviço de primeiro plano com notificação própria. Mudar para Cliente ou desligar e salvar a opção encerra o serviço; uma chamada já enviada pode ainda terminar e notificar sua resposta.

As duas opções são independentes. Com `analysisLatestOnly=true`, cada ciclo procura a última foto e só chama a API se ela, sua versão, a pasta ou o prompt tiverem mudado. Com `analysisLatestOnly=false`, cada ciclo analisa novamente todas as imagens da pasta, mesmo que sejam as mesmas do ciclo anterior; isso aumenta tráfego e chamadas cobradas. A captura é controlada separadamente por `enabled`.

A cadência usa `intervalSeconds`, mas rede e API podem ser mais lentas. Não há análises simultâneas neste celular nem fila acumulada; os horários perdidos são pulados. A análise manual usa o mesmo bloqueio. No modo última foto, imagens intermediárias podem não ser analisadas se a captura for mais rápida que a API. Após falhas, espera pelo menos 30 segundos para tentar novamente. Cada resposta é salva sem alteração e gera sua notificação. O estado aparece na aba Análise.

A rotina relê o JSON a cada ciclo; desligamentos remotos só são percebidos depois da chamada atual. Não reinicia após reboot ou encerramento pelo sistema. No Android 15 ou superior, serviços `dataSync` têm orçamento de 6 horas em segundo plano por período de 24 horas; o app trata o timeout encerrando o serviço e informando a interrupção. Abra o servidor para verificar/reiniciar quando o sistema permitir. A rotina só pode ser iniciada com o app visível e autorização de notificações, e também depende das regras de bateria do aparelho.

Com o servidor aberto, se a rotina estiver habilitada mas o serviço tiver parado, o app permite nova tentativa a cada 60 segundos e ao voltar ao app, em vez de bloquear permanentemente após a primeira tentativa. A aba Análise tem **Verificar / reiniciar rotina automática** e mostra horários da última consulta e última resposta, nome da foto verificada e último erro com horário. O estado distingue pasta vazia de foto já respondida. Não se consulta o cache HTTP para decidir qual é a última imagem do Drive. A recuperação continua sujeita às permissões e aos limites do Android, sem contornar restrições do sistema.

## Limpar a pasta de fotos

Na tela **Configuração**, nos modos Servidor e Servidor + Cliente, **Apagar arquivos da pasta de fotos** consulta a pasta salva no JSON (não uma seleção ainda não salva) e mostra uma confirmação com nome da pasta, quantidade e amostra dos arquivos. **Mover para a lixeira** faz exclusão recuperável pelo Drive; não apaga permanentemente. A pasta, as subpastas e o JSON de configuração ativo são preservados. Os demais arquivos diretamente dentro da pasta, inclusive formatos que não sejam imagem, entram na limpeza.

Somente os arquivos listados na confirmação são processados. Cada arquivo é reconferido antes da alteração; arquivos movidos para outra pasta ou já na lixeira são ignorados. A operação informa sucessos e falhas sem apresentar uma limpeza parcial como total. Se o cliente continuar capturando ou enviar fotos pendentes, novos arquivos podem aparecer depois. A limpeza não altera o comando de captura nem a fila local do cliente.

## Análise das imagens com OpenAI

Na aba **Análise**, nos modos Servidor ou Servidor + Cliente, o prompt principal vem preenchido para descrever o conteúdo visível e transcrever o texto legível. **Salvar prompt e opções no JSON** publica o texto. **Restaurar prompt padrão** restaura o texto no formulário. Em **Editar prompts da pasta inteira**, edite as instruções de lote e de consolidação e toque em **Salvar no JSON**.

Cada resposta concluída, mesmo idêntica à anterior ou indeterminada, recebe um aviso próprio. O aviso fica pendente em disco até a publicação; falhas e bloqueios de permissão são tentados novamente pelo WorkManager, sem repetir chamadas à IA. Mantém até 20 avisos de respostas na bandeja para evitar o limite do Android; respostas completas continuam salvas. Som, banner e tempo das tentativas dependem das configurações e restrições do Android.

Cada análise concluída gera uma notificação Android no aparelho que executou a análise, com prévia expansível da resposta. Tocar abre a aba Análise com a resposta completa correspondente àquela notificação, mesmo que outra análise tenha sido concluída depois. Resultados das notificações são guardados localmente em `files/analysis-results`. No Android 13 ou superior, o app pede a permissão de notificações ao iniciar a análise, separadamente da permissão de câmera. Se a permissão ou o canal estiver bloqueado, a resposta continua disponível no app e ele informa a ausência do aviso. A notificação usa canal próprio de respostas, sem substituir a notificação de captura.

São notificações locais ao receber a resposta da API, sem Firebase nem consumo adicional de rede para o aviso. Não são enviadas ao outro celular e não significam que a análise rode com o app encerrado pelo sistema. O Android pode limitar a prévia visível; o texto completo permanece na tela Análise. A resposta exibida/copiada continua intacta.

**Testar análise das fotos** salva o prompt e envia à API OpenAI Responses, diretamente do aparelho, a última imagem ou as imagens da pasta `folderId`, conforme a opção selecionada. A chamada usa o modelo e o esforço selecionados. Na análise da pasta inteira, a consulta fixa um instante inicial para excluir fotos criadas durante a análise. A tela mostra a resposta e a quantidade de imagens e permite copiar o texto. O prompt é compartilhado no JSON do Drive e enviado à API com as imagens. O app guarda a chave e o histórico das respostas no aparelho que executa a análise.

Para este protótipo pessoal, a chave é configurada em **Configurar chave OpenAI neste celular** e criptografada com AES-GCM/Android Keystore em armazenamento privado sem backup. Ela não está no APK, no código-fonte nem no JSON. Pode ser trocada ou removida no mesmo diálogo. A configuração temporária por USB aceita `no_backup/key-import.txt`, importa para o Keystore e remove o texto puro na primeira verificação da chave. Não coloque credenciais no repositório. A API tem autenticação e faturamento próprios.

Não há limite de 50 imagens por pasta. A análise pagina toda a listagem e processa lotes de até 12 imagens JPEG/PNG/WEBP ou 20 MB preparados por chamada. As últimas duas imagens se repetem no lote seguinte para preservar o contexto. Os relatórios dos lotes são consolidados, com etapas intermediárias em grupos de até oito relatórios para pastas maiores. A divisão em lotes pode perder detalhes de conteúdos que se estendem por muitas fotos. Pastas maiores consomem mais tempo e chamadas cobradas de API.

As instruções de lote e consolidação são combinadas com o prompt principal na análise da pasta inteira. Em **Somente última foto**, somente o prompt principal acompanha a imagem. O app exibe, salva e copia o texto retornado pela última chamada da API. O formato da resposta é definido no prompt. A quantidade de imagens e lotes aparece em um campo separado.

Cada original continua limitado a 15 MB. A imagem é reduzida para no máximo 2048 pixels no maior lado, com rotação EXIF aplicada e conversão JPEG. Só imagens diretamente dentro da pasta são lidas; subpastas e fotos ainda pendentes de envio não entram. Uma pasta vazia gera uma mensagem e não chama a OpenAI. A tela mostra lote e imagem atuais e permanece acesa durante a análise. Se uma chamada falhar, o app não apresenta uma resposta parcial como concluída; relatórios já processados ficam em `files/analysis-partial.json` para diagnóstico, sem retomada automática. Repetir a análise faz novas chamadas.

O script `scripts/test-analysis-device.ps1 -Serial ID_DO_SERVIDOR` executa uma análise real pelo app no aparelho e pode gerar cobrança de API. Exige chave e Drive já autorizados. Grava o resultado em `files/last-analysis-test.json` e no histórico local da tela. Instala com `-r` e não desinstala o app. Os testes comuns de compilação não chamam a API.

O app pode criar o JSON na pasta escolhida. Para enviá-lo manualmente ao Drive, use o [exemplo de configuração](../examples/photoautomation-config.json) e um nome que termine em `.json`. Alterações manuais são aplicadas na próxima leitura. O app preserva campos extras ao salvar e identifica o arquivo pelo ID do Drive.

O controlador recarrega automaticamente o JSON enquanto a tela está aberta. Alterações locais em andamento são preservadas. Para descartar o formulário e usar os dados remotos, toque em **Descartar alterações e recarregar**. Ao salvar, o app verifica se a configuração mudou e usa `If-Match` quando o Drive fornece ETag; conflitos exigem recarregar. Evite editar simultaneamente em vários controladores, especialmente se não houver ETag na resposta.

## Receptor, tela bloqueada e limites

O Android exige que o cliente de câmera seja iniciado com o app visível. Depois, ele tenta continuar com a tela bloqueada. Enquanto iniciado, o serviço mantém a câmera preparada e uma notificação, inclusive quando `enabled=false`; nesse estado não salva novas fotos. No modo Cliente, o controle central indica o estado. No modo Servidor + Cliente, **Stop** encerra o cliente local e publica a pausa no Drive. Abra o app novamente para conectar o cliente se o Android encerrar o serviço. O app precisa ser aberto depois de reiniciar o aparelho.

Se o receptor não confirmar um JSON válido por mais de 15 segundos, pausa as capturas e tenta restabelecer a leitura. Pode continuar fotografando por até 15 segundos com o último comando confirmado. Não usa configuração antiga indefinidamente sem conexão. Depois de reconectar, volta a obedecer ao JSON. A autorização Google deve estar válida; se expirar, abra o app e reconecte.

No Galaxy S23, configure a bateria do app como **Sem restrições** se necessário. O funcionamento bloqueado depende do Android, da Samsung, de temperatura e bateria, e precisa ser validado no aparelho. O bloqueio de CPU tem duração máxima de 24 horas; para sessões maiores desarme e arme de novo.

O intervalo é a cadência desejada. Câmera, gravação e rede podem ser mais lentas; não há capturas sobrepostas nem garantia de envio ao Drive na mesma frequência. Intervalos muito curtos aumentam bateria, calor, tráfego e armazenamento. O JSON é consultado a cada 3 segundos, independentemente do intervalo das fotos; alterações muito breves entre duas consultas podem não ser observadas.

Cada foto mantém o destino configurado no instante da captura. Mudar a pasta não redireciona pendências. A fila usa armazenamento privado, depende da conexão para envio, aplica espera crescente nas falhas e limita a captura a aproximadamente 500 MB pendentes. Limpar dados ou desinstalar o app apaga pendências. Excluir a pasta de destino impede o envio das fotos que a referenciam.

O contador mostra fotos locais da sessão confirmadas no Drive. Zera quando o receptor passa de pausado para capturando, inclusive após interrupção por configuração expirada/conexão. Reabrir a tela não zera. A prévia é uma cópia local da última foto capturada, independente de já ter sido enviada. Contador, prévia e pendências não são espelhados para o controlador remoto.

## Captura oculta

Na aba Monitoramento, o botão Captura oculta está disponível nos modos Servidor e Servidor + Cliente. Ele cobre a interface com fundo preto e apenas um botão central grande em tons próximos do preto. O botão compartilha a ação, estado Play/Stop e bloqueios do controle normal, incluindo captura única e comandos pelo JSON. Enquanto a sobreposição estiver visível, a tela permanece ligada. Voltar fecha a sobreposição sem alterar a captura e restaura o tempo normal de desligamento da tela. A rotação preserva a tela oculta. As barras do Android ficam ocultas, mas podem aparecer por gesto; permissões e telas do sistema continuam sob controle do Android.

## Acesso Google

Para usar o APK desta publicação com o OAuth em teste, a conta Google precisa estar na lista de usuários de teste do projeto OAuth que registrou o APK. O escopo `https://www.googleapis.com/auth/drive` é restrito e dá acesso amplo ao Drive. O app o usa para selecionar pastas e JSONs existentes, ler e gravar a configuração, enviar fotos e mover arquivos para a lixeira mediante confirmação. A abertura do acesso a contas externas em produção requer a verificação aplicável do Google. [Estados do OAuth](https://developers.google.com/identity/protocols/oauth2/production-readiness/overview) · [Escopos do Drive](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)

Para manter sua própria configuração OAuth, habilite a Google Drive API em um projeto do Google Cloud, configure Google Auth Platform como Externo em Teste e adicione as contas de teste e o escopo acima. Compile o app localmente e execute `signingReport` para obter o SHA-1 da sua compilação.

Crie um cliente OAuth Android para:

- Pacote: `com.ricferreira.photoautomation`
- SHA-1 da variante que você instalar. O [APK desta publicação](../resources/README.md) já tem seu próprio certificado documentado.

O mesmo APK nos dois celulares usa o mesmo cadastro. Outra assinatura exige cadastro correspondente. Nenhum JSON de credenciais ou segredo precisa entrar no código. Em modo de teste, a autorização pode expirar e exigir reconexão.

## Compilar e instalar por USB

Para compilar a partir de um clone, instale JDK 17 e o Android SDK com a plataforma 35, Build Tools 35.0.0 e Platform Tools. Configure `JAVA_HOME` e `ANDROID_HOME` ou abra o projeto no Android Studio. Use o Gradle Wrapper na raiz do projeto:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:signingReport
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No Linux e macOS, use `sh ./gradlew` com as mesmas tarefas. Se o SDK estiver fora de `ANDROID_HOME`, indique o caminho em `local.properties`. A instalação por ADB requer depuração USB autorizada.

Os scripts PowerShell abaixo pressupõem um ambiente local em `.tools` com JDK 17, SDK Android 35, Build Tools 35.0.0, ADB e Gradle 8.11.1. Para reinstalar nesse ambiente: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-android.ps1`.

Compilar, testar e analisar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-android.ps1
```

Compilar, testar, analisar, instalar e abrir no celular USB autorizado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-android.ps1
```

Se houver mais de um dispositivo, acrescente `-Serial NUMERO_DO_DISPOSITIVO`. A instalação preserva os dados (`adb install -r`). APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Verificação

Se o JSON abrir por link, mas não aparecer na pasta, verifique a lixeira: o Drive permite ler um arquivo na lixeira por ID, enquanto os seletores normais o ocultam. O app agora recusa usar um JSON na lixeira e mostra **Restaurar JSON da lixeira** na tela Configuração. A restauração preserva ID, conteúdo e pasta; não é criado outro arquivo. Pode ser necessário reconectar ao Google.

Para diagnóstico real do JSON selecionado no único dispositivo USB conectado, execute `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-drive-device.ps1`. Esse script instala os APKs com `-r` e chama o teste diretamente para preservar os dados do app. Não use `connectedDebugAndroidTest` neste aparelho pessoal: o executor pode desinstalar o app após os testes. A opção explícita `-RestoreTrashed` também restaura o JSON selecionado antes da verificação. O diagnóstico grava `files/drive-diagnostic.json` no armazenamento privado, com metadata e listagem da pasta, sem tokens.

Testes locais cobrem validação do JSON, tipos, intervalos fracionários, zoom e pausa por configuração expirada. A compilação e o lint são executados pelos scripts acima.

Para teste real: crie um JSON desativado, arme o receptor, ative e salve no controlador; confirme prévia, contador e arquivos. Mude intervalo para 0.5 e zoom para 2, depois desative. Mude a pasta e confira que novas fotos seguem para ela. Bloqueie o receptor e repita o controle. Desligue a internet dele por mais de 15 segundos, confirme a pausa e reative. Teste conflito de edição em dois controladores. O fluxo completo com dois aparelhos e Google Drive depende de validação em dispositivos.

O botão Testar disparador do relógio, na aba Monitor, abre um diagnóstico que registra comandos de tecla recebidos enquanto a janela está aberta, sem acionar captura. O último evento fica em files/watch-shutter-diagnostic.json. A recepção precisa ser testada com o relógio real; botões físicos do celular também podem produzir o mesmo evento.

Após o teste confirmado no S23, o comando KEYCODE_VOLUME_UP do dispositivo Redmi Watch 5 Lite B692 pode controlar o mesmo Play/Stop do app. A opção local Controlar Play/Stop pelo relógio fica em Monitor e só aparece em Servidor ou Servidor + Cliente. Funciona com a Activity visível, inclusive Captura oculta. O nome do dispositivo é comparado ao registrado; botões físicos de volume não acionam captura. Repetições e toques em menos de um segundo são ignorados, assim como ações durante operações em andamento. Não há suporte a disparo com tela bloqueada ou app em segundo plano.

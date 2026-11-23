# Publicação no GitHub

## Descrição do repositório

App Android para controlar a captura de fotos em um ou dois celulares pelo Google Drive, com fila local de envio e análise opcional via OpenAI.

Tópicos sugeridos — `android`, `kotlin`, `camerax`, `google-drive`, `photo-automation`, `openai`.

## Notas da versão

**PhotoAutomation 0.26.0** · Tag sugerida `v0.26.0`

- Controle de captura pelo Google Drive, com modos Servidor, Cliente e Servidor + Cliente.
- Captura contínua ou de uma foto por comando, zoom configurável e fila local de envio.
- Monitoramento do estado da sessão e prévia das fotos.
- Análise manual ou automática da última foto ou da pasta, com processamento em lotes e notificações locais.

Anexe `resources/photoautomation-0.26.0.apk` e `resources/photoautomation-0.26.0.apk.sha256` à release. Informe na descrição que o app requer Android 8.0 ou superior e uma conta autorizada para acessar o Drive. Enquanto o OAuth estiver em teste, habilite as contas que participarão do teste. Uma mudança de certificado exige cadastrar a nova impressão digital do APK no OAuth e afeta a possibilidade de atualizar instalações existentes.

## Antes de publicar

- O primeiro commit local contém fotos, capturas de tela e logs de diagnóstico. Esses arquivos saíram da árvore de publicação, mas continuam no histórico. Se esse histórico ainda não foi compartilhado, publique um primeiro commit criado a partir da árvore limpa, em uma cópia separada. Se ele já foi compartilhado, revise as cópias remotas antes de torná-las públicas.
- Mantenha arquivos de assinatura, chaves de API e `local.properties` fora do repositório. O APK tem a assinatura pública. A chave privada fica com o mantenedor.
- Defina a licença antes de apresentar o projeto como código aberto. A publicação do código, por si só, não concede uma licença de reutilização.

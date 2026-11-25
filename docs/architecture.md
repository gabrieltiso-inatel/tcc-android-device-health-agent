# Arquitetura do agente

O agente coleta informações e executa solicitações no Android. Inicia as conexões HTTP; o controlador entrega solicitações nas respostas ao polling e mantém os snapshots e o histórico compartilhados.

## Vocabulário

| Tipo | Significado |
| --- | --- |
| `Action` | Solicitação identificada, com origem, alvo e estado de execução. |
| `ActionExecutionResult` | Retorno da execução: sucesso, estado, mensagem, código e dados coletados opcionais. |
| `CollectedDeviceData` | Dados de uma coleta: `StorageSummary`, `ApplicationInventory` ou `FileInventory`. |
| `LatestDeviceSnapshots` | Últimas coletas conhecidas, consultadas no controlador. |
| `InstalledApp`, `FileSource`, `FileItem` | Aplicativo, fonte autorizada e arquivo descritos nos inventários. |
| `PendingApplicationRemoval`, `PendingFileRemoval` | Dados locais necessários para revisar e retomar uma remoção. |

**Ação é um pedido acompanhado; snapshot é a última informação conhecida.** Um inventário contém os itens encontrados numa coleta. A coleta pode acontecer automaticamente ou durante uma ação explícita. Seus dados atualizam o snapshot; apenas a solicitação explícita tem resultado no histórico de ações. Consultar um snapshot não executa uma nova coleta.

Esses conceitos permanecem os mesmos do artigo. Os nomes internos mais completos não alteram o contrato: as ações continuam `collectTelemetry`, `collectStorageSummary`, `collectAppInventory`, `collectFileInventory`, `removeApplication` e `removeFile`. O campo HTTP `result` contém os dados representados por `collectedData` no agente. O tipo `ActionResult` do controlador representa esses dados; não é o envelope `ActionExecutionResult` do agente.

## Organização

Caminhos relativos a `app/src/main/kotlin/com/tcc/devicehealth/agent/`:

```text
MainActivity.kt                 Activity, permissões e confirmações oficiais
AgentDependencies.kt            Composição das dependências
ActionPollingWorker.kt          Entrada persistida do WorkManager
model/
  Action.kt                     Solicitação, resultado e aprovações
  CollectedDeviceData.kt        Coletas e itens dos inventários
  LatestDeviceSnapshots.kt      Últimos dados conhecidos
  DeviceTelemetry.kt
  DisplayPreferences.kt
data/
  DeviceRepository.kt          Pareamento, configurações, bateria, armazenamento e snapshots
  ApplicationRepository.kt     Coleta, sincronização e validação de aplicativos
  FileRepository.kt            Coleta, sincronização, autorização e remoção de arquivos
  ActionRepository.kt          Ciclo da ação, aprovações, idempotência e reenvio
  RepositoryOperation.kt       Execução no dispatcher e tratamento de falhas
  android/                     Integrações Android por fonte concreta
  local/                       Preferências e resultados privados
  remote/                      Cliente HTTP e adaptação JSON
actions/ActionExecutor.kt      Seleção da operação e mapeamento do resultado
sync/                         Loops, intervalos e agendamento
ui/                           ViewModel, estado, telas, apresentação e componentes
```

As interfaces e implementações dos repositories ficam juntas. Fontes acessam uma integração concreta; repositories coordenam os dados e as regras da operação; a UI renderiza estado e encaminha eventos. Declarações relacionadas permanecem agrupadas. A organização segue a [camada de dados Android](https://developer.android.com/topic/architecture/data-layer) e as [convenções Kotlin](https://kotlinlang.org/docs/coding-conventions.html).

| Integração | Arquivo |
| --- | --- |
| Identificação e bateria | `data/android/DeviceTelemetryDataSource.kt` |
| Totais de armazenamento | `data/android/StorageDataSource.kt` |
| Aplicativos e validação da remoção | `data/android/PackageManagerDataSource.kt` |
| Mídia compartilhada e consentimento para remoção | `data/android/MediaStoreDataSource.kt` |
| Arquivos em pastas autorizadas | `data/android/DocumentTreeDataSource.kt` |
| Notificações de aprovação | `data/android/ActionNotificationDataSource.kt` |
| Referências opacas | `data/android/FileReference.kt` |
| Identidade, token, endereço, tema, idioma e pastas | `data/local/AgentPreferencesDataSource.kt` |
| Resultados e aprovações | `data/local/LocalActionDataSource.kt` |
| Formato dos dados coletados salvos localmente | `data/local/StoredCollectedDataJsonAdapter.kt` |
| Transporte e endereço | `data/remote/ControllerApiClient.kt` |
| Conversões entre JSON HTTP e modelos | `data/remote/ControllerApiJsonAdapter.kt` |

Os modelos não contêm JSON ou `IntentSender`. Os adaptadores convertem os formatos nas fronteiras; a persistência não depende do adaptador HTTP. `AndroidFileRemovalConfirmation` pertence à integração Android e leva a confirmação até a Activity. URIs necessários à retomada permanecem locais.

## Por onde estudar

Comece em `AgentDependencies.kt` para ver as dependências. Depois acompanhe um fluxo de cada vez, nesta ordem:

### 1. Atualização automática

```text
ForegroundSync ou ActionPollingWorker
→ ApplicationRepository.syncApplications
→ PackageManagerDataSource.collect
→ ControllerApiClient.sendAppInventory
→ snapshot atualizado no controlador
```

Para bateria e armazenamento, a entrada equivalente está em `DeviceRepository`. Arquivos são coletados explicitamente e sincronizados após remoções. Esse caminho não passa por `ActionRepository` e não cria histórico.

### 2. Solicitação de atualização

```text
AgentViewModel.submitAction ou ação recebida pelo polling
→ ActionRepository.submitAction / processPendingActions
→ processAction verifica resultado já conhecido
→ ActionExecutor.execute
→ ApplicationRepository.collectApplications
→ PackageManagerDataSource.collect
→ ActionExecutionResult com collectedData
→ LocalActionDataSource.saveActionResult
→ ControllerApiClient.sendActionResult
→ histórico e snapshot atualizados no controlador
```

`submitAction` registra uma solicitação de origem `device` antes da execução. A origem `controller` chega pelo polling. Ambas reutilizam o mesmo executor e as mesmas operações de coleta.

### 3. Remoção de aplicativo

1. `ui/AppsScreen.kt` encaminha o aplicativo a `AgentViewModel.removeApplication`. A solicitação conserva a versão observada e chega a `ActionRepository.submitAction`.
2. `ActionExecutor` chama `ApplicationRepository.validateApplicationRemoval`, que consulta `PackageManagerDataSource`. O retorno indica `awaiting_approval`.
3. `ActionRepository.recordApproval` persiste a pendência e emite a notificação. `processAction` conserva e envia o resultado intermediário.
4. `ui/ActionsScreen.kt` encaminha a revisão ao ViewModel e a `ActionRepository.reviewApplicationRemoval`. O repository revalida o alvo e verifica se ele ainda existe.
5. `MainActivity` abre a confirmação oficial usando `PackageManagerDataSource.createRemovalIntent`. A Activity somente inicia essa interação; as regras de validação ficam na camada de dados.
6. `completeApplicationRemoval` decide pelo estado real do pacote, persiste o resultado final, sincroniza o inventário, envia o resultado e encerra a pendência.

Uma ação recebida do controlador entra no passo 2 pelo processamento do polling.

### 4. Remoção de arquivo

O início é equivalente: `FilesScreen` → ViewModel → `ActionRepository` → executor → `FileRepository`. O repository resolve `fileId`, revalida revisão e autorização e conserva a aprovação. Após a decisão do usuário:

- pastas autorizadas usam `DocumentTreeDataSource` e a API de documentos;
- mídia usa `MediaStoreDataSource`: Android 11+ abre `MediaStore.createDeleteRequest`; Android 10 trata autorização recuperável; Android 8/9 exige também escrita;
- a confirmação oficial retorna à Activity e depois a `completePlatformFileRemoval`;
- o resultado é preservado antes da sincronização e do envio. Uma falha deixa dados suficientes para retomar sem repetir o efeito.

O indicador `platformRequestStarted` permite conferir a ausência da mídia após interrupções. Sem autorização suficiente para verificar o resultado, a pendência permanece. Fechar a revisão conserva a pendência; recusá-la registra cancelamento.

## Concorrência, falhas e persistência

Os repositories deslocam operações bloqueantes para um dispatcher de IO injetável. A UI pode chamá-los a partir da thread principal. Cancelamentos de corrotinas são propagados, conforme as [recomendações Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices).

`AgentDependencies` fornece o mesmo mutex às instâncias de `ActionRepository` usadas pela Activity e pelo worker. Ele serializa mudanças no ciclo das ações; não permanece retido enquanto o usuário responde ao diálogo Android. Consultas de histórico e pendências não adquirem esse mutex.

O nome do DataStore e suas chaves permanecem iguais. Alterações nas listas de aprovações e pastas são feitas dentro de `edit`, evitando perda de gravações concorrentes. O primeiro resultado final local é conservado e reutilizado em reenvios. Um conflito de resultado final no controlador encerra a pendência local.

A consulta de mídia acumula os itens de cada fonte separadamente. Uma falha descarta a coleta incompleta e informa a fonte como `unavailable`. Os limites existentes de 5.000 arquivos e profundidade 20 são preservados. Identificadores e revisões mantêm a mesma geração baseada nas referências locais.

## Interface e sincronização

`AgentViewModel` expõe `AgentUiState` por `StateFlow`. As telas renderizam esse estado e emitem eventos. `MainActivity` executa permissões, seleção de pastas e confirmações oficiais, encaminhando os retornos ao ViewModel.

`ForegroundSync` mantém coroutines independentes enquanto a Activity está visível e o agente pareado. Os loops começam imediatamente; cada operação aguarda sua conclusão antes do próximo intervalo. Os tempos ficam em [SyncIntervals.kt](../app/src/main/kotlin/com/tcc/devicehealth/agent/sync/SyncIntervals.kt).

| Operação em primeiro plano | Intervalo após a tentativa |
| --- | --- |
| Consulta de ações | 3 segundos, com espera progressiva até 30 segundos após falhas |
| Telemetria | 30 segundos |
| Armazenamento | 1 minuto |
| Aplicativos | 5 minutos |
| Snapshots, histórico e pendências | 15 segundos |

WorkManager sincroniza telemetria, armazenamento e aplicativos e consulta ações a cada 15 minutos, com rede disponível e sujeito às restrições do Android. O nome completo de `ActionPollingWorker` permanece igual para trabalhos já cadastrados.

A UI consulta snapshots em `GET /api/devices/:deviceId/state`, autenticado por dispositivo. Falhas conservam os dados apresentados com aviso. As listas oferecem pesquisa, filtro de histórico e paginação de 20 entradas. Tema e idioma são persistidos; textos usam recursos de português e inglês.

## Contratos e segurança

- Capabilities usam identificadores de ações, não permissões Android.
- Inventários auxiliam a seleção; remoções revalidam o estado real no dispositivo.
- O próprio agente permanece visível com `canRemove=false`.
- Conteúdo, URI e caminho físico dos arquivos não são enviados ao controlador.
- O endereço é associado às credenciais; o cliente não segue redirecionamentos HTTP.
- A build debug permite HTTP. Produção requer HTTPS, assinatura persistente e proteção de acesso no controlador.

## Validação

O [CI](../.github/workflows/ci.yml) usa JDK 17 para testes unitários, Android Lint e builds debug do agente e da fixture. Testes instrumentados exigem emulador ou dispositivo conectado. A suíte verifica o contrato JSON, leitura dos dados persistidos anteriores, concorrência de aprovações, resultado final, coleta de mídia e documentos e interações da UI. Comandos no [README](../README.md).

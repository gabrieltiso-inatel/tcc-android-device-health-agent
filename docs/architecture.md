# Arquitetura do agent

## Papel no sistema

O agent coleta estado local, inicia toda comunicação HTTP, executa ações e apresenta informações do próprio dispositivo. O controlador mantém o estado compartilhado e o histórico auditável.

## Componentes atuais

O agent apresenta os snapshots mantidos pelo controller através de `GET /api/devices/:deviceId/state`, autenticado pelo token do próprio dispositivo. `ControllerClient` desserializa os dados e o repository os fornece ao ViewModel. A visualização é atualizada a cada `SyncIntervals.screenRefreshMillis` (15 segundos), após ações e ao retomar a tela; essa leitura não executa coletores nem cria histórico. Falhas preservam os dados já apresentados e exibem um aviso. Listas de apps, arquivos e histórico usam páginas de 20 entradas; a paginação é apenas de apresentação.

`DeviceHealthTheme` define cores claras e escuras, tipografia e cores de estado; componentes visuais compartilhados e formatação ficam separados da execução. A navegação inferior tem Início, Apps, Arquivos, Aprovações e Histórico; a engrenagem abre Configurações. O Início resume a saúde do aparelho com um veredito calculado por `healthVerdict` (aprovações pendentes, bateria fraca fora da tomada, armazenamento acima de 90% ou leituras salvas). Textos ficam em `strings.xml` (inglês) e `values-pt-rBR`; o ViewModel emite `AgentMessage` tipadas e a UI as traduz e exibe em snackbar. Tema (sistema, claro, escuro) e idioma (sistema, português, inglês) são `DisplayPreferences` persistidas no DataStore; `LocalizedContent` aplica o idioma escolhido apenas à composição. O estado das permissões é lido pela Activity ao retomar e entregue ao ViewModel como `PermissionStatus`. Solicitações de remoção locais usam `executeDeviceAction`, incluindo versão/revisão esperada, com as mesmas validações das solicitações remotas. Fechar a revisão de um arquivo preserva a pendência; recusar atua explicitamente no `actionId` selecionado. O nome de arquivo resolvido localmente é persistido na aprovação para ser reconhecível após reiniciar o processo; o identificador opaco permanece o alvo da execução.

- `MainActivity`: composição da aplicação e conexão da UI ao ViewModel;
- `AgentScreen`: renderização e interação da UI Compose;
- `AgentViewModel`: estado imutável da interface e coordenação de eventos;
- `DeviceHealthRepository`: casos de uso e coordenação dos fluxos;
- `ControllerClient`: transporte HTTP e serialização;
- `DeviceTelemetrySource`: informações básicas e bateria;
- `StorageDataSource`: resumo agregado de armazenamento;
- `AppInventoryDataSource`: metadados dos aplicativos instalados pelo usuário;
- `FileInventoryDataSource`: metadados de mídia acessível e pastas autorizadas pelo usuário;
- `FileRemovalDataSource`: resolução, revalidação e remoção de arquivos de pastas e mídia autorizada;
- `MediaFileRemovalDataSource`: integração com MediaStore, confirmação oficial e verificação da exclusão;
- `ActionExecutor`: execução centralizada das ações suportadas;
- `AgentPreferences`: identidade, token e resultados usados na idempotência;
- `ActionPollingWorker`: consulta periódica em segundo plano.

O inventário mantém o próprio agent visível, mas marca sua referência com `canRemove=false`; essa decisão é apenas uma indicação de interface e não substitui a validação local da ação.

## Fluxos principais

### Observação automática

`SyncIntervals` centraliza os intervalos de consulta de ações, sincronizações e WorkManager. Com a Activity visível, coroutines independentes sincronizam telemetria (30 segundos), armazenamento (1 minuto) e apps (5 minutos), imediatamente no início e com espera após cada tentativa. O ViewModel controla o ciclo de vida; o repository continua responsável pelas operações de dados. Cada loop aguarda sua própria tentativa e repete falhas no próximo intervalo. A consulta de ações não aguarda esses loops. Ao ocultar a Activity, as coroutines são canceladas; arquivos não são percorridos periodicamente. WorkManager sincroniza também telemetria em segundo plano e usa atualização do trabalho periódico existente para aplicar mudanças de configuração.

```text
fonte local coleta estado
→ repository envia ao controlador
→ controlador atualiza o estado atual
→ nenhum histórico de ação é criado
```

O resumo de armazenamento segue o mesmo fluxo de observação automática, em um endpoint próprio, e atualiza somente o snapshot atual no controlador.

O inventário de aplicativos segue o mesmo fluxo em um endpoint próprio e atualiza somente os snapshots atuais no controlador.

O inventário de arquivos é coletado por `collectFileInventory` e sincronizado após remoções. A fonte usa APIs oficiais para consultar mídia autorizada e diretórios escolhidos pelo usuário; somente metadados e referências opacas são serializados.

### Ação recebida

Com a Activity visível, o ViewModel consulta imediatamente e espera 3 segundos entre consultas concluídas. Falhas aplicam espera progressiva de 6, 12, 24 e até 30 segundos; sucesso restaura 3 segundos. Ao ocultar a Activity, o loop é cancelado; ao retornar, uma nova consulta é imediata. Esse loop é separado das sincronizações de inventários. Em segundo plano, o WorkManager mantém o agendamento de 15 minutos sujeito às restrições do Android.

```text
polling recebe Action(origin=controller)
→ ActionExecutor executa
→ resultado é preservado para idempotência
→ resultado é enviado ao controlador
```

### Ação local

```text
usuário toca em uma ação
→ agent registra Action(origin=device)
→ ActionExecutor executa
→ resultado é enviado ao controlador
→ histórico compartilhado é atualizado
```

### Remoção de aplicação

```text
removeApplication é recebido
→ agent valida aplicação e versão e registra awaiting_approval
→ usuário revisa: agent valida novamente antes da confirmação oficial do Android
→ resultado é decidido pelo estado real do pacote após a confirmação
→ resultado final é persistido, enviado e o inventário é sincronizado
```

Cancelar na lista de pendências registra `approval_declined` sem abrir a confirmação do Android. Se a aplicação já não existir na revisão, o resultado é `already_absent`.

## Invariantes

- coletores não decidem se algo deve entrar no histórico;
- apenas ações explícitas usam `ActionExecutor` e geram histórico;
- sincronizações automáticas atualizam estado sem criar ações;
- o agent anuncia capabilities do contrato e mapeia internamente as intenções para APIs Android;
- URIs, permissões e caminhos físicos de arquivos permanecem no agent e nunca são enviados ao controller;
- referências de inventário não substituem a validação local antes de uma futura remoção;
- a remoção de arquivos usa a autorização da fonte e valida novamente a revisão antes de excluir;
- resultados enviados ao controlador representam o domínio da aplicação e não códigos Android diretamente;
- aprovações pendentes e resultados finais não enviados devem ser persistidos para retomada automática;
- toda ação destrutiva exige iniciativa explícita;
- rede e armazenamento nunca devem bloquear a thread principal;
- tokens e dados sensíveis não devem aparecer em logs;
- uma ação redeliverada reutiliza o resultado persistido em vez de repetir seu efeito;
- um resultado final já persistido é reenviado sem nova execução; um conflito do controlador encerra a pendência local.

## Limites entre camadas

Para mídia, o repository persiste URI local, nome, revisão e indicador de solicitação iniciada na aprovação. No Android 11+, `MediaStore.createDeleteRequest` executa a exclusão após o consentimento; o retorno é verificado sem repetir `delete`. No Android 10, `RecoverableSecurityException` fornece a confirmação e a exclusão é tentada novamente após a autorização e revalidação. No Android 8/9, o fluxo exige permissão de escrita e a confirmação explícita no agent. A Activity apenas lança o IntentSender e encaminha o resultado associado ao `actionId`; esse identificador sobrevive à recriação da Activity. Se o processo perder o retorno, a próxima consulta verifica a ausência de uma mídia cuja solicitação já havia sido iniciada. Sem acesso suficiente para confirmar, a pendência é mantida. Resultados finais são persistidos antes do envio e o inventário é sincronizado também nas retomadas. URIs e detalhes da plataforma não fazem parte do contrato com o controller.

O endereço do controlador é configurado antes do pareamento e persistido no DataStore. O repository fornece o mesmo endereço ao cliente HTTP no primeiro plano e no WorkManager, com fallback para o endereço de build para instalações anteriores. Credenciais existentes não podem ser enviadas a outro controlador por uma edição de endereço. O cliente não segue redirecionamentos HTTP automaticamente.

- Composables somente renderizam estado e emitem eventos;
- ViewModel não acessa Android services, banco ou HTTP diretamente;
- repository coordena, mas delega coleta, transporte e execução;
- data sources conhecem apenas a API Android necessária à sua coleta;
- `ControllerClient` não contém regras de interface ou execução de ações.

## Distribuição e validação

O aplicativo requer Android 8.0 (API 26) ou superior. O CI usa JDK 17 para executar testes unitários, Android Lint e builds de depuração do agente e do aplicativo descartável. O APK do agente é disponibilizado como artifact. Testes instrumentados são iniciados manualmente com um emulador ou dispositivo conectado. O fluxo está definido em [ci.yml](../.github/workflows/ci.yml).

A build de depuração permite HTTP para avaliação. A distribuição de produção exige HTTPS e assinatura persistente. O uso de um controlador acessível publicamente também depende da proteção de acesso desse servidor.

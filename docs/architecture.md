# Arquitetura do agent

## Papel no sistema

O agent coleta estado local, inicia toda comunicação HTTP, executa ações e apresenta informações do próprio dispositivo. O controlador mantém o estado compartilhado e o histórico auditável.

## Componentes atuais

- `MainActivity`: composição da aplicação e conexão da UI ao ViewModel;
- `AgentScreen`: renderização e interação da UI Compose;
- `AgentViewModel`: estado imutável da interface e coordenação de eventos;
- `DeviceHealthRepository`: casos de uso e coordenação dos fluxos;
- `ControllerClient`: transporte HTTP e serialização;
- `DeviceTelemetrySource`: informações básicas e bateria;
- `StorageDataSource`: resumo agregado de armazenamento;
- `AppInventoryDataSource`: metadados dos aplicativos instalados pelo usuário;
- `ActionExecutor`: execução centralizada das ações suportadas;
- `AgentPreferences`: identidade, token e resultados usados na idempotência;
- `ActionPollingWorker`: consulta periódica em segundo plano.

## Fluxos principais

### Observação automática

```text
fonte local coleta estado
→ repository envia ao controlador
→ controlador atualiza o estado atual
→ nenhum histórico de ação é criado
```

O resumo de armazenamento segue o mesmo fluxo de observação automática, em um endpoint próprio, e atualiza somente o snapshot atual no controlador.

O inventário de aplicativos segue o mesmo fluxo em um endpoint próprio e atualiza somente os snapshots atuais no controlador.

### Ação recebida

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

## Invariantes

- coletores não decidem se algo deve entrar no histórico;
- apenas ações explícitas usam `ActionExecutor` e geram histórico;
- sincronizações automáticas atualizam estado sem criar ações;
- o agent anuncia capabilities do contrato e mapeia internamente as intenções para APIs Android;
- resultados enviados ao controlador representam o domínio da aplicação e não códigos Android diretamente;
- aprovações pendentes e resultados finais não enviados devem ser persistidos para retomada automática;
- toda ação destrutiva exige iniciativa explícita;
- rede e armazenamento nunca devem bloquear a thread principal;
- tokens e dados sensíveis não devem aparecer em logs;
- uma ação redeliverada reutiliza o resultado persistido em vez de repetir seu efeito.

## Limites entre camadas

- Composables somente renderizam estado e emitem eventos;
- ViewModel não acessa Android services, banco ou HTTP diretamente;
- repository coordena, mas delega coleta, transporte e execução;
- data sources conhecem apenas a API Android necessária à sua coleta;
- `ControllerClient` não contém regras de interface ou execução de ações.

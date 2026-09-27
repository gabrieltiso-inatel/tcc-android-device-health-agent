# Casos de uso do agent

Este documento registra o escopo funcional do aplicativo Android. Atualize o estado dos casos de uso quando uma entrega for concluída ou repriorizada.

O agent deve executar o máximo de ações oficiais de gerenciamento que o dispositivo permitir. Consentimento, notificação, retomada e histórico são partes do caso de uso, não motivos para abandonar a ação.

## Concluído

### Cadastrar dispositivo

- informar código temporário de pareamento;
- persistir UUID e token no armazenamento privado;
- retomar comunicação nas utilizações seguintes.

### Visualizar estado básico

- mostrar nome do dispositivo, bateria, carregamento e instante da coleta;
- enviar fabricante, modelo, Android, versão do agent e capabilities.

### Executar ações

- receber ações do controlador por polling em primeiro plano e WorkManager;
- iniciar ações locais com origem `device`;
- executar `collectTelemetry`, `collectStorageSummary` e `collectAppInventory`;
- preservar resultados necessários para idempotência.

### Consultar armazenamento

- coletar espaço total, usado e disponível sem acessar arquivos.

### Consultar aplicativos

- coletar nome, identificador, versão e datas dos aplicativos instalados pelo usuário;
- marcar o próprio agent como não removível, mantendo-o visível no inventário;
- excluir aplicativos identificados pelo Android como sistema;
- não acessar dados internos de outros aplicativos.

### Visualizar histórico de ações

- consultar o histórico mantido pelo controlador;
- mostrar origem, estado, instante e resultado;
- compartilhar a mesma fonte de verdade com a interface web.

## Parcial

### Sincronizar estado automaticamente

- telemetria básica, armazenamento e inventário de aplicativos já são sincronizados automaticamente;
- sincronizações automáticas futuras não devem gerar histórico de ações.

### Remover aplicação

- validar a aplicação e a versão no momento da ação;
- preservar uma aprovação pendente quando o processo do agent for encerrado;
- iniciar a confirmação oficial do Android quando o usuário revisar a ação;
- registrar o resultado e sincronizar o inventário depois da decisão.

## Planejado

- melhorar a apresentação e filtragem do histórico;
- ampliar informações disponíveis sobre aplicativos;
- acessar arquivos escolhidos pelo usuário através das APIs oficiais;
- ampliar ações de gerenciamento de aplicações possíveis com consentimento;
- avaliar capacidades adicionais em modo Device Owner/Profile Owner.

`removeApplication` é a intenção compartilhada. O agent decide internamente se a execução exige consentimento ou pode usar uma política administrativa validada.

## Limitações da plataforma

- arquivos compartilhados exigem seleção ou consentimento do usuário;
- inventário de pacotes é informação sensível e requer visibilidade declarada;
- último uso e tamanho de outros aplicativos podem exigir acessos adicionais;
- limpeza silenciosa de dados e algumas operações administrativas podem exigir Device Owner/Profile Owner;
- ações destrutivas não devem ser executadas implicitamente.

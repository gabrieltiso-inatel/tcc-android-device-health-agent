# Casos de uso do agent

Este documento registra o escopo funcional do aplicativo Android. Atualize o estado dos casos de uso quando uma entrega for concluída ou repriorizada.

O agent deve executar o máximo de ações oficiais de gerenciamento que o dispositivo permitir. Consentimento, notificação, retomada e histórico são partes do caso de uso, não motivos para abandonar a ação.

## Concluído

### Cadastrar dispositivo

- informar a URL do controlador antes do pareamento e reutilizá-la nas conexões seguintes;
- informar código temporário de pareamento;
- persistir UUID e token no armazenamento privado;
- retomar comunicação nas utilizações seguintes.

### Visualizar estado básico

- mostrar nome do dispositivo, bateria, carregamento e instante da coleta;
- enviar fabricante, modelo, Android, versão do agent e capabilities.

### Executar ações

- receber ações do controlador por polling em primeiro plano e WorkManager;
- consultar imediatamente ao exibir a tela e a cada 3 segundos enquanto visível, com espera progressiva até 30 segundos em falhas;
- iniciar ações locais com origem `device`;
- executar `collectTelemetry`, `collectStorageSummary` e `collectAppInventory`;
- preservar resultados necessários para idempotência.

### Consultar armazenamento

- coletar espaço total, usado e disponível sem acessar arquivos.
- apresentar o snapshot compartilhado com o controller e oferecer atualização explícita.

### Consultar aplicativos

- coletar nome, identificador, versão e datas dos aplicativos instalados pelo usuário;
- marcar o próprio agent como não removível, mantendo-o visível no inventário;
- excluir aplicativos identificados pelo Android como sistema;
- não acessar dados internos de outros aplicativos.
- apresentar apps, pesquisar nomes e iniciar remoção local com a versão observada.

### Consultar arquivos autorizados

- coletar metadados de mídia compartilhada quando o usuário autorizar acesso;
- coletar arquivos e subpastas de diretórios escolhidos pelo usuário;
- enviar nome, tipo, tamanho, data, fonte, revisão e possibilidade de remoção;
- não enviar conteúdo, URI ou caminho físico Android;
- preservar o inventário como resultado da ação `collectFileInventory`.
- apresentar arquivos, pesquisar nomes e iniciar remoção local com a revisão observada;
- manter nomes legíveis nas aprovações e notificações, sem usar nomes como autoridade de execução.

### Visualizar histórico de ações

- consultar o histórico mantido pelo controlador;
- mostrar origem, estado, instante e resultado;
- compartilhar a mesma fonte de verdade com a interface web.
- filtrar por estado e navegar em páginas de 20 entradas.

### Remover aplicação

- validar a aplicação e a versão no momento da ação e novamente antes da confirmação;
- preservar uma aprovação pendente quando o processo do agent for encerrado;
- iniciar a confirmação oficial do Android quando o usuário revisar a ação;
- permitir cancelar a aprovação diretamente na lista de pendências;
- decidir o resultado pelo estado real do pacote após a confirmação;
- registrar o resultado sem repetir o efeito, reenviá-lo após falhas de conexão e sincronizar o inventário depois da decisão.

## Parcial

### Sincronizar estado automaticamente

- telemetria básica, armazenamento e inventário de aplicativos já são sincronizados automaticamente;
- com a tela visível, sincronizar imediatamente e repetir telemetria a cada 30 segundos, armazenamento a cada 1 minuto e apps a cada 5 minutos; intervalos configuráveis em `SyncIntervals.kt`;
- sincronizações automáticas futuras não devem gerar histórico de ações.

### Remover arquivo

- resolver a referência opaca localmente;
- verificar novamente existência, revisão e possibilidade de remoção;
- solicitar o consentimento oficial necessário;
- persistir e enviar o resultado sem repetir o efeito em uma redelivery;
- remover arquivos de pastas autorizadas e mídia acessível (Images, Videos e Audio), usando confirmação oficial quando exigida;
- no Android 11+, abrir a solicitação de exclusão do sistema e verificar seu resultado; no Android 10, tratar a autorização recuperável; no Android 8/9, exigir também a permissão de escrita;
- preservar a referência local e a solicitação em andamento para retomar resultados após encerramento do processo.

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

# Casos de uso do agent

Este documento registra o escopo funcional do aplicativo Android. Atualize o estado dos casos de uso quando uma entrega for concluída ou repriorizada.

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

- coletar nome, pacote, versão e datas dos aplicativos instalados pelo usuário;
- excluir aplicativos identificados pelo Android como sistema;
- não acessar dados internos de outros aplicativos.

### Visualizar histórico de ações

- consultar o histórico mantido pelo controlador;
- mostrar origem, estado, instante e resultado;
- compartilhar a mesma fonte de verdade com a interface web.

## Parcial

### Sincronizar estado automaticamente

- telemetria básica e armazenamento já são sincronizados automaticamente;
- aplicativos ainda dependem de ação explícita;
- sincronizações automáticas futuras não devem gerar histórico de ações.

## Planejado

- melhorar a apresentação e filtragem do histórico;
- ampliar informações disponíveis sobre aplicativos;
- permitir ações locais explícitas de gerenciamento;
- acessar arquivos escolhidos pelo usuário através das APIs oficiais;
- avaliar capacidades privilegiadas em modo Device Owner/Profile Owner.

## Limitações da plataforma

- arquivos compartilhados exigem seleção ou consentimento do usuário;
- inventário de pacotes é informação sensível e requer visibilidade declarada;
- último uso e tamanho de outros aplicativos podem exigir acessos adicionais;
- limpeza silenciosa de dados e algumas operações administrativas exigem Device Owner/Profile Owner;
- ações destrutivas não devem ser executadas implicitamente.

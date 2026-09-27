# Device Health Agent

Agente Android do primeiro fluxo ponta a ponta. Ele mostra nome e bateria localmente, envia telemetria e capacidades ao controlador e executa ações por polling.

## Abrir e executar

1. Abra esta pasta no Android Studio.
2. Inicie o AVD `Phone_1` no Device Manager.
3. Execute a configuração `app`.

O endereço padrão do controlador para o emulator é `http://10.0.2.2:3000` e está em `app/build.gradle.kts` como `CONTROLLER_BASE_URL`.

Para testar com telefone físico, substitua o endereço pelo IP LAN do computador e mantenha o servidor controlador acessível nessa rede. Nunca use a configuração HTTP de debug em uma build de produção.

## Linha de comando

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug

# Instala um aplicativo descartável para repetir o fluxo de remoção.
./scripts/install-test-fixture.sh
```

## Fluxo manual

1. Inicie o controlador em `http://localhost:3000`.
2. No dashboard, clique em **Add device** para gerar um código temporário.
3. Abra o agente, informe o código de seis dígitos e toque em **Connect**.
4. Aguarde o envio automático da primeira telemetria.
5. Atualize o dashboard do controlador e confirme o dispositivo.
6. Crie uma ação suportada pela API do controlador.
7. Aguarde o polling do agente processar a ação.
8. Confirme o resultado no histórico do dispositivo.

Enquanto a tela do agente está aberta, ele consulta ações automaticamente a cada 30 segundos e sincroniza telemetria, armazenamento e inventário de aplicativos. Em segundo plano, o WorkManager faz essas sincronizações periodicamente quando há rede; o Android define o instante exato e não garante execução em tempo real. A interface também permite solicitar atualizações explícitas e mostra o mesmo histórico mantido pelo controlador.

Nesta primeira versão, o identificador do dispositivo é um UUID aleatório persistido apenas no armazenamento privado do app. Não são usados identificadores de hardware. O token recebido no pareamento também permanece no DataStore privado do agente.

A ação `collectStorageSummary` usa APIs públicas do Android e retorna somente espaço total, usado e disponível, sem listar arquivos ou solicitar permissões de armazenamento.

A ação `collectAppInventory` retorna metadados básicos dos aplicativos instalados pelo usuário. Para cumprir esse caso de uso de gerenciamento, o agente declara visibilidade dos pacotes instalados, mas não acessa dados internos dos aplicativos.

O próprio agente aparece no inventário para manter a leitura completa, mas é marcado como não removível; o controlador não exibe ação de remoção para ele. O app `com.tcc.devicehealth.fixture` é um alvo descartável para testes manuais e pode ser reinstalado com `./scripts/install-test-fixture.sh` após cada execução.

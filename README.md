# Device Health Agent

Agente Android do primeiro fluxo ponta a ponta. Ele mostra nome e bateria localmente, envia telemetria e capacidades ao controlador, consulta comandos por polling e executa `collectTelemetry`.

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
```

## Fluxo manual

1. Inicie o controlador em `http://localhost:3000`.
2. No dashboard, clique em **Add device** para gerar um código temporário.
3. Abra o agente, informe o código de seis dígitos e toque em **Connect**.
4. Aguarde o envio automático da primeira telemetria.
5. Atualize o dashboard do controlador e confirme o dispositivo.
6. Clique em **Collect telemetry** no controlador.
7. Aguarde o polling do agente processar o comando.
8. Confirme no dashboard a nova telemetria e o histórico do comando.

Enquanto a tela do agente está aberta, ele consulta comandos automaticamente a cada 30 segundos. Em segundo plano, o WorkManager consulta comandos periodicamente quando há rede; o Android define o instante exato e não garante execução em tempo real. A interface do agente não expõe controles de telemetria; controles serão adicionados apenas para ações de gerenciamento.

Nesta primeira versão, o identificador do dispositivo é um UUID aleatório persistido apenas no armazenamento privado do app. Não são usados identificadores de hardware. O token recebido no pareamento também permanece no DataStore privado do agente.

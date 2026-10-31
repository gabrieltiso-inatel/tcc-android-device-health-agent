# Device Health Agent

Agente Android do primeiro fluxo ponta a ponta. Ele mostra nome e bateria localmente, envia telemetria e capacidades ao controlador e executa ações por polling.

## Abrir e executar

1. Abra esta pasta no Android Studio.
2. Inicie o AVD `Phone_1` no Device Manager.
3. Execute a configuração `app`.

O endereço padrão do controlador para o emulator é `http://10.0.2.2:3000` e está em `app/build.gradle.kts` como `CONTROLLER_BASE_URL`.

Na tela de pareamento, informe a URL do controlador. Para telefone físico, use por exemplo `http://192.168.1.10`; a porta HTTP padrão é 3000. URLs HTTPS usam a porta padrão 443, e portas explícitas são preservadas. A URL é salva e reutilizada também pelo WorkManager. Após parear, o endereço permanece associado às credenciais; trocar de controlador exige limpar os dados do app e parear novamente. HTTP continua permitido somente no debug; builds de produção exigem HTTPS. Um controlador remoto ainda requer revisão de segurança antes da exposição pública.

## Linha de comando

O workflow `CI` executa testes unitários, build debug, lint e build do aplicativo descartável automaticamente em pushes e pull requests, usando Java 17. O resultado aparece na aba **Actions** do GitHub; também é possível executar por **Run workflow**. Não requer secrets nem dispositivo conectado. Os testes instrumentados continuam sendo executados separadamente com emulator ou telefone.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug

# Instala um aplicativo descartável para repetir o fluxo de remoção.
./scripts/install-test-fixture.sh
```

## Baixar APK de teste

Na aba **Actions**, abra uma execução bem-sucedida de `CI` e baixe `agent-debug-apk` em **Artifacts** (é necessário estar autenticado no GitHub). Extraia o ZIP e instale `app-debug.apk` no Android, autorizando a instalação dessa origem quando solicitado. Informe a URL do computador e o código de pareamento. No emulator, use `http://10.0.2.2:3000`.

O artifact é uma build debug para avaliação, com HTTP permitido. Não é uma release de produção. A chave debug do runner pode mudar entre builds; uma atualização pode exigir desinstalar a versão anterior, apagando os dados locais e exigindo novo pareamento. Uma distribuição de produção precisará de chave de assinatura persistente e HTTPS. Os artifacts ficam disponíveis por 14 dias.

## Fluxo manual

1. Inicie o controlador em `http://localhost:3000`.
2. No dashboard, clique em **Add device** para gerar um código temporário.
3. Abra o agente, informe o código de seis dígitos e toque em **Connect**.
4. Aguarde o envio automático da primeira telemetria.
5. Atualize o dashboard do controlador e confirme o dispositivo.
6. Crie uma ação suportada pela API do controlador.
7. Aguarde o polling do agente processar a ação.
8. Confirme o resultado no histórico do dispositivo.

Os intervalos estão centralizados em `app/src/main/kotlin/com/tcc/devicehealth/agent/SyncIntervals.kt`. Enquanto a tela está visível, o agent consulta ações imediatamente e espera 3 segundos entre consultas, com backoff até 30 segundos em falhas. Em loops independentes, sincroniza imediatamente telemetria, armazenamento e apps, repetindo respectivamente a cada 30 segundos, 1 minuto e 5 minutos após a tentativa anterior. Cada loop aguarda sua operação terminar antes de agendar a próxima tentativa; falhas são tentadas novamente no próximo intervalo. Ao ocultar a tela, os loops são cancelados; ao retornar, reiniciam imediatamente. Essas sincronizações não criam ações nem histórico. Arquivos continuam com coleta explícita e atualização após remoção. Em segundo plano, o WorkManager sincroniza telemetria, armazenamento, apps e consulta ações no agendamento de 15 minutos, sujeito às restrições do Android. Esse intervalo deve permanecer em pelo menos 15 minutos, e o backoff do WorkManager em pelo menos 10 segundos. Alterar as constantes exige recompilar; o agendamento existente é atualizado na próxima abertura do app.

Nesta primeira versão, o identificador do dispositivo é um UUID aleatório persistido apenas no armazenamento privado do app. Não são usados identificadores de hardware. O token recebido no pareamento também permanece no DataStore privado do agente.

A ação `collectStorageSummary` usa APIs públicas do Android e retorna somente espaço total, usado e disponível, sem listar arquivos ou solicitar permissões de armazenamento.

A ação `collectAppInventory` retorna metadados básicos dos aplicativos instalados pelo usuário. Para cumprir esse caso de uso de gerenciamento, o agente declara visibilidade dos pacotes instalados, mas não acessa dados internos dos aplicativos.

O próprio agente aparece no inventário para manter a leitura completa, mas é marcado como não removível; o controlador não exibe ação de remoção para ele. O app `com.tcc.devicehealth.fixture` é um alvo descartável para testes manuais e pode ser reinstalado com `./scripts/install-test-fixture.sh` após cada execução.

O mesmo script cria uma árvore de arquivos de teste em `Download/device-health-file-fixture`, com arquivos grandes, antigos, aninhados e mutáveis. No agent, escolha essa pasta em `Files > Choose folder`, execute a coleta e confirme o inventário no controller. Para testar a revisão esperada, altere `mutable.txt` antes de solicitar a remoção e confirme que a ação falha com `file_changed`.

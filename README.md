# Android Device Health — Agente Android

Aplicativo Android do TCC Android Device Health, voltado ao acompanhamento e à manutenção de dispositivos em ambientes familiares. O agente coleta informações, executa solicitações e permite acompanhar o próprio dispositivo, em conjunto com o [controlador](https://github.com/gabrieltiso-inatel/tcc-android-device-health-controller).

## Funcionalidades

- Bateria, carregamento e resumo de armazenamento.
- Inventários de aplicativos instalados pelo usuário e arquivos autorizados, com pesquisa e paginação.
- Remoção de aplicativos e arquivos, com validação local e aprovação.
- Pendências de aprovação e histórico compartilhado com o controlador.
- Configurações de permissões, pastas autorizadas, tema e idioma em português ou inglês.

O aplicativo utiliza APIs oficiais do Android. Aprovações e resultados são persistidos para retomar solicitações após interrupções e evitar a repetição de efeitos. O fluxo principal funciona com um agente comum, sem exigir Device Owner ou Profile Owner.

## Instalar e conectar

Requer **Android 8.0 (API 26) ou superior**.

1. Na aba [Actions](https://github.com/gabrieltiso-inatel/tcc-android-device-health-agent/actions), abra uma execução bem-sucedida de **CI** e baixe **agent-debug-apk**. O download exige login no GitHub.
2. Extraia o ZIP e instale `app-debug.apk`, autorizando a instalação dessa origem quando solicitado.
3. Inicie o controlador e selecione **Add device** no painel para gerar o código temporário.
4. No agente, informe o endereço do controlador e o código de seis dígitos e toque em **Connect**.

No emulador, use `http://10.0.2.2:3000`. Em um dispositivo físico na mesma rede do controlador, use o IP do computador, por exemplo `http://192.168.1.10:3000`.

O endereço e as credenciais são salvos no armazenamento privado do aplicativo. Para associar o agente a outro controlador, limpe os dados do app e faça um novo pareamento.

O APK disponibilizado pelo CI é uma versão de depuração para avaliação e permite HTTP. Os artifacts ficam disponíveis por 14 dias. A assinatura de depuração pode variar entre builds, exigindo desinstalar a versão anterior e parear novamente. Uma distribuição de produção exige assinatura persistente e HTTPS; o controlador também precisa de proteção de acesso para exposição pública.

## Utilização

A navegação reúne **Home**, **Apps**, **Files**, **Approvals** e **History**. A engrenagem abre **Settings**, onde é possível escolher idioma e tema, conceder acesso à mídia e autorizar pastas pelo seletor oficial do Android. Os rótulos acompanham o idioma escolhido.

Telemetria, armazenamento e aplicativos são sincronizados automaticamente. Os controles de atualização solicitam novas coletas e registram a ação no histórico; o inventário de arquivos utiliza esse fluxo explícito e também é atualizado após remoções. Para gerenciar arquivos, autorize as fontes nas configurações e atualize o inventário.

As remoções podem ser solicitadas no agente ou no controlador. Revise as pendências no agente e confirme a operação quando solicitado. O próprio agente aparece no inventário como protegido contra remoção. Apenas metadados e referências opacas dos arquivos são enviados ao controlador.

## Desenvolvimento e validação

Abra esta pasta no **Android Studio**, configure o **SDK Android 34** e use **JDK 17**. Inicie um emulador ou conecte um dispositivo e execute o módulo `app`. O Gradle Wrapper está incluído no repositório.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :test-fixture:assembleDebug
```

Essas tarefas também são executadas pelo CI em pushes e pull requests. O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk`. Os testes instrumentados podem ser iniciados com um emulador ou dispositivo conectado:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Os intervalos ficam em [SyncIntervals.kt](app/src/main/kotlin/com/tcc/devicehealth/agent/SyncIntervals.kt). Em primeiro plano, a consulta de ações ocorre a cada 3 segundos, com espera progressiva após falhas. Em segundo plano, o WorkManager utiliza intervalos de 15 minutos, sujeitos às restrições do Android. A visualização dos dados recebidos e as coletas possuem intervalos próprios.

## Dados para testes manuais

Com um emulador ou dispositivo de testes conectado, execute:

```sh
./scripts/install-test-fixture.sh
```

O script instala um aplicativo descartável e cria arquivos em `Download/device-health-file-fixture`, incluindo documentos para pesquisa e paginação, subpastas, arquivos de tamanhos variados e uma imagem válida para o inventário de mídia. Os arquivos de preenchimento `.jpg`, `.mp4` e `.pdf` servem para testar tamanho e remoção. Cada execução recria os arquivos de nomes fixos e reinstala o aplicativo.

Em **Settings**, autorize essa pasta e, se necessário, o acesso à mídia. Atualize os inventários de aplicativos e arquivos para testar as remoções. Alterar `mutable.txt` após a coleta permite verificar a rejeição de uma revisão desatualizada.

O script procura ADB no PATH e nos locais padrão do SDK. Para indicar outro executável ou selecionar um dispositivo, use `ADB_BIN=/caminho/platform-tools/adb` e `ANDROID_SERIAL=emulator-5554` antes do comando.

## Documentação

- [Arquitetura](docs/architecture.md)
- [Casos de uso](docs/use-cases.md)
- [Controlador](https://github.com/gabrieltiso-inatel/tcc-android-device-health-controller)

# AGENTS.md — Android Application

## Purpose

This file defines the shared development rules for this Android repository. Follow these instructions unless a task explicitly requires otherwise.

## Project Context

Este repositório contém o agent Android do projeto Android Device Health, uma solução acadêmica para acompanhamento e gerenciamento de dispositivos Android em ambientes familiares.

- O agent usa Kotlin e Jetpack Compose e inicia toda comunicação HTTP com o controlador.
- O primeiro acesso usa um código temporário de pareamento; identidade, token e resultados necessários para idempotência ficam no armazenamento privado do app.
- Telemetria e consulta de ações funcionam em primeiro plano e por WorkManager em segundo plano.
- O estado enviado inclui identificação do dispositivo, bateria, versão do Android e capabilities.
- O agent coleta resumo agregado de armazenamento e inventário dos aplicativos instalados pelo usuário sem acessar dados internos desses aplicativos.
- Ações explícitas podem ter origem `controller` ou `device`, usam o mesmo `ActionExecutor` e são registradas no histórico mantido pelo controlador.
- As ações atuais são `collectTelemetry`, `collectStorageSummary` e `collectAppInventory`.
- Sincronizações automáticas representam estado observado e não devem criar entradas no histórico de ações.
- Ações destrutivas ou privilegiadas exigem iniciativa explícita e devem respeitar permissões, consentimento e limitações de Device Owner/Profile Owner do Android.

## Current Direction

Esta seção mantém continuidade entre sessões curtas. Consulte-a antes de propor novos trabalhos e atualize-a sempre que uma etapa for concluída, removida ou repriorizada. Registre somente o estado atual e os próximos objetivos; detalhes históricos pertencem aos commits.

Concluído:

- framework unificado de ações com origem `controller` ou `device`;
- histórico compartilhado de ações;
- telemetria, resumo de armazenamento e inventário de aplicativos;
- pareamento, autenticação, retry, expiração e idempotência.

Próximo:

- automatizar a sincronização do inventário de aplicativos sem criar histórico de ações;
- melhorar a apresentação do histórico compartilhado.

Depois:

- ampliar informações e ações explícitas sobre aplicativos;
- acessar arquivos somente com consentimento do usuário;
- avaliar capacidades privilegiadas com Device Owner/Profile Owner.

## Context Routing

- Leia `docs/use-cases.md` ao planejar funcionalidades, revisar escopo ou definir o próximo passo.
- Leia `docs/architecture.md` antes de alterar contratos, persistência, permissões, comunicação ou limites entre camadas.
- Não carregue toda a documentação para correções locais que não dependam desse contexto.

## Communication

- Write explanations and responses in concise Brazilian Portuguese.
- Keep technical identifiers, source code, filenames, and commit messages in English.
- Prefer short, direct explanations focused on decisions and outcomes.

## Language and Naming

- Write all code in English using plain, simple terms.
- Use concise, meaningful names that reveal intent.
- Avoid vague names such as `data`, `item`, `object`, `manager`, `helper`, or `utils` when a more precise name exists.
- Use consistent naming patterns for equivalent concepts across the project.
- Name repositories, data sources, use cases, screens, states, events, and tests consistently with existing project conventions.
- Follow standard Kotlin and Android naming conventions.
- Do not invent abbreviations unless they are widely understood in the project.

## Code Quality

- Follow established software-engineering principles and official platform guidance.
- Prefer simple solutions over clever or unnecessarily abstract ones.
- Keep functions small, focused, and responsible for one clear behavior.
- Keep classes cohesive and dependencies explicit.
- Minimize coupling and avoid hidden dependencies or global mutable state.
- Remove duplication when doing so improves clarity; do not create premature abstractions.
- Preserve existing public contracts and behavior unless a change is explicitly requested.
- Do not modify unrelated code.
- Do not leave dead code, debug code, placeholders, or unfinished TODOs.

## Comments and Documentation

- Do not write comments in source code.
- Make code self-explanatory through structure and naming.
- If behavior cannot be understood without a comment, refactor the code instead.
- Use required API documentation only when the project or a public contract explicitly demands it.

## Android Architecture

- Follow the current official Android development documentation and recommendations.
- Use the recommended layered architecture:
  - UI layer for rendering state and handling user interaction.
  - Data layer for repositories, data sources, persistence, and external services.
  - Optional domain layer for reusable or sufficiently complex business logic.
- Keep business logic out of Activities, Fragments, Views, and Composables.
- Use unidirectional data flow and expose immutable UI state.
- Keep clear boundaries between layers and depend on abstractions where appropriate.
- Do not access databases, network clients, or platform services directly from the UI layer.
- Respect Android lifecycle rules.
- Use coroutines and Flow with structured concurrency and lifecycle-aware collection.
- Never block the main thread.
- Use Kotlin null safety and avoid `!!`.
- Use Jetpack Compose or Views according to the established project choice; do not mix approaches without a clear requirement.
- Keep Composables small, focused, stateless when practical, and easy to preview and test.

## Error Handling and Security

- Handle failures explicitly; never silently ignore exceptions.
- Provide useful user-facing errors without exposing internal or sensitive information.
- Never commit secrets, credentials, tokens, signing files, local configuration, personal data, or environment files.
- Do not log credentials, tokens, personal data, or other sensitive values.
- Validate data received from users, storage, network services, and external intents.
- Use secure platform APIs and avoid deprecated APIs.

## Dependencies

- Prefer Android and Kotlin standard libraries and existing project dependencies.
- Do not add a dependency when a small, clear implementation is sufficient.
- Request approval before adding or replacing a significant dependency, plugin, or framework.
- Prefer maintained, stable dependencies with clear documentation.
- Keep dependency versions and configuration consistent with the project.

## Testing

- Add or update unit and integration tests whenever behavior changes or new behavior is introduced.
- Test observable behavior and contracts, not private implementation details.
- Cover successful paths, relevant failure paths, and important edge cases.
- Keep tests deterministic, independent, concise, and readable.
- Use a consistent Arrange–Act–Assert structure unless the existing suite defines another convention.
- Name tests according to behavior and expected outcome.
- Avoid unnecessary mocking; prefer simple fakes when they make behavior clearer.
- Create fixtures, builders, or test factories for shared test objects when they reduce meaningful duplication.
- Do not duplicate production logic inside tests.
- Fix the cause of a failing test; do not weaken or remove a valid test merely to make it pass.

## Validation

Before considering work complete:

- Format the changed code.
- Run the relevant static analysis and lint checks.
- Run relevant unit and integration tests.
- Run the build for the affected module when practical.
- Report what was validated and clearly state anything that could not be run.

## Version Control

- Do not commit or push unless explicitly asked.
- Do not add co-authors to commits.
- Use extremely short, clear commit messages in Portuguese, following the repository's existing pattern.
- Keep each commit focused on one coherent change.
- Do not use force push, destructive Git commands, history rewriting, or broad rebases without explicit approval.
- Do not include unrelated formatting or refactoring in a commit.

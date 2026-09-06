---
project_name: 'MyFoodTracker'
user_name: 'Georgii'
date: '2026-07-25'
sections_completed:
  ['technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'quality_rules', 'workflow_rules', 'anti_patterns']
status: 'complete'
rule_count: 19
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

- **Android SDK**: Target 36, Min 35 (Java 11 compatibility)
- **Android Gradle Plugin (AGP)**: 9.2.1
- **Koin (Dependency Injection)**: 3.5.6
- **Room Database**: 2.6.1 (using KSP compiler 2.2.10-2.0.2)
- **Jetpack Navigation**: 2.6.0 (Fragment & UI KTX)
- **Material Components**: 1.10.0
- **AppCompat**: 1.6.1

## Critical Implementation Rules

### Language-Specific Rules (Kotlin)

- **Synchronous Execution**: Database queries and VM updates are synchronous. Do not introduce `suspend` modifiers or coroutines unless refactoring the Room database builder.
- **Threading Exception**: Synchronous queries are permitted ONLY for local Room DB operations under `.allowMainThreadQueries()`. Any network or async API integrations must use Kotlin Coroutines.
- **Data Model Decoupling**: Database entities must be mapped to domain models within the repository layer using private extension mappings. Never leak entities to domain or presentation layers.
- **UseCase Pattern**: Define use cases as factory-injected classes using `operator fun invoke()` for clean calling conventions.

### Framework-Specific Rules (Android Jetpack & Koin)

- **View Binding Lifecycle**: In Fragments, always nullify the backing field (`_binding = null`) in `onDestroyView()`.
- **LiveData Observation**: Always observe `LiveData` within Fragments using `viewLifecycleOwner`.
- **Koin DI Registration**: Register dependencies in `AppModule.kt` using `single` for data layers, `factory` for Use Cases, and `viewModel` for ViewModels. Inject VMs via `by viewModel()`.
- **Room Relationships**: Child database entities must define a foreign key constraint with cascade-on-delete (`ForeignKey.CASCADE`) and an index on the foreign key column.

### Testing Rules

- **Dependency Prep**: Before writing ViewModel or Use Case tests, add mocking libraries (e.g., MockK) and `androidx.arch.core:core-testing` to Gradle configs to support LiveData testing.
- **Mocking Boundaries**: Mock repository interfaces when testing Use Cases and ViewModels. Never use concrete database components in pure unit tests.
- **Database Isolation**: Integration tests for DAOs must run in the `androidTest` source set using Room's in-memory builder (`Room.inMemoryDatabaseBuilder`) and be closed in `tearDown()`.

### Code Quality & Style Rules

- **Resource Naming**: Layout files must use prefix-based snake_case (`activity_`, `fragment_`, `item_`). Layout view IDs must use snake_case (e.g., `@+id/button_add`), which compile to camelCase properties under View Binding.
- **Clean Architecture Layers**: Package-level separation must be maintained: `domain/` for business logic (no Android imports), `data/` for infrastructure, and `presentation/` for UI and ViewModels.
- **Dependency Registration**: Keep DI declarations centered in `AppModule.kt`. Avoid scatter declarations.

### Development Workflow Rules

- **Build & Test Verification**: Run `.\gradlew.bat compileDebugSources` and `.\gradlew.bat testDebugUnitTest` to verify that code changes compile and pass all existing unit tests.
- **Emulator Control**: Run the emulator detached on Windows using: `Start-Process -FilePath "emulator" -ArgumentList "-avd", "medium_phone"`.
- **Structure Updates**: Always update the files and folders map in `.agents/project_structure.md` when adding, deleting, or renaming code/resource files.

### Critical Don't-Miss Rules

- **No View/Context in ViewModels**: ViewModels must never reference Android `Context` or UI views. Pass string resource IDs instead of resolved strings where translation is required.
- **DAO SQL Isolation**: Keep all SQLite queries isolated to DAO interfaces. Repositories must delegate all DB interactions to DAOs.
- **Room Schema Changes**: If database models change, increment the database version in `AppDatabase.kt` and use `.fallbackToDestructiveMigration()` in the database builder in `AppModule.kt` for local dev simplicity.
- **Cascade Deletes**: Let SQLite handle cascade deletion for related tables (e.g., removing a meal deletes all its food items). Avoid writing manual child deletion queries.

---

## Usage Guidelines

**For AI Agents:**

- Read this file before implementing any code.
- Follow ALL rules exactly as documented.
- When in doubt, prefer the more restrictive option.
- Update this file if new patterns emerge.

**For Humans:**

- Keep this file lean and focused on agent needs.
- Update when technology stack changes.
- Review quarterly for outdated rules.
- Remove rules that become obvious over time.

Last Updated: 2026-07-25

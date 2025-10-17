# Predictable Agents Constitution

## Core Principles

### I. Pure Functional Programming (NON-NEGOTIABLE)
All code MUST adhere to pure functional programming principles without exception. Functions must be referentially transparent with no side effects outside designated effect boundaries. This ensures predictability, testability, composability, and maintainability across the entire platform.

**Rationale**: The mathematical nature of AI agent behavior requires predictable, testable code that can be reasoned about formally. Pure functions eliminate entire categories of bugs and enable reliable composition of complex agent behaviors.

### II. Function Size Discipline
Every function MUST be 10 lines or fewer, including signature, body, and return statement. Complex operations MUST be decomposed into smaller, focused functions that compose to achieve the desired behavior.

**Rationale**: Small functions enforce single responsibility, improve readability, enable easier testing, and prevent cognitive overload. This constraint forces proper functional decomposition and composition.

### III. Forbidden Imperative Constructs
The following constructs are STRICTLY PROHIBITED: for/while loops, mutable variables (var/let), try-catch-throw exception handling, and uncontained side effects (console.log, direct I/O, random generation).

**Rationale**: These imperative constructs introduce unpredictability and break referential transparency. Pure functional alternatives provide better composition, testing, and reasoning about program behavior.

### IV. Type Safety and Totality
All code MUST have 100% type coverage with no any/dynamic types. Functions MUST handle all possible inputs in their domain using algebraic data types (Result, Option) rather than exceptions.

**Rationale**: Complete type safety eliminates runtime type errors and makes invalid states unrepresentable. Totality ensures functions are mathematically sound and handle all cases explicitly.

### V. Immutable Data Structures
All data structures MUST be immutable by default. Updates MUST use copy-on-write semantics or persistent data structures with structural sharing for performance.

**Rationale**: Immutability eliminates data races, simplifies reasoning about state changes, and enables safe parallelization of agent operations.

## Quality Gates

### Code Structure Requirements
- All files MUST be 1000 lines or fewer
- All functions MUST be 10 lines or fewer
- No function exceeds cyclomatic complexity of 5
- No code duplication across modules

### Detekt Rule Enforcement (NON-NEGOTIABLE)
All detekt custom rules and configuration MUST be strictly enforced without modification or suppression unless explicitly approved by the user. AI assistants and developers MUST NOT:
- Add `@Suppress` annotations to bypass detekt rules
- Modify detekt configuration files without explicit user consent
- Disable or weaken existing detekt rules
- Add file-level or line-level suppressions without justification

**Required Process for Rule Modifications**:
1. Present the detekt violation to the user with full context
2. Explain why the rule is triggering and what it's protecting against
3. Propose compliant solutions that satisfy the rule
4. Only if the user explicitly requests suppression, add minimal `@Suppress` with justification comment
5. Document any suppressions in PR descriptions

**Rationale**: Detekt rules encode critical architectural constraints and quality standards. Bypassing them silently undermines code quality, introduces technical debt, and violates the functional programming discipline. Every rule violation represents a potential bug or design flaw that must be addressed properly, not suppressed.

**Gradle Daemon Cache Issue**: When developing or modifying custom detekt rules, the Gradle Daemon aggressively caches classloaders and service provider configurations. After any changes to custom detekt rules, developers MUST run `./gradlew --stop` to clear the daemon cache before running detekt again. Failure to do so will result in rules not being loaded or old versions being used.

**Build Verification Requirement (NON-NEGOTIABLE)**: After ANY code changes, developers MUST run `./gradlew build` at the repository root and fix ALL Detekt violations before considering the work complete. The build MUST pass with 0 Detekt violations. This applies to:
- All new feature implementations
- All bug fixes
- All refactorings
- All documentation updates that include code examples
- All test additions or modifications

**Process**:
1. Make code changes
2. Run `./gradlew build` from repository root
3. If Detekt violations are found, fix ALL violations (not just constitutional ones)
4. Repeat steps 2-3 until build passes with 0 violations
5. Only then is the work considered complete

**Rationale**: A clean Detekt build ensures code quality consistency across the entire codebase. Partial fixes or ignoring minor violations leads to technical debt accumulation and degraded code quality over time.

### Testing Requirements
Property-based testing MUST be used for all pure functions. Tests MUST verify mathematical properties, invariants, and round-trip properties rather than specific examples.

## Development Workflow

### Module Organization
Code MUST be organized by domain feature, not technical layer. Dependencies MUST flow in one direction: Core → Services → Infrastructure → Adapters.

### Error Handling
All errors MUST be handled through Arrow's Raise context parameters exclusively. Functions MUST use `context(_: Raise<Error>)` with underscore naming and use context functions for error handling. Exception-based control flow is strictly forbidden. Error types MUST be explicit and composable.

**Required Imports**:
```kotlin
import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.withError
```

**Available Context Functions** (from `arrow.core.raise.context`):
- `raise(error)` - Short-circuit with typed error
- `ensure(condition) { error }` - Type-safe alternative to `require`
- `ensureNotNull(value) { error }` - Type-safe alternative to `requireNotNull`
- `withError(transform) { block }` - Transform error type
- `Either.bind()` - Extract Right value or raise Left
- `Option.bind()` - Extract Some value or raise singleton error
- `Effect.bind()` - Extract effect value or raise error
- `Result.bind()` - Extract success or raise failure
- `bindAll()` - Bind all values in collections (Map, Iterable, NonEmptyList, NonEmptySet)

**Required Pattern**:
```kotlin
// ✅ Function definition with underscore-named context parameter
context(_: Raise<ValidationError>)
fun validateData(input: String): Data {
    ensure(input.isNotBlank()) { ValidationError("Input cannot be blank") }
    return Data(input)
}

// ✅ Calling from another context function - automatic propagation
context(_: Raise<ValidationError>)
fun processUserInput(input: String): ProcessedData {
    val data = validateData(input) // context propagates automatically
    return process(data)
}

// ✅ Extract Either values with bind()
context(_: Raise<DomainError>)
fun createUser(idResult: Either<DomainError, UserId>): User {
    val id = idResult.bind() // automatically raises on Left
    return User(id)
}

// ✅ Transform error types with withError
context(_: Raise<ParseError>)
fun parseAndValidate(input: String): Data {
    withError({ domainError: DomainError ->
        ParseError.InvalidField(domainError.message)
    }) {
        validateData(input) // raises DomainError, transformed to ParseError
    }
}
```

**Boundary Pattern** (Converting context to Either):
```kotlin
// ✅ Internal functions - use context
context(_: Raise<ParseError>)
fun parseData(input: String): Data = ...

context(_: Raise<ParseError>)
fun validateData(data: Data): ValidData = ...

// ✅ Boundary function - converts context to Either using either { }
fun processInput(input: String): Either<ParseError, ValidData> = either {
    val data = parseData(input)
    validateData(data)
}
```

**Key Points**:
- Use `context(_: Raise<Error>)` with underscore - context parameters MUST be named in Kotlin
- Import ALL functions from `arrow.core.raise.context` package
- The Raise context propagates automatically through call chains - no explicit passing needed
- **ALL internal functions** must use context parameters, NOT return Either/Result
- **ONLY at API boundaries** (entry points, public APIs) use `either { }` to convert context to Either
- Use `.bind()` to extract values from `Either`, `Option`, `Effect`, `Result`, etc.
- Never use `try-catch` - use `arrow.core.raise.catch { }` if needed for exceptions

**Forbidden Patterns**:
- ❌ `context(Raise<Error>)` - not naming the parameter (Kotlin requires naming)
- ❌ `context(raise: Raise<Error>)` - descriptive naming (use `_:` for unused parameter)
- ❌ `Raise<Error>.()` receiver syntax (use `context(_: Raise<Error>)` instead)
- ❌ `Result<T, E>` return types anywhere (use context parameters)
- ❌ Returning `Either<Error, T>` from internal functions (only at boundaries)
- ❌ `try-catch-throw` (use context functions instead)
- ❌ Nullable return types for error cases (use context with `option { }` at boundary)
- ❌ `raise.raise(error)` - use `raise(error)` directly from context
- ❌ `raise.ensure(condition)` - use `ensure(condition)` directly from context
- ❌ `either { }.getOrElse { }` - use `withError { }` or `recover { }` for error transformation

### Effect Boundaries
Side effects MUST be contained within designated effect systems (IO monad, dependency injection). Pure domain logic MUST be separated from impure operations.

**API Boundaries**: The `either { }` function is ONLY permitted at true API boundaries where Raise context must be converted to Either for external consumers:
- ✅ HTTP handlers (files matching pattern `**/handlers/**Handlers.kt`) that convert Raise to Either for HTTP responses
- ✅ `main()` functions that initialize the application
- ✅ Test helper files (`TestHelpers.kt`) that adapt Raise for test frameworks
- ❌ Service layer functions (use Raise context throughout)
- ❌ Repository layer functions (use Raise context throughout)
- ❌ Domain layer functions (use Raise context throughout)

HTTP handlers MUST use `@file:Suppress("NoEitherUsage")` with the justification comment: `// HTTP handlers are the API boundary where Either is required`

### External Library Integration Requirements (NON-NEGOTIABLE)

**Mandatory Research Protocol for Library Migrations**:
Before implementing ANY migration that involves adopting an external library (especially from external repositories like GitHub), developers MUST:

1. **Clone the library repository to /tmp** for investigation
2. **Research the library's architecture** including:
   - API structure and design patterns
   - Error handling approach
   - Type system compatibility
   - Immutability guarantees
   - Multiplatform support
   - Dependency requirements
3. **Create a specialized agent** in `.claude/agents/` with library-specific expertise
4. **Document findings** in the feature's research.md file
5. **Only then proceed** with implementation tasks

**Rationale**: External libraries may have fundamentally different architectures than anticipated. Jumping directly to implementation without thorough research risks architectural mismatches, wasted effort, and technical debt. A specialized agent captures library-specific knowledge for consistent application across all migration tasks.

**Koog Integration Mandatory Research Protocol (NON-NEGOTIABLE)**:
Before implementing ANY task that involves Koog AI framework integration, developers MUST:

1. **Research Koog's actual implementation** in `/tmp/koog` repository for:
   - How Koog handles the specific feature (compression, streaming, persistence, etc.)
   - What patterns and constraints Koog enforces
   - Integration points and session lifecycle
   - Type conversions and adapters required
   - Actual behavior vs. assumed behavior
2. **Document findings** in the feature specification before creating tasks
3. **Verify assumptions** - Do NOT assume how Koog works; READ the code
4. **Only then proceed** with task implementation

**Rationale**: Koog is our underlying agent framework. Implementing features without understanding Koog's actual architecture leads to:
- Architectural mismatches (wrapping vs. reimplementation decisions)
- Duplicate functionality (implementing what Koog already provides)
- Missing constraints (edge cases that don't exist in Koog)
- Integration bugs (incorrect session lifecycle, type conversions)

Every Koog integration task MUST start with research in `/tmp/koog` to validate assumptions and understand actual behavior.

### AI Agent Integration

#### Claude Code Agent Selection (MANDATORY)
When working with Claude Code, Claude Code MUST use the appropriate specialized agents from `.claude/` for specific tasks. DO NOT attempt manual implementation when a specialized agent exists.

**Agent Selection Rules**:
- **Test Automation** → Use `test-automator` agent for comprehensive testing strategies, test generation, and quality engineering
- **Code Review** → Use `code-reviewer` agent for code quality analysis, security vulnerabilities, and performance optimization
- **Debugging** → Use `debugger` agent for errors, test failures, and unexpected behavior
- **Error Investigation** → Use `error-detective` agent to search logs and codebases for error patterns and root causes
- **DevOps Troubleshooting** → Use `devops-troubleshooter` agent for incident response, log analysis, and system reliability
- **Database Issues** → Use `database-optimizer` agent for query optimization, performance tuning, and scalability
- **TypeScript/JavaScript** → Use `typescript-pro` or `javascript-pro` agents for language-specific expertise
- **Python Development** → Use `python-pro` agent for modern Python patterns and optimization
- **Kotlin Development** → Use `kotlin-pro` agent for multiplatform, functional programming, and Arrow patterns
- **Koog Integration** → Use `koog-integration-expert` agent for JetBrains Koog AI framework integration, type adapters, and Raise context wrapping
- **Architecture Review** → Use `architect-review` agent for system design and architectural integrity
- **Frontend Issues** → Use `frontend-developer` agent for React, UI components, and client-side concerns
- **Security Audits** → Use `security-auditor` agent for vulnerability assessment and compliance
- **Performance Issues** → Use `performance-engineer` agent for observability and optimization

**Rationale**: Specialized agents have domain expertise, proper tool access, and established patterns for their areas. Manual implementation duplicates effort and produces lower quality results.

#### Mandatory Subagent Usage for Implementation Tasks (NON-NEGOTIABLE)

ALL implementation tasks MUST be delegated to specialized subagents. Claude Code MUST NOT manually implement features when a specialized agent exists. This ensures consistent quality, proper testing, and architectural compliance across all implementations.

**Required Implementation Pattern**:
1. **Identify the task type** - Determine which specialized subagent is appropriate (frontend, backend, mobile, Kotlin, etc.)
2. **Delegate to implementation subagent** - Use the Task tool to launch the appropriate specialized agent for the implementation
3. **Mandatory verification** - ALWAYS launch a verification subagent after implementation completes
4. **Update task tracking** - Mark task complete in tasks.md ONLY after verification passes

**Implementation Subagent Selection** (by task type):
- **Frontend/React Components** → `frontend-developer` agent (React 19, Next.js 15, Tailwind CSS, shadcn/ui expertise)
- **Kotlin Multiplatform Code** → `kotlin-pro` agent (Arrow Raise, multiplatform, constitutional compliance)
- **Kotlin Mobile/Compose** → `flutter-expert` agent for Compose Multiplatform patterns, OR `kotlin-pro` for Compose logic
- **TypeScript/JavaScript** → `typescript-pro` or `javascript-pro` agents
- **API Integration** → `backend-architect` agent for REST/GraphQL API design and integration
- **Database/Schema** → `database-optimizer` agent for schema design and query optimization
- **Security Implementation** → `security-auditor` agent for authentication, authorization, input validation
- **Testing** → `test-automator` agent for comprehensive test strategies and implementation

**Mandatory Verification Subagent** (ALWAYS required):
After ANY implementation task completes, Claude Code MUST launch a **verification subagent** to validate the implementation:

1. **For Kotlin code** → `kotlin-pro` agent to verify:
    - Constitutional compliance (pure FP, ≤10 lines, Raise context)
    - Zero Detekt violations (`./gradlew build` passes)
    - Proper multiplatform organization (shared vs. platform-specific)
    - Arrow Raise context usage (no Either returns in internal functions)

2. **For TypeScript/React code** → `frontend-developer` agent to verify:
    - Component functionality matches requirements
    - Props/state management is correct
    - Styling matches design system (Scandinavian minimalist)
    - No console errors or warnings
    - Proper TypeScript typing (no `any`)

3. **For ALL code** → `code-reviewer` agent to verify:
    - Code quality and maintainability
    - Security vulnerabilities
    - Performance issues
    - Test coverage
    - Documentation completeness

**Verification Must Confirm**:
- ✅ Implementation fulfills ALL task requirements
- ✅ All tests pass (unit, integration, E2E as applicable)
- ✅ Build succeeds with ZERO warnings/errors
- ✅ Constitutional principles followed (Kotlin only)
- ✅ No regressions introduced
- ✅ Documentation updated (if applicable)

**Failure Handling**:
If verification subagent reports issues:
1. **DO NOT mark task as complete**
2. Launch implementation subagent again with fixes
3. Re-run verification subagent
4. Repeat until verification passes
5. Only then mark task complete in tasks.md

**Forbidden Patterns**:
- ❌ Manual implementation when specialized subagent exists
- ❌ Marking task complete without verification
- ❌ Skipping verification subagent ("looks good to me")
- ❌ Ignoring verification failures
- ❌ Partial fixes without re-verification

**Example Workflow**:
```
Task: T019 Create api-client.ts in frontend/lib/

Step 1: Launch frontend-developer agent
  Prompt: "Implement api-client.ts with Axios instance configured for Ktor backend.
          Base URL from env, JWT interceptor, error handling, token refresh logic.
          Must match requirements in task T019."

Step 2: Wait for implementation completion

Step 3: Launch code-reviewer agent (MANDATORY)
  Prompt: "Verify frontend/lib/api-client.ts implementation:
          - Fulfills T019 requirements
          - TypeScript typing is correct (no any)
          - Error handling is robust
          - JWT refresh logic works correctly
          - No security vulnerabilities
          Report any issues that must be fixed."

Step 4a: If verification PASSES → Mark T019 complete in tasks.md
Step 4b: If verification FAILS → Fix issues, re-verify, then mark complete

Step 5: Update tasks.md with [X] for T019
```

**Rationale**: Specialized subagents have deep domain expertise and proper tool access for their areas. Manual implementation bypasses quality controls, produces inconsistent code, and violates architectural principles. Mandatory verification ensures every implementation meets quality standards before being marked complete. This two-phase approach (implementation + verification) prevents technical debt and ensures tasks are truly done, not just "mostly working."

### Parallel Execution (MANDATORY)
All parallel suspend function execution MUST use Arrow's higher-level parallel APIs. Manual creation of coroutine jobs and async blocks is strictly forbidden.

**Required Patterns**:
```kotlin
import arrow.fx.coroutines.parMap
import arrow.fx.coroutines.parZip

// ✅ Parallel list processing with parMap
val results: List<Result> = items.parMap { item ->
    processItem(item)
}

// ✅ Parallel execution of independent operations with parZip
val (userResult, orderResult, inventoryResult) = parZip(
    { fetchUser(userId) },
    { fetchOrders(userId) },
    { checkInventory(productId) }
) { user, orders, inventory -> Triple(user, orders, inventory) }

// ✅ Parallel flow processing
flowOf(items)
    .parMap { item -> processItem(item) }
    .collect { result -> handleResult(result) }
```

**Forbidden Patterns**:
- `async { }` / `await()` manual coroutine creation (use `parZip`, `parMap`)
- `launch { }` manual job creation (use Arrow parallel APIs)
- `runBlocking { }` blocking coroutine execution (breaks non-blocking guarantees)
- `GlobalScope.launch` or `GlobalScope.async` (always forbidden)
- `withContext(Dispatchers.IO)` for parallelism (use `parMap`/`parZip`)

**Rationale**: Arrow's parallel APIs provide structured concurrency, automatic error handling, resource safety, and cancellation support. Manual coroutine creation bypasses these guarantees and introduces potential memory leaks and error propagation issues.

## Governance

This constitution supersedes all other development practices and coding standards. All code reviews MUST verify compliance with these principles before approval.

**Amendment Process**: Changes require documentation of rationale, impact analysis across dependent templates, and migration plan for existing code. Version must increment according to semantic versioning.

**Compliance Review**: All Pull Requests must pass automated linting that enforces these principles. Manual review must verify adherence to functional programming discipline and constitutional requirements.

**Migration Strategy**: Existing imperative code must be gradually refactored to functional style, starting with hot paths and extracting pure functions from impure code.


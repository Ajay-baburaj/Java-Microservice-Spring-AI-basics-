# Microservices with Spring AI: Combined Learning List

Everything to learn from **both** services in this repo, `ticket-service` and `ai-classifier-service`. The list follows a ticket on its way through the system: Client → ticket-service → ai-classifier-service → Groq → back → database. Each item names the file where the concept appears, so you can study with your own code open.

★ = interviewers most often go deep on this topic.

The ticket-service-only version, written before the AI service existed, is [`ticket-service/Ticket_Service_Learning_List.pdf`](ticket-service/Ticket_Service_Learning_List.pdf). This list replaces it and also covers everything added since: the AI service, Spring AI, WebClient, Resilience4j and Swagger.

---

## 1. The big picture: why two services

**[README.md](README.md) (architecture and data-flow diagrams)**
- ★ **Monolith vs microservices:** the classifier is split out because it has different failure modes (an external LLM), different scaling needs and different secrets (the Groq key). Be ready to argue both for and against the split.
- ★ **Independent deployability:** the two services even run on different Spring Boot major versions (4.1 and 3.4). That's only possible because they share nothing but an HTTP contract.
- **Synchronous vs asynchronous communication:** today the ticket waits for the AI over REST. The alternative is to save the ticket first and classify it later from an event (Kafka/RabbitMQ). Know the trade-offs: latency, coupling, and eventual consistency.
- **Graceful degradation:** if the AI is down, the ticket is still created with default labels. Designing a fallback is a product decision, not just a technical one.

---

## 2. Build and project setup

**`ticket-service/pom.xml`**
- **Maven basics:** the `<parent>` POM, `groupId`/`artifactId`/`version` (`in.ajay` / `ticket-service` / `0.0.1-SNAPSHOT`), and what `SNAPSHOT` means.
- ★ **Spring Boot starters**, and why most dependencies have no version (the parent's BOM manages them). Spot the exceptions that do pin a version (`springdoc`, `resilience4j`, `mapstruct`) and explain why.
- **Dependency scopes:** `runtime` for `h2` and `postgresql` (JDBC drivers are only needed when the app runs), and `test` for the test starters.
- **Spring Boot 4 / Spring Framework 7:** `spring-boot-starter-webmvc` replaced `-web`, and you include both, which is redundant. Boot 4 also split modules like `spring-boot-h2console` and moved to Jackson 3.
- **`spring-boot-starter-webflux` in an MVC app:** added only to get `WebClient`. Know what it pulls in (Reactor, Netty) and the lighter option, `RestClient`, which is built into Spring 6.1+.
- **Library vs Boot version alignment:** `resilience4j-spring-boot3` and `springdoc 2.x` target Boot 3 but run on Boot 4 here. Learn to check compatibility matrices before upgrading.
- **MapStruct:** the library is present but the annotation processor (`maven-compiler-plugin` → `annotationProcessorPaths`) is missing, so `TicketMapper` generates nothing. Lombok uses the same mechanism.

**`ai-classifier-service/pom.xml`**
- ★ **Importing a BOM:** `<dependencyManagement>` with `spring-ai-bom` (`<type>pom</type>`, `<scope>import</scope>`). This is how Spring AI versions stay consistent.
- **Milestone releases:** Spring AI `1.0.0-M5` comes from the `repo.spring.io/milestone` repository declared in `<repositories>`. In the GA release (1.0+) the starter is renamed `spring-ai-starter-model-openai`. Know why milestone versions are risky in production.
- **`spring-ai-openai-spring-boot-starter` used for Groq:** any OpenAI-compatible API works by changing `base-url`.

**Both: `mvnw`, `.mvn/wrapper`, `.gitignore`, `.gitattributes`**
- **The Maven Wrapper:** everyone builds with the same Maven version without installing it.
- ★ **Keeping secrets out of git:** the service `.gitignore` files contain `.env/`, which only matches a *folder*, so a `.env` *file* would be committed. The root `.gitignore` fixes it with `.env`. Learn how gitignore patterns match, and commit `.env.example` instead.
- **Package naming:** reverse-domain and lowercase (`in.ajay.ticketservice`). Hyphens aren't allowed, which is why Spring Initializr turns `week-01` into `week_01`.

---

## 3. Application entry points

**`TicketServiceApplication.java`, `Week01AiClassifierServiceApplication.java`**
- ★ **`@SpringBootApplication`** = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`.
- ★ **Component scanning starts at the main class's package.** Before the rename to `in.ajay.*`, the exception handler lived outside the scanned package and never ran. Be ready to explain why.
- ★ **Auto-configuration:** how Spring set up the H2 DataSource, JPA, Tomcat, the Spring AI `ChatClient.Builder` and the Resilience4j aspects just from your dependencies (`@ConditionalOnClass`, `@ConditionalOnMissingBean`). Run with `--debug` to see the conditions report.
- **What `SpringApplication.run()` does:** creates the `ApplicationContext`, starts embedded Tomcat, and runs the runners.
- **Naming:** `Week01AiClassifierServiceApplication` still has the course week in its name. Renaming a main class means updating the test class too.

---

## 4. Configuration

**`ticket-service/src/main/resources/application.yml`**
- ★ **Multi-document YAML and profiles:** documents are separated by `---`, the active profile is set with `spring.profiles.active: h2`, and each document says which profile it belongs to with `spring.config.activate.on-profile`.
- ★ **A real bug from this file:** the `springdoc`, `resilience4j`, `management.health` and second `app` blocks were appended *after* the `postgres` document, so they only apply with the postgres profile. Under `h2` (the default), Resilience4j runs on its built-in defaults. Learn to read which document a key lands in.
- **Configuration precedence:** yml < environment variables < command-line arguments. `${CLASSIFIER_SERVICE_BASE_URL:http://localhost:8090}` shows a placeholder with a default.
- **Custom properties:** `app.classifier-service.*` read with `@Value`. Learn the type-safe alternative, `@ConfigurationProperties`, with validation.
- ★ **`ddl-auto` values** (`none`/`validate`/`update`/`create`/`create-drop`), and why production uses Flyway or Liquibase.
- **The H2 console and Actuator exposure:** what `health`, `info` and `metrics` show, and why `*` or the H2 console must never reach production.
- **Secrets:** the Postgres password is in plaintext. Use `${DB_PASSWORD}`.

**`ai-classifier-service/src/main/resources/application.yaml` and `.env.example`**
- ★ **`spring.ai.openai.*`:** `api-key: ${GROQ_API_KEY}`, `base-url`, `chat.options.model` (`openai/gpt-oss-120b`), and `embedding.enabled: false` (Groq has no embeddings endpoint).
- **Loading a `.env` file:** Spring Boot does *not* read `.env` by itself. You export it (`set -a; source .env; set +a`) or add `spring-dotenv`. In Docker or Kubernetes you'd use env vars or secrets instead.
- **Resilience4j config by instance name:** `resilience4j.retry.instances.groq-classify` matches `@Retry(name = "groq-classify")`. `retry-exceptions` restricts retries to `AiProviderException`.

---

## 5. Ticket service: entity and database layer

**`Ticket.java`**
- ★ **`@Entity`/`@Table`**, JPA vs Hibernate, and `jakarta.persistence` vs the old `javax`.
- ★ **The no-arg constructor:** Hibernate builds objects by reflection and creates proxies.
- ★ **`@GeneratedValue(IDENTITY)`** vs `SEQUENCE` vs UUID, and why IDENTITY disables JDBC batch inserts.
- ★ **`@Column(length = 100)` vs the DTO's `@Size(max = 200)`:** a 150-character subject passes validation and then fails in the database with a 500. A great real bug for interviews.
- ★ **`@Enumerated(STRING)` vs `ORDINAL`.**
- ★ **Lifecycle callbacks** `@PrePersist`/`@PreUpdate` vs Spring Data auditing (`@CreatedDate`, `@LastModifiedDate`). `@PreUpdate` only fires on flush, which is why the PATCH response shows the old `updatedAt`.
- **The AI-derived columns** (`priority`, `sentiment`, `intent`, `suggestedTeam`) are plain `String`s. Should they be enums? What happens when the LLM returns a value you don't expect?
- ★ **`equals`/`hashCode` on entities**, and why Lombok's `@Data` is dangerous here.

**`TicketStatus.java`**
- **Enums with behavior:** add allowed transitions (for example `CLOSED` can't go back to `OPEN`). Right now any status can change to any other.

**`TicketRepository.java`**
- ★ **`JpaRepository`** and its free methods, and how an interface with no code works (`SimpleJpaRepository` proxy).
- **Derived queries:** `findByStatus(TicketStatus, Pageable)`, needed for the commented-out filter in the controller.

---

## 6. DTOs and contracts

**Ticket service: `CreateTicketRequest`, `UpdateTicketStatusRequest`, `TicketResponse`**
- ★ **Records as DTOs**, and how Jackson builds a record through its canonical constructor.
- ★ **Bean Validation:** `@NotBlank` vs `@NotEmpty` vs `@NotNull`, `@Size`, `@Email`. Know what Hibernate Validator is.
- **Enums in JSON:** `"IN_PROGRESS"` is converted to the enum. What does `"FOO"` return?
- ★ **Static factory `TicketResponse.from(Ticket)`** vs MapStruct (`TicketMapper`, unused) vs ModelMapper.
- **Privacy:** `requesterEmail` is personal data returned on every endpoint.

**Classifier: `ClassifyRequest`, `TicketClassification` (with `@JsonPropertyDescription`)**
- ★ **`@JsonPropertyDescription` feeds the LLM:** Spring AI turns the record into a JSON Schema and the descriptions end up in the prompt (see section 9).
- `@JsonProperty` on record components, and when it's actually needed.

**The same contract written twice:** `ticket-service/.../client/TicketClassification` and `ClassifyRequest`
- ★ **API contracts between services:** each service has its own copy of the DTOs, so if one side renames a field the other silently gets `null`. Learn consumer-driven contract testing (Spring Cloud Contract, Pact) and why a shared-DTO library has trade-offs too.
- **`TicketClassification.unclassified()`:** a static factory for the fallback value. Constants are kept in one place.

---

## 7. Ticket service: service layer

**`TicketService.java`**
- ★ **Constructor injection** with `final` fields, and when Spring injects without `@Autowired`.
- ★★ **`@Transactional`, the most important topic here:**
  - The AOP proxy, and why a call to a `@Transactional` method from inside the same class skips the transaction.
  - ★ **`readOnly = true` on `updateStatus` is a real bug:** Hibernate sets flush mode to manual, so the new status is never written. PATCH returns `RESOLVED`, but a GET afterwards shows `OPEN`.
  - **Dirty checking:** inside a write transaction, `ticket.setStatus()` is saved even without calling `save()`.
  - Propagation (`REQUIRED`, `REQUIRES_NEW`), isolation basics, and rollback rules (only unchecked exceptions by default).
- ★ **A remote call inside a transaction:** `create()` calls the AI service *inside* `@Transactional`, holding a database connection while waiting on an LLM that can take seconds. Classify first, then open a short transaction to save.
- ★ **Checked vs unchecked exceptions:** `findOrThrow` throws a plain `Exception`, so nothing maps it to 404 and every caller is forced to declare `throws Exception`. Throw `ResourceNotFoundException` instead.
- `Optional.orElseThrow(Supplier)`, method references (`TicketResponse::from`), and `Page.map()`.
- **Paging:** `Pageable`, `Page` vs `Slice`, and the extra count query that `Page` runs.

---

## 8. Ticket service: controller and REST

**`TicketController.java`**
- ★ `@RestController` vs `@Controller`, and `@ResponseBody`.
- ★ `@PathVariable`, `@RequestBody`, `@RequestParam`, and `@Valid`, which raises `MethodArgumentNotValidException`.
- `ResponseEntity`: `201 Created` (add a `Location` header), `204 No Content`.
- ★ **REST design:** PUT vs PATCH, idempotency, the sub-resource `/{id}/status`, and choosing status codes.
- **Returning `Page<T>` directly:** Spring Data warns that its JSON shape isn't stable. Learn `PagedModel` and `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`.
- ★ **The Spring MVC request path:** `DispatcherServlet` → handler mapping → argument resolvers → message converters (Jackson) → your method → response.

---

## 9. Service-to-service call: `ClassifierClient`

**`ticket-service/.../client/ClassifierClient.java` and `config/WebClientConfig.java`**
- ★ **`WebClient`:** builder, `baseUrl`, `post().uri().bodyValue().retrieve().bodyToMono()`.
- ★ **`.block()` in a servlet app:** it turns reactive code back into blocking code. Fine here, but know why you must never call `block()` on a Netty event-loop thread, and when `RestClient` is the simpler choice.
- ★ **Timeouts:** `app.classifier-service.timeout-ms` is read into `callTimeout`, but `callTimeout` is **never used**, so the call has no response timeout at all. Learn `.timeout(Duration)`, Reactor Netty's `responseTimeout`, connect timeouts, and the idea of a time budget per request.
- ★ **Resilience4j annotations:**
  - **Retry:** max attempts, wait duration, exponential backoff, and which exceptions are retried or ignored.
  - **Circuit breaker:** CLOSED → OPEN → HALF_OPEN, the sliding window, failure-rate threshold, and minimum number of calls.
  - ★ **Aspect order:** Retry wraps CircuitBreaker, so the `fallbackMethod` sits on `@Retry` and runs only after all retries fail. When the breaker is OPEN it throws `CallNotPermittedException`, which you should ignore rather than retry.
  - **Fallback signature:** the same parameters plus a final `Throwable`. `@SuppressWarnings("unused")` is there because only reflection calls it.
- ★ **Retry amplification:** the ticket service retries 3 times and the classifier retries Groq 3 times, so one ticket can cause up to **9 LLM calls**. Retry at one layer, or keep the retry budgets small at each layer.
- **Logging:** SLF4J `Logger`, parameterised messages (`{}`), and why you log the fallback reason.
- **Cleanup:** duplicate imports (`Duration`, `Component`, `WebClient`).

---

## 10. AI classifier service: the Spring AI part

**`TicketClassifierService.java`**
- ★ **`ChatClient`:** built from the auto-configured `ChatClient.Builder`, then `chatClient.prompt(prompt).call().entity(TicketClassification.class)`.
- ★ **`PromptTemplate`:** `{subject}` and `{description}` placeholders filled from a `Map`. A text block (`"""`) holds the prompt.
- ★★ **Structured output:** `.entity(Class)` uses a `BeanOutputConverter`. It generates a JSON Schema from the record (including the `@JsonPropertyDescription` text), appends format instructions to your prompt, and parses the reply into the record. Know what happens when the model returns invalid JSON.
- ★ **Don't trust LLM output:** the reply is typed as `String`s, so nothing stops `"POSITVE"` or `"SUPER_URGENT"` from being saved. Validate against an allowed set or map to enums, and decide what to do with a bad value (retry, default, or reject).
- **Prompt engineering basics:** clear rules, closed label sets, no guessing. The current prompt has typos ("must ne one of", "POSITVE") that can lead to wrong labels. Prompts are code: review and test them.
- **Model settings:** temperature (lower means more consistent classification), max tokens, and choosing a model for cost and speed.
- **Prompt injection:** the ticket text goes straight into the prompt. What could a malicious ticket description make the model do, and how do you limit the damage?
- ★ **Exception translation:** any provider failure is wrapped in `AiProviderException`, which gives callers one clear signal and makes the retry rule simple. Note the re-throw so a null-result exception isn't wrapped twice.
- `@Retry(name = "groq-classify")` only works because the controller calls this method through the Spring proxy (same AOP rule as `@Transactional`).

**`config/AiClientConfig.java`**
- ★ **`RestClientCustomizer`:** Spring AI calls Groq with `RestClient`, and this bean sets a connect timeout (3 s) and read timeout (8 s) on every `RestClient.Builder`. Learn customizer beans as Boot's extension point.
- **Worst-case latency:** 3 attempts × 8 s plus backoff ≈ 25 s for one classification. Multiply by the caller's retries, and you see why timeouts must be planned end to end.

**`ClassifierController.java`**
- A thin controller: validate, delegate, return `200`. Business logic belongs in the service.

---

## 11. Error handling, across both services

**`ai-classifier-service/.../GlobalExceptionHandler.java`**
- ★ **Handler resolution:** `AiProviderException` → 503 wins over the catch-all `Exception` → 500, because Spring picks the most specific type.
- **503 vs 500 vs 502/504:** choose the status that tells the caller whether retrying makes sense.
- ★ **Leaking internals:** the catch-all returns `ex.getMessage()`. Earlier this exposed Groq's raw `401 Invalid API Key` body to the caller. Log the details and return a generic message.

**`ticket-service/.../GloabalExceptionHandler.java`** (also fix the "Gloabal" spelling)
- Handles `ResourceNotFoundException` → 404 and validation → 400. There is **no catch-all**, so other errors fall through to Spring's default JSON. That uses `timestamp`, while your `ApiError` uses `timeStamp`, so clients see two error shapes.
- ★ **`ProblemDetail` (RFC 9457)** and `spring.mvc.problemdetails.enabled` as the standard alternative.
- Custom exception hierarchies, and why they extend `RuntimeException`.

---

## 12. Observability and API docs

- **Actuator:** `/actuator/health`, `info`, `metrics`. With `management.health.circuitbreakers.enabled`, circuit-breaker state shows up in health, but only once that config is in the right YAML document (section 4).
- **Swagger / OpenAPI:** `springdoc-openapi-starter-webmvc-ui`, `OpenApiConfig` (title and description), `/swagger-ui.html` and `/v3/api-docs`.
- **What's missing, and worth learning next:**
  - correlation IDs passed from ticket-service to the classifier
  - distributed tracing (Micrometer Tracing + Zipkin/Tempo)
  - metrics on LLM latency and token usage

---

## 13. Testing, for both services

**`TicketServiceApplicationTests`, `Week01AiClassifierServiceApplicationTests`**
- What `@SpringBootTest` + `contextLoads()` checks: only that the app starts. That's the only test in each service.
- ★ **Tests to write:**
  - `@WebMvcTest(TicketController.class)` + MockMvc + `@MockitoBean TicketService`
  - `@DataJpaTest` for `TicketRepository`
  - Plain JUnit 5 + Mockito for `TicketService`, mocking `ClassifierClient`
  - **WireMock or MockWebServer** for `ClassifierClient`: return 500s and slow responses, and check that the retry, circuit breaker and fallback behave
  - Mock the `ChatClient` in `TicketClassifierService` so tests never call Groq or need a key
- **Testing LLM features:** unit tests stub the model. Separate *evaluation* runs use real ticket examples to measure label accuracy when you change the prompt or model.
- **Good first tests are your bugs:** status update persists; a missing ID returns 404; a 150-character subject returns 400; the fallback runs when the classifier is down.

---

## 14. Where this is going (Project 2)

The next project builds on these exact ideas with more services:
- **Service discovery** (Eureka) instead of hard-coded `localhost:8090`
- **API gateway** (Spring Cloud Gateway) with **JWT** validation at the edge
- **Event-driven flow** with Kafka: order → payment, inventory, notification, AI risk scoring
- **Idempotency** for at-least-once message delivery
- **Docker / Docker Compose** for running everything together, plus Postgres and **Flyway** migrations

---

## Bugs and gaps found while reading (learning exercises)

| # | Problem | Where | Topic to learn |
|---|---|---|---|
| 1 | Status updates aren't saved | `TicketService.updateStatus` | `@Transactional(readOnly)` and flush mode |
| 2 | A missing ticket returns 500, not 404 | `TicketService.findOrThrow` | Checked vs unchecked exceptions |
| 3 | ~~Exception handlers never ran~~ **Fixed** by moving everything under `in.ajay.ticketservice` | `exception` package | Component scanning |
| 4 | Subjects of 101–200 characters cause a 500 | `CreateTicketRequest` vs `Ticket` | Keeping DTO and DB limits in sync |
| 5 | MapStruct generates nothing | `ticket-service/pom.xml` | Annotation processors |
| 6 | Any status can change to any other | `TicketStatus` | Domain rules and state machines |
| 7 | Both `-web` and `-webmvc` starters | `ticket-service/pom.xml` | Spring Boot 4 starters |
| 8 | Resilience4j, springdoc and CB-health settings ignored under `h2` | `application.yml` (inside the postgres document) | Multi-document YAML and profiles |
| 9 | `timeout-ms` is read but never applied | `ClassifierClient.callTimeout` | WebClient timeouts and time budgets |
| 10 | One ticket can trigger up to 9 Groq calls | Retries in both services | Retry amplification |
| 11 | A DB transaction stays open during the AI call | `TicketService.create` | Transaction boundaries |
| 12 | Prompt typos ("ne", "POSITVE") | `TicketClassifierService` | Prompt quality |
| 13 | LLM labels are never validated | `TicketClassification` (Strings) | Never trust model output |
| 14 | The catch-all leaks provider error text | Classifier `GlobalExceptionHandler` | Error hygiene and security |
| 15 | Two different error JSON shapes | Ticket service has no catch-all handler | A consistent error contract |
| 16 | PATCH response shows the old `updatedAt` | `@PreUpdate` runs on flush | The JPA flush lifecycle |
| 17 | `.env/` in `.gitignore` only matches folders | Service `.gitignore` files | gitignore patterns and secrets |
| 18 | Only `contextLoads` tests | Both `src/test` | Test slices, WireMock, mocking AI |

Fixing these yourself, one at a time and with a test for each, is the best practice for interviews. Each one is a real story you can tell about something you debugged.

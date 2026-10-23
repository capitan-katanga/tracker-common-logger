# CLAUDE.md

Guía para Claude Code en este repositorio.

## Qué es

`com.expense.tracker:tracker-common-logger:0.1.0`: starter de **autoconfiguración** (Spring Boot 4.1.1,
Java 25, sin main) que centraliza la observabilidad de los microservicios del expense tracker. Exporta por
OTLP hacia el stack LGTM y loguea la entrada y salida HTTP con correlación. Los consumidores
(`crud-tracker`, `ocr-service`) solo declaran la dependencia. El README es la doc de uso; acá va la interna.

## Build

```bash
mvn clean install                      # tests + JaCoCo check + instala en ~/.m2 (los consumidores lo necesitan)
mvn test -Dtest=ClassName#methodName   # un test
```

JaCoCo exige 90% instrucciones / 80% ramas **por paquete y sin exclusiones**: todo el código de la lib cuenta.

## Arquitectura

- `config/ObservabilityDefaultsPostProcessor`: `EnvironmentPostProcessor` (registrado en
  `META-INF/spring.factories`, clave `org.springframework.boot.EnvironmentPostProcessor`) que agrega con
  `addLast` los defaults OTLP, el sampling y `logging.pattern.correlation`. Una lib no debe traer
  `application.yaml`: el del consumidor lo taparía.
- `autoconfigure/TrackerLoggerAutoConfiguration` (en `META-INF/spring/...AutoConfiguration.imports`):
  `ProcessorMetrics`, `MdcTaskDecorator` (ambos `@ConditionalOnMissingBean`), el instalador del appender
  OTel y configs anidadas por feature:
  - servlet + `tracker.logger.http.server.enabled`: `HttpLoggingFilter` (orden `HIGHEST_PRECEDENCE + 10`,
    después del `ServerHttpObservationFilter` de Boot en `+1`, así el `traceId` ya está en el MDC);
  - además Spring Security en el classpath (`@ConditionalOnClass` por nombre): `UserIdMdcFilter` (orden
    `-99`, después de la cadena de Security en `-100`);
  - `RestClient` + `tracker.logger.http.client.enabled`: un `RestClientCustomizer` con
    `@Order(LOWEST_PRECEDENCE)`, para que el interceptor quede después de los de auth y loguee los headers
    finales. Boot 4 aplica los `RestClientCustomizer` también a los HTTP service groups
    (`RestClientCustomizerHttpServiceGroupConfigurer`).
- Los filtros **no** son `@Component`: se registran con `FilterRegistrationBean`, porque Boot
  auto-registra cualquier bean `Filter` y quedarían duplicados.
- `filter/HttpLoggingFilter`: lee el body textual no-form ANTES de la cadena con `CachedBodyRequestWrapper`
  (la línea REQUEST sale antes del controller). Multipart y forms no se consumen: se leen por
  `getPart`/`getParameter`, que no pasan por el stream. La response va con `ContentCachingResponseWrapper` +
  `copyBodyToResponse()`. En `finally` limpia `correlationId` **y** `userId` del MDC; `UserIdMdcFilter` no
  limpia su clave, así `userId` llega a la línea RESPONSE.
- `client/LoggingClientInterceptor`: agrega `X-Correlation-Id` desde el MDC (sin pisar uno explícito). No
  toca `traceparent`, que lo inyecta la Observation de Boot antes de los interceptores. Bufferea la
  response (`BufferedClientHttpResponse`).
- `util/LogMasking` (headers, `maskBody` regex sin Jackson, `sanitize` CR/LF) y `util/LogBodies` (qué
  content-types se loguean, truncado).
- Logback: `logback-spring.xml` raíz → incluye `com/expense/tracker/logger/logback-base.xml` (Boot
  `base.xml` + appender `OTEL` con `captureMdcAttributes=*`, `captureKeyValuePairAttributes=true` y
  `captureExperimentalAttributes=true`, que agrega `thread.name`/`thread.id`). Antes del include define
  `CONSOLE_LOG_PATTERN` (el default de Boot sin PID, app name ni hilo, y logger `%logger{0}`) con `${CONSOLE_LOG_PATTERN:-...}`: el
  `defaults.xml` de Boot respeta una property ya definida y `logging.pattern.console` sigue ganando.
  `LogbackBaseConfigTest` lo cubre con un `LoggerContext` propio.
  Las líneas HTTP usan la fluent API de SLF4J (`addKeyValue`) para que los atributos lleguen a Loki.

## Convenciones

- Sin Lombok ni MapStruct; records para datos (`TrackerLoggerProperties` es un record con `@DefaultValue`);
  constructor injection; `private static final Logger log = LoggerFactory.getLogger(...)`.
- Comentarios y docs en español rioplatense; describen el comportamiento actual, sin historia de cambios.
- Tests: JUnit 5 + Hamcrest (+ Mockito si hace falta), clases package-private, `@DisplayName` en todos,
  `assertAll` para agrupar. Contexto liviano en vez de `@SpringBootTest`: `WebApplicationContextRunner`
  para la autoconfig, MockMvc standalone para el filtro, `MockRestServiceServer` para el interceptor.
  `LogCapture` (ListAppender de logback) para asertar líneas; `TestProperties` arma las properties sin
  Spring.
- Kafka (headers en Spring Cloud Stream) queda para otra iteración (ver `specs/FEAT-001-plan.md` del harness).

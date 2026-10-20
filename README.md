# tracker-common-logger (Spring Boot 4 / Java 25)

Starter de observabilidad de los microservicios del expense tracker. Con declarar la dependencia, el
servicio exporta trazas, métricas y logs por OTLP al OTel Collector (stack LGTM: Loki, Grafana, Tempo,
Mimir) y loguea la entrada y salida HTTP con un `correlationId` de punta a punta.

## Qué provee

- **Dependencias transitivas**: `spring-boot-starter-actuator`, `spring-boot-starter-opentelemetry`
  (Micrometer Tracing con bridge OTel, exporter OTLP, registry OTLP) y el appender de logback de
  OpenTelemetry (`opentelemetry-logback-appender-1.0`).
- **Defaults OTLP** (`ObservabilityDefaultsPostProcessor`, precedencia mínima: el `application.yaml` del
  servicio los pisa):

  ```yaml
  management:
    otlp.metrics.export.url: http://localhost:4318/v1/metrics
    opentelemetry:
      tracing.export.otlp.endpoint: http://localhost:4318/v1/traces
      logging.export.otlp.endpoint: http://localhost:4318/v1/logs
    tracing.sampling.probability: 1.0   # pisalo en producción
  logging.pattern.correlation: "[%X{traceId:-},%X{spanId:-}] [%X{correlationId:-}] [%X{userId:-}] "
  ```
- **`logback-spring.xml` base**: consola de Boot + appender `OTEL` (logs a Loki por OTLP, con el MDC y los
  key-values como atributos). El servicio no necesita su propio `logback-spring.xml`.
- **Logueo de entrada** (`HttpLoggingFilter`, servlet): correlación + líneas `REQUEST`/`RESPONSE`.
- **Logueo de salida** (`LoggingClientInterceptor`): se engancha solo a todo `RestClient.Builder` de Boot
  (incluidos los HTTP service groups `@HttpExchange`).
- **`userId` en el MDC** (`UserIdMdcFilter`): solo si Spring Security está en el classpath; toma el nombre
  del `Authentication` (el `sub` del JWT).
- **`@Async`**: un `TaskDecorator` que lleva MDC y traza al hilo de la tarea (si el servicio no define otro).
- `ProcessorMetrics` (convenciones semánticas de OTel para CPU) e instalación del appender OTel en el SDK.

## Cómo se consume

1. `mvn clean install` en este repo (todavía no se publica en un registry).
2. Declarar la dependencia:
   ```xml
   <dependency>
       <groupId>com.expense.tracker</groupId>
       <artifactId>tracker-common-logger</artifactId>
       <version>0.1.0</version>
   </dependency>
   ```
3. Sacar del servicio lo que ahora trae la lib: `spring-boot-starter-actuator`,
   `spring-boot-starter-opentelemetry`, `opentelemetry-logback-appender-1.0`, el `logback-spring.xml`, la
   clase que instalaba el appender OTel y el bloque `management.otlp`/`management.opentelemetry` del yaml.
4. Definir `spring.application.name`: es el `service.name` de OTel con el que se filtra en Grafana.

No hace falta ninguna anotación: es autoconfiguración.

## Correlación

| Header | Dirección | Qué es |
|---|---|---|
| `X-Correlation-Id` | request (opcional) y response | Id de negocio de punta a punta. Si el request no lo trae, se genera un UUID. Se guarda en el MDC como `correlationId` y el interceptor lo propaga a las llamadas salientes. |
| `X-Trace-Id` | response | `traceId` de micrometer, para buscar la traza en Tempo. |
| `traceparent` | salida | Lo propaga la instrumentación de Boot (W3C), no la lib. |

Claves MDC: `traceId`, `spanId` (micrometer), `correlationId` (filtro) y `userId` (con Spring Security).

## Formato de las líneas

```
REQUEST  --> POST /api/v1/transactions | headers={Content-Type="application/json", Authorization="*****"} | body={"amount":100,"password":"*****"}
RESPONSE <-- 201 (35ms) POST /api/v1/transactions | headers={X-Correlation-Id="3f1c..."} | body={"id":1}
CLIENT REQUEST  --> GET http://transactional/api/v1/transactions/1 | headers={traceparent="00-...", X-Correlation-Id="3f1c..."} | body=
CLIENT RESPONSE <-- 200 (12ms) GET http://transactional/api/v1/transactions/1 | headers={...} | body={...}
CLIENT ERROR    <-- GET http://transactional/api/v1/transactions/1 | error=java.net.ConnectException: ...
```

- Cada línea lleva además key-values (`http.method`, `http.uri`, `http.status`, `duration_ms`) que llegan a
  Loki como atributos.
- La línea `REQUEST` sale antes del controller; la de `CLIENT REQUEST`, antes de ejecutar la llamada.
- Solo se loguean bodies textuales (JSON, texto, XML). Un multipart o un binario sale como
  `<multipart/form-data;boundary=..., N bytes omitido>`: los bytes de la imagen de un comprobante nunca
  llegan al log.
- Los bodies se cortan en `max-body-length` caracteres; CR/LF se escapan (log forging).
- No se loguean `/actuator/**`, `/swagger-ui/**`, `/swagger-ui.html` ni `/v3/api-docs/**`.
- Los loggers son `com.expense.tracker.logger.filter.HttpLoggingFilter` y
  `com.expense.tracker.logger.client.LoggingClientInterceptor`: para silenciarlos alcanza con
  `logging.level.<logger>=WARN`.

## Properties

| Property | Default | Efecto |
|---|---|---|
| `tracker.logger.http.server.enabled` | `true` | registra el filtro de entrada (y el de `userId`) |
| `tracker.logger.http.server.excluded-paths` | `/actuator/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` | paths (sintaxis `PathPattern`) que no se loguean |
| `tracker.logger.http.client.enabled` | `true` | engancha el interceptor a los `RestClient.Builder` |
| `tracker.logger.masked-headers` | `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key` | headers enmascarados (case-insensitive) |
| `tracker.logger.masked-body-fields` | `password`, `token`, `accessToken`, `refreshToken`, `secret`, `clientSecret`, `apiKey` | claves JSON enmascaradas en los bodies |
| `tracker.logger.max-body-length` | `4096` | máximo de caracteres de body por línea |

El enmascarado es solo del log: el request y la response reales no se tocan.

## Logback propio

Si un servicio necesita loggers o appenders extra, escribe su `logback-spring.xml` incluyendo la base:

```xml
<configuration>
    <include resource="com/expense/tracker/logger/logback-base.xml"/>
    <logger name="org.hibernate.SQL" level="DEBUG"/>
</configuration>
```

Para JSON por consola (además de lo que va a Loki por OTLP): `logging.structured.format.console: ecs`.

## Build

```bash
mvn clean install   # tests + JaCoCo check (90% instrucciones / 80% ramas por paquete) + JAR en ~/.m2
mvn test -Dtest=HttpLoggingFilterTest#logsRequestAndResponse
```

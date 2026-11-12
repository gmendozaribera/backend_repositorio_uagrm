# Repositorio Digital UAGRM — Backend

Sistema de repositorio digital universitario (FICCT, escalable a toda la UAGRM via entidad Facultad).
Referencia funcional completa: `modelo_proyecto_v2.jsx` (entidades, API, historias, sprints).
**Excepción acordada:** el stack real es **Java 25 + Spring Boot 4.1.1** (el modelo dice Java 17 / Boot 3).

Frontend Angular: repo aparte `F:\IdeaProjects\repositorio-digital`.

## Memoria (Engram)
- Proyecto Engram compartido backend + frontend: **`repositorio-uagrm`** (pasar siempre `project: "repositorio-uagrm"`).
- Al iniciar: llama a `mem_current_project` y `mem_context` para recuperar el trabajo previo.
- Antes de rediscutir una decisión (auth JWT, estados de Trabajo, umbrales de plagio, etc.), usa `mem_search`.
- Guarda con `mem_save` las decisiones de arquitectura, bugs resueltos y convenciones, usando topic_key estables
  (ej. `architecture/auth-model`, `domain/work-lifecycle`, `integration/copyleaks`).
- Antes de cerrar la sesión o tras una compactación, guarda un `mem_session_summary`.
- Si las tools MCP no están cargadas, usar la CLI: `engram search "<q>" --project repositorio-uagrm` /
  `engram save "<titulo>" "<contenido>" --type <tipo> --topic <topic_key> --project repositorio-uagrm`.

## Comandos
El `java` del PATH es 1.8; Gradle necesita el JDK 25:
```
export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"
docker compose -f docker-compose/dev/docker-compose.yml up -d      # PostgreSQL 15, Redis 7, MinIO
./gradlew build                                                    # compila + tests
SPRING_PROFILES_ACTIVE=dev ./gradlew :auth-service:bootRun         # :8081, admin / Admin2026.
```

## Estructura
- `source/dao` — entidades JPA, repositorios, migraciones Flyway (`src/main/resources/db/migration`)
- `source/dto` — records Request/Response + mappers MapStruct
- `source/imp` — servicios, Redis (`imp.redis`, keys solo en `ClavesRedis`), seguridad compartida, `GlobalExceptionHandler`
- `source/spec` — contratos OpenAPI · `source/mockup` — builders para tests
- `source/ws/<servicio>` — `SecurityFilterChain` + controllers. Las `Application` viven en el paquete raíz `com.uagrm.repositorio`.

## Contratos OpenAPI (contract-first)
El contrato manda: `source/spec/src/main/resources/openapi/<servicio>-api.yaml` (OpenAPI **3.0.3**).
Todo cambio de API empieza editando el YAML; después el código.
- **Backend:** NO genera código. Lo verifica `<Servicio>ContratoTest` (`@WebMvcTest` + `openapi-request-validator-mockmvc`):
  cada request/response real se valida contra el YAML; propiedades no declaradas fallan.
- **Frontend:** genera modelos y cliente con `ng-openapi-gen` desde el YAML (nunca escribir interfaces a mano).
- **Mock:** `docker compose -f docker-compose/dev/docker-compose.yml --profile mock up -d contrato-mock` → Prism en :4010.
- **Control de cambios:** `script/contratos.sh` (Redocly lint + oasdiff contra `origin/main`). Un breaking change
  falla el build salvo que se suba la versión MAYOR de `info.version`. Corre en la etapa `Contratos` de Jenkins.
- Envoltorios planos por tipo (`RespuestaTokens`, `RespuestaLogin`...): 3.0 no tiene genéricos y `allOf` rompe la
  validación estricta. Objeto nullable: `type: object` + `nullable: true` + `allOf: [$ref]`.
- Excepciones del linter en `.redocly.lint-ignore.yaml`, cada una con su justificación.

## Reglas de desarrollo
1. Implementar solo el Sprint actual; no adelantar lógica de sprints futuros. Preguntar sprint y módulo antes de implementar.
2. Si la tarea toca Redis, confirmar namespace de keys y TTL con el usuario antes de generar código.
3. MapStruct para todo mapeo entidad↔DTO. Bean Validation en entidades y DTOs.
4. Controllers solo delegan al service. Cero lógica de negocio en controllers.
5. Flyway para todo cambio de esquema; `ddl-auto: validate`.
6. Redis con TTL explícito siempre. Auth es fail-closed (503 si Redis cae); catálogos degradan a PostgreSQL; uploads 503.
7. Éxito: `ApiResponse { data, message, status, timestamp }`. Error: `ProblemDetail` (RFC 7807) con `timestamp`.
8. CORS solo para el origen del frontend. Config externalizable por variables de entorno (ConfigMap/Secret).
9. Mínimo un test unitario por servicio; integración con Testcontainers (PostgreSQL, MinIO, Redis) en repos críticos.
10. El usuario tiene poca experiencia en seguridad: explicar las decisiones de seguridad en términos simples.

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
SPRING_PROFILES_ACTIVE=dev ./gradlew :auth-service:bootRun         # :8081, admin / Admin2026. (aplica Flyway)
SPRING_PROFILES_ACTIVE=dev ./gradlew :catalog-service:bootRun      # :8082, arrancar DESPUES de auth-service
SPRING_PROFILES_ACTIVE=dev ./gradlew :user-service:bootRun         # :8083, arrancar DESPUES de auth-service
SPRING_PROFILES_ACTIVE=dev ./gradlew :work-service:bootRun         # :8084, DESPUES de auth-service; usa MinIO
SPRING_PROFILES_ACTIVE=dev ./gradlew :plagiarism-service:bootRun   # :8085, DESPUES de auth-service; motor SIMULADO
```
**Demo FICCT** (presentaciones y E2E del frontend): `SPRING_PROFILES_ACTIVE=dev,demo ./gradlew :auth-service:bootRun`
(solo auth-service lleva el perfil; los demas servicios con `dev`). Agrega a Flyway `classpath:db/demo`
(`source/ws/auth-service/src/main/resources/db/demo/R__demo_ficct.sql`: repetible e idempotente, se aplica despues de
todas las V y se puede volver a arrancar sin `demo`: `ignore-migration-patterns: repeatable:missing`). Datos: FICCT
(acceso externo, escala 100, 10 MB), INF/SIS/RED, INF412/INF422/INF312, semestre 2-2026, grupos INF412-SA/SB e
INF312-SA, fases Perfil/Avance/Final, tipos "Proyecto de materia"/"Monografia", un trabajo EVALUADO (enlace GitHub).
Usuarios (`Demo2026!`): `doc.maria` (Admin de Curso INF412-SA e INF312-SA), `doc.jose` (docente INF412-SA),
`219000001` (Juan, INF), `219000002` (Ana, SIS). Padron sin cuenta: `219000003` / CI `7000003` (password `Uagrm2026`).
`DemoUsuariosListener` (perfil demo, al arrancar): admin y usuarios demo sin cambio de contrasena obligatorio ni 2FA
(no toca contrasenas) y limpia las caches Redis de catalogos y config de FICCT. NUNCA en prod ni en tests normales
(solo `DemoSeedIntegracionTest` lo activa para probar el seed).

**Swagger UI** (contrato, no generado): `http://localhost:808x/swagger-ui.html` -> carga `/openapi/<servicio>-api.yaml`
(el YAML de `source/spec`, solo el del servicio). Apagado por defecto; `SWAGGER_UI_ENABLED=true` lo enciende (perfil
dev: encendido). Implementado en imp `SwaggerUiConfig` con el webjar `org.webjars:swagger-ui` (springdoc apaga su UI
con `api-docs.enabled=false`); con el flag apagado esas rutas caen en el `denyAll` de cada servicio (401). Encendido,
una `SecurityFilterChain` propia (orden mas alto) permite solo GET de la UI y del YAML; la API sigue pidiendo token.

Migraciones: viven en `source/dao` y las aplica SOLO auth-service; los demas servicios no traen Flyway y
validan el esquema (`ddl-auto: validate`). En docker-compose, catalog-, user-, work- y plagiarism-service esperan el
healthcheck de auth-service.

plagiarism-service (CU14, `plagio-api.yaml`): logica en imp `PlagioService` (comun a los motores); en el servicio
los motores (`MotorPlagio`), Quartz (RAMJobStore: envio, simulacion, timeout cada minuto, reanudacion al arrancar) y
el webhook. Variables: `PLAGIO_MOTOR` = `SIMULADO` | `COPYLEAKS` (obligatoria fuera de dev; dev = SIMULADO),
`COPYLEAKS_EMAIL`, `COPYLEAKS_API_KEY`, `COPYLEAKS_SANDBOX`, `COPYLEAKS_REPOSITORIO_ID` (Private Cloud Hub, opcional),
`PLAGIO_WEBHOOK_BASE_URL` (https), `PLAGIO_WEBHOOK_TOKEN` (>= 32 caracteres; vacio = webhook apagado) y las mismas
`MINIO_*` que work-service (solo lectura). Con COPYLEAKS las credenciales, la URL y el token son obligatorios (no
arranca sin ellos). Parametros: `PLAGIO_GROUP` (`TIMEOUT_MINUTOS`, `SIMULADO_SEGUNDOS`, `MAX_FUENTES`). Redis:
`plagio:status:{idTrabajo}` (TTL 600 s; si falta o Redis cae, /estado lee la BD). Token de Copyleaks solo en memoria.

work-service (trabajos y documentos): MinIO por `MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`,
`MINIO_BUCKET` (defaults solo en el perfil dev: `http://localhost:9000`, minioadmin / minioadmin123, `trabajos`;
el bucket se crea solo). Implementa `imp.almacenamiento.AlmacenamientoArchivos` (los demas servicios no traen el
cliente de MinIO). Keys `trabajos/{idTrabajo}/{uuid}.{ext}`; descargas siempre por proxy del servicio.
Multipart hasta 101 MB (`MULTIPART_MAX_FILE`); los limites reales: `WORK_GROUP.VIDEO_MAX_MB` y
`ConfigFacultad.tamanoMaxArchivoMB`. `MinioAlmacenamientoTest` usa Testcontainers (se salta sin Docker).

## Estructura
- `source/dao` — entidades JPA, repositorios, migraciones Flyway (`src/main/resources/db/migration`)
- `source/dto` — records Request/Response + mappers MapStruct
- `source/imp` — servicios, Redis (`imp.redis`, keys solo en `ClavesRedis`), seguridad compartida, `GlobalExceptionHandler`,
  parametros de dominio (`imp.dominio`: `ConstantesDominio` + `ParametrosService`)
- `source/spec` — contratos OpenAPI · `source/mockup` — builders para tests
- `source/ws/<servicio>` — `SecurityFilterChain` + controllers. Las `Application` viven en el paquete raíz `com.uagrm.repositorio`.

## Contratos OpenAPI (contract-first)
El contrato manda: `source/spec/src/main/resources/openapi/<servicio>-api.yaml` (OpenAPI **3.0.3**).
Todo cambio de API empieza editando el YAML; después el código.
- **Backend:** NO genera código. Lo verifica `<Servicio>ContratoTest` (`@WebMvcTest` + `openapi-request-validator-mockmvc`):
  cada request/response real se valida contra el YAML; propiedades no declaradas fallan.
- **Frontend:** genera modelos y cliente con `ng-openapi-gen` desde el YAML (nunca escribir interfaces a mano).
- **Mock:** `docker compose -f docker-compose/dev/docker-compose.yml --profile mock up -d contrato-mock` → Prism en :4010
  (auth); `contrato-mock-catalog` → :4011 (catalog); `contrato-mock-user` → :4012 (user);
  `contrato-mock-work` → :4013 (work); `contrato-mock-plagio` → :4014 (plagio).
- **Multipart:** el validador no ve las partes de un request multipart de MockMvc; `UserContratoTest` ignora solo
  `validation.request.body.missing` en `/padron/import` (ruta, seguridad y respuesta si se validan).
  `WorkContratoTest` ademas ignora `validation.request.parameter.query.unexpected` en la subida (el adaptador
  presenta los campos de texto del formulario como query). Descarga binaria: solo status y cabeceras.
- **Webhook sin sesion** (`security: []`): `PlagioContratoTest` lo valida con el validador estricto, sin header
  Authorization (el SecurityFilterChain ignora el header en esa ruta).
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
   Todo 400 de validacion (body `@Valid`, `@PathVariable`/`@RequestParam` con restricciones, tipo invalido, query
   obligatorio ausente, `ConstraintViolationException`) sale igual: title "Datos invalidos" + `errores` campo ->
   mensaje (para parametros, su nombre en la URL). Lo arma `GlobalExceptionHandler`.
8. CORS solo para el origen del frontend. Config externalizable por variables de entorno (ConfigMap/Secret).
9. Mínimo un test unitario por servicio; integración con Testcontainers (PostgreSQL, MinIO, Redis) en repos críticos.
   Base compartida: mockup `integracion.ContenedoresIntegracion` (PostgreSQL 15 + Redis 7, Flyway en el test, se
   salta sin Docker): `AuthIntegracionTest`, `PadronRegistroIntegracionTest`, `CatalogoCacheIntegracionTest`.
10. El usuario tiene poca experiencia en seguridad: explicar las decisiones de seguridad en términos simples.
11. **Todo parámetro configurable del sistema se registra en la tabla `dominio`** (`domain_group`, `domain_name`,
    `domain_value`, `description`) con una migración Flyway que lo siembra, y su grupo/nombre se declaran como
    constantes SOLO en `imp` `ConstantesDominio` (nunca strings sueltos). Se lee con el servicio de dominio
    (caché Redis `config:dominio:{GRUPO}`, TTL 600 s). Se administra con `/api/v1/dominios` (user-service).
    Excepción: la infraestructura (URLs, credenciales, secretos, puertos) sigue en variables de entorno.
    Parámetros **secretos** (flag `secreto` en `Parametro`, p. ej. `SMTP_GROUP.PASSWORD`): se guardan cifrados
    AES-GCM (`v1:...`, `CifradorDominio`) con la llave `DOMINIO_CIFRADO_KEY` (solo user-service, obligatoria fuera
    de dev); la API nunca devuelve su valor y solo `ParametrosService.secreto()` los descifra (uso interno).
12. Configuración por facultad: leerla SIEMPRE con `ConfigFacultadService.configDe(idFacultad)` (fila de
    `config_facultad` o defaults de `FACULTAD_DEFAULT_GROUP`; caché `config:facultad:{id}`, TTL 600 s).

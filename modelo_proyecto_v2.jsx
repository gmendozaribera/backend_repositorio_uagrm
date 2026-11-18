import { useState } from "react";

const C = {
  primary:"#1B3A6B", accent:"#2563EB", accentLt:"#EFF6FF",
  border:"#DBEAFE", text:"#1E293B", muted:"#64748B", bg:"#F8FAFC",
  success:"#166534", successBg:"#DCFCE7", warn:"#92400E", warnBg:"#FEF3C7",
  danger:"#991B1B", dangerBg:"#FEE2E2", purple:"#5B21B6", purpleBg:"#EDE9FE",
};

const NAV = [
  { id:"overview",     icon:"🏠", label:"Visión General" },
  { id:"decisions",    icon:"✅", label:"Decisiones" },
  { id:"entities",     icon:"🗄️", label:"Modelo de Datos" },
  { id:"architecture", icon:"🏗️", label:"Arquitectura" },
  { id:"structure",    icon:"📁", label:"Estructura" },
  { id:"api",          icon:"🔌", label:"API REST" },
  { id:"userstories",  icon:"📋", label:"Historias" },
  { id:"sprints",      icon:"🏃", label:"Sprints" },
  { id:"angular",      icon:"🅰️", label:"Frontend" },
];

function Card({ children, style={} }) {
  return <div style={{ background:"#fff", border:`1px solid ${C.border}`, borderRadius:10, padding:"20px 24px", marginBottom:16, ...style }}>{children}</div>;
}
function SectionHeader({ title, subtitle }) {
  return <div style={{ marginBottom:28 }}><h2 style={{ color:C.primary, fontSize:22, fontWeight:700, margin:0 }}>{title}</h2>{subtitle && <p style={{ color:C.muted, marginTop:4, fontSize:13, margin:"6px 0 0" }}>{subtitle}</p>}</div>;
}
function Code({ children }) {
  return <pre style={{ background:"#0F172A", color:"#E2E8F0", borderRadius:8, padding:"16px 20px", fontSize:12, lineHeight:1.75, overflowX:"auto", margin:0, fontFamily:"'Fira Code',monospace" }}>{children}</pre>;
}
function Badge({ label, bg, color }) {
  return <span style={{ background:bg, color, fontSize:11, fontWeight:700, padding:"2px 9px", borderRadius:12 }}>{label}</span>;
}
function MethodBadge({ m }) {
  const map = { GET:{bg:"#DBEAFE",c:"#1D4ED8"}, POST:{bg:"#D1FAE5",c:"#065F46"}, PUT:{bg:"#FEF3C7",c:"#92400E"}, PATCH:{bg:"#EDE9FE",c:"#5B21B6"}, DELETE:{bg:"#FEE2E2",c:"#991B1B"} };
  const s = map[m]||{bg:"#F1F5F9",c:"#475569"};
  return <span style={{ background:s.bg, color:s.c, fontSize:11, fontWeight:700, fontFamily:"monospace", padding:"2px 8px", borderRadius:4, minWidth:52, display:"inline-block", textAlign:"center" }}>{m}</span>;
}

const DECISIONS = [
  { cat:"Autenticación", items:[
    { q:"2FA obligatorio", a:"Opcional por usuario. Habilitado desde el perfil. No obligatorio para ningún rol. Implementado con TOTP (Google Authenticator compatible)." },
    { q:"Duración sesión JWT", a:"Configurable por administrador en ConfigFacultad (jwtExpiracionHoras de la facultad del usuario). Por defecto: 1 hora (access token) + 7 días (refresh token, con rotación: cada refresh se usa una sola vez)." },
    { q:"Firma y contenido del JWT [implementado S1]", a:"HS256 con Nimbus (spring-security-oauth2-resource-server), secreto JWT_SECRET (Base64 ≥ 32 bytes, obligatorio fuera de dev). Claims: sub=idUsuario, usuario, rol, pwd_change, typ (access|refresh), jti, iat_ms (milisegundos: evita que un token emitido en el mismo segundo sobreviva a un cambio de contraseña). Todos los servicios validan el mismo token como resource servers." },
    { q:"Login con 2FA [implementado S1]", a:"Paso 1 /login devuelve un tokenTemporal aleatorio (no el idUsuario, para evitar fuerza bruta) válido 5 min; paso 2 /2fa/verify. Máx. 5 códigos erróneos, el sexto da 429 y anula la sesión pendiente. Activación en dos pasos: /2fa/enable (secreto + QR) y /2fa/confirm." },
    { q:"Primer login / contraseña temporal [implementado S1]", a:"Columna usuario.requiere_cambio_password (no estaba en el modelo original). Con pwd_change=true el token solo permite /password/change y /logout (403 en lo demás). Política: 8–72 caracteres con mayúscula, minúscula y número. Admin inicial creado al arrancar (ADMIN_INICIAL_PASSWORD) y obligado a cambiarla." },
    { q:"Redis caído en autenticación [implementado S1]", a:"Fail-closed: si Redis no responde, toda petición autenticada devuelve 503 (no se puede verificar si el token fue revocado). Aplica también a los GET de catálogos (decisión 2026-11-16)." },
  ]},
  { cat:"Parámetros configurables — tabla de dominio", items:[
    { q:"Dónde viven los parámetros", a:"Todo parámetro configurable del sistema se registra en la tabla dominio (domain_id, domain_group, domain_name, domain_value, description, last_user, last_time; único por grupo + nombre), sembrado con una migración Flyway. La infraestructura (URLs, credenciales, secretos, puertos) sigue en variables de entorno." },
    { q:"Constantes centralizadas", a:"Grupos y nombres se declaran SOLO en la clase ConstantesDominio (imp), con su tipo, rango válido y valor por defecto de respaldo. Ningún otro código usa esos strings sueltos, así 'buscar usos' muestra qué clases dependen de cada parámetro." },
    { q:"Lectura con caché", a:"Hash Redis config:dominio:{GRUPO} (campo = domain_name), TTL 600 s, invalidado después del commit en toda escritura: un cambio se aplica en todos los servicios al instante. Si Redis falla se lee PostgreSQL; si el parámetro no existe se usa el valor por defecto del código." },
    { q:"Administración", a:"CRUD /api/v1/dominios en user-service (solo ADMIN). Grupo y nombre son inmutables; los parámetros que usa el sistema no se pueden eliminar (409) y su valor se valida por tipo (400). last_user = login del administrador que hizo el cambio." },
    { q:"Parámetros actuales", a:"AUTH_GROUP: LOGIN_INTENTOS = 10, LOGIN_WAITING = 15 min · FACULTAD_DEFAULT_GROUP: valores por defecto de ConfigFacultad (TAMANO_MAX_ARCHIVO_MB 10, UMBRAL_PLAGIO_MEDIO 30, UMBRAL_PLAGIO_ALTO 50, COMPARACION_INTERNET false, ACCESO_EXTERNO false, ESCALA_CALIF_MAX 100, JWT_EXPIRACION_HORAS 1) · SMTP_GROUP: HOST, PUERTO 587, CORREO, PASSWORD, SEGURIDAD STARTTLS, ACTIVO false." },
    { q:"Parámetros secretos", a:"Los parámetros marcados como secretos (p. ej. SMTP_GROUP.PASSWORD) se guardan cifrados con AES-GCM usando la llave DOMINIO_CIFRADO_KEY (variable de entorno / Kubernetes Secret). Redis solo guarda el texto cifrado y la API nunca devuelve el valor: indica si está configurado." },
    { q:"ConfigFacultad", a:"Se mantiene la tabla config_facultad (una fila por facultad, columnas tipadas con CHECK). GET /api/v1/facultades/{id}/config (cualquier autenticado) devuelve la fila o, si la facultad no tiene configuración propia, los valores de FACULTAD_DEFAULT_GROUP con personalizada=false. PUT (ADMIN) hace upsert. Caché Redis config:facultad:{idFacultad} TTL 600 s. Endpoints en catalog-service." },
  ]},
  { cat:"Stack y contratos (decisiones de implementación)", items:[
    { q:"Versión del stack", a:"Java 25 + Spring Boot 4.1.1 (Spring Security 7, Jackson 3, Hibernate 7, Gradle 9 multimodulo) en lugar de Java 17 / Boot 3. Frontend Angular 22 standalone, zoneless, signals, sin librería UI (CSS propio con la paleta del modelo), tests con Vitest." },
    { q:"Contract-first con OpenAPI 3.0.3", a:"Cada servicio tiene su contrato en source/spec/src/main/resources/openapi/<servicio>-api.yaml y todo cambio de API empieza ahí. El backend NO genera código: <Servicio>ContratoTest valida cada request/response real contra el YAML (estricto con propiedades extra). El frontend genera modelos y cliente con ng-openapi-gen (código generado versionado). Contratos: auth-api 1.2.0, catalog-api 1.2.0, user-api 1.3.0, work-api 1.2.0, plagio-api 1.0.0." },
    { q:"Control de cambios de contrato", a:"script/contratos.sh (etapa 'Contratos' de Jenkins): Redocly lint + oasdiff breaking contra origin/main. Un breaking change falla el build salvo que se suba la versión MAYOR de info.version." },
    { q:"Mocks para desarrollo paralelo", a:"Prism 5.12.0 por contrato en docker-compose (profile mock): auth :4010, catalog :4011, user :4012, work :4013, plagio :4014." },
    { q:"Puertos de los microservicios", a:"auth-service :8081, catalog-service :8082, user-service :8083 (se intercambiaron user y catalog respecto al modelo original porque catálogos se implementó primero). Nginx/proxy enrutan por prefijo: /api/v1/auth → 8081; /api/v1/usuarios, /tipos-usuario, /padron → 8083; resto → 8082." },
    { q:"Migraciones", a:"Flyway en source/dao; las aplica SOLO auth-service. Los demás servicios validan el esquema (ddl-auto: validate) y en docker-compose esperan el healthcheck de auth-service. Futuro K8s: Job de migración dedicado." },
    { q:"Formato de respuestas", a:"Éxito: { data, message, status, timestamp } con un envoltorio plano por tipo en OpenAPI (RespuestaX) porque 3.0 no tiene genéricos. Error: ProblemDetail RFC 7807 con timestamp y, en validaciones, mapa 'errores' campo → mensaje." },
  ]},
  { cat:"Registro de usuarios", items:[
    { q:"Auto-registro estudiantes", a:"POST /api/v1/auth/register {ci, registro} (público). Si coinciden con una fila habilitada de EstudianteHabilitado se crea Persona + Usuario ESTUDIANTE con usuario = número de registro y contraseña = password_default del padrón (cambio obligatorio en el primer login). No devuelve tokens. Sin match o inhabilitado → 404 'No encontrado en el padrón institucional'; ya tiene cuenta → 409. Conocer el CI y registro de otra persona no da acceso: hace falta la contraseña entregada por la facultad." },
    { q:"Importación padrón institucional", a:"CSV (coma o punto y coma, UTF-8), XLSX o XLS subido por el administrador (máx. 5 MB y 10 000 filas). Cabecera obligatoria con las columnas en cualquier orden. Upsert por registro (insertados / actualizados), errores por fila sin cortar la importación; las cuentas ya creadas no se tocan. La sigla_carrera se resuelve a id_carrera (FK). password_default se guarda cifrada con BCrypt, nunca en texto plano." },
    { q:"Recuperar contraseña", a:"Mientras no exista recuperación por correo (llega con SMTP), el administrador restablece la contraseña desde user-service (POST /usuarios/{id}/password/reset): contraseña temporal + cambio obligatorio + cierre de todas las sesiones." },
    { q:"Límite de intentos en login y auto-registro", a:"Por IP, solo intentos FALLIDOS (los exitosos no cuentan porque muchos estudiantes comparten la IP del laboratorio). Al superar LOGIN_INTENTOS → 429 con Retry-After durante LOGIN_WAITING minutos. Keys auth:rate:login:{ip} y auth:rate:register:{ip}. Evita adivinar contraseñas y descubrir quién está en el padrón." },
    { q:"CI y celular", a:"Texto (VARCHAR 20), no entero: el CI puede tener complemento (1234567-1A). Se guarda sin espacios y en mayúsculas." },
    { q:"Estudiante y carrera", a:"Un estudiante siempre tiene carrera y registro: al editar se mantienen o se cambian, nunca quedan vacíos (en el PUT, omitidos o null = se conservan)." },
    { q:"Desactivar usuario", a:"Borrado lógico (estado 0) que además cierra todas sus sesiones (auth:revocado-desde). Cambiar el rol también las cierra. Un admin no puede desactivarse ni quitarse el rol ADMIN a sí mismo." },
    { q:"Estructura CSV padrón", a:"registro, ci, correo, password_default, nombres, apellidos, sigla_carrera. Ejemplo: 123456,7654321,est@uagrm.edu.bo,Uagrm2024.,Juan,Pérez,ITIN" },
  ]},
  { cat:"Archivos y formatos", items:[
    { q:"Documentos", a:"PDF (obligatorio para entrega final), DOCX, ODT. Máx. 10 MB por defecto (configurable)." },
    { q:"Presentaciones", a:"PPTX, PDF." },
    { q:"Código fuente", a:"ZIP, RAR, 7Z + URL de repositorio GitHub/GitLab (solo guarda la URL como texto)." },
    { q:"Videos", a:"URL externa (YouTube, Vimeo, Google Drive). Subida directa: MP4, WebM — máx. 100 MB fijo." },
    { q:"Imágenes/Diagramas", a:"PNG, JPG, SVG." },
    { q:"Tamaño máximo por archivo", a:"10 MB por defecto, configurable por administrador por facultad. Videos directos: fijo 100 MB." },
  ]},
  { cat:"Trabajos y flujo", items:[
    { q:"Múltiples documentos por trabajo", a:"Sí. Un trabajo puede tener N documentos asociados, cada uno asignado a una Fase." },
    { q:"Aprobación con observaciones", a:"Sí. Estado: APROBADO_CON_OBSERVACIONES. El estudiante puede subir versión corregida." },
    { q:"Visibilidad trabajos no aprobados", a:"Solo el autor y el docente asignado. No aparecen en búsqueda pública. Solo trabajos EVALUADOS son visibles." },
    { q:"Estados del trabajo", a:"BORRADOR → (enviar, requiere ≥1 PDF) → PENDIENTE → EN_REVISION → APROBADO | APROBADO_CON_OBSERVACIONES | RECHAZADO → EVALUADO (S4). RECHAZADO y APROBADO_CON_OBSERVACIONES vuelven a PENDIENTE al reenviar. BORRADOR se agregó porque un trabajo lleva varios archivos que se suben uno a uno antes de enviarlo. Transición no permitida → 409; todas quedan en trabajo_historial." },
    { q:"Permisos sobre un trabajo [S3]", a:"Lo ven sus autores, los docentes asignados al grupo y ADMIN (para el resto no existe: 404). Editar, subir/quitar documentos y enviar: autores, solo en BORRADOR / RECHAZADO / APROBADO_CON_OBSERVACIONES. Revisar, aprobar y rechazar: docentes del grupo. Materia y semestre se derivan del grupo." },
    { q:"Seguridad de archivos [S3]", a:"Allowlist de extensiones por categoría + tipo real verificado por contenido (Apache Tika, magic bytes): un .exe renombrado a .pdf se rechaza. SVG con scripts se rechaza (XSS). Enlaces solo https y hosts exactos permitidos. Key de MinIO con UUID (nunca el nombre original). Descargas por proxy de work-service verificando permisos en cada descarga; MinIO nunca queda expuesto." },
  ]},
  { cat:"Notificaciones", items:[
    { q:"Canales de notificación", a:"En-app (campana/badge en navbar) + correo SMTP opcional: cada usuario lo activa en su perfil (usuario.notificar_correo, por defecto desactivado) y solo se envía si SMTP_GROUP.ACTIVO. Un fallo de correo nunca rompe la operación que lo originó." },
    { q:"Campana y polling [S2]", a:"Tabla notificacion en user-service. La campana consulta GET /notificaciones/pendientes cada NOTIF_GROUP.POLLING_SEGUNDOS (30 s, lo devuelve el propio endpoint) y se pausa con la pestaña oculta. El contador vive en Redis notif:pendientes:{idUsuario} (TTL 1 día), así el polling no toca PostgreSQL; si Redis cae se cuenta en la BD." },
    { q:"Retención y Quartz [S2]", a:"La tarea original 'limpieza de tokens JWT expirados' no hace falta (Redis expira las keys por TTL). Se reemplazó por un job Quartz diario que borra notificaciones leídas con más de NOTIF_GROUP.RETENCION_DIAS (90)." },
    { q:"Eventos que notifican", a:"Sprint 2: BIENVENIDA (auto-registro y alta por admin), PASSWORD_RESTABLECIDA. Sprint 3+: cambios de estado del trabajo, plagio finalizado. El servicio de notificaciones vive en imp para que lo use work-service." },
    { q:"Configuración SMTP", a:"Configurable por administrador en la tabla de dominio, grupo SMTP_GROUP: HOST, PUERTO, CORREO (remitente), PASSWORD (secreto), SEGURIDAD (SSL | STARTTLS | NINGUNA), ACTIVO. Se prueba con POST /api/v1/config/smtp/prueba (user-service). La tabla config_smtp del modelo original se eliminó (V6)." },
  ]},
  { cat:"Detección de plagio", items:[
    { q:"Motor de plagio", a:"Interfaz MotorPlagio intercambiable por variable de entorno: SIMULADO (desarrollo y tests, sin red, resultado determinístico) y COPYLEAKS (producción, API key en Kubernetes Secret, sandbox de Copyleaks para probar). Copyleaks avisa por webhook a /api/v1/plagio/webhook/copyleaks/{token}/... protegido con un token secreto en la URL comparado en tiempo constante." },
    { q:"Servicio y rutas de plagio [S4]", a:"plagiarism-service :8085 (plagio-api 1.0.0). Rutas bajo /api/v1/plagio/trabajos/{id} (en vez de /trabajos/{id}/plagio del modelo original) para que nginx enrute todo /api/v1/plagio a un solo servicio. Lo inician y ven los docentes del grupo y ADMIN; los estudiantes no ven resultados. Un análisis en curso por trabajo (409); timeout → ERROR." },
    { q:"Umbral configurable", a:"Por administrador por facultad en ConfigFacultad. Por defecto: 30% = Riesgo Medio (amarillo), >50% = Riesgo Alto (rojo), <30% = OK (verde)." },
    { q:"Fuentes de comparación", a:"Repositorio interno (siempre activo) + internet (activable por administrador por facultad en ConfigFacultad)." },
    { q:"Procesamiento", a:"Asíncrono vía Quartz Scheduler. El docente inicia el análisis (202); el frontend consulta /estado cada 5 s (Redis); el resultado llega también por notificación PLAGIO_COMPLETADO." },
  ]},
  { cat:"Calificación", items:[
    { q:"Escala de calificación", a:"Configurable por administrador por facultad/curso en ConfigFacultad. Por defecto: 0–100." },
    { q:"Quién califica [S4]", a:"Solo el docente Administrador de Curso (grupo_docente.esAdminCurso) del grupo, desde APROBADO. PATCH /trabajos/{id}/calificar → EVALUADO, visible en el repositorio y notificación TRABAJO_EVALUADO a los autores. La calificación nunca se muestra en el repositorio (solo autores, docentes del grupo y ADMIN)." },
    { q:"Búsqueda full-text [S4]", a:"Columna trabajo.busqueda (tsvector, configuración español + unaccent) con pesos título A, hashtags B, resumen C, índice GIN. websearch_to_tsquery: frases entre comillas y -palabra para excluir; orden por relevancia cuando hay texto." },
    { q:"Acceso anónimo [S4]", a:"/api/v1/publico/** sin token: solo trabajos EVALUADO de facultades con ConfigFacultad.accesoExterno; solo metadatos, descargar exige iniciar sesión. Frontend: /explorar; si ninguna facultad lo habilita, pantalla de acceso restringido." },
  ]},
  { cat:"Roles y permisos", items:[
    { q:"Docente Administrador de Curso", a:"Designado por administrador. Puede ser Admin de N cursos simultáneamente. Solo gestiona trabajos y calificaciones dentro de esos grupos. No puede crear/eliminar estudiantes. Se asigna con POST /grupos/{id}/docentes y se cambia con PATCH /grupos/{id}/docentes/{idUsuario} {esAdminCurso}." },
    { q:"Permisos por servicio [implementado S2]", a:"catalog-service: GET para cualquier rol autenticado, escrituras solo ADMIN. user-service: todo solo ADMIN. Con contraseña pendiente de cambio → 403 en todo salvo /password/change y /logout." },
    { q:"Acceso externo", a:"Configurable por facultad. Usuario anónimo: solo metadatos. Descarga de archivos: requiere cuenta registrada en el sistema." },
  ]},
  { cat:"Escalabilidad y base de datos", items:[
    { q:"Entidad Facultad", a:"Agregada desde el inicio. Carrera depende de Facultad. Cada facultad tiene su ConfigFacultad independiente. Permite que toda la UAGRM adopte el sistema." },
    { q:"Base de datos definitiva", a:"PostgreSQL 15. Full-text search con tsvector/tsquery nativo. El documento Word había referenciado MySQL — PostgreSQL es la elección definitiva." },
    { q:"GitHub/GitLab", a:"Solo guarda URL como texto. No requiere autenticación OAuth." },
    { q:"Catálogos: convenciones [implementado S2]", a:"Sin borrado físico: DELETE desactiva (estado 0) y devuelve el registro; se reactiva con PUT estado 1. Listados sin paginar, por defecto solo activos (incluirInactivos=true para admin). Siglas en MAYÚSCULAS; sigla de carrera única global (la usa el padrón). Semestre tiene estado (no estaba en el modelo) y tipo_trabajo.estado es INT. Materia y semestre de un grupo deben ser de la misma facultad." },
  ]},
  { cat:"Redis — Caché, Rate Limiting y Colas", items:[
    { q:"¿Por qué Redis?", a:"Múltiples estudiantes suben documentos simultáneamente al cierre de semestre. Redis protege MinIO y work-service ante picos de carga sin rechazar peticiones válidas, y además resuelve invalidación de JWT, caché de catálogos y polling eficiente de plagio." },
    { q:"Rate limiting en uploads", a:"INCR work:rate:upload:{idUsuario} con ventana de 60 s; máximo WORK_GROUP.UPLOADS_POR_MINUTO (10). Al superarlo → 429 con Retry-After. Si Redis cae → 503 (uploads son fail-closed)." },
    { q:"Blacklist JWT (logout real) [implementado S1]", a:"Logout y rotación de refresh: SET auth:blacklist:{jti} con TTL = vida restante del token. Cambio de contraseña, reset, cambio de rol o desactivación: SET auth:revocado-desde:{idUsuario} (epoch ms, TTL 7 d) que invalida todos los tokens emitidos antes. Un único MGET por request." },
    { q:"Caché de catálogos [implementado S2]", a:"Cache-aside en catalog-service: catalog:cache:{entidad}:todas | catalog:cache:{entidad}:facultad:{id}, TTL 600 s. Un SET índice catalog:cache:{entidad}:indice permite invalidar sin KEYS/SCAN; la invalidación ocurre después del commit. Se cachean facultades, carreras, materias, semestres, fases y tipos de trabajo (no grupos ni listados con inactivos). Si Redis falla en la caché → PostgreSQL (sin 503)." },
    { q:"Estado del análisis de plagio [confirmado S4]", a:"plagio:status:{idTrabajo} (EN_PROCESO / COMPLETADO / ERROR) con TTL 600 s. Angular consulta GET /plagio/trabajos/{id}/estado cada 5 s y se responde desde Redis; si la key expiró o Redis cae, desde la BD. El resultado completo vive en resultado_plagio." },
    { q:"Cola de uploads (DIFERIDA)", a:"Descartada por ahora (decisión 2026-11-17): los archivos (hasta 100 MB) no deben pasar por Redis. Se suben en streaming a MinIO dentro de la petición; protegen ante picos el rate limit por usuario, los límites de tamaño y nginx con request buffering desactivado. Se reevaluará (cola solo de metadatos para post-proceso) si hiciera falta." },
    { q:"Namespaces de keys por microservicio", a:"auth:blacklist:{jti} · auth:revocado-desde:{idUsuario} · auth:rate:login:{ip} · auth:rate:register:{ip} (TTL = LOGIN_WAITING min) · config:dominio:{GRUPO} (600 s) · config:facultad:{idFacultad} (600 s) · notif:pendientes:{idUsuario} (1 día) · auth:2fa:pending:{tokenTemporal} (300 s) · auth:2fa:intentos:{tokenTemporal} (300 s) · catalog:cache:* (600 s) · work:rate:upload:* · work:queue:uploads:* · plagio:status:*. Todas las keys se construyen solo en imp.redis.ClavesRedis y siempre con TTL." },
    { q:"Dependencias Spring Boot", a:"spring-boot-starter-data-redis + spring-boot-starter-cache. Un contenedor Redis más en docker-compose y Kubernetes. Sin cambios arquitecturales a los microservicios existentes." },
  ]},
];

const MERMAID_ER = `erDiagram
    FACULTAD { int idFacultad PK; varchar nombre; varchar sigla; int estado }
    CARRERA { int idCarrera PK; varchar nombre; varchar sigla; int idFacultad FK; int estado }
    ESTUDIANTE_HABILITADO { int id PK; int registro UK; varchar ci UK; varchar correo; varchar passwordDefault; varchar nombres; varchar apellidos; int idCarrera FK; boolean habilitado; timestamp fechaImportacion }
    PERSONA { int idPersona PK; varchar nombre; varchar apellidos; varchar ci; varchar correo; varchar celular; date fechaNac; int registro; int idCarrera FK }
    TIPO_USUARIO { int idTipoUsuario PK; varchar nombre; varchar descripcion; int estado }
    USUARIO { int idUsuario PK; varchar usuario; varchar password; boolean requiereCambioPassword; boolean notificarCorreo; boolean twoFactorEnabled; varchar totpSecret; int idTipoUsuario FK; int idPersona FK; int estado }
    SEMESTRE { int idSemestre PK; int semestre; int anio; int idFacultad FK; int estado }
    MATERIA { int idMateria PK; varchar nombre; varchar sigla; int idFacultad FK; int estado }
    GRUPO { int idGrupo PK; varchar nombre; int idMateria FK; int idSemestre FK; int estado }
    GRUPO_DOCENTE { int idGrupo FK; int idUsuario FK; boolean esAdminCurso }
    TIPO_TRABAJO { int idTipoTrabajo PK; varchar nombre; int estado; int idFacultad FK }
    FASE { int idFase PK; varchar nombre; varchar descripcion; int idFacultad FK; int estado }
    TRABAJO { int idTrabajo PK; varchar titulo; varchar resumen; varchar estado; int calificacion; varchar observaciones; int idPersonaCreador FK; int idMateria FK; int idTipoTrabajo FK; int idGrupo FK; int idSemestre FK; timestamp fechaCreacion; timestamp fechaActualizacion; timestamp fechaEnvio }
    TRABAJO_AUTOR { int idTrabajo FK; int idPersona FK }
    TRABAJO_HISTORIAL { int id PK; int idTrabajo FK; varchar estadoAnterior; varchar estadoNuevo; int idUsuario FK; varchar comentario; timestamp fecha }
    DOCUMENTO { int idDocumento PK; varchar titulo; varchar descripcion; varchar tipo; varchar categoria; varchar nombreArchivo; varchar extension; varchar contentType; varchar rutaArchivo; varchar urlExterno; bigint tamanoBytes; char sha256; int idTrabajo FK; int idFase FK; timestamp fechaSubida }
    HASHTAG { int idHashtag PK; varchar texto }
    DETALLE_TRABAJO_HASHTAG { int idTrabajo FK; int idHashtag FK }
    RESULTADO_PLAGIO { int id PK; int idTrabajo FK; float porcentajeSimilitud; varchar fuenteComparacion; varchar detalle; timestamp fechaAnalisis }
    CONFIG_FACULTAD { int id PK; int idFacultad FK; int tamanoMaxArchivoMB; int umbralPlagioMedio; int umbralPlagioAlto; boolean comparacionInternet; boolean accesoExterno; int escalaCalifMax; int jwtExpiracionHoras }
    NOTIFICACION { int idNotificacion PK; int idUsuario FK; varchar tipo; varchar titulo; varchar mensaje; varchar enlace; boolean leida; timestamp fechaCreacion; timestamp fechaLectura }
    DOMINIO { int domainId PK; varchar domainGroup; varchar domainName; varchar domainValue; varchar description; varchar lastUser; timestamp lastTime }

    FACULTAD ||--o{ CARRERA : tiene
    FACULTAD ||--o{ SEMESTRE : define
    FACULTAD ||--o{ MATERIA : ofrece
    FACULTAD ||--o{ TIPO_TRABAJO : categoriza
    FACULTAD ||--o{ FASE : configura
    FACULTAD ||--o| CONFIG_FACULTAD : configura
    CARRERA ||--o{ PERSONA : pertenece
    CARRERA ||--o{ ESTUDIANTE_HABILITADO : registra
    PERSONA ||--|| USUARIO : tiene
    TIPO_USUARIO ||--o{ USUARIO : clasifica
    MATERIA ||--o{ GRUPO : agrupa
    SEMESTRE ||--o{ GRUPO : organiza
    GRUPO ||--o{ GRUPO_DOCENTE : asigna
    USUARIO ||--o{ GRUPO_DOCENTE : administra
    GRUPO ||--o{ TRABAJO : contiene
    MATERIA ||--o{ TRABAJO : categoriza
    SEMESTRE ||--o{ TRABAJO : pertenece
    TIPO_TRABAJO ||--o{ TRABAJO : tipifica
    PERSONA ||--o{ TRABAJO : registra
    TRABAJO ||--o{ TRABAJO_AUTOR : tiene
    PERSONA ||--o{ TRABAJO_AUTOR : participa
    TRABAJO ||--o{ TRABAJO_HISTORIAL : registra
    TRABAJO ||--o{ DOCUMENTO : contiene
    FASE ||--o{ DOCUMENTO : clasifica
    TRABAJO ||--o{ DETALLE_TRABAJO_HASHTAG : etiqueta
    HASHTAG ||--o{ DETALLE_TRABAJO_HASHTAG : aparece
    TRABAJO ||--o{ RESULTADO_PLAGIO : analiza
    USUARIO ||--o{ NOTIFICACION : recibe`;

const ENTITIES = [
  { name:"Facultad",              color:"#1B3A6B", attrs:[["idFacultad","INT PK"],["nombre","VARCHAR"],["sigla","VARCHAR"],["estado","INT"]] },
  { name:"Carrera",               color:"#1B3A6B", attrs:[["idCarrera","INT PK"],["nombre","VARCHAR"],["sigla","VARCHAR"],["idFacultad","INT FK"],["estado","INT"]] },
  { name:"EstudianteHabilitado",  color:"#065F46", attrs:[["id","INT PK"],["registro","INT UNIQUE"],["ci","VARCHAR UNIQUE"],["correo","VARCHAR"],["passwordDefault","VARCHAR (BCrypt)"],["nombres","VARCHAR"],["apellidos","VARCHAR"],["idCarrera","INT FK"],["habilitado","BOOLEAN"],["fechaImportacion","TIMESTAMP"]] },
  { name:"Persona",               color:"#374151", attrs:[["idPersona","INT PK"],["nombre","VARCHAR"],["apellidos","VARCHAR"],["ci","VARCHAR UNIQUE"],["correo","VARCHAR"],["celular","VARCHAR"],["fechaNac","DATE"],["registro","INT"],["idCarrera","INT FK"]] },
  { name:"TipoUsuario",           color:"#374151", attrs:[["idTipoUsuario","INT PK"],["nombre","VARCHAR"],["descripcion","VARCHAR"],["estado","INT"]] },
  { name:"Usuario",               color:"#374151", attrs:[["idUsuario","INT PK"],["usuario","VARCHAR"],["password","VARCHAR"],["requiereCambioPassword","BOOLEAN"],["notificarCorreo","BOOLEAN"],["twoFactorEnabled","BOOLEAN"],["totpSecret","VARCHAR"],["idTipoUsuario","INT FK"],["idPersona","INT FK"],["estado","INT"]] },
  { name:"Semestre",              color:"#92400E", attrs:[["idSemestre","INT PK"],["semestre","INT"],["anio","INT"],["idFacultad","INT FK"],["estado","INT"]] },
  { name:"Materia",               color:"#92400E", attrs:[["idMateria","INT PK"],["nombre","VARCHAR"],["sigla","VARCHAR"],["idFacultad","INT FK"],["estado","INT"]] },
  { name:"Grupo",                 color:"#92400E", attrs:[["idGrupo","INT PK"],["nombre","VARCHAR"],["idMateria","INT FK"],["idSemestre","INT FK"],["estado","INT"]] },
  { name:"GrupoDocente",          color:"#5B21B6", attrs:[["idGrupo","INT FK"],["idUsuario","INT FK"],["esAdminCurso","BOOLEAN"]] },
  { name:"TipoTrabajo",           color:"#065F46", attrs:[["idTipoTrabajo","INT PK"],["nombre","VARCHAR"],["estado","INT"],["idFacultad","INT FK"]] },
  { name:"Fase",                  color:"#065F46", attrs:[["idFase","INT PK"],["nombre","VARCHAR"],["descripcion","VARCHAR"],["idFacultad","INT FK"],["estado","INT"]] },
  { name:"Trabajo",               color:"#991B1B", attrs:[["idTrabajo","INT PK"],["titulo","VARCHAR(200)"],["resumen","VARCHAR(3000)"],["estado","VARCHAR (CHECK)"],["calificacion","INT (S4)"],["observaciones","VARCHAR"],["idPersonaCreador","INT FK"],["idMateria","INT FK"],["idTipoTrabajo","INT FK"],["idGrupo","INT FK"],["idSemestre","INT FK"],["fechaCreacion","TIMESTAMP"],["fechaActualizacion","TIMESTAMP"],["fechaEnvio","TIMESTAMP"]] },
  { name:"TrabajoAutor",          color:"#991B1B", attrs:[["idTrabajo","INT FK"],["idPersona","INT FK"]] },
  { name:"TrabajoHistorial",      color:"#991B1B", attrs:[["id","INT PK"],["idTrabajo","INT FK"],["estadoAnterior","VARCHAR"],["estadoNuevo","VARCHAR"],["idUsuario","INT FK"],["comentario","VARCHAR"],["fecha","TIMESTAMP"]] },
  { name:"Documento",             color:"#991B1B", attrs:[["idDocumento","INT PK"],["titulo","VARCHAR"],["descripcion","VARCHAR"],["tipo","ARCHIVO|ENLACE"],["categoria","VARCHAR"],["nombreArchivo","VARCHAR"],["extension","VARCHAR"],["contentType","VARCHAR"],["rutaArchivo","VARCHAR (key MinIO)"],["urlExterno","VARCHAR"],["tamanoBytes","BIGINT"],["sha256","CHAR(64)"],["idTrabajo","INT FK"],["idFase","INT FK"],["fechaSubida","TIMESTAMP"]] },
  { name:"Hashtag",               color:"#374151", attrs:[["idHashtag","INT PK"],["texto","VARCHAR"]] },
  { name:"DetalleTrabajoHashtag", color:"#374151", attrs:[["idTrabajo","INT FK"],["idHashtag","INT FK"]] },
  { name:"ResultadoPlagio",       color:"#B91C1C", attrs:[["id","INT PK"],["idTrabajo","INT FK"],["porcentajeSimilitud","FLOAT"],["fuenteComparacion","VARCHAR"],["detalle","TEXT"],["fechaAnalisis","TIMESTAMP"]] },
  { name:"ConfigFacultad",        color:"#0369A1", attrs:[["id","INT PK"],["idFacultad","INT FK"],["tamanoMaxArchivoMB","INT"],["umbralPlagioMedio","INT"],["umbralPlagioAlto","INT"],["comparacionInternet","BOOLEAN"],["accesoExterno","BOOLEAN"],["escalaCalifMax","INT"],["jwtExpiracionHoras","INT"]] },
  { name:"Notificacion",          color:"#5B21B6", attrs:[["idNotificacion","INT PK"],["idUsuario","INT FK"],["tipo","VARCHAR"],["titulo","VARCHAR"],["mensaje","VARCHAR"],["enlace","VARCHAR"],["leida","BOOLEAN"],["fechaCreacion","TIMESTAMP"],["fechaLectura","TIMESTAMP"]] },
  { name:"Dominio",               color:"#0369A1", attrs:[["domainId","INT PK"],["domainGroup","VARCHAR"],["domainName","VARCHAR"],["domainValue","VARCHAR"],["description","VARCHAR"],["lastUser","VARCHAR"],["lastTime","TIMESTAMP"]] },
];

const STATES = [
  { state:"BORRADOR",                   color:"#475569", bg:"#F1F5F9", desc:"Trabajo creado; el estudiante sube sus documentos antes de enviarlo (agregado en S3)." },
  { state:"PENDIENTE",                  color:"#92400E", bg:"#FEF3C7", desc:"Enviado (con al menos un PDF), esperando revisión del docente." },
  { state:"EN_REVISION",               color:"#1D4ED8", bg:"#DBEAFE", desc:"Docente está revisando el trabajo." },
  { state:"APROBADO",                  color:"#166534", bg:"#DCFCE7", desc:"Aprobado sin observaciones." },
  { state:"APROBADO_CON_OBSERVACIONES",color:"#065F46", bg:"#D1FAE5", desc:"Aprobado pero el estudiante debe subir versión corregida." },
  { state:"RECHAZADO",                 color:"#991B1B", bg:"#FEE2E2", desc:"Rechazado. El estudiante puede corregir y reenviar." },
  { state:"EVALUADO",                  color:"#5B21B6", bg:"#EDE9FE", desc:"Calificado y publicado en el repositorio." },
];

const FILE_FORMATS = [
  { tipo:"Documentos",     formatos:["PDF","DOCX","ODT"],                                   nota:"PDF obligatorio para entrega. Máx. 10 MB (configurable)." },
  { tipo:"Presentaciones", formatos:["PPTX","PDF"],                                          nota:"" },
  { tipo:"Código fuente",  formatos:["ZIP","RAR","7Z","URL GitHub","URL GitLab"],            nota:"URL solo guarda el link como texto." },
  { tipo:"Videos",         formatos:["URL YouTube","URL Vimeo","URL Drive","MP4","WebM"],    nota:"Subida directa: máx. 100 MB (fijo)." },
  { tipo:"Imágenes",       formatos:["PNG","JPG","SVG"],                                     nota:"" },
];

const CSV_STRUCTURE = `# Estructura del CSV de padrón estudiantil
registro,ci,correo,password_default,nombres,apellidos,sigla_carrera

# Ejemplo:
123456,7654321,juan.perez@uagrm.edu.bo,Uagrm2024.,Juan Carlos,Pérez López,ITIN
789012,3456789,maria.gomez@uagrm.edu.bo,Uagrm2024.,María,Gómez Ríos,ITIC

# Notas:
# - Formatos: .csv (coma o punto y coma, UTF-8), .xlsx, .xls (primera hoja). Máx. 5 MB / 10 000 filas
# - La cabecera es obligatoria; columnas en cualquier orden, sin distinguir mayúsculas
# - registro identifica la fila: si ya existe se actualiza, si no se inserta
# - password_default: contraseña temporal (se guarda con BCrypt); el estudiante debe cambiarla en primer login
# - sigla_carrera: debe coincidir con la sigla registrada en la tabla Carrera
# - El sistema valida cada fila y reporta errores sin interrumpir la importación`;

const API_GROUPS = [
  { group:"Auth — auth-service :8081 (auth-api 1.2.0)", color:"#5B21B6", endpoints:[
    { m:"POST", p:"/api/v1/auth/login",           d:"Paso 1: JWT access + refresh, o tokenTemporal si tiene 2FA (429 tras LOGIN_INTENTOS fallidos por IP)" },
    { m:"POST", p:"/api/v1/auth/2fa/verify",       d:"Paso 2: código TOTP → tokens (máx. 5 intentos)" },
    { m:"POST", p:"/api/v1/auth/refresh",          d:"Renovar sesión (rotación del refresh token)" },
    { m:"POST", p:"/api/v1/auth/logout",           d:"Cerrar sesión (blacklist del access y del refresh)" },
    { m:"POST", p:"/api/v1/auth/register",         d:"Auto-registro estudiante {ci, registro} validado contra el padrón (sin tokens, 429 por intentos fallidos)" },
    { m:"POST", p:"/api/v1/auth/password/change",  d:"Cambiar contraseña (obligatorio en primer login) → tokens nuevos" },
    { m:"POST", p:"/api/v1/auth/2fa/enable",       d:"Iniciar activación 2FA: secreto + QR" },
    { m:"POST", p:"/api/v1/auth/2fa/confirm",      d:"Confirmar activación 2FA con un código" },
    { m:"POST", p:"/api/v1/auth/2fa/disable",      d:"Desactivar 2FA con un código" },
  ]},
  { group:"Usuarios, Padrón, Dominio y Notificaciones — user-service :8083 (user-api 1.3.0; ADMIN salvo notificaciones/perfil)", color:"#1B3A6B", endpoints:[
    { m:"GET",    p:"/api/v1/usuarios",                       d:"Listar paginado (q, rol, idCarrera, incluirInactivos, pagina, tamano)" },
    { m:"GET",    p:"/api/v1/usuarios/{id}",                  d:"Obtener usuario" },
    { m:"POST",   p:"/api/v1/usuarios",                       d:"Crear usuario con contraseña inicial (cambio obligatorio)" },
    { m:"PUT",    p:"/api/v1/usuarios/{id}",                  d:"Actualizar datos, rol y estado (login inmutable)" },
    { m:"DELETE", p:"/api/v1/usuarios/{id}",                  d:"Desactivar usuario y cerrar sus sesiones" },
    { m:"POST",   p:"/api/v1/usuarios/{id}/password/reset",   d:"Restablecer contraseña temporal + cerrar sesiones" },
    { m:"GET",    p:"/api/v1/tipos-usuario",                  d:"Listar roles" },
    { m:"POST",   p:"/api/v1/padron/import",                  d:"Importar CSV/XLSX/XLS del padrón (multipart 'archivo') → resumen + errores por fila" },
    { m:"GET",    p:"/api/v1/padron",                         d:"Listar padrón paginado (q, idCarrera, registrado, habilitado)" },
    { m:"PATCH",  p:"/api/v1/padron/{id}",                    d:"Habilitar / inhabilitar estudiante para el auto-registro" },
    { m:"GET",    p:"/api/v1/dominios",                       d:"Listar parámetros configurables (filtro domainGroup)" },
    { m:"POST",   p:"/api/v1/dominios",                       d:"Crear parámetro (grupo + nombre únicos)" },
    { m:"GET",    p:"/api/v1/dominios/{id}",                  d:"Obtener parámetro" },
    { m:"PUT",    p:"/api/v1/dominios/{id}",                  d:"Cambiar valor y descripción (se aplica al instante en todos los servicios)" },
    { m:"DELETE", p:"/api/v1/dominios/{id}",                  d:"Eliminar parámetro (409 si lo usa el sistema)" },
    { m:"GET",    p:"/api/v1/notificaciones",                 d:"Mis notificaciones paginadas (cualquier rol)" },
    { m:"GET",    p:"/api/v1/notificaciones/pendientes",      d:"Cantidad sin leer + pollingSegundos (campana, lee Redis)" },
    { m:"PATCH",  p:"/api/v1/notificaciones/{id}/leida",      d:"Marcar leída (404 si es ajena)" },
    { m:"POST",   p:"/api/v1/notificaciones/leidas",          d:"Marcar todas como leídas" },
    { m:"GET",    p:"/api/v1/perfil/preferencias",            d:"Mis preferencias (notificarCorreo)" },
    { m:"PUT",    p:"/api/v1/perfil/preferencias",            d:"Cambiar mis preferencias" },
  ]},
  { group:"Catálogos — catalog-service :8082 (catalog-api 1.2.0)", color:"#065F46", endpoints:[
    { m:"GET",    p:"/api/v1/facultades",                       d:"Listar (incluirInactivos) — caché Redis" },
    { m:"POST",   p:"/api/v1/facultades",                       d:"Crear (ADMIN)" },
    { m:"PUT",    p:"/api/v1/facultades/{id}",                  d:"Actualizar / reactivar con estado 1 (ADMIN)" },
    { m:"DELETE", p:"/api/v1/facultades/{id}",                  d:"Desactivar, borrado lógico (ADMIN)" },
    { m:"GET",    p:"/api/v1/carreras",                         d:"Listar (idFacultad, incluirInactivos) — mismo CRUD que facultades" },
    { m:"GET",    p:"/api/v1/materias",                         d:"Listar (idFacultad) — POST / PUT / DELETE igual" },
    { m:"GET",    p:"/api/v1/semestres",                        d:"Listar (idFacultad) — POST / PUT / DELETE igual" },
    { m:"GET",    p:"/api/v1/grupos",                           d:"Listar (idFacultad, idMateria, idSemestre) — sin caché; POST / PUT / DELETE" },
    { m:"GET",    p:"/api/v1/grupos/{id}/docentes",             d:"Docentes del grupo" },
    { m:"POST",   p:"/api/v1/grupos/{id}/docentes",             d:"Asignar docente {idUsuario, esAdminCurso} (ADMIN)" },
    { m:"PATCH",  p:"/api/v1/grupos/{id}/docentes/{idUsuario}", d:"Cambiar esAdminCurso (ADMIN)" },
    { m:"DELETE", p:"/api/v1/grupos/{id}/docentes/{idUsuario}", d:"Quitar docente (borrado físico de la relación)" },
    { m:"GET",    p:"/api/v1/fases",                            d:"Listar (idFacultad) — POST / PUT / DELETE" },
    { m:"GET",    p:"/api/v1/tipos-trabajo",                    d:"Listar (idFacultad) — POST / PUT / DELETE" },
  ]},
  { group:"Trabajos, Revisión y Repositorio — work-service :8084 (work-api 1.2.0)", color:"#991B1B", endpoints:[
    { m:"POST",   p:"/api/v1/trabajos",                  d:"Crear trabajo en BORRADOR (ESTUDIANTE; materia y semestre salen del grupo)" },
    { m:"GET",    p:"/api/v1/trabajos/mios",             d:"Mis trabajos (autor o coautor), paginado" },
    { m:"GET",    p:"/api/v1/trabajos/revision",         d:"Bandeja del docente (sus grupos; ADMIN todos)" },
    { m:"GET",    p:"/api/v1/trabajos/{id}",             d:"Detalle con documentos, historial y acciones permitidas" },
    { m:"PUT",    p:"/api/v1/trabajos/{id}",             d:"Editar (autores, estado editable)" },
    { m:"DELETE", p:"/api/v1/trabajos/{id}",             d:"Eliminar borrador y sus archivos" },
    { m:"PATCH",  p:"/api/v1/trabajos/{id}/enviar",      d:"Enviar a revisión (≥1 PDF) → PENDIENTE" },
    { m:"PATCH",  p:"/api/v1/trabajos/{id}/revisar",     d:"Docente toma la revisión → EN_REVISION" },
    { m:"PATCH",  p:"/api/v1/trabajos/{id}/aprobar",     d:"Aprobar; con observaciones → APROBADO_CON_OBSERVACIONES" },
    { m:"PATCH",  p:"/api/v1/trabajos/{id}/rechazar",    d:"Rechazar con motivo obligatorio" },
    { m:"PATCH",  p:"/api/v1/trabajos/{id}/calificar",   d:"Calificar (Admin de Curso; APROBADO → EVALUADO)" },
    { m:"GET",    p:"/api/v1/repositorio",               d:"Búsqueda full-text de EVALUADOS (q, hashtag, facultad, carrera, materia, semestre, grupo, tipo)" },
    { m:"GET",    p:"/api/v1/repositorio/{id}",          d:"Ficha pública (sin calificación, observaciones ni historial)" },
    { m:"GET",    p:"/api/v1/publico/repositorio",       d:"Búsqueda anónima (solo facultades con acceso externo)" },
    { m:"GET",    p:"/api/v1/publico/repositorio/{id}",  d:"Ficha anónima (sin descarga)" },
    { m:"GET",    p:"/api/v1/publico/estado",            d:"¿Hay acceso anónimo habilitado?" },
  ]},
  { group:"Documentos — work-service :8084", color:"#92400E", endpoints:[
    { m:"POST",   p:"/api/v1/trabajos/{id}/documentos",  d:"Subir archivo (multipart; tipo real + tamaño; 429 por minuto)" },
    { m:"POST",   p:"/api/v1/trabajos/{id}/enlaces",     d:"Agregar enlace externo (https, hosts permitidos)" },
    { m:"DELETE", p:"/api/v1/documentos/{id}",           d:"Quitar documento y su archivo" },
    { m:"GET",    p:"/api/v1/documentos/{id}/descarga",  d:"Descargar por proxy (verifica permisos)" },
    { m:"GET",    p:"/api/v1/documentos/reglas",         d:"Formatos, tamaños y hosts permitidos de una facultad" },
  ]},
  { group:"Plagio — plagiarism-service :8085 (plagio-api 1.0.0)", color:"#B91C1C", endpoints:[
    { m:"POST", p:"/api/v1/plagio/trabajos/{id}",          d:"Iniciar análisis (docente del grupo) → 202 EN_PROCESO" },
    { m:"GET",  p:"/api/v1/plagio/trabajos/{id}",          d:"Último análisis con porcentaje, semáforo y fuentes" },
    { m:"GET",  p:"/api/v1/plagio/trabajos/{id}/estado",   d:"Estado liviano para polling (Redis)" },
    { m:"GET",  p:"/api/v1/plagio/trabajos/{id}/historial",d:"Todos los análisis del trabajo" },
    { m:"POST", p:"/api/v1/plagio/webhook/copyleaks/{token}/{evento}/{scanId}", d:"Webhook de Copyleaks (token secreto, sin sesión)" },
  ]},
  { group:"Configuración", color:"#0369A1", endpoints:[
    { m:"GET",  p:"/api/v1/facultades/{id}/config",      d:"catalog-service: configuración de la facultad (o valores por defecto)" },
    { m:"PUT",  p:"/api/v1/facultades/{id}/config",      d:"catalog-service: guardar config (tamaño archivo, umbrales plagio, escala, JWT, acceso externo)" },
    { m:"PUT",  p:"/api/v1/dominios/{id}",               d:"user-service: SMTP se edita como parámetros de SMTP_GROUP (contraseña cifrada)" },
    { m:"POST", p:"/api/v1/config/smtp/prueba",          d:"user-service: enviar correo de prueba con la configuración actual" },
  ]},
];

const USER_STORIES = [
  { cu:"CU1", sprint:1, name:"Iniciar Sesión",
    story:"Como usuario registrado, quiero iniciar sesión con mi usuario y contraseña para acceder según mi rol.",
    criteria:[
      "DADO credenciales válidas, CUANDO presiona Iniciar Sesión, ENTONCES recibe JWT y es redirigido al dashboard de su rol.",
      "DADO credenciales inválidas, CUANDO presiona Iniciar Sesión, ENTONCES ve mensaje de error sin revelar qué campo es incorrecto.",
      "DADO que el usuario tiene 2FA habilitado, CUANDO ingresa credenciales correctas, ENTONCES el sistema solicita el código TOTP antes de otorgar acceso.",
      "DADO que el access token JWT expira, CUANDO accede a recurso protegido, ENTONCES el sistema usa el refresh token; si también expiró, redirige al login.",
    ]},
  { cu:"CU-REG", sprint:2, name:"Auto-registro Estudiante",
    story:"Como estudiante de la UAGRM, quiero registrarme en el sistema ingresando mi CI y número de registro para obtener acceso.",
    criteria:[
      "DADO que el estudiante ingresa CI y registro válidos, CUANDO el sistema valida contra EstudianteHabilitado (habilitado), ENTONCES crea su cuenta con usuario = número de registro y le indica que inicie sesión con la contraseña entregada por su facultad.",
      "DADO que el CI/registro no existe en EstudianteHabilitado, CUANDO intenta registrarse, ENTONCES ve: 'No encontrado en el padrón institucional. Contacte a su facultad.'",
      "DADO que ya tiene cuenta, CUANDO intenta registrarse de nuevo con el mismo CI, ENTONCES el sistema informa duplicado y sugiere contactar a su facultad para restablecer la contraseña.",
      "DADO que es primer login con password_default, CUANDO accede, ENTONCES el sistema obliga al cambio de contraseña antes de continuar.",
    ]},
  { cu:"CU-IMP", sprint:2, name:"Importar Padrón Estudiantil",
    story:"Como administrador, quiero importar el padrón de estudiantes desde un CSV/Excel para habilitar su auto-registro.",
    criteria:[
      "DADO CSV válido con columnas (registro, ci, correo, password_default, nombres, apellidos, sigla_carrera), CUANDO se importa, ENTONCES el sistema inserta/actualiza en EstudianteHabilitado y muestra resumen (insertados, actualizados, errores).",
      "DADO una fila con datos inválidos (CI no numérico, carrera inexistente), CUANDO se procesa, ENTONCES esa fila va al log de errores sin interrumpir la importación.",
      "DADO archivo con formato incorrecto (.pdf), CUANDO se intenta subir, ENTONCES el sistema rechaza con formatos válidos (CSV, XLSX, XLS).",
      "DADO un registro que ya existe en el padrón, CUANDO se reimporta, ENTONCES se actualizan sus datos y su contraseña por defecto (cifrada), sin modificar una cuenta ya creada.",
    ]},
  { cu:"CU6", sprint:3, name:"Crear Trabajo",
    story:"Como estudiante, quiero crear un trabajo asociado a una materia, grupo y semestre para enviarlo a revisión.",
    criteria:[
      "DADO datos completos (título, resumen, materia, grupo, semestre, tipo, al menos 1 documento PDF), CUANDO envía, ENTONCES el trabajo queda en PENDIENTE y el docente del grupo recibe notificación.",
      "DADO archivo con formato no permitido, CUANDO adjunta, ENTONCES el sistema rechaza con mensaje de formatos válidos.",
      "DADO archivo que supera el límite configurado por la facultad, CUANDO intenta adjuntar, ENTONCES el sistema rechaza con mensaje del límite vigente.",
      "DADO que el trabajo fue creado, CUANDO consulta 'Mis Trabajos', ENTONCES aparece con estado PENDIENTE.",
    ]},
  { cu:"CU10", sprint:3, name:"Aprobar / Rechazar Trabajo",
    story:"Como docente, quiero aprobar, aprobar con observaciones, o rechazar un trabajo para gestionar su publicación.",
    criteria:[
      "DADO un trabajo PENDIENTE, CUANDO el docente aprueba sin observaciones, ENTONCES el estado cambia a APROBADO y el estudiante es notificado (in-app + correo si habilitado).",
      "DADO que el docente aprueba con observaciones, CUANDO ingresa el texto, ENTONCES el estado cambia a APROBADO_CON_OBSERVACIONES y el estudiante puede subir versión corregida.",
      "DADO que el docente rechaza, CUANDO ingresa el motivo (obligatorio), ENTONCES el estado cambia a RECHAZADO y el estudiante puede corregir y reenviar.",
      "DADO que no hay trabajos pendientes, CUANDO el docente accede a Revisión, ENTONCES ve 'Sin trabajos pendientes'.",
    ]},
  { cu:"CU11", sprint:3, name:"Buscar y Consultar Trabajos",
    story:"Como usuario autenticado (o anónimo externo si está habilitado), quiero buscar trabajos por título, materia, carrera, facultad, semestre o hashtag.",
    criteria:[
      "DADO un término de búsqueda, CUANDO ejecuta, ENTONCES ve resultados paginados solo de trabajos EVALUADOS.",
      "DADO filtros combinados (facultad + materia + semestre), CUANDO los aplica, ENTONCES los resultados respetan todos simultáneamente.",
      "DADO usuario anónimo con acceso externo habilitado, CUANDO consulta un trabajo, ENTONCES puede ver metadatos pero el botón Descargar requiere iniciar sesión.",
      "DADO usuario anónimo con acceso externo deshabilitado, CUANDO intenta acceder al repositorio, ENTONCES ve pantalla de acceso restringido.",
    ]},
  { cu:"CU14", sprint:4, name:"Detectar Plagio (Copyleaks)",
    story:"Como docente, quiero ejecutar la detección de plagio sobre un trabajo para verificar su originalidad antes de aprobarlo.",
    criteria:[
      "DADO que el docente inicia el análisis, CUANDO Copyleaks procesa el documento de forma asíncrona, ENTONCES muestra porcentaje de similitud con semáforo (verde/amarillo/rojo según umbrales de ConfigFacultad).",
      "DADO que el análisis supera 30 segundos, CUANDO está en proceso, ENTONCES el sistema muestra indicador de progreso y notifica al docente al finalizar.",
      "DADO que comparación con internet está habilitada en ConfigFacultad, CUANDO se analiza, ENTONCES el resultado incluye fuentes externas detectadas.",
    ]},
  { cu:"CU18", sprint:4, name:"Evaluar y Calificar Trabajo",
    story:"Como docente, quiero calificar un trabajo aprobado para que quede publicado en el repositorio con su nota final.",
    criteria:[
      "DADO un trabajo en estado APROBADO, CUANDO el docente ingresa calificación dentro del rango de ConfigFacultad, ENTONCES el estado cambia a EVALUADO y queda visible en búsqueda pública.",
      "DADO calificación fuera del rango configurado, CUANDO valida, ENTONCES el campo muestra el rango permitido.",
      "DADO que el docente es Admin de Curso, CUANDO califica, ENTONCES solo puede gestionar trabajos de los grupos que administra.",
    ]},
  { cu:"CU-CFG", sprint:5, name:"Configurar Facultad",
    story:"Como administrador, quiero configurar los parámetros de una facultad de forma independiente.",
    criteria:[
      "DADO parámetros válidos (tamaño archivo, umbral plagio, escala calificación, acceso externo, SMTP), CUANDO guarda, ENTONCES todos los módulos de esa facultad aplican los nuevos valores inmediatamente.",
      "DADO que se configura el servidor SMTP, CUANDO el administrador lo prueba, ENTONCES el sistema envía un correo de prueba y confirma el resultado.",
    ]},
];

// Estado de tareas: ✅ hecho · 🚧 en curso · ⏳ pendiente · ➖ descartada/diferida
const SPRINTS = [
  { num:1, title:"Infraestructura + Auth + 2FA ✅", duration:"3 semanas · cerrado",
    goal:"Sistema base funcional con autenticación JWT, 2FA opcional, estructura multimodulo Gradle configurada y entorno Docker levantado.",
    tasks:[
      "✅ Configurar proyecto multimodulo Gradle: dao / dto / imp / spec / mockup / ws/",
      "✅ Configurar PostgreSQL 15 + Flyway para migraciones de esquema",
      "✅ Entidades: Facultad, Carrera, Persona, TipoUsuario, Usuario, ConfigFacultad, ConfigSMTP",
      "✅ Spring Security + JWT (access 1h / refresh 7d, configurable por ConfigFacultad)",
      "✅ 2FA con TOTP (Google Authenticator) — opcional por usuario",
      "✅ Flujo cambio de contraseña obligatorio en primer login (password_default)",
      "✅ Angular: proyecto standalone, routing, AuthGuard, RoleGuard, JwtInterceptor",
      "✅ Módulo Auth Angular: login + pantalla verificación 2FA + cambio contraseña",
      "✅ docker-compose dev: PostgreSQL + MinIO + Redis + servicios",
      "✅ Configurar Redis: namespaces por microservicio, TTLs base, cliente Spring Data Redis",
      "✅ Implementar blacklist JWT en Redis (logout real + cambio de contraseña)",
      "✅ Pipeline Jenkins básico: build + test + etapa 'Contratos' (Redocly + oasdiff)",
      "✅ Contratos OpenAPI 3.0.3 contract-first + test de contrato + mock Prism (agregado)",
    ], cu:["CU1"]},
  { num:2, title:"Usuarios + Padrón CSV + Catálogos ✅", duration:"3 semanas · completo (pendiente merge a dev)",
    goal:"Auto-registro validado contra padrón importado, CRUD de usuarios, catálogos completos por facultad y configuración SMTP.",
    tasks:[
      "✅ ws/catalog-service (:8082): Facultad, Carrera, Materia, Semestre, Grupo, GrupoDocente, Fase, TipoTrabajo (contrato catalog-api 1.1.0)",
      "✅ Endpoint asignación docente a grupo con flag esAdminCurso + PATCH para cambiarlo + filtro de grupos por facultad",
      "✅ Caché Redis para catálogos (catalog:cache:*, TTL 10 min, índice SET, invalidación tras commit, degrada a PostgreSQL)",
      "✅ Angular: cliente generado con ng-openapi-gen + Admin de Catálogos (/admin/catalogos)",
      "✅ ws/user-service (:8083): CRUD Persona + Usuario, TipoUsuario, reset de contraseña (contrato user-api 1.0.0)",
      "✅ EstudianteHabilitado: tabla (V4) + importación CSV/XLSX/XLS (Apache POI) con errores por fila y password_default en BCrypt",
      "✅ Flujo auto-registro: POST /auth/register valida CI + registro contra el padrón → crea la cuenta",
      "✅ Angular: /registro, /admin/usuarios, /admin/padron y buscador de docentes en grupos",
      "✅ Tabla de dominio (parámetros configurables) + ConstantesDominio + caché Redis config:dominio:{GRUPO} + CRUD /dominios + pantalla /admin/parametros",
      "✅ Rate limiting por IP de intentos fallidos en /auth/login y /auth/register (AUTH_GROUP.LOGIN_INTENTOS / LOGIN_WAITING)",
      "✅ CI y celular como texto; estudiante siempre con carrera",
      "✅ ConfigFacultad: GET/PUT en catalog-service (defaults en FACULTAD_DEFAULT_GROUP, caché config:facultad:{id}) + /admin/configuracion",
      "✅ SMTP en dominio (SMTP_GROUP, contraseña cifrada AES-GCM) + 'Enviar correo de prueba' + pestaña Correo en /admin/configuracion",
      "✅ Notificaciones in-app: tabla notificacion + campana con polling (contador Redis notif:pendientes:{id}) + /notificaciones",
      "✅ Notificaciones por correo SMTP vía CorreoService (opcional por usuario en /perfil)",
      "✅ Quartz job: purga de notificaciones leídas antiguas (reemplaza 'limpieza de tokens JWT', innecesaria por el TTL de Redis)",
      "✅ Pruebas unitarias JUnit 5 + Mockito + tests de contrato: servicios usuario y catálogo",
    ], cu:["CU-REG","CU-IMP","CU-CFG"]},
  { num:3, title:"Repositorio de Trabajos ✅", duration:"3 semanas · completo (pendiente merge a dev)",
    goal:"Flujo completo de creación → revisión → aprobación con múltiples documentos y todos los formatos permitidos.",
    tasks:[
      "✅ ws/work-service (:8084): Trabajo, TrabajoAutor, TrabajoHistorial, Documento, Hashtag, TrabajoHashtag (V8, contrato work-api 1.0.0)",
      "✅ Validación de formatos (extensión + tipo real con Tika) y tamaño según ConfigFacultad; videos WORK_GROUP.VIDEO_MAX_MB",
      "✅ MinIO: almacenamiento en streaming, key con UUID, sha256, compensación si falla la BD",
      "✅ Soporte URL externa para videos (YouTube, Vimeo, Drive) y código (GitHub, GitLab), solo https",
      "✅ Máquina de estados: BORRADOR → PENDIENTE → EN_REVISION → APROBADO / APROBADO_CON_OBSERVACIONES / RECHAZADO (EVALUADO en S4) + historial",
      "✅ API REST: /trabajos (crear, mios, revision, detalle, editar, eliminar, enviar, revisar, aprobar, rechazar), /documentos (subir, enlace, quitar, descarga, reglas)",
      "✅ Módulo Angular Estudiante: mis trabajos, crear/editar, carga múltiple de documentos por fase con progreso",
      "✅ Módulo Angular Docente: bandeja de revisión, tomar revisión, aprobar/observaciones/rechazar",
      "✅ Módulo Angular Repositorio: /repositorio (búsqueda simple + filtros, solo EVALUADOS) y ficha /repositorio/:id (CU11 básico; full-text y anónimo en S4)",
      "✅ Regla visibilidad: solo EVALUADOS en el repositorio; sin calificación ni observaciones en la ficha pública",
      "✅ Rate limiting Redis en uploads: work:rate:upload:{idUsuario} ventana 60 s, WORK_GROUP.UPLOADS_POR_MINUTO → 429",
      "➖ Cola Redis de uploads: diferida (los archivos no pasan por Redis; streaming directo a MinIO)",
      "✅ Notificaciones al cambiar estado (TRABAJO_ENVIADO a docentes; EN_REVISION / APROBADO / CON_OBSERVACIONES / RECHAZADO a autores)",
    ], cu:["CU6","CU10","CU11"]},
  { num:4, title:"Búsqueda Full-text + Plagio + Evaluación 🚧", duration:"3 semanas · en curso",
    goal:"Motor de búsqueda con filtros por facultad, integración Copyleaks async y evaluación con calificación configurable.",
    tasks:[
      "✅ CU18: PATCH /trabajos/{id}/calificar (Admin de Curso, rango 0..escalaCalifMax) → EVALUADO + notificación",
      "✅ Full-text search PostgreSQL (tsvector español + unaccent, pesos, GIN, websearch_to_tsquery) en título, resumen, hashtags",
      "✅ Filtros: facultad, carrera, materia, semestre, grupo, tipo, hashtag (estado: el repositorio solo muestra EVALUADO)",
      "✅ Acceso externo: /publico/** anónimo (solo metadatos) vs. autenticado (descarga); /explorar en Angular",
      "⏳ ws/plagiarism-service (:8085): MotorPlagio SIMULADO / COPYLEAKS (API key en Secret, webhook con token)",
      "⏳ Análisis asíncrono vía Quartz: job envía al motor → guarda resultado_plagio; job de timeout → ERROR",
      "⏳ Semáforo visual: verde/amarillo/rojo según umbrales de ConfigFacultad",
      "⏳ Comparación configurable: repositorio interno + internet (ConfigFacultad.comparacionInternet)",
      "✅ Módulo Angular Docente: calificación en el detalle + 'Por calificar' en la bandeja",
      "✅ Módulo Angular Docente: panel de plagio con polling y resultado visual",
      "⏳ Estado del análisis en Redis: plagio:status:{idTrabajo} EX 600 → polling cada 5 s sin hit a BD",
      "⏳ Notificación al docente cuando el análisis de plagio finaliza",
    ], cu:["CU14","CU18","CU11"]},
  { num:5, title:"Pruebas, Hardening y Despliegue K8s", duration:"3 semanas",
    goal:"Sistema probado, documentado, desplegado en Kubernetes con pipeline CI/CD completo.",
    tasks:[
      "Pruebas integración: Spring Boot Test + Testcontainers (PostgreSQL, MinIO, Redis)",
      "Pruebas E2E Angular con Cypress: flujos login, crear trabajo, aprobar, buscar",
      "Hardening Spring Security: CORS, CSRF, rate limiting, headers HTTP (HSTS, CSP)",
      "Manifiestos K8s: Deployments, Services, Ingress, ConfigMaps, Secrets por microservicio",
      "Jenkinsfile: build → test → docker build → push → kubectl apply",
      "Swagger UI por microservicio (los contratos OpenAPI ya existen desde S1); cifrado de totpSecret",
      "Seed de datos FICCT para demo: facultad, carreras, materias, usuarios de prueba",
      "Revisión final de criterios de aceptación de todos los CU",
    ], cu:[]},
];

const BACKEND_STRUCT = `proyecto-ws/
├── source/
│   ├── build.gradle (padre — BOM Spring Boot 4.1.1, Java 25)
│   ├── dao/          # @Entity JPA, JpaRepository, Specifications (filtros dinámicos)
│   ├── dto/          # Request/Response DTOs, MapStruct mappers
│   ├── imp/          # Services, Validators, Quartz Jobs (plagio async, JWT cleanup), Redis Services
│   ├── spec/         # openapi/ contratos fuente de verdad (auth-api, catalog-api, user-api...)
│   ├── mockup/       # Mocks para testing
│   └── ws/
│       ├── auth-service/        # :8081 — JWT, 2FA TOTP, refresh, logout, auto-registro
│       ├── catalog-service/     # :8082 — Facultades, carreras, materias, grupos, fases (caché Redis)
│       ├── user-service/        # :8083 — Usuarios, roles, importación CSV/Excel padrón
│       ├── work-service/        # :8084 — Trabajos, documentos, hashtags, MinIO
│       └── plagiarism-service/  # :8085 — Análisis Copyleaks async, ResultadoPlagio
├── docker-compose/
│   ├── dev/          # PostgreSQL + MinIO + Redis + servicios
│   └── prod/
├── k8s/
│   ├── deployments/  # Un Deployment por microservicio
│   ├── services/
│   ├── ingress/      # Nginx Ingress Controller
│   ├── configmaps/   # application.yml por entorno
│   └── secrets/      # JWT secret, Copyleaks API key, SMTP password
└── script/
    ├── Jenkinsfile
    ├── contratos.sh  # Redocly lint + oasdiff breaking changes
    ├── build.sh
    └── deploy.sh`;

const ANGULAR_STRUCT = `repositorio-digital/
├── src/app/
│   ├── api/                           # GENERADO por ng-openapi-gen (npm run api:gen): auth, catalog, user
│   ├── core/
│   │   ├── api/api.providers.ts       # provideApi(): rootUrl común de los clientes generados
│   │   ├── auth/
│   │   │   ├── auth.service.ts        # login, logout, 2FA, refresh, primer login
│   │   │   ├── auth.guard.ts
│   │   │   ├── role.guard.ts          # RoleGuard('ADMIN'|'DOCENTE'|'ESTUDIANTE')
│   │   │   └── jwt.interceptor.ts     # Bearer token + manejo 401 → refresh
│   │   ├── services/
│   │   │   ├── trabajo.service.ts
│   │   │   ├── usuario.service.ts
│   │   │   ├── facultad.service.ts
│   │   │   ├── catalogo.service.ts
│   │   │   ├── documento.service.ts
│   │   │   ├── plagio.service.ts
│   │   │   └── notificacion.service.ts
│   │   └── http/error-message.ts      # ProblemDetail → mensajes y errores por campo
│   │
│   ├── shared/
│   │   ├── components/
│   │   │   ├── file-upload/           # Validación formato + tamaño según ConfigFacultad
│   │   │   ├── estado-badge/          # Badge por estado de trabajo (color semáforo)
│   │   │   ├── plagio-gauge/          # Semáforo verde/amarillo/rojo
│   │   │   ├── data-table/
│   │   │   ├── notification-bell/     # Campana con badge de notificaciones
│   │   │   └── url-input/             # Input para URLs externas (video, GitHub)
│   │   └── pipes/
│   │
│   ├── features/
│   │   ├── auth/
│   │   │   ├── login.component.ts
│   │   │   ├── register.component.ts  # Auto-registro con validación padrón
│   │   │   ├── two-factor.component.ts
│   │   │   └── change-password.component.ts  # Cambio obligatorio primer login
│   │   │
│   │   ├── admin/
│   │   │   ├── usuarios/              # CU2, CU3, CU4, CU5
│   │   │   ├── padron/                # CU-IMP: importar CSV/Excel + resumen + listado
│   │   │   ├── facultades/
│   │   │   ├── catalogos/             # Materias, semestres, fases, tipos
│   │   │   ├── configuracion/         # ConfigFacultad + ConfigSMTP
│   │   │   └── admin.routes.ts
│   │   │
│   │   ├── estudiante/
│   │   │   ├── mis-trabajos/          # Lista trabajos + estados
│   │   │   ├── nuevo-trabajo/         # Formulario + carga múltiple docs por fase
│   │   │   └── estudiante.routes.ts
│   │   │
│   │   ├── docente/
│   │   │   ├── bandeja-revision/      # CU10 — aprobar/rechazar/observaciones
│   │   │   ├── evaluacion/            # CU18 — calificar
│   │   │   ├── plagio/                # CU14 — iniciar análisis + semáforo resultado
│   │   │   └── docente.routes.ts
│   │   │
│   │   └── repositorio/
│   │       ├── search/                # CU11 — búsqueda full-text con filtros
│   │       ├── trabajo-detalle/       # Metadatos + documentos + descarga
│   │       └── repositorio.routes.ts
│   │
│   ├── app.config.ts                  # provideRouter, provideHttpClient, JWT
│   └── app.routes.ts                  # Lazy loading por rol`;

const ROUTES_TABLE = [
  { path:"/login",                    comp:"LoginComponent",           guard:"—",                                rol:"Público" },
  { path:"/registro",                 comp:"RegistroComponent",        guard:"GuestGuard",                       rol:"Público" },
  { path:"/2fa",                      comp:"TwoFactorComponent",       guard:"—",                                rol:"Público" },
  { path:"/change-password",          comp:"ChangePasswordComponent",  guard:"AuthGuard",                        rol:"Todos" },
  { path:"/admin/usuarios",           comp:"UsuariosListComponent",    guard:"AuthGuard+RoleGuard('ADMIN')",      rol:"Admin" },
  { path:"/admin/padron",             comp:"PadronComponent",          guard:"AuthGuard+RoleGuard('ADMIN')",      rol:"Admin" },
  { path:"/notificaciones",           comp:"NotificacionesComponent",  guard:"AuthGuard",                        rol:"Todos" },
  { path:"/admin/parametros",         comp:"ParametrosComponent",      guard:"AuthGuard+RoleGuard('ADMIN')",      rol:"Admin" },
  { path:"/admin/catalogos/:entidad", comp:"Catálogos (facultades … tipos-trabajo)", guard:"AuthGuard+RoleGuard('ADMIN')", rol:"Admin" },
  { path:"/perfil/seguridad",         comp:"SeguridadComponent (2FA)", guard:"AuthGuard",                        rol:"Todos" },
  { path:"/admin/configuracion",      comp:"ConfiguracionComponent (Facultad · Correo SMTP)", guard:"AuthGuard+RoleGuard('ADMIN')", rol:"Admin" },
  { path:"/estudiante/mis-trabajos",  comp:"MisTrabajosComponent",     guard:"AuthGuard+RoleGuard('ESTUDIANTE')", rol:"Estudiante" },
  { path:"/estudiante/nuevo-trabajo", comp:"NuevoTrabajoComponent",    guard:"AuthGuard+RoleGuard('ESTUDIANTE')", rol:"Estudiante" },
  { path:"/docente/revision",         comp:"BandejaRevisionComponent", guard:"AuthGuard+RoleGuard('DOCENTE')",    rol:"Docente" },
  { path:"/docente/evaluacion/:id",   comp:"EvaluacionComponent",      guard:"AuthGuard+RoleGuard('DOCENTE')",    rol:"Docente" },
  { path:"/docente/plagio/:id",       comp:"PlagioComponent",          guard:"AuthGuard+RoleGuard('DOCENTE')",    rol:"Docente" },
  { path:"/repositorio",              comp:"SearchComponent",          guard:"AuthGuard (o anónimo si ext.)",     rol:"Todos" },
  { path:"/repositorio/:id",          comp:"TrabajoDetalleComponent",  guard:"AuthGuard (o anónimo si ext.)",     rol:"Todos" },
];

// ── Sections ─────────────────────────────────────────────────────────────────
function OverviewSection() {
  const stats = [
    { v:"21", l:"Entidades BD" }, { v:"5",  l:"Microservicios" },
    { v:"9+", l:"Casos de Uso" }, { v:"5",  l:"Sprints" },
    { v:"43+",l:"Endpoints API"}, { v:"6",  l:"Capas Redis" },
  ];
  const stack = [
    { cat:"Backend", items:["Java 25 + Spring Boot 4.1.1","Spring Security + JWT + TOTP 2FA","Spring Data JPA + Flyway","PostgreSQL 15 (full-text nativo)","Redis 7 (caché, rate limiting, blacklist JWT, colas, estado plagio)","Quartz Scheduler (plagio async)","Gradle multimodulo","MapStruct + Bean Validation","OpenAPI 3.0.3 contract-first (test de contrato)","Apache POI (import CSV/Excel)","Spring Mail (SMTP configurable)"] },
    { cat:"Almacenamiento", items:["MinIO (S3-compatible)","Redis 7 (caché L2 + colas de uploads)","URL externa: YouTube, Vimeo, Drive, GitHub, GitLab"] },
    { cat:"Frontend", items:["Angular 22 Standalone + zoneless","TypeScript 5","CSS propio (sin librería UI)","RxJS + Signals","Cliente generado con ng-openapi-gen","Vitest","Cypress (E2E)"] },
    { cat:"Infraestructura", items:["Docker + Docker Compose","Kubernetes + Nginx Ingress","Jenkins CI/CD","Prism (mocks de contrato) + Redocly + oasdiff","Copyleaks API (detección plagio)","Redis 7 (contenedor adicional en docker-compose y K8s)"] },
  ];
  return (
    <div>
      <SectionHeader title="Repositorio Digital UAGRM" subtitle="FICCT como implementación inicial · Escalable a toda la universidad mediante entidad Facultad" />
      <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:10, marginBottom:20 }}>
        {stats.map(s=>(
          <Card key={s.l} style={{ textAlign:"center", padding:"14px 10px" }}>
            <div style={{ fontSize:28, fontWeight:800, color:C.accent }}>{s.v}</div>
            <div style={{ fontSize:12, color:C.muted }}>{s.l}</div>
          </Card>
        ))}
      </div>
      <Card>
        <h3 style={{ marginTop:0, color:C.primary, fontSize:15 }}>Stack Tecnológico Definitivo</h3>
        <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:10 }}>
          {stack.map(g=>(
            <div key={g.cat} style={{ background:C.bg, borderRadius:8, padding:"12px 14px" }}>
              <div style={{ fontWeight:700, color:C.primary, fontSize:13, marginBottom:8 }}>{g.cat}</div>
              {g.items.map(i=><div key={i} style={{ fontSize:12, color:C.text, padding:"3px 0", borderBottom:`1px solid ${C.border}` }}>{i}</div>)}
            </div>
          ))}
        </div>
      </Card>
      <Card>
        <h3 style={{ marginTop:0, color:C.primary, fontSize:15 }}>Redis 7 — Casos de Uso en el Sistema</h3>
        {[
          { key:"Rate Limiting uploads",       ns:"work:rate:upload:{idUsuario}",    desc:"INCR + TTL 60s. HTTP 429 si supera límite. Protege MinIO en picos de cierre de semestre." },
          { key:"Blacklist JWT ✅",            ns:"auth:blacklist:{jti}",            desc:"SET EX {vida restante}. Logout real y rotación de refresh tokens." },
          { key:"Revocación por usuario ✅",   ns:"auth:revocado-desde:{idUsuario}", desc:"Epoch ms, TTL 7 d. Invalida todos los tokens previos (cambio/reset de contraseña, cambio de rol, desactivación)." },
          { key:"Caché catálogos ✅",          ns:"catalog:cache:{entidad}:todas|facultad:{id}", desc:"TTL 10 min + SET índice para invalidar. Hit → <5ms. Si Redis falla → PostgreSQL." },
          { key:"Cola de uploads ➖",          ns:"(diferida)",                      desc:"No implementada: los archivos van en streaming a MinIO; ver Decisiones." },
          { key:"Estado análisis plagio",      ns:"plagio:status:{idTrabajo}",       desc:"SET EX 600. Angular polling cada 5s sin hit a BD durante el análisis Copyleaks." },
          { key:"Sesión 2FA pendiente ✅",     ns:"auth:2fa:pending:{tokenTemporal}", desc:"SET EX 300 → idUsuario. Token aleatorio (no el id) + auth:2fa:intentos:{tokenTemporal} (máx. 5)." },
        ].map(r=>(
          <div key={r.key} style={{ display:"grid", gridTemplateColumns:"160px 280px 1fr", gap:12, padding:"9px 0", borderBottom:`1px solid ${C.bg}`, alignItems:"start" }}>
            <span style={{ fontWeight:700, color:"#BE123C", fontSize:12 }}>{r.key}</span>
            <code style={{ fontSize:11, color:C.muted, fontFamily:"monospace", background:C.bg, padding:"2px 6px", borderRadius:4 }}>{r.ns}</code>
            <span style={{ fontSize:12, color:C.text }}>{r.desc}</span>
          </div>
        ))}
      </Card>
      <Card>
        <h3 style={{ marginTop:0, color:C.primary, fontSize:15 }}>Máquina de Estados del Trabajo</h3>
        {STATES.map(s=>(
          <div key={s.state} style={{ display:"flex", alignItems:"center", gap:12, padding:"8px 0", borderBottom:`1px solid ${C.bg}` }}>
            <Badge label={s.state} bg={s.bg} color={s.color} />
            <span style={{ fontSize:13, color:C.text }}>{s.desc}</span>
          </div>
        ))}
      </Card>
      <Card>
        <h3 style={{ marginTop:0, color:C.primary, fontSize:15 }}>Formatos de Archivo Permitidos</h3>
        {FILE_FORMATS.map(f=>(
          <div key={f.tipo} style={{ padding:"10px 0", borderBottom:`1px solid ${C.bg}` }}>
            <div style={{ fontWeight:700, color:C.primary, fontSize:13, marginBottom:6 }}>{f.tipo}</div>
            <div style={{ display:"flex", flexWrap:"wrap", gap:6 }}>
              {f.formatos.map(fmt=><Badge key={fmt} label={fmt} bg={C.accentLt} color={C.accent} />)}
              {f.nota && <span style={{ fontSize:12, color:C.muted, alignSelf:"center" }}>— {f.nota}</span>}
            </div>
          </div>
        ))}
      </Card>
    </div>
  );
}

function DecisionsSection() {
  return (
    <div>
      <SectionHeader title="Decisiones Tomadas" subtitle="Registro completo de todas las decisiones del proyecto" />
      {DECISIONS.map(d=>(
        <Card key={d.cat}>
          <h3 style={{ marginTop:0, color:C.primary, fontSize:15, borderBottom:`2px solid ${C.accent}`, paddingBottom:8 }}>{d.cat}</h3>
          {d.items.map(i=>(
            <div key={i.q} style={{ padding:"10px 0", borderBottom:`1px solid ${C.bg}` }}>
              <div style={{ fontWeight:700, color:C.primary, fontSize:13 }}>→ {i.q}</div>
              <div style={{ color:C.text, fontSize:13, marginTop:4 }}>{i.a}</div>
            </div>
          ))}
        </Card>
      ))}
      <Card>
        <h3 style={{ marginTop:0, color:C.primary, fontSize:15 }}>Estructura CSV Padrón Estudiantil</h3>
        <Code>{CSV_STRUCTURE}</Code>
      </Card>
    </div>
  );
}

function EntitiesSection() {
  return (
    <div>
      <SectionHeader title="Modelo de Datos" subtitle="21 entidades · Escalable a toda la UAGRM mediante entidad Facultad · PostgreSQL 15" />
      <Card>
        <h3 style={{ marginTop:0, color:C.primary, fontSize:15 }}>Diagrama ER — Mermaid</h3>
        <p style={{ color:C.muted, fontSize:13, margin:"0 0 12px" }}>Pegar en <a href="https://mermaid.live" target="_blank" rel="noopener noreferrer" style={{ color:C.accent }}>mermaid.live</a> para visualizar interactivamente.</p>
        <Code>{MERMAID_ER}</Code>
      </Card>
      <div style={{ display:"grid", gridTemplateColumns:"repeat(2,1fr)", gap:12 }}>
        {ENTITIES.map(e=>(
          <Card key={e.name} style={{ padding:"14px 18px" }}>
            <div style={{ fontWeight:700, fontSize:14, color:e.color, borderBottom:`2px solid ${e.color}`, paddingBottom:6, marginBottom:8 }}>{e.name}</div>
            {e.attrs.map(([name,type])=>(
              <div key={name} style={{ display:"flex", justifyContent:"space-between", fontSize:12, padding:"3px 0", borderBottom:`1px solid ${C.bg}` }}>
                <span style={{ fontFamily:"monospace", color: type.includes("PK")?C.accent : type.includes("FK")?C.warn : C.text }}>{name}</span>
                <span style={{ color:C.muted, fontSize:11 }}>{type}</span>
              </div>
            ))}
          </Card>
        ))}
      </div>
    </div>
  );
}

function ArchSection() {
  const svcs = [
    { n:"auth-service",       p:8081, d:"JWT, 2FA TOTP, refresh, auto-registro",    c:"#5B21B6" },
    { n:"catalog-service",    p:8082, d:"Facultades, carreras, materias, grupos (caché Redis)", c:"#065F46" },
    { n:"user-service",       p:8083, d:"Usuarios, roles, padrón CSV/Excel, SMTP",  c:"#1B3A6B" },
    { n:"work-service",       p:8084, d:"Trabajos, documentos, MinIO, Redis queue/rate", c:"#92400E" },
    { n:"plagiarism-service", p:8085, d:"Copyleaks API async, ResultadoPlagio",     c:"#991B1B" },
  ];
  return (
    <div>
      <SectionHeader title="Arquitectura de Microservicios" subtitle="Gradle multimodulo · Docker + Kubernetes · Jenkins CI/CD · Nginx Ingress" />
      <Card>
        <div style={{ display:"flex", flexDirection:"column", gap:8 }}>
          <div style={{ background:C.purpleBg, border:`2px solid ${C.purple}`, borderRadius:8, padding:"12px 20px", textAlign:"center" }}>
            <span style={{ fontWeight:700, color:C.purple }}>Cliente Angular 22 (SPA Standalone)</span>
            <span style={{ color:C.purple, marginLeft:12, fontSize:12 }}>HTTP/S + JWT Bearer</span>
          </div>
          <div style={{ textAlign:"center", color:C.muted }}>↓</div>
          <div style={{ background:C.accentLt, border:`2px solid ${C.accent}`, borderRadius:8, padding:"12px 20px", textAlign:"center" }}>
            <span style={{ fontWeight:700, color:C.accent }}>Nginx — Reverse Proxy / API Gateway</span>
            <span style={{ color:C.muted, marginLeft:12, fontSize:12 }}>Routing · CORS · Rate Limiting · Acceso externo configurable por facultad</span>
          </div>
          <div style={{ textAlign:"center", color:C.muted }}>↓</div>
          <div style={{ display:"grid", gridTemplateColumns:"repeat(5,1fr)", gap:8 }}>
            {svcs.map(s=>(
              <div key={s.n} style={{ background:"#fff", border:`2px solid ${s.c}`, borderRadius:8, padding:"10px 8px", textAlign:"center" }}>
                <div style={{ fontWeight:700, color:s.c, fontSize:11 }}>{s.n}</div>
                <div style={{ fontSize:10, color:C.muted, marginTop:3 }}>:{s.p}</div>
                <div style={{ fontSize:11, color:C.text, marginTop:5 }}>{s.d}</div>
              </div>
            ))}
          </div>
          <div style={{ textAlign:"center", color:C.muted }}>↓</div>
          <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:8 }}>
            <div style={{ background:"#F0FDF4", border:"2px solid #16A34A", borderRadius:8, padding:"12px", textAlign:"center" }}>
              <div style={{ fontWeight:700, color:"#15803D" }}>PostgreSQL 15</div>
              <div style={{ fontSize:12, color:C.muted }}>Full-text search nativo · Schemas por módulo</div>
            </div>
            <div style={{ background:"#FFF7ED", border:"2px solid #EA580C", borderRadius:8, padding:"12px", textAlign:"center" }}>
              <div style={{ fontWeight:700, color:"#C2410C" }}>MinIO (S3-compatible)</div>
              <div style={{ fontSize:12, color:C.muted }}>PDF, DOCX, ZIP, MP4, SVG, PNG...</div>
            </div>
          </div>
          <div style={{ textAlign:"center", color:C.muted }}>↓</div>
          <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:8 }}>
            <div style={{ background:"#FEF2F2", border:"2px solid #EF4444", borderRadius:8, padding:"12px", textAlign:"center" }}>
              <div style={{ fontWeight:700, color:"#B91C1C" }}>Copyleaks API (externo)</div>
              <div style={{ fontSize:12, color:C.muted }}>Detección de plagio · API key en Kubernetes Secret</div>
            </div>
            <div style={{ background:"#FFF1F2", border:"2px solid #E11D48", borderRadius:8, padding:"12px", textAlign:"center" }}>
              <div style={{ fontWeight:700, color:"#BE123C" }}>Redis 7</div>
              <div style={{ fontSize:12, color:C.muted }}>Rate limiting · Blacklist JWT · Caché catálogos · Cola uploads · Estado plagio</div>
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}

function StructSection() {
  return (
    <div>
      <SectionHeader title="Estructura del Proyecto" subtitle="Backend multimodulo Gradle + Frontend Angular Standalone" />
      <Card><h3 style={{ marginTop:0, color:C.primary, fontSize:15 }}>Backend — proyecto-ws/</h3><Code>{BACKEND_STRUCT}</Code></Card>
      <Card><h3 style={{ marginTop:0, color:C.primary, fontSize:15 }}>Frontend Angular</h3><Code>{ANGULAR_STRUCT}</Code></Card>
    </div>
  );
}

function ApiSection() {
  return (
    <div>
      <SectionHeader title="API REST" subtitle="43+ endpoints · Prefijo base /api/v1 · OpenAPI 3 por microservicio" />
      {API_GROUPS.map(g=>(
        <Card key={g.group}>
          <h3 style={{ marginTop:0, color:g.color, fontSize:15, borderBottom:`2px solid ${g.color}`, paddingBottom:8 }}>{g.group}</h3>
          {g.endpoints.map(e=>(
            <div key={e.m+e.p} style={{ display:"flex", alignItems:"center", gap:12, padding:"7px 0", borderBottom:`1px solid ${C.bg}` }}>
              <MethodBadge m={e.m} />
              <code style={{ fontSize:12, color:C.text, flex:1 }}>{e.p}</code>
              <span style={{ fontSize:12, color:C.muted, maxWidth:300, textAlign:"right" }}>{e.d}</span>
            </div>
          ))}
        </Card>
      ))}
    </div>
  );
}

function StoriesSection() {
  const [open, setOpen] = useState(null);
  return (
    <div>
      <SectionHeader title="Historias de Usuario" subtitle="Criterios de aceptación en formato Gherkin (DADO / CUANDO / ENTONCES)" />
      {USER_STORIES.map((s,i)=>(
        <Card key={s.cu} style={{ padding:0, overflow:"hidden" }}>
          <div onClick={()=>setOpen(open===i?null:i)}
            style={{ display:"flex", alignItems:"center", justifyContent:"space-between", padding:"14px 20px", cursor:"pointer", background:open===i?C.accentLt:"#fff" }}>
            <div style={{ display:"flex", alignItems:"center", gap:10 }}>
              <Badge label={s.cu} bg={C.accentLt} color={C.accent} />
              <span style={{ fontWeight:600, color:C.primary, fontSize:14 }}>{s.name}</span>
              <Badge label={`Sprint ${s.sprint}`} bg={C.bg} color={C.muted} />
            </div>
            <span style={{ color:C.muted }}>{open===i?"▲":"▼"}</span>
          </div>
          {open===i&&(
            <div style={{ padding:"16px 20px", borderTop:`1px solid ${C.border}` }}>
              <div style={{ background:C.bg, borderRadius:8, padding:"12px 16px", fontSize:13, fontStyle:"italic", color:C.text, marginBottom:14 }}>"{s.story}"</div>
              <div style={{ fontWeight:700, color:C.success, fontSize:13, marginBottom:8 }}>Criterios de Aceptación</div>
              {s.criteria.map((c,ci)=>(
                <div key={ci} style={{ display:"flex", gap:8, marginBottom:8 }}>
                  <span style={{ color:C.success, fontWeight:700, minWidth:18 }}>✓</span>
                  <span style={{ fontSize:13, color:C.text }}>{c}</span>
                </div>
              ))}
            </div>
          )}
        </Card>
      ))}
    </div>
  );
}

function SprintsSection() {
  const [open, setOpen] = useState(0);
  return (
    <div>
      <SectionHeader title="Planificación de Sprints" subtitle="5 Sprints · 3 semanas c/u · 15 semanas totales · Metodología Scrum" />
      <div style={{ display:"flex", gap:8, marginBottom:20, overflowX:"auto" }}>
        {SPRINTS.map((s,i)=>(
          <button key={s.num} onClick={()=>setOpen(i)}
            style={{ padding:"8px 18px", borderRadius:20, border:"none", cursor:"pointer", whiteSpace:"nowrap",
              background:open===i?C.primary:C.bg, color:open===i?"#fff":C.muted, fontWeight:open===i?700:400, fontSize:13 }}>
            Sprint {s.num}
          </button>
        ))}
      </div>
      {SPRINTS.map((s,i)=>open===i&&(
        <Card key={s.num}>
          <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start", marginBottom:14 }}>
            <div>
              <h3 style={{ margin:0, color:C.primary, fontSize:18 }}>Sprint {s.num}: {s.title}</h3>
              <span style={{ color:C.muted, fontSize:13 }}>{s.duration}</span>
            </div>
            <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
              {s.cu.map(c=><Badge key={c} label={c} bg={C.accentLt} color={C.accent} />)}
            </div>
          </div>
          <div style={{ background:C.accentLt, borderLeft:`4px solid ${C.accent}`, borderRadius:6, padding:"10px 16px", marginBottom:14 }}>
            <strong style={{ fontSize:13, color:C.primary }}>Objetivo: </strong>
            <span style={{ fontSize:13, color:C.text }}>{s.goal}</span>
          </div>
          {s.tasks.map((t,ti)=>(
            <div key={ti} style={{ display:"flex", gap:10, padding:"8px 0", borderBottom:`1px solid ${C.bg}`, alignItems:"flex-start" }}>
              <span style={{ color:C.accent, fontWeight:700, minWidth:22 }}>{ti+1}.</span>
              <span style={{ fontSize:13, color:C.text }}>{t}</span>
            </div>
          ))}
        </Card>
      ))}
    </div>
  );
}

function FrontendSection() {
  return (
    <div>
      <SectionHeader title="Frontend Angular 22 — Standalone" subtitle="Lazy loading por rol · Guards · 2FA · Notificaciones · Importación padrón" />
      <Card><Code>{ANGULAR_STRUCT}</Code></Card>
      <Card>
        <h3 style={{ marginTop:0, color:C.primary, fontSize:15 }}>Tabla de Rutas</h3>
        <div style={{ overflowX:"auto" }}>
          <table style={{ width:"100%", borderCollapse:"collapse", fontSize:12 }}>
            <thead>
              <tr style={{ background:C.primary, color:"#fff" }}>
                {["Ruta","Componente","Guard","Rol"].map(h=>(
                  <th key={h} style={{ padding:"9px 12px", textAlign:"left", fontWeight:600 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {ROUTES_TABLE.map((r,i)=>(
                <tr key={r.path} style={{ background:i%2===0?"#fff":C.bg }}>
                  <td style={{ padding:"7px 12px", fontFamily:"monospace", color:C.accent, fontSize:11 }}>{r.path}</td>
                  <td style={{ padding:"7px 12px", fontFamily:"monospace", fontSize:11 }}>{r.comp}</td>
                  <td style={{ padding:"7px 12px", fontSize:11, color:C.muted }}>{r.guard}</td>
                  <td style={{ padding:"7px 12px" }}><Badge label={r.rol} bg={C.accentLt} color={C.primary} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

export default function App() {
  const [active, setActive] = useState("overview");
  const render = () => {
    switch(active) {
      case "overview":     return <OverviewSection />;
      case "decisions":    return <DecisionsSection />;
      case "entities":     return <EntitiesSection />;
      case "architecture": return <ArchSection />;
      case "structure":    return <StructSection />;
      case "api":          return <ApiSection />;
      case "userstories":  return <StoriesSection />;
      case "sprints":      return <SprintsSection />;
      case "angular":      return <FrontendSection />;
      default: return null;
    }
  };
  return (
    <div style={{ display:"flex", minHeight:"100vh", background:C.bg, fontFamily:"'Inter','Segoe UI',sans-serif" }}>
      <div style={{ width:210, background:C.primary, padding:"24px 0", position:"sticky", top:0, height:"100vh", overflowY:"auto", flexShrink:0 }}>
        <div style={{ padding:"0 18px 18px", borderBottom:"1px solid rgba(255,255,255,0.1)" }}>
          <div style={{ color:"#fff", fontWeight:800, fontSize:13, lineHeight:1.3 }}>Repositorio Digital</div>
          <div style={{ color:"rgba(255,255,255,0.45)", fontSize:11, marginTop:3 }}>FICCT → UAGRM</div>
        </div>
        <nav style={{ padding:"10px 0" }}>
          {NAV.map(item=>(
            <button key={item.id} onClick={()=>setActive(item.id)}
              style={{ display:"block", width:"100%", textAlign:"left", padding:"10px 18px",
                border:"none", cursor:"pointer",
                background:active===item.id?"rgba(255,255,255,0.13)":"transparent",
                color:active===item.id?"#fff":"rgba(255,255,255,0.55)",
                fontWeight:active===item.id?700:400, fontSize:13,
                borderLeft:active===item.id?"3px solid #60A5FA":"3px solid transparent" }}>
              <span style={{ marginRight:8 }}>{item.icon}</span>{item.label}
            </button>
          ))}
        </nav>
      </div>
      <div style={{ flex:1, padding:"32px 36px", overflowY:"auto", maxWidth:980 }}>
        {render()}
      </div>
    </div>
  );
}

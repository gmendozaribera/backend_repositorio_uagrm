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
    { q:"Duración sesión JWT", a:"Configurable por administrador en ConfigFacultad. Por defecto: 1 hora (access token) + 7 días (refresh token)." },
  ]},
  { cat:"Registro de usuarios", items:[
    { q:"Auto-registro estudiantes", a:"Permitido con validación previa: el CI/registro debe existir en EstudianteHabilitado (padrón importado). Sin match → registro bloqueado." },
    { q:"Importación padrón institucional", a:"CSV/Excel manual subido por el administrador. Estructura: registro, ci, correo, password_default, nombres, apellidos, idCarrera (o sigla_carrera). El sistema valida e inserta en EstudianteHabilitado." },
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
    { q:"Estados del trabajo", a:"PENDIENTE → EN_REVISION → APROBADO | APROBADO_CON_OBSERVACIONES | RECHAZADO → EVALUADO" },
  ]},
  { cat:"Notificaciones", items:[
    { q:"Canales de notificación", a:"En-app (campana/badge en navbar) + correo SMTP opcional habilitado por el estudiante desde su perfil." },
    { q:"Configuración SMTP", a:"Configurable por administrador: host, puerto, correo remitente, contraseña, SSL/TLS. Tabla ConfigSMTP global del sistema." },
  ]},
  { cat:"Detección de plagio", items:[
    { q:"Motor de plagio", a:"Integración con API externa Copyleaks. La API key se configura como variable de entorno en Docker/Kubernetes Secrets." },
    { q:"Umbral configurable", a:"Por administrador por facultad en ConfigFacultad. Por defecto: 30% = Riesgo Medio (amarillo), >50% = Riesgo Alto (rojo), <30% = OK (verde)." },
    { q:"Fuentes de comparación", a:"Repositorio interno (siempre activo) + internet (activable por administrador por facultad en ConfigFacultad)." },
    { q:"Procesamiento", a:"Asíncrono vía Quartz Scheduler. El docente inicia el análisis; el resultado llega por notificación." },
  ]},
  { cat:"Calificación", items:[
    { q:"Escala de calificación", a:"Configurable por administrador por facultad/curso en ConfigFacultad. Por defecto: 0–100." },
  ]},
  { cat:"Roles y permisos", items:[
    { q:"Docente Administrador de Curso", a:"Designado por administrador. Puede ser Admin de N cursos simultáneamente. Solo gestiona trabajos y calificaciones dentro de esos grupos. No puede crear/eliminar estudiantes." },
    { q:"Acceso externo", a:"Configurable por facultad. Usuario anónimo: solo metadatos. Descarga de archivos: requiere cuenta registrada en el sistema." },
  ]},
  { cat:"Escalabilidad y base de datos", items:[
    { q:"Entidad Facultad", a:"Agregada desde el inicio. Carrera depende de Facultad. Cada facultad tiene su ConfigFacultad independiente. Permite que toda la UAGRM adopte el sistema." },
    { q:"Base de datos definitiva", a:"PostgreSQL 15. Full-text search con tsvector/tsquery nativo. El documento Word había referenciado MySQL — PostgreSQL es la elección definitiva." },
    { q:"GitHub/GitLab", a:"Solo guarda URL como texto. No requiere autenticación OAuth." },
  ]},
  { cat:"Redis — Caché, Rate Limiting y Colas", items:[
    { q:"¿Por qué Redis?", a:"Múltiples estudiantes suben documentos simultáneamente al cierre de semestre. Redis protege MinIO y work-service ante picos de carga sin rechazar peticiones válidas, y además resuelve invalidación de JWT, caché de catálogos y polling eficiente de plagio." },
    { q:"Rate limiting en uploads", a:"INCR rate:upload:usuario:{id} con TTL 60s. Si supera N requests/minuto → HTTP 429 Too Many Requests. Protege MinIO y work-service en picos de subida masiva sin tocar PostgreSQL." },
    { q:"Blacklist JWT (logout real)", a:"Al hacer logout o cambio de contraseña: SET blacklist:jwt:{jti} 'revoked' EX {expiracion}. Cada request valida que el jti del token no esté en blacklist. Resuelve la invalidación real de tokens antes de su expiración natural." },
    { q:"Caché de catálogos", a:"Materias, semestres, carreras, tipos de trabajo y fases cambian poco pero se consultan constantemente. Redis caché con TTL 10 min. Hit → respuesta <5ms. Miss → consulta PostgreSQL → guarda en Redis." },
    { q:"Estado del análisis de plagio", a:"SET plagio:status:{idTrabajo} 'EN_PROCESO' EX 600. Angular hace polling cada 5s contra Redis (no contra PostgreSQL). Cuando Copyleaks responde: SET plagio:status:{idTrabajo} 'COMPLETADO'. Evita N hits a BD durante el análisis." },
    { q:"Cola de uploads (buffer ante picos)", a:"En lugar de que N estudiantes golpeen MinIO directamente: LPUSH queue:uploads:{facultad} {payload}. work-service consume con BRPOP procesando de a N en paralelo. Buffer ante subidas simultáneas masivas al cierre de semestre." },
    { q:"Namespaces de keys por microservicio", a:"auth:blacklist:* · auth:2fa:pending:* · catalog:cache:* · work:rate:upload:* · work:queue:uploads:* · plagio:status:* · notif:pending:*" },
    { q:"Dependencias Spring Boot", a:"spring-boot-starter-data-redis + spring-boot-starter-cache. Un contenedor Redis más en docker-compose y Kubernetes. Sin cambios arquitecturales a los microservicios existentes." },
  ]},
];

const MERMAID_ER = `erDiagram
    FACULTAD { int idFacultad PK; varchar nombre; varchar sigla; int estado }
    CARRERA { int idCarrera PK; varchar nombre; varchar sigla; int idFacultad FK; int estado }
    ESTUDIANTE_HABILITADO { int id PK; int ci; int registro; varchar nombre; varchar apellidos; varchar siglaCarrera; boolean habilitado }
    PERSONA { int idPersona PK; varchar nombre; varchar apellidos; int ci; varchar correo; int celular; date fechaNac; int registro; int idCarrera FK }
    TIPO_USUARIO { int idTipoUsuario PK; varchar nombre; varchar descripcion; int estado }
    USUARIO { int idUsuario PK; varchar usuario; varchar password; boolean twoFactorEnabled; varchar totpSecret; int idTipoUsuario FK; int idPersona FK; int estado }
    SEMESTRE { int idSemestre PK; int semestre; int anio; int idFacultad FK }
    MATERIA { int idMateria PK; varchar nombre; varchar sigla; int idFacultad FK; int estado }
    GRUPO { int idGrupo PK; varchar nombre; int idMateria FK; int idSemestre FK; int estado }
    GRUPO_DOCENTE { int idGrupo FK; int idUsuario FK; boolean esAdminCurso }
    TIPO_TRABAJO { int idTipoTrabajo PK; varchar nombre; varchar estado; int idFacultad FK }
    FASE { int idFase PK; varchar nombre; varchar descripcion; int idFacultad FK; int estado }
    TRABAJO { int idTrabajo PK; varchar titulo; varchar resumen; int estado; int calificacion; varchar observaciones; int idPersona FK; int idMateria FK; int idTipoTrabajo FK; int idGrupo FK; int idSemestre FK }
    AUTOR { int idAutor PK; int idTrabajo FK }
    DETALLE_PERSONA_AUTOR { int idPersona FK; int idAutor FK }
    DOCUMENTO { int idDocumento PK; varchar titulo; varchar tipoArchivo; varchar rutaArchivo; varchar urlExterno; varchar descripcion; int tamanoBytes; int idTrabajo FK; int idFase FK }
    HASHTAG { int idHashtag PK; varchar texto }
    DETALLE_TRABAJO_HASHTAG { int idTrabajo FK; int idHashtag FK }
    RESULTADO_PLAGIO { int id PK; int idTrabajo FK; float porcentajeSimilitud; varchar fuenteComparacion; varchar detalle; timestamp fechaAnalisis }
    CONFIG_FACULTAD { int id PK; int idFacultad FK; int tamanoMaxArchivoMB; int umbralPlagioMedio; int umbralPlagioAlto; boolean comparacionInternet; boolean accesoExterno; int escalaCalifMax; int jwtExpiracionHoras }
    CONFIG_SMTP { int id PK; varchar host; int puerto; varchar correo; varchar password; boolean ssl; boolean activo }

    FACULTAD ||--o{ CARRERA : tiene
    FACULTAD ||--o{ SEMESTRE : define
    FACULTAD ||--o{ MATERIA : ofrece
    FACULTAD ||--o{ TIPO_TRABAJO : categoriza
    FACULTAD ||--o{ FASE : configura
    FACULTAD ||--|| CONFIG_FACULTAD : configura
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
    TRABAJO ||--o{ AUTOR : tiene
    AUTOR ||--o{ DETALLE_PERSONA_AUTOR : integra
    PERSONA ||--o{ DETALLE_PERSONA_AUTOR : participa
    TRABAJO ||--o{ DOCUMENTO : contiene
    FASE ||--o{ DOCUMENTO : clasifica
    TRABAJO ||--o{ DETALLE_TRABAJO_HASHTAG : etiqueta
    HASHTAG ||--o{ DETALLE_TRABAJO_HASHTAG : aparece
    TRABAJO ||--o{ RESULTADO_PLAGIO : analiza`;

const ENTITIES = [
  { name:"Facultad",              color:"#1B3A6B", attrs:[["idFacultad","INT PK"],["nombre","VARCHAR"],["sigla","VARCHAR"],["estado","INT"]] },
  { name:"Carrera",               color:"#1B3A6B", attrs:[["idCarrera","INT PK"],["nombre","VARCHAR"],["sigla","VARCHAR"],["idFacultad","INT FK"],["estado","INT"]] },
  { name:"EstudianteHabilitado",  color:"#065F46", attrs:[["id","INT PK"],["ci","INT"],["registro","INT"],["nombre","VARCHAR"],["apellidos","VARCHAR"],["siglaCarrera","VARCHAR"],["habilitado","BOOLEAN"]] },
  { name:"Persona",               color:"#374151", attrs:[["idPersona","INT PK"],["nombre","VARCHAR"],["apellidos","VARCHAR"],["ci","INT"],["correo","VARCHAR"],["celular","INT"],["fechaNac","DATE"],["registro","INT"],["idCarrera","INT FK"]] },
  { name:"TipoUsuario",           color:"#374151", attrs:[["idTipoUsuario","INT PK"],["nombre","VARCHAR"],["descripcion","VARCHAR"],["estado","INT"]] },
  { name:"Usuario",               color:"#374151", attrs:[["idUsuario","INT PK"],["usuario","VARCHAR"],["password","VARCHAR"],["twoFactorEnabled","BOOLEAN"],["totpSecret","VARCHAR"],["idTipoUsuario","INT FK"],["idPersona","INT FK"],["estado","INT"]] },
  { name:"Semestre",              color:"#92400E", attrs:[["idSemestre","INT PK"],["semestre","INT"],["anio","INT"],["idFacultad","INT FK"]] },
  { name:"Materia",               color:"#92400E", attrs:[["idMateria","INT PK"],["nombre","VARCHAR"],["sigla","VARCHAR"],["idFacultad","INT FK"],["estado","INT"]] },
  { name:"Grupo",                 color:"#92400E", attrs:[["idGrupo","INT PK"],["nombre","VARCHAR"],["idMateria","INT FK"],["idSemestre","INT FK"],["estado","INT"]] },
  { name:"GrupoDocente",          color:"#5B21B6", attrs:[["idGrupo","INT FK"],["idUsuario","INT FK"],["esAdminCurso","BOOLEAN"]] },
  { name:"TipoTrabajo",           color:"#065F46", attrs:[["idTipoTrabajo","INT PK"],["nombre","VARCHAR"],["estado","VARCHAR"],["idFacultad","INT FK"]] },
  { name:"Fase",                  color:"#065F46", attrs:[["idFase","INT PK"],["nombre","VARCHAR"],["descripcion","VARCHAR"],["idFacultad","INT FK"],["estado","INT"]] },
  { name:"Trabajo",               color:"#991B1B", attrs:[["idTrabajo","INT PK"],["titulo","VARCHAR"],["resumen","TEXT"],["estado","INT"],["calificacion","INT"],["observaciones","TEXT"],["idPersona","INT FK"],["idMateria","INT FK"],["idTipoTrabajo","INT FK"],["idGrupo","INT FK"],["idSemestre","INT FK"]] },
  { name:"Autor",                 color:"#991B1B", attrs:[["idAutor","INT PK"],["idTrabajo","INT FK"]] },
  { name:"DetallePersonaAutor",   color:"#991B1B", attrs:[["idPersona","INT FK"],["idAutor","INT FK"]] },
  { name:"Documento",             color:"#991B1B", attrs:[["idDocumento","INT PK"],["titulo","VARCHAR"],["tipoArchivo","VARCHAR"],["rutaArchivo","VARCHAR"],["urlExterno","VARCHAR"],["descripcion","VARCHAR"],["tamanoBytes","INT"],["idTrabajo","INT FK"],["idFase","INT FK"]] },
  { name:"Hashtag",               color:"#374151", attrs:[["idHashtag","INT PK"],["texto","VARCHAR"]] },
  { name:"DetalleTrabajoHashtag", color:"#374151", attrs:[["idTrabajo","INT FK"],["idHashtag","INT FK"]] },
  { name:"ResultadoPlagio",       color:"#B91C1C", attrs:[["id","INT PK"],["idTrabajo","INT FK"],["porcentajeSimilitud","FLOAT"],["fuenteComparacion","VARCHAR"],["detalle","TEXT"],["fechaAnalisis","TIMESTAMP"]] },
  { name:"ConfigFacultad",        color:"#0369A1", attrs:[["id","INT PK"],["idFacultad","INT FK"],["tamanoMaxArchivoMB","INT"],["umbralPlagioMedio","INT"],["umbralPlagioAlto","INT"],["comparacionInternet","BOOLEAN"],["accesoExterno","BOOLEAN"],["escalaCalifMax","INT"],["jwtExpiracionHoras","INT"]] },
  { name:"ConfigSMTP",            color:"#0369A1", attrs:[["id","INT PK"],["host","VARCHAR"],["puerto","INT"],["correo","VARCHAR"],["password","VARCHAR"],["ssl","BOOLEAN"],["activo","BOOLEAN"]] },
];

const STATES = [
  { state:"PENDIENTE",                  color:"#92400E", bg:"#FEF3C7", desc:"Trabajo creado, esperando revisión del docente." },
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
# - password_default: contraseña temporal, el estudiante debe cambiarla en primer login
# - sigla_carrera: debe coincidir con la sigla registrada en la tabla Carrera
# - El sistema valida cada fila y reporta errores sin interrumpir la importación`;

const API_GROUPS = [
  { group:"Auth", color:"#5B21B6", endpoints:[
    { m:"POST", p:"/api/v1/auth/login",           d:"Autenticar → JWT access + refresh" },
    { m:"POST", p:"/api/v1/auth/refresh",          d:"Renovar access token" },
    { m:"POST", p:"/api/v1/auth/logout",           d:"Invalidar sesión" },
    { m:"POST", p:"/api/v1/auth/register",         d:"Auto-registro estudiante (valida EstudianteHabilitado)" },
    { m:"POST", p:"/api/v1/auth/2fa/enable",       d:"Habilitar 2FA TOTP en la cuenta" },
    { m:"POST", p:"/api/v1/auth/2fa/verify",       d:"Verificar código TOTP" },
    { m:"POST", p:"/api/v1/auth/password/change",  d:"Cambiar contraseña (obligatorio en primer login con password_default)" },
  ]},
  { group:"Usuarios", color:"#1B3A6B", endpoints:[
    { m:"GET",    p:"/api/v1/usuarios",                       d:"Listar usuarios paginados con filtros" },
    { m:"GET",    p:"/api/v1/usuarios/{id}",                  d:"Obtener usuario" },
    { m:"POST",   p:"/api/v1/usuarios",                       d:"Crear usuario (Admin)" },
    { m:"PUT",    p:"/api/v1/usuarios/{id}",                  d:"Actualizar usuario" },
    { m:"DELETE", p:"/api/v1/usuarios/{id}",                  d:"Desactivar usuario" },
    { m:"GET",    p:"/api/v1/tipos-usuario",                  d:"Listar tipos de usuario" },
    { m:"POST",   p:"/api/v1/padron/import",                  d:"Importar CSV/Excel de padrón estudiantil (multipart)" },
    { m:"GET",    p:"/api/v1/padron",                         d:"Listar estudiantes habilitados" },
  ]},
  { group:"Facultades & Catálogos", color:"#065F46", endpoints:[
    { m:"GET",  p:"/api/v1/facultades",                d:"Listar facultades" },
    { m:"POST", p:"/api/v1/facultades",                d:"Crear facultad" },
    { m:"PUT",  p:"/api/v1/facultades/{id}",           d:"Actualizar facultad" },
    { m:"GET",  p:"/api/v1/carreras",                  d:"Listar carreras (filtro: idFacultad)" },
    { m:"POST", p:"/api/v1/carreras",                  d:"Crear carrera" },
    { m:"GET",  p:"/api/v1/materias",                  d:"Listar materias (filtro: idFacultad)" },
    { m:"POST", p:"/api/v1/materias",                  d:"Crear materia" },
    { m:"GET",  p:"/api/v1/semestres",                 d:"Listar semestres" },
    { m:"POST", p:"/api/v1/semestres",                 d:"Crear semestre" },
    { m:"GET",  p:"/api/v1/grupos",                    d:"Listar grupos (filtro: idMateria, idSemestre)" },
    { m:"POST", p:"/api/v1/grupos",                    d:"Crear grupo" },
    { m:"POST", p:"/api/v1/grupos/{id}/docentes",      d:"Asignar docente a grupo (con flag esAdminCurso)" },
    { m:"GET",  p:"/api/v1/fases",                     d:"Listar fases" },
    { m:"POST", p:"/api/v1/fases",                     d:"Crear fase" },
    { m:"GET",  p:"/api/v1/tipos-trabajo",             d:"Listar tipos de trabajo" },
  ]},
  { group:"Trabajos", color:"#991B1B", endpoints:[
    { m:"GET",    p:"/api/v1/trabajos",                  d:"Listar trabajos (filtros: facultad, carrera, materia, semestre, tipo, hashtag, estado)" },
    { m:"GET",    p:"/api/v1/trabajos/{id}",             d:"Obtener trabajo completo" },
    { m:"POST",   p:"/api/v1/trabajos",                  d:"Crear trabajo" },
    { m:"PUT",    p:"/api/v1/trabajos/{id}",             d:"Actualizar trabajo" },
    { m:"DELETE", p:"/api/v1/trabajos/{id}",             d:"Eliminar trabajo" },
    { m:"PATCH",  p:"/api/v1/trabajos/{id}/aprobar",     d:"Aprobar trabajo (observaciones opcionales)" },
    { m:"PATCH",  p:"/api/v1/trabajos/{id}/rechazar",    d:"Rechazar trabajo con motivo" },
    { m:"PATCH",  p:"/api/v1/trabajos/{id}/calificar",   d:"Calificar (rango desde ConfigFacultad)" },
    { m:"GET",    p:"/api/v1/trabajos/search",           d:"Búsqueda full-text (PostgreSQL tsvector)" },
    { m:"GET",    p:"/api/v1/trabajos/public",           d:"Trabajos públicos (anónimo, solo EVALUADOS)" },
  ]},
  { group:"Documentos", color:"#92400E", endpoints:[
    { m:"GET",    p:"/api/v1/trabajos/{id}/documentos",  d:"Listar documentos del trabajo" },
    { m:"POST",   p:"/api/v1/trabajos/{id}/documentos",  d:"Subir documento (multipart/form-data)" },
    { m:"DELETE", p:"/api/v1/documentos/{id}",           d:"Eliminar documento" },
    { m:"GET",    p:"/api/v1/documentos/{id}/download",  d:"Descargar (requiere cuenta si acceso externo)" },
  ]},
  { group:"Plagio (Copyleaks)", color:"#B91C1C", endpoints:[
    { m:"POST", p:"/api/v1/trabajos/{id}/plagio",        d:"Iniciar análisis de plagio (async Quartz + Copyleaks API)" },
    { m:"GET",  p:"/api/v1/trabajos/{id}/plagio",        d:"Obtener último resultado de análisis" },
  ]},
  { group:"Configuración", color:"#0369A1", endpoints:[
    { m:"GET", p:"/api/v1/facultades/{id}/config",       d:"Obtener configuración de la facultad" },
    { m:"PUT", p:"/api/v1/facultades/{id}/config",       d:"Actualizar config (umbral plagio, escala calif, JWT, tamaño archivo, acceso externo, SMTP)" },
    { m:"GET", p:"/api/v1/config/smtp",                  d:"Obtener configuración SMTP global" },
    { m:"PUT", p:"/api/v1/config/smtp",                  d:"Actualizar SMTP (host, puerto, correo, password, SSL)" },
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
      "DADO que el estudiante ingresa CI y registro válidos, CUANDO el sistema valida contra EstudianteHabilitado, ENTONCES permite completar el registro.",
      "DADO que el CI/registro no existe en EstudianteHabilitado, CUANDO intenta registrarse, ENTONCES ve: 'No encontrado en el padrón institucional. Contacte a su facultad.'",
      "DADO que ya tiene cuenta, CUANDO intenta registrarse de nuevo con el mismo CI, ENTONCES el sistema informa duplicado y sugiere recuperar contraseña.",
      "DADO que es primer login con password_default, CUANDO accede, ENTONCES el sistema obliga al cambio de contraseña antes de continuar.",
    ]},
  { cu:"CU-IMP", sprint:2, name:"Importar Padrón Estudiantil",
    story:"Como administrador, quiero importar el padrón de estudiantes desde un CSV/Excel para habilitar su auto-registro.",
    criteria:[
      "DADO CSV válido con columnas (registro, ci, correo, password_default, nombres, apellidos, sigla_carrera), CUANDO se importa, ENTONCES el sistema inserta/actualiza en EstudianteHabilitado y muestra resumen (insertados, actualizados, errores).",
      "DADO una fila con datos inválidos (CI no numérico, carrera inexistente), CUANDO se procesa, ENTONCES esa fila va al log de errores sin interrumpir la importación.",
      "DADO archivo con formato incorrecto (.pdf), CUANDO se intenta subir, ENTONCES el sistema rechaza con formatos válidos (CSV, XLSX, XLS).",
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

const SPRINTS = [
  { num:1, title:"Infraestructura + Auth + 2FA", duration:"3 semanas",
    goal:"Sistema base funcional con autenticación JWT, 2FA opcional, estructura multimodulo Gradle configurada y entorno Docker levantado.",
    tasks:[
      "Configurar proyecto multimodulo Gradle: dao / dto / imp / spec / mockup / ws/",
      "Configurar PostgreSQL 15 + Flyway para migraciones de esquema",
      "Entidades: Facultad, Carrera, Persona, TipoUsuario, Usuario, ConfigFacultad, ConfigSMTP",
      "Spring Security + JWT (access 1h / refresh 7d, configurable por ConfigFacultad)",
      "2FA con TOTP (Google Authenticator) — opcional por usuario",
      "Flujo cambio de contraseña obligatorio en primer login (password_default)",
      "Angular: proyecto standalone, routing, AuthGuard, RoleGuard, JwtInterceptor",
      "Módulo Auth Angular: login + pantalla verificación 2FA + cambio contraseña",
      "docker-compose dev: PostgreSQL + MinIO + Redis + servicios",
      "Configurar Redis: namespaces por microservicio, TTLs base, cliente Spring Data Redis",
      "Implementar blacklist JWT en Redis (logout real + cambio de contraseña)",
      "Pipeline Jenkins básico: build + test",
    ], cu:["CU1"]},
  { num:2, title:"Usuarios + Padrón CSV + Catálogos", duration:"3 semanas",
    goal:"Auto-registro validado contra padrón importado, CRUD de usuarios, catálogos completos por facultad y configuración SMTP.",
    tasks:[
      "ws/user-service: CRUD Persona, Usuario, TipoUsuario",
      "EstudianteHabilitado: tabla + endpoint importación CSV/Excel (Apache POI)",
      "Validación de estructura CSV + reporte de errores por fila",
      "Flujo auto-registro: validación CI/registro → creación de cuenta",
      "ws/catalog-service: Facultad, Carrera, Materia, Semestre, Grupo, GrupoDocente, Fase, TipoTrabajo",
      "Endpoint asignación docente a grupo con flag esAdminCurso",
      "ConfigFacultad: GET/PUT + pantalla de configuración en Admin Angular",
      "ConfigSMTP: GET/PUT + pantalla de configuración + botón 'Enviar correo de prueba'",
      "Notificaciones in-app: sistema de campana + badge con polling",
      "Notificaciones por correo SMTP: integración Spring Mail (opcional por usuario)",
      "Módulo Admin Angular: usuarios, importación padrón, catálogos",
      "Quartz job: limpieza de tokens JWT expirados",
      "Caché Redis para catálogos: materias, semestres, carreras, fases (TTL 10 min, invalidación al actualizar)",
      "Pruebas unitarias JUnit 5 + Mockito: servicios usuario y catálogo",
    ], cu:["CU-REG","CU-IMP","CU-CFG"]},
  { num:3, title:"Repositorio de Trabajos", duration:"3 semanas",
    goal:"Flujo completo de creación → revisión → aprobación con múltiples documentos y todos los formatos permitidos.",
    tasks:[
      "ws/work-service: Trabajo, Autor, DetallePersonaAutor, Documento, Hashtag, DetalleTrabajoHashtag",
      "Validación de formatos y tamaño de archivo según ConfigFacultad",
      "MinIO: almacenamiento de documentos (PDF, DOCX, ZIP, MP4, SVG, etc.)",
      "Soporte URL externa para videos (YouTube, Vimeo, Drive) y código (GitHub, GitLab)",
      "Máquina de estados: PENDIENTE → EN_REVISION → APROBADO / APROBADO_CON_OBSERVACIONES / RECHAZADO → EVALUADO",
      "API REST: /trabajos (CRUD + PATCH aprobar/rechazar/calificar), /documentos",
      "Módulo Angular Estudiante: crear trabajo, carga múltiple de documentos por fase",
      "Módulo Angular Docente: bandeja de revisión, aprobar/rechazar/observaciones",
      "Módulo Angular Repositorio: búsqueda pública (solo EVALUADOS), detalle de trabajo",
      "Regla visibilidad: trabajos no EVALUADOS ocultos en búsqueda pública",
      "Rate limiting Redis en uploads: INCR rate:upload:usuario:{id} EX 60 → HTTP 429 si supera límite configurable",
      "Cola Redis de uploads: LPUSH queue:uploads:{facultad} → work-service consume con BRPOP (buffer ante picos de cierre de semestre)",
      "Notificaciones al cambiar estado (in-app + correo si habilitado por el usuario)",
    ], cu:["CU6","CU7","CU8","CU9","CU10","CU11","CU13","CU16"]},
  { num:4, title:"Búsqueda Full-text + Plagio + Evaluación", duration:"3 semanas",
    goal:"Motor de búsqueda con filtros por facultad, integración Copyleaks async y evaluación con calificación configurable.",
    tasks:[
      "Full-text search PostgreSQL (tsvector/tsquery) en título, resumen, hashtags",
      "Filtros: facultad, carrera, materia, semestre, grupo, tipo, hashtag, estado",
      "ws/plagiarism-service: integración con API Copyleaks (API key en Kubernetes Secret)",
      "Análisis asíncrono vía Quartz: job encola trabajos → llama Copyleaks → guarda ResultadoPlagio",
      "Semáforo visual: verde/amarillo/rojo según umbrales de ConfigFacultad",
      "Comparación configurable: repositorio interno + internet (campo en ConfigFacultad)",
      "API REST: PATCH /trabajos/{id}/calificar (rango desde ConfigFacultad)",
      "Módulo Angular Docente: panel plagio + resultado visual + evaluación + calificación",
      "Acceso externo: lógica anónimo (solo metadatos) vs. autenticado (descarga)",
      "Estado del análisis en Redis: SET plagio:status:{idTrabajo} 'EN_PROCESO' EX 600 → Angular polling cada 5s sin hit a BD",
      "Notificación al docente cuando el análisis de plagio finaliza",
    ], cu:["CU14","CU18","CU11"]},
  { num:5, title:"Pruebas, Hardening y Despliegue K8s", duration:"3 semanas",
    goal:"Sistema probado, documentado, desplegado en Kubernetes con pipeline CI/CD completo.",
    tasks:[
      "Pruebas integración: Spring Boot Test + Testcontainers (PostgreSQL, MinIO, Redis)",
      "Pruebas E2E Angular con Cypress: flujos login, crear trabajo, aprobar, buscar",
      "Hardening Spring Security: CORS, CSRF, rate limiting, headers HTTP (HSTS, CSP)",
      "Manifiestos K8s: Deployments, Services, Ingress, ConfigMaps, Secrets por microservicio",
      "Jenkinsfile: build → test → docker build → push → kubectl apply",
      "Documentación OpenAPI 3 / Swagger UI por microservicio",
      "Seed de datos FICCT para demo: facultad, carreras, materias, usuarios de prueba",
      "Revisión final de criterios de aceptación de todos los CU",
    ], cu:[]},
];

const BACKEND_STRUCT = `proyecto-ws/
├── source/
│   ├── build.gradle (padre — BOM Spring Boot 3)
│   ├── dao/          # @Entity JPA, JpaRepository, Specifications (filtros dinámicos)
│   ├── dto/          # Request/Response DTOs, MapStruct mappers
│   ├── imp/          # Services, Validators, Quartz Jobs (plagio async, JWT cleanup), Redis Services
│   ├── spec/         # openapi/ (auth-api.yaml, work-api.yaml, plagiarism-api.yaml...)
│   ├── mockup/       # Mocks para testing
│   └── ws/
│       ├── auth-service/        # :8081 — JWT, 2FA TOTP, refresh, logout, auto-registro
│       ├── user-service/        # :8082 — Usuarios, roles, importación CSV padrón
│       ├── catalog-service/     # :8083 — Facultades, carreras, materias, grupos, fases
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
    ├── build.sh
    └── deploy.sh`;

const ANGULAR_STRUCT = `repositorio-digital/
├── src/app/
│   ├── core/
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
│   │   └── models/                    # Interfaces TypeScript (DTOs)
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
│   │   │   ├── importar-padron/       # CU-IMP: upload CSV/Excel + vista previa
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
  { path:"/register",                 comp:"RegisterComponent",        guard:"—",                                rol:"Público" },
  { path:"/2fa",                      comp:"TwoFactorComponent",       guard:"—",                                rol:"Público" },
  { path:"/change-password",          comp:"ChangePasswordComponent",  guard:"AuthGuard",                        rol:"Todos" },
  { path:"/admin/usuarios",           comp:"UsuariosListComponent",    guard:"AuthGuard+RoleGuard('ADMIN')",      rol:"Admin" },
  { path:"/admin/importar-padron",    comp:"ImportarPadronComponent",  guard:"AuthGuard+RoleGuard('ADMIN')",      rol:"Admin" },
  { path:"/admin/facultades",         comp:"FacultadesComponent",      guard:"AuthGuard+RoleGuard('ADMIN')",      rol:"Admin" },
  { path:"/admin/catalogos",          comp:"CatalogosComponent",       guard:"AuthGuard+RoleGuard('ADMIN')",      rol:"Admin" },
  { path:"/admin/configuracion/:id",  comp:"ConfigFacultadComponent",  guard:"AuthGuard+RoleGuard('ADMIN')",      rol:"Admin" },
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
    { cat:"Backend", items:["Java 17 + Spring Boot 3","Spring Security + JWT + TOTP 2FA","Spring Data JPA + Flyway","PostgreSQL 15 (full-text nativo)","Redis 7 (caché, rate limiting, blacklist JWT, colas, estado plagio)","Quartz Scheduler (plagio async)","Gradle multimodulo","MapStruct + Bean Validation","Apache POI (import CSV/Excel)","Spring Mail (SMTP configurable)"] },
    { cat:"Almacenamiento", items:["MinIO (S3-compatible)","Redis 7 (caché L2 + colas de uploads)","URL externa: YouTube, Vimeo, Drive, GitHub, GitLab"] },
    { cat:"Frontend", items:["Angular 17 Standalone","TypeScript 5","Angular Material / PrimeNG","RxJS + Signals (Angular 17)","Jasmine + Karma","Cypress (E2E)"] },
    { cat:"Infraestructura", items:["Docker + Docker Compose","Kubernetes + Nginx Ingress","Jenkins CI/CD","OpenAPI 3 / Swagger UI","Copyleaks API (detección plagio)","Redis 7 (contenedor adicional en docker-compose y K8s)"] },
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
          { key:"Rate Limiting uploads",       ns:"work:rate:upload:{usuarioId}",    desc:"INCR + TTL 60s. HTTP 429 si supera límite. Protege MinIO en picos de cierre de semestre." },
          { key:"Blacklist JWT",               ns:"auth:blacklist:{jti}",            desc:"SET EX {expiracion}. Logout real e invalidación inmediata al cambiar contraseña." },
          { key:"Caché catálogos",             ns:"catalog:cache:{entidad}:{id}",   desc:"TTL 10 min. Materias, semestres, carreras. Hit → <5ms. Reduce carga PostgreSQL." },
          { key:"Cola de uploads",             ns:"work:queue:uploads:{facultad}",   desc:"LPUSH / BRPOP. Buffer ante subidas masivas simultáneas. N workers en paralelo." },
          { key:"Estado análisis plagio",      ns:"plagio:status:{idTrabajo}",       desc:"SET EX 600. Angular polling cada 5s sin hit a BD durante el análisis Copyleaks." },
          { key:"Sesión 2FA pendiente",        ns:"auth:2fa:pending:{usuarioId}",    desc:"SET EX 300. Guarda TOTP temporal mientras el usuario completa la verificación." },
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
    { n:"user-service",       p:8082, d:"Usuarios, roles, padrón CSV/Excel, SMTP",  c:"#1B3A6B" },
    { n:"catalog-service",    p:8083, d:"Facultades, carreras, materias, grupos",   c:"#065F46" },
    { n:"work-service",       p:8084, d:"Trabajos, documentos, MinIO, Redis queue/rate", c:"#92400E" },
    { n:"plagiarism-service", p:8085, d:"Copyleaks API async, ResultadoPlagio",     c:"#991B1B" },
  ];
  return (
    <div>
      <SectionHeader title="Arquitectura de Microservicios" subtitle="Gradle multimodulo · Docker + Kubernetes · Jenkins CI/CD · Nginx Ingress" />
      <Card>
        <div style={{ display:"flex", flexDirection:"column", gap:8 }}>
          <div style={{ background:C.purpleBg, border:`2px solid ${C.purple}`, borderRadius:8, padding:"12px 20px", textAlign:"center" }}>
            <span style={{ fontWeight:700, color:C.purple }}>Cliente Angular 17 (SPA Standalone)</span>
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
            <div key={e.p} style={{ display:"flex", alignItems:"center", gap:12, padding:"7px 0", borderBottom:`1px solid ${C.bg}` }}>
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
      <SectionHeader title="Frontend Angular 17 — Standalone" subtitle="Lazy loading por rol · Guards · 2FA · Notificaciones · Importación padrón" />
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

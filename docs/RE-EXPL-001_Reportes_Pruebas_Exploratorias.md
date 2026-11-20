# Reporte de anomalías de pruebas exploratorias (RE-EXPL-001)

| Campo | Valor |
| --- | --- |
| Proyecto | Migrador ClickUp / Jira |
| Norma de clasificación | IEEE 1044 |
| Fecha | 19 de noviembre de 2026 |
| Entorno | Spring Boot local, H2 en memoria, MockMvc y MockWebServer; JMeter y ZAP apuntando solo a `127.0.0.1` |
| Build | Rama `exploratory_tests`; Java 22.0.2; Spring Boot 4.1.1 |
| Estado | Hallazgos reproducidos; no se modificó código de producción |
| Alcance | Hallazgos observados mediante pruebas. No incluye conclusiones basadas solo en lectura de código. |

## 1. Resumen de ejecución

| Actividad | Ejecución | Resultado |
| --- | --- | --- |
| Anomalías reproducidas | Sesiones exploratorias y herramientas pasivas | 17 reportes: 9 anteriores y 8 hallazgos nuevos (EXP-010 a EXP-017). |
| Resiliencia de API | `ExploratoryApiResilienceTest` | 14 pruebas, 0 fallos |
| Seguridad web | `ExploratoryWebSecurityTest` | 9 pruebas, 0 fallos |
| Adaptabilidad del registro | `ExploratoryAdaptabilityTest` | 1 prueba exploratoria; incorpora un conector simulado sin cambios en los componentes centrales. |
| Desempeño del lote | `LoteReferenciaMedicionTest` | Cinco ejecuciones del lote de 100 elementos con APIs simuladas; detalle de métricas en RE-EXPL-002. |
| Regresión de integración | `MigrationPipelineTest` | 4 pruebas, 0 fallos |
| Timeout Jira existente | `JiraSessionTest#tiempoDeEsperaAgotadoSeControla` | Reproducido: la excepción esperada no se lanzó con una respuesta demorada 2 s. Ya estaba registrado como AR-16. |
| Suite completa | `mvn test` | 155 pruebas, 138 aprobadas, 17 fallidas y 0 errores. Los 17 fallos coinciden con los previamente abiertos en AR-001; no aparecieron fallos adicionales. |
| Carga | JMeter 5.6.3, ocho perfiles locales | 10 610 muestras, 0 fallidas. Perfiles previos: 610 muestras. Pruebas de concurrencia: 25 usuarios × 20 iteraciones, 1 000 muestras (promedio 4,15 ms; p95 7 ms; máximo 334 ms); 50 × 20, 2 000 (1,94 / 3 / 43 ms); 100 × 10, 2 000 (1,78 / 3 / 41 ms); 250 × 10, 5 000 (1,63 / 3 / 42 ms). En todos hubo `302` al iniciar sesión y `200` al consultar servidores. No se observó fallo hasta 250 usuarios concurrentes en este escenario. |
| Escaneo pasivo | OWASP ZAP 2.17.0, spider y passive scanner; sin active scan | 8 URLs encontradas y 4 alertas pasivas en la instancia local. |

JMeter se instaló en el perfil local tras verificar el SHA-512 del archivo oficial. ZAP Core se obtuvo de la publicación oficial y se verificó su SHA-256. Los resultados detallados del escaneo quedan en `target/exploratory-zap/` (directorio de build ignorado por Git); el plan reproducible está en `tools/exploratory-zap-passive.yaml`. La carga está descrita en `tools/exploratory-load.jmx`.

## 2. Clasificación y prioridad

Se usa la escala de severidad del registro AR-001: **Critical**, **Major** y **Minor**. La severidad representa el impacto observado; la prioridad indica urgencia de corrección. Los hallazgos de cabeceras describen controles ausentes observados en respuestas HTTP, no una explotación demostrada.

| # | ID | Severidad | Prioridad | Tipo / origen |
|---|---|---|---|---|
| 1 | EXP-001 | Critical | Alta | Autenticación / inicialización de usuario administrador |
| 2 | EXP-002 | Critical | Alta | Exposición de credenciales / configuración de conectores |
| 3 | EXP-003 | Critical | Alta | Integridad e idempotencia / alta de ítems |
| 4 | EXP-004 | Critical | Alta | Disponibilidad / timeout de lectura Jira; revalidación de AR-16 |
| 5 | EXP-005 | Major | Alta | Protección de credenciales / registro y autenticación |
| 6 | EXP-006 | Major | Alta | Protección de solicitudes / formularios y operaciones con sesión |
| 7 | EXP-007 | Major | Media | Cabecera HTTP de seguridad / respuesta de la aplicación |
| 8 | EXP-008 | Major | Media | Protección contra framing / respuesta de login |
| 9 | EXP-009 | Minor | Baja | Interpretación MIME / respuesta de recurso estático |
| 10 | EXP-010 | Major | Alta | Integridad de datos / se selecciona la misma configuración como origen y destino |
| 11 | EXP-011 | Major | Alta | Integridad de migración / respuesta HTTP 200 con esquema de error se interpreta como lote vacío |
| 12 | EXP-012 | Major | Alta | Consistencia destino/base local / falta el identificador en una respuesta HTTP 201 |
| 13 | EXP-013 | Major | Alta | Consistencia / migraciones concurrentes crean dos recursos remotos para el mismo origen |
| 14 | EXP-014 | Major | Alta | Disponibilidad / cursor de paginación repetido no se detecta |
| 15 | EXP-015 | Critical | Alta | Autorización / una cuenta recién registrada puede borrar configuraciones compartidas |
| 16 | EXP-016 | Major | Media | Sesión / el identificador no rota al autenticarse |
| 17 | EXP-017 | Major | Alta | Navegador / un URL `javascript:` persistido aparece como enlace activo en reportes |

## 3. Reportes de anomalía

### EXP-001 — Credenciales administrativas predeterminadas permiten iniciar sesión

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — autenticación insuficiente |
| Severidad / prioridad | Critical / Alta |
| Origen | Inicialización de usuario y autenticación (`MigradorApplication.seedAdmin`, `AuthController.login`) |
| Contexto | Base de datos de prueba vacía; petición HTTP `POST /login` |
| Pasos de reproducción | 1. Iniciar la aplicación con una base vacía. 2. Enviar `username=admin` y `password=admin` a `/login`. 3. Observar la respuesta y la sesión. |
| Resultado esperado | No debe existir una cuenta administrativa con una contraseña pública y fija. |
| Resultado obtenido | El login redirige a `/` y crea una sesión autenticada. |
| Evidencia | `ExploratoryWebSecurityTest.credencialesAdministrativasPredeterminadasInicianSesion`; `MigradorApplication.java:17-25`; `AuthController.java:31-39`. |
| Estado | Reproducido; abierto |

### EXP-002 — Jira envía el token guardado a cualquier URL aceptada por el formulario

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — divulgación de credenciales a destino no validado |
| Severidad / prioridad | Critical / Alta |
| Origen | Persistencia de URL de servidor y encabezados de autenticación (`ServerController.save`, `JiraSession`) |
| Contexto | Destino de prueba controlado por `MockWebServer` en loopback; se usa un token ficticio |
| Pasos de reproducción | 1. Enviar una configuración Jira a `POST /servers` con una URL que apunte al servidor local de prueba y credenciales ficticias. 2. Cargar la configuración guardada y conectar el conector Jira. 3. Consultar la solicitud recibida por el servidor local. |
| Resultado esperado | Rechazar destinos no autorizados y no enviar credenciales a una URL arbitraria. |
| Resultado obtenido | El servidor local recibe `Authorization: Basic ...` con el usuario y token ficticios. El mismo flujo podría revelar credenciales reales si se configura un host no confiable. |
| Evidencia | `ExploratoryWebSecurityTest.conectorJiraEnviaCredencialFicticiaAUrlConfiguradaPorFormulario`; `ServerController.java:49-59`; `JiraSession.java:28-39`. No se contactó ningún host externo. |
| Estado | Reproducido; abierto |

### EXP-003 — Un reintento puede crear un duplicado tras perderse la respuesta del alta

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Integridad de datos — operación no idempotente |
| Severidad / prioridad | Critical / Alta |
| Origen | Sincronización después de `ConnectorSession.create` (`ItemCreator.sync`) |
| Contexto | ClickUp y Jira simulados; el doble de Jira modela que el primer `POST` se aplica remotamente, pero la conexión se cierra antes de entregar la respuesta |
| Pasos de reproducción | 1. Ejecutar una migración con un ítem. 2. Simular que Jira aplica el primer alta y corta la conexión antes de responder. 3. Verificar que la corrida no registra equivalencia. 4. Repetir la misma migración con el mismo ítem. |
| Resultado esperado | La recuperación debería identificar o reconciliar el alta previa para no crear otra copia. |
| Resultado obtenido | El primer intento cuenta un error y no guarda mapping; el reintento envía otro alta. El doble registra dos altas remotas para el mismo ítem de origen. |
| Evidencia | `ExploratoryApiResilienceTest.reintentoTrasAltaAplicadaPeroRespuestaPerdidaPuedeDuplicarElItem`; `ItemCreator.java:35-49`. La aplicación remota se simula; no se hizo una llamada real a Jira. |
| Estado | Reproducido en escenario simulado; abierto |

### EXP-004 — La respuesta Jira demorada no activa el timeout esperado (AR-16)

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Fiabilidad — manejo de tiempo de espera |
| Severidad / prioridad | Critical / Alta |
| Origen | Cliente REST de Jira (`JiraSession`) |
| Contexto | MockWebServer demora 2 segundos los encabezados de una búsqueda y luego devuelve JSON válido |
| Pasos de reproducción | 1. Ejecutar `JiraSessionTest#tiempoDeEsperaAgotadoSeControla`. 2. Esperar la respuesta demorada. 3. Comprobar si se lanza `ResourceAccessException`. |
| Resultado esperado | El oráculo existente espera que el conector interrumpa la solicitud por timeout y señale el fallo. |
| Resultado obtenido | No se lanza `ResourceAccessException`; la respuesta se procesa después de la demora. |
| Evidencia | Falla reproducible en `JiraSessionTest.java:309-326`; reporte previo AR-16. |
| Estado | Reproducido nuevamente; ya abierto en AR-001 |

### EXP-005 — El registro persiste la contraseña exactamente como se envía

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — protección inadecuada de datos de autenticación |
| Severidad / prioridad | Major / Alta |
| Origen | Registro y persistencia de usuarios (`AuthController.register`, entidad `User`) |
| Contexto | Base H2 aislada; contraseña inventada exclusivamente para la prueba |
| Pasos de reproducción | 1. Enviar una contraseña ficticia a `POST /register`. 2. Consultar el usuario recién persistido en la base de prueba. |
| Resultado esperado | La contraseña persistida debe ser una derivación hash adaptativa, no el texto original. |
| Resultado obtenido | El valor leído de la entidad coincide carácter por carácter con el valor enviado. |
| Evidencia | `ExploratoryWebSecurityTest.registroPersisteLaContrasenaSinTransformarla`; `AuthController.java:51-63`; `User.java:23-35`. |
| Estado | Reproducido; abierto |

### EXP-006 — Una operación autenticada acepta un origen HTTP no confiable

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — protección CSRF no demostrada |
| Severidad / prioridad | Major / Alta |
| Origen | Manejo de formularios con sesión (`WebConfig`, `ServerController.save`) |
| Contexto | Petición de prueba autenticada con `Origin: https://attacker.invalid`; no se usa un navegador ni un destino externo |
| Pasos de reproducción | 1. Enviar `POST /servers` con una sesión autenticada y un encabezado `Origin` de otro sitio. 2. Verificar el estado HTTP y consultar si el cambio quedó guardado. |
| Resultado esperado | La aplicación debería rechazar solicitudes de cambio de origen no confiable o exigir una protección anti-CSRF. |
| Resultado obtenido | La aplicación responde con redirección de éxito y persiste la configuración; ZAP también detectó ausencia de token en el formulario de login. |
| Evidencia | `ExploratoryWebSecurityTest.postDeCambioAceptaOrigenNoConfiableConSesionAutenticada`; alerta pasiva ZAP `Absence of Anti-CSRF Tokens`. |
| Estado | Comportamiento del endpoint reproducido; confirmar explotación en navegador queda pendiente, pues esta prueba no modela las políticas de envío de cookies del navegador. |

### EXP-007 — La respuesta no incluye Content-Security-Policy

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — cabecera defensiva ausente |
| Severidad / prioridad | Major / Media |
| Origen | Respuestas HTTP de la aplicación |
| Contexto | Navegación local rastreada por ZAP; `GET /` |
| Pasos de reproducción | 1. Iniciar la aplicación de prueba. 2. Consultar `GET /`. 3. Revisar la respuesta HTTP y el reporte pasivo ZAP. |
| Resultado esperado | Definir y devolver una CSP apropiada para las vistas servidas. |
| Resultado obtenido | La respuesta observada no contiene `Content-Security-Policy`. |
| Evidencia | Alerta ZAP `Content Security Policy (CSP) Header Not Set`, riesgo Medium, confianza High; `target/exploratory-zap/exploratory-passive.html`. |
| Estado | Ausencia de cabecera reproducida; abierto |

### EXP-008 — La respuesta de login carece de protección anti-clickjacking

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — cabecera defensiva ausente |
| Severidad / prioridad | Major / Media |
| Origen | Respuesta del formulario de login |
| Contexto | `POST /login` observado durante el rastreo pasivo local |
| Pasos de reproducción | 1. Consultar el formulario/login en la instancia local. 2. Revisar `X-Frame-Options` y `Content-Security-Policy: frame-ancestors` en la respuesta. |
| Resultado esperado | Impedir el framing no autorizado de páginas sensibles. |
| Resultado obtenido | ZAP no observó ninguna de las dos protecciones en la respuesta analizada. |
| Evidencia | Alerta pasiva `Missing Anti-clickjacking Header`, riesgo Medium, confianza Medium; `target/exploratory-zap/exploratory-passive.html`. |
| Estado | Ausencia observada; abierto |

### EXP-009 — El recurso CSS no devuelve `X-Content-Type-Options: nosniff`

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — cabecera defensiva ausente |
| Severidad / prioridad | Minor / Baja |
| Origen | Respuesta de recursos estáticos |
| Contexto | `GET /css/app.css` en la instancia local |
| Pasos de reproducción | 1. Solicitar el CSS local. 2. Revisar la respuesta por `X-Content-Type-Options`. |
| Resultado esperado | Devolver `X-Content-Type-Options: nosniff`. |
| Resultado obtenido | La cabecera no está presente en la respuesta observada. |
| Evidencia | Alerta pasiva ZAP `X-Content-Type-Options Header Missing`, riesgo Low, confianza Medium; `target/exploratory-zap/exploratory-passive.html`. |
| Estado | Ausencia observada; abierto |

### EXP-010 — Dos extremos apuntando al mismo servidor pueden duplicar datos en el origen

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Integridad de datos — configuración de migración inválida no rechazada |
| Severidad / prioridad | Major / Alta |
| Origen | Orquestación de migración (`MigrationService`) y formulario de selección |
| Contexto | Se probó tanto una configuración ClickUp idéntica en ambos extremos como dos configuraciones distintas con la misma URL y lista; API simulada local. |
| Pasos de reproducción | 1. Guardar dos configuraciones ClickUp con la misma URL. 2. Usar la misma lista como origen y destino. 3. Ejecutar la migración y observar el `POST /list/{id}/task` recibido por la misma API. |
| Resultado esperado | Rechazar extremos que resuelven al mismo servidor y tablero, aunque tengan IDs de configuración distintos. |
| Resultado obtenido | La migración crea una copia dentro de la lista de origen; el flujo acepta tanto IDs idénticos como configuraciones duplicadas que apuntan al mismo backend. |
| Evidencia | `ExploratoryApiResilienceTest.migrarAlMismoServidorCreaUnaCopiaEnElOrigen` y `configuracionesDuplicadasDelMismoServidorPuedenDuplicarItemsEnElMismoTablero`; solo MockWebServer. |
| Estado | Reproducido; abierto |

### EXP-011 — Una respuesta HTTP 200 con esquema de error se anuncia como migración vacía exitosa

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Integridad / completitud — respuesta API malformada aceptada como extracción vacía |
| Severidad / prioridad | Major / Alta |
| Origen | Interpretación de respuesta ClickUp (`ClickUpSession.extractAll`, `Json.list`) |
| Contexto | API simulada responde `200` con `{"error":"upstream temporarily unavailable"}` en vez del objeto que contiene `tasks`. |
| Pasos de reproducción | 1. Ejecutar una migración de ClickUp a Jira. 2. Hacer que la extracción ClickUp responda con HTTP 200 y un objeto de error sin `tasks`. 3. Revisar el resultado y las solicitudes al destino. |
| Resultado esperado | Detectar que la respuesta incumple el esquema de extracción y reportar la migración como fallida, no como lote vacío. |
| Resultado obtenido | La migración se informa como completada con 0 creados y 0 actualizados; no se contacta Jira. |
| Evidencia | `ExploratoryApiResilienceTest.migrateRespuestaDeErrorConHttp200SeReportaComoMigracionVaciaExitosa`; MockWebServer local. |
| Estado | Reproducido; abierto |

### EXP-012 — Una creación aceptada sin identificador de respuesta deja un ítem sin equivalencia

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Integridad de datos — operación remota completada sin registro local reconciliable |
| Severidad / prioridad | Major / Alta |
| Origen | Creación en Jira y persistencia de equivalencia (`JiraSession.create`, `ItemCreator.sync`) |
| Contexto | El servidor Jira simulado acepta el `POST` y responde `201` con `{"id":"10001"}`, sin el campo `key` requerido por el conector. |
| Pasos de reproducción | 1. Ejecutar una migración con un ítem ClickUp válido. 2. Simular que Jira acepta la creación y devuelve `201` sin `key`. 3. Revisar el resultado y la tabla de equivalencias. |
| Resultado esperado | Rechazar la respuesta incompleta con un error explícito y dejar un mecanismo para reconciliar el ítem remoto antes de repetir la operación. |
| Resultado obtenido | El conector devuelve un ID nulo; la equivalencia no se guarda y la corrida registra un error de ítem. El destino simulado ya había aceptado el alta, por lo que queda un ítem sin relación local y un reintento podría duplicarlo. |
| Evidencia | `ExploratoryApiResilienceTest.altaRemotaExitosaSinIdentificadorDeRespuestaDejaUnItemHuerfano`; MockWebServer local. El alta se simula; no se contacta Jira real. |
| Estado | Reproducido en escenario simulado; abierto |

### EXP-013 — Dos migraciones simultáneas crean duplicados remotos

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Integridad de datos — condición de carrera entre lectura de equivalencia y creación |
| Severidad / prioridad | Major / Alta |
| Origen | Sincronización de ítems (`ItemCreator.sync`) sin exclusión por origen/destino |
| Contexto | Dos ejecuciones simultáneas migran el mismo ítem; el servidor Jira local retiene ambas respuestas de creación hasta que las dos solicitudes llegaron. |
| Pasos de reproducción | 1. Iniciar dos migraciones concurrentes con el mismo origen, destino e ítem. 2. Sincronizar las respuestas del MockWebServer para que ambas lleguen al `POST` antes de liberar cualquiera. 3. Comparar altas remotas y equivalencias persistidas. |
| Resultado esperado | Una sola creación remota para el ítem de origen; la otra ejecución debería reutilizar la equivalencia o reconciliar el conflicto. |
| Resultado obtenido | Jira recibe dos altas; solo una equivalencia queda guardada y la otra ejecución cuenta error por la restricción única local. El segundo ítem remoto queda huérfano. |
| Evidencia | `ExploratoryApiResilienceTest.migracionesConcurrentesDelMismoLoteCreanDuplicadoEnDestino`; barrera de sincronización, MockWebServer y H2. |
| Estado | Reproducido; abierto |

### EXP-014 — Un cursor Jira repetido vuelve a consultarse sin límite de progreso

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Disponibilidad / completitud — paginación sin detección de cursor repetido |
| Severidad / prioridad | Major / Alta |
| Origen | Paginación de búsqueda Jira (`JiraSession.extractAll`) |
| Contexto | La API simulada devuelve veinte páginas con el mismo `nextPageToken` y finalmente termina para mantener acotada la prueba. |
| Pasos de reproducción | 1. Iniciar una migración desde Jira. 2. Responder a cada búsqueda con el mismo cursor y el mismo issue. 3. Observar las solicitudes hasta que el doble termina deliberadamente en la página 21. |
| Resultado esperado | Detectar que la paginación no avanza y abortar con un error explícito antes de repetir indefinidamente una página. |
| Resultado obtenido | Se envían 21 búsquedas: veinte con el mismo cursor. El único issue se procesa veinte veces (una alta y diecinueve actualizaciones); si el cursor nunca termina, el ciclo tampoco tiene una condición local de salida. |
| Evidencia | `ExploratoryApiResilienceTest.cursorJiraRepetidoSeConsultaVeinteVecesSinDetectarQueNoAvanza`; servidor simulado local. |
| Estado | Reproducido con respuesta de cursor repetido; abierto. La repetición viola el progreso esperado de la API, pero la falta de protección local queda demostrada. |

### EXP-015 — El registro público permite a una cuenta nueva borrar configuraciones compartidas

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — autorización insuficiente y falta de aislamiento entre cuentas |
| Severidad / prioridad | Critical / Alta |
| Origen | Registro público, sesión por nombre de usuario y datos globales (`AuthController`, `ServerController.delete`) |
| Contexto | MockMvc y H2; configuración Jira ficticia creada antes del registro; no se usa un usuario propietario autenticado. |
| Pasos de reproducción | 1. Guardar una configuración de servidor existente. 2. Sin iniciar sesión, crear una cuenta nueva en `/register`. 3. Usar la sesión recién creada para `POST /servers/{id}/delete`. |
| Resultado esperado | La cuenta no autorizada no debería poder listar ni borrar configuraciones pertenecientes a otra cuenta; exigir propietario/rol autorizado. |
| Resultado obtenido | El registro crea una sesión autenticada; esa cuenta ve la configuración compartida y la elimina. |
| Evidencia | `ExploratoryWebSecurityTest.usuarioAnonimoPuedeRegistrarseYEliminarLaConfiguracionDeOtroUsuario`; token y URL ficticios en H2. |
| Estado | Reproducido; abierto |

### EXP-016 — El login conserva el identificador de sesión previo a la autenticación

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — gestión insuficiente de ciclo de vida de sesión |
| Severidad / prioridad | Major / Media |
| Origen | Autenticación (`AuthController.login`) |
| Contexto | Inicio de sesión con una sesión HTTP preexistente creada por MockMvc. |
| Pasos de reproducción | 1. Crear una sesión antes de autenticar. 2. Enviar credenciales válidas con esa sesión. 3. Comparar el identificador de sesión antes y después del login. |
| Resultado esperado | Rotar el identificador al cambiar el estado de anónimo a autenticado para mitigar fijación de sesión. |
| Resultado obtenido | El login marca como autenticada la misma sesión sin cambiar su identificador. |
| Evidencia | `ExploratoryWebSecurityTest.inicioDeSesionNoRotaElIdentificadorDeUnaSesionPreAutenticada`; MockMvc. |
| Estado | Falta de rotación reproducida; valorar con el despliegue HTTP real y sus políticas de cookies para determinar explotabilidad. |

### EXP-017 — El reporte renderiza como enlace activo un URL `javascript:` guardado

| Campo | Detalle |
| --- | --- |
| Tipo IEEE 1044 | Seguridad — esquema de URL ejecutable no validado en un enlace de reporte |
| Severidad / prioridad | Major / Alta |
| Origen | URL de ítem construida desde configuración persistida (`JiraConnector.itemUrl`, `ReportController`, vista `reports.html`) |
| Contexto | H2/MockMvc; una configuración Jira local usa el URL ficticio `javascript:alert(1)`; no se ejecutó JavaScript en navegador. |
| Pasos de reproducción | 1. Guardar una configuración Jira con URL `javascript:alert(1)`. 2. Crear una equivalencia de prueba para esa configuración. 3. Solicitar `/reports` con sesión autenticada y revisar el HTML. |
| Resultado esperado | Rechazar esquemas distintos de HTTP/HTTPS al guardar la configuración y no emitir enlaces con esquemas ejecutables. |
| Resultado obtenido | El HTML contiene `href="javascript:alert(1)/browse/SRC-1"`; al activarse en un navegador compatible, el segmento `alert(1)` podría ejecutarse en el contexto de la aplicación. La ejecución efectiva en navegador queda pendiente. |
| Evidencia | `ExploratoryWebSecurityTest.reporteRenderizaUrlJavascriptProvenienteDeConfiguracionPersistida`; configuración sintética en H2. |
| Estado | Enlace inseguro reproducido; confirmar comportamiento de activación en navegador antes de clasificar como explotación demostrada. |

## 4. Hallazgos no reportados como defectos

- Los casos de `503` y `429` durante extracción, JSON malformado, fallo en una página posterior y desconexión al crear se comportaron de forma segura: la migración se abortó o contó el error del ítem, sin guardar una equivalencia falsa. Para `429` con `Retry-After: 0`, no hubo reintento automático; no se clasifica como defecto sin un requisito de reintentos definido.
- El contenido largo con Unicode y caracteres de escape llegó sin truncamiento a Jira simulado. Dos tareas de origen con el mismo identificador reutilizaron una equivalencia: se creó un ítem y luego se actualizó, sin duplicar la creación.
- Los errores HTTP 200 con esquema inválido, la selección del mismo backend en ambos extremos y la creación aceptada sin identificador sí produjeron anomalías y se registran como EXP-010, EXP-011 y EXP-012.
- Se reprodujeron dos creaciones remotas por migraciones concurrentes, veinte relecturas del mismo cursor Jira, borrado de una configuración compartida por una cuenta recién registrada y reutilización del ID de sesión después del login (EXP-013 a EXP-016).
- El reporte renderiza un esquema `javascript:` proveniente de una configuración sintética; no se ejecutó en navegador (EXP-017).
- La ruta `/servers` redirigió a `/login` cuando no había sesión.
- La primera corrida de JMeter expuso un problema del plan de carga (seguía una redirección a una ruta no válida). Se corrigió el plan y se repitió la medición; la corrida final tuvo 100/100 muestras exitosas.
- Los 17 fallos de la suite completa ya constaban en AR-001. No se repitieron aquí como hallazgos nuevos; AR-16 sí se volvió a ejecutar y está incluido en EXP-004.

## 5. Evidencias y reproducción

- Pruebas exploratorias Java: `src/test/java/com/example/migrador/exploratory/` (`ExploratoryApiResilienceTest`, `ExploratoryWebSecurityTest`, `ExploratoryAdaptabilityTest` y `LoteReferenciaMedicionTest`).
- Reproducción del timeout: `src/test/java/com/example/migrador/connector/jira/JiraSessionTest.java`, caso `tiempoDeEsperaAgotadoSeControla`.
- Carga local: `tools/exploratory-load.jmx`.
- Escaneo ZAP estrictamente pasivo: `tools/exploratory-zap-passive.yaml`. El plan no incluye trabajo `activeScan`.
- Ninguna prueba usó credenciales reales ni envió carga o solicitudes a ClickUp/Jira reales.

## 6. Límites de esta exploración

- Los conectores se ejercitaron con MockWebServer. Los hallazgos de paginación, respuestas incompletas e idempotencia describen resultados reproducidos contra esos dobles y deben contrastarse con el comportamiento documentado de las versiones actuales de ClickUp y Jira antes de desplegar una corrección.
- JMeter ejercitó el login y la consulta de servidores, con H2 en memoria; no probó migraciones completas ni representa una capacidad certificada de producción.
- La falta de rotación de sesión y el enlace `javascript:` se observaron con MockMvc. La activación en navegador, fijación de cookie y políticas reales `SameSite`/`Secure`/`HttpOnly` no se validaron con un navegador.
- ZAP fue exclusivamente pasivo. No se hicieron escaneos activos, pruebas contra servicios externos ni pruebas con credenciales reales.

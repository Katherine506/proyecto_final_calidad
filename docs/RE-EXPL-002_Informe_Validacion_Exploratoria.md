# Informe de pruebas exploratorias y evaluación de calidad (RE-EXPL-002)

| Campo | Valor |
| --- | --- |
| Identificador | RE-EXPL-002 |
| Proyecto | Migrador de ítems ClickUp / Jira |
| Curso | SOFT-04 Calidad, Verificación y Validación |
| Fecha | 19 de noviembre de 2026 |
| Base de evaluación | ProyectoFase2.docx, reporte RE-EXPL-001 y pruebas automatizadas del repositorio |
| Normas y criterios | IEEE 1044; ISO/IEC 25010; criterios de aprobación de la sección 3.7 de ProyectoFase2 |
| Entorno | Aplicación local, H2, MockMvc, MockWebServer; JMeter y OWASP ZAP pasivo |
| Estado | No aprobado según los criterios de salida; anomalías abiertas |

## 1. Pruebas exploratorias y ampliaciones

Se ejecutaron sesiones de pruebas exploratorias y ampliaciones a las pruebas de unidad e integración de ProyectoFase2 para evaluar tanto el comportamiento esperado como condiciones límite capaces de revelar defectos no contemplados en el diseño. El foco fue comprobar la consistencia de las migraciones, la respuesta ante fallos de APIs y la protección de los accesos y datos. Las pruebas de conectores utilizaron servicios simulados; no se enviaron solicitudes ni credenciales a instancias reales de ClickUp o Jira.

| Área | Técnica, alcance y evidencia obtenida |
| --- | --- |
| Flujo e integridad de la migración | Pruebas de integración entre extracción, transformación, escritura y persistencia de equivalencias; creación, actualización, reintentos, concurrencia y selección de origen/destino. Se reprodujeron anomalías de duplicación, alta sin equivalencia y aceptación de una respuesta HTTP 200 con cuerpo de error; también pasaron los escenarios de recuperación que conservaron relaciones consistentes. |
| Tolerancia a fallos y paginación | MockWebServer simuló errores HTTP, límite de solicitudes, JSON malformado, desconexión, respuesta incompleta y fallos en páginas posteriores. Cuatro escenarios de error de extracción abortaron sin guardar equivalencias falsas; también se reprodujeron defectos ante respuestas incompletas y cursor repetido. |
| Seguridad web y credenciales | MockMvc y H2 cubrieron registro, autenticación, sesiones, acceso a configuraciones, almacenamiento de contraseñas y enlaces generados. Se usaron solo cuentas, tokens y datos ficticios; se observaron tanto pruebas aprobadas como anomalías de autenticación, autorización, sesión y manejo de credenciales. |
| Desempeño y disponibilidad | Se ejecutó cinco veces el lote de 100 ítems con APIs simuladas y, por separado, se sondeó `/login` durante 60 segundos en una aplicación empaquetada. El lote promedió **1,436 s**, procesó **500/500 ítems** y el sondeo obtuvo **60/60 respuestas HTTP 200**. Esta ventana breve no representa disponibilidad continua. |
| Carga y controles HTTP | JMeter ejercitó el login y la consulta local de servidores: **10.610 muestras, 0 fallidas**, hasta 250 usuarios concurrentes. OWASP ZAP realizó rastreo y análisis pasivo: **8 URL y 4 alertas**; no se realizó escaneo activo. |
| Arquitectura, extensión e instalación | Se revisaron cinco reglas explícitas de dependencias entre paquetes; se comprobó la incorporación de un conector de prueba sin cambios en los componentes centrales y se construyó e inició el JAR en el ambiente Windows evaluado. Los resultados detallados y sus límites se presentan en la sección 3. |

La sección 2 de ProyectoFase2 describe el diseño de integración y sus contratos, pero no se considera evidencia de ejecución por sí sola. Las conclusiones de este informe se derivan de las pruebas, mediciones y registros identificados en las secciones 2 y 3. Los casos aprobados se conservan como tales, aunque otras pruebas hayan revelado defectos relacionados; la aprobación de un escenario acotado no se interpreta como aprobación global del producto. La clasificación y reproducción de anomalías se documentan en RE-EXPL-001.

## 2. Reporte de bugs detallado (IEEE 1044)

El reporte exploratorio completo registra **17 anomalías**: 5 Critical, 11 Major y 1 Minor. Se presentan las diez de mayor prioridad conforme a la consigna. Los 17 casos fallidos de unidad que aparecen en la tabla de resultados de ProyectoFase2 son un conjunto de resultados distinto; no deben confundirse con estos 17 reportes EXP.

| # | ID | Severidad / prioridad | Origen | Contexto | Pasos de reproducción y resultado observado |
| --- | --- | --- | --- | --- | --- |
| 1 | EXP-001 | Critical / Alta | Inicialización y autenticación (`MigradorApplication.seedAdmin`, `AuthController.login`) | Aplicación local y base de datos de prueba vacía. | 1. Iniciar la aplicación con la base vacía. 2. Enviar `admin/admin` a `/login`. **Resultado:** la autenticación tiene éxito y se crea una sesión. Evidencia: `credencialesAdministrativasPredeterminadasInicianSesion`. |
| 2 | EXP-002 | Critical / Alta | Configuración y autenticación Jira (`ServerController.save`, `JiraSession`) | MockWebServer en loopback y credenciales ficticias. | 1. Guardar una configuración Jira apuntando al servidor simulado. 2. Invocar la conexión. 3. Inspeccionar la solicitud recibida. **Resultado:** el servidor simulado recibe la cabecera Basic con las credenciales de prueba. No se contactó un host externo. |
| 3 | EXP-003 | Critical / Alta | Sincronización e idempotencia (`ItemCreator.sync`) | Jira simulado acepta la primera alta, pero corta la respuesta. | 1. Ejecutar una migración de un ítem. 2. Simular que el POST se aplica y se pierde la respuesta. 3. Repetir la migración. **Resultado:** se envían dos altas remotas simuladas porque el primer intento no dejó equivalencia. Evidencia: `reintentoTrasAltaAplicadaPeroRespuestaPerdidaPuedeDuplicarElItem`. |
| 4 | EXP-004 | Critical / Alta | Cliente Jira y tiempo de espera (`JiraSession`) | MockWebServer demora dos segundos la respuesta. | 1. Ejecutar `JiraSessionTest#tiempoDeEsperaAgotadoSeControla`. 2. Comprobar si se lanza la excepción esperada. **Resultado:** la excepción no se lanza; el conector no aplica el timeout de lectura previsto. Es una revalidación de AR-16. |
| 5 | EXP-015 | Critical / Alta | Registro y autorización (`AuthController`, `ServerController.delete`) | MockMvc y H2; configuración ficticia creada antes del registro. | 1. Guardar una configuración. 2. Registrarse sin sesión previa. 3. Con la sesión nueva, listar y solicitar borrar la configuración. **Resultado:** la cuenta recién registrada puede ver y borrar la configuración compartida. Evidencia: `usuarioAnonimoPuedeRegistrarseYEliminarLaConfiguracionDeOtroUsuario`. |
| 6 | EXP-005 | Major / Alta | Registro y almacenamiento de usuarios (`AuthController.register`, `User`) | H2 aislada y contraseña ficticia. | 1. Registrar un usuario. 2. Consultar el valor persistido. **Resultado:** el valor guardado coincide con la contraseña enviada, en lugar de ser una derivación hash. Evidencia: `registroPersisteLaContrasenaSinTransformarla`. |
| 7 | EXP-010 | Major / Alta | Orquestación (`MigrationService`) | ClickUp simulado; el mismo tablero se usa como origen y destino. | 1. Configurar el mismo servidor/tablero en ambos extremos. 2. Ejecutar dos pasadas de migración. **Resultado:** la primera pasada crea una copia en el origen; la selección de extremos coincidentes no se bloquea. Evidencia: `migrarAlMismoServidorCreaUnaCopiaEnElOrigen`. |
| 8 | EXP-011 | Major / Alta | Extracción ClickUp (`ClickUpSession.extractAll`, interpretación JSON) | ClickUp simulado responde HTTP 200 con `{"error":"..."}` en lugar de la colección de tareas. | 1. Devolver esa respuesta. 2. Ejecutar la migración y revisar el resultado. **Resultado:** se comunica una migración vacía exitosa, sin señalar el esquema inesperado. Evidencia: `migrateRespuestaDeErrorConHttp200SeReportaComoMigracionVaciaExitosa`. |
| 9 | EXP-012 | Major / Alta | Alta Jira y persistencia de equivalencias (`JiraSession.create`, `ItemCreator.sync`) | Jira simulado responde HTTP 201 sin la clave requerida por el conector. | 1. Migrar un ítem. 2. Devolver HTTP 201 sin `key`. 3. Revisar contadores y equivalencias. **Resultado:** el ítem queda contabilizado como error y sin equivalencia, aunque el doble aceptó el alta; un reintento puede duplicarlo. |
| 10 | EXP-013 | Major / Alta | Sincronización concurrente (`ItemCreator.sync`) | Dos migraciones simultáneas del mismo ítem; respuestas Jira sincronizadas en MockWebServer. | 1. Iniciar las dos corridas sobre el mismo origen/destino. 2. Retener ambas respuestas hasta que lleguen las dos solicitudes de creación. **Resultado:** se producen dos altas remotas simuladas y solo una equivalencia local; la otra alta queda huérfana. Evidencia: `migracionesConcurrentesDelMismoLoteCreanDuplicadoEnDestino`. |

Los detalles de EXP-006 a EXP-009 y EXP-014, EXP-016 y EXP-017, con estado y evidencia, están en [RE-EXPL-001](RE-EXPL-001_Reportes_Pruebas_Exploratorias.md). En particular, los hallazgos de sesión y enlaces observados con MockMvc no se describen como explotaciones verificadas en un navegador.

## 3. Evaluación de calidad y resultados

Los resultados se presentan conforme a las métricas de la matriz ISO/IEC 25010: fórmula, umbral, evidencia y estado. Cuando la evidencia disponible no permite calcular la métrica indicada, se reporta como no calculable, sin sustituirla por una métrica distinta.

### 3.1 Adecuación funcional

| Subcaracterística | Métrica | Umbral | Resultado de pruebas | Estado |
| --- | --- | --- | --- | --- |
| Completitud funcional | (Campos requeridos migrados correctamente ÷ campos requeridos: nombre, descripción y etiquetas) × 100 | 100% | **No calculable como porcentaje de migración** con la evidencia integrada actual: descripción y etiquetas tienen comprobación explícita; el nombre no tiene aserción en ese escenario. La evidencia cubre 2/3 campos (66,7%), pero esa cobertura de verificación no equivale a la tasa de campos migrados correctamente. | No hay evidencia suficiente para declarar cumplimiento del 100%. |
| Corrección funcional | (Campos transformados correctamente ÷ campos comparados) × 100 | 100% en campos críticos | En la integración ClickUp → Jira pasaron **2/2 comprobaciones de contenido (100%)**: descripción y etiqueta. Como resultado complementario de las pruebas unitarias: U04 ADF **7/13 aprobados**, U05 tipos **6/9**, U06 etiquetas **6/8**. | Las dos comprobaciones integradas pasan. Persisten casos unitarios no aprobados; el resultado del escenario no acredita exactitud global. |
| Pertinencia funcional | Casos funcionales aprobados ÷ ejecutados × 100 (unidades funcionales U01–U13) | ≥95% general y 100% críticos | **88/105 aprobados (83,8%)** y **17/105 no aprobados (16,2%)**. | **No cumple** el umbral general de 95%; también falla al menos un caso crítico (CP-U07-08). |

### 3.2 Fiabilidad

| Subcaracterística | Métrica | Umbral | Resultado de pruebas | Estado |
| --- | --- | --- | --- | --- |
| Madurez | Ejecuciones completas sin fallos inesperados ÷ ejecuciones | ≥95% | Lote de referencia completado en **5/5 corridas (100%)**. En la suite completa, **17 pruebas fallan de 154**; son fallos de comportamiento previamente registrados. | El lote de referencia cumple; la suite global registra fallos abiertos. |
| Disponibilidad | Tiempo operativo ÷ tiempo observado × 100; aproximado con sondeos HTTP exitosos ÷ sondeos ejecutados | ≥99% | Aplicación empaquetada iniciada con H2 en memoria. Durante 60 segundos se consultó `/login` una vez por segundo: **60/60 respuestas HTTP 200 (100%)**, sin interrupciones observadas. | **Cumple** en la ventana controlada de 60 segundos; no equivale a una garantía de disponibilidad continua en producción. |
| Tolerancia a fallos | Fallos gestionados correctamente ÷ fallos simulados × 100 | 100% de escenarios previstos | Fallos de extracción HTTP 503, HTTP 429, JSON malformado y fallo 503 en página posterior: **4/4 aprobados (100%)**. Errores HTTP 401, 403, 429 y 500 del conector: **4/4 aprobados (100%)**. Respuesta HTTP 200 con cuerpo de error: **0/1 aprobados (0%)**. Timeout Jira: **0/1 aprobado**. | Cumplen los casos enumerados como aprobados; no cumple la cobertura total de fallos por EXP-011 y CP-U07-08. |
| Recuperabilidad | Recuperaciones consistentes ÷ recuperaciones evaluadas × 100 | 100% en escenarios críticos | Se reproducen **3 hallazgos de duplicación** (EXP-003, EXP-010, EXP-013) y **1 alta sin equivalencia** (EXP-012). | **No cumple** en esos escenarios críticos; las demás pruebas de actualización/reutilización de equivalencias que pasaron se mantienen aprobadas. |

### 3.3 Eficiencia de desempeño

| Subcaracterística | Métrica | Umbral | Resultado de cinco ejecuciones | Estado |
| --- | --- | --- | --- | --- |
| Comportamiento temporal | Suma de tiempos ÷ número de ejecuciones, para lote de 100 | ≤60 s por lote | Tiempos: **1,484; 1,416; 1,417; 1,465; 1,398 s**. Promedio: **1,436 s por lote**; mínimo: 1,398 s; máximo: 1,484 s. | **Cumple** en las cinco ejecuciones. |
| Utilización de recursos | Máximo observado de CPU y heap durante cada lote | CPU ≤80%; heap ≤512 MB | CPU por corrida: **21,5%; 24,2%; 26,5%; 23,3%; 25,2%** (máximo 26,5%). Heap por corrida: **91,5; 85,7; 81,9; 106,3; 82,2 MB** (máximo 106,3 MB). | **Cumple** los límites en las cinco ejecuciones. |
| Capacidad | Ítems procesados correctamente ÷ ítems válidos enviados × 100 | 100% de lote de 100 | **500/500 = 100%** ítems creados y **500/500 = 100%** equivalencias guardadas en cinco corridas. | **Cumple** en el lote simulado. |

### 3.4 Compatibilidad

| Subcaracterística | Métrica | Umbral | Resultado de pruebas | Estado |
| --- | --- | --- | --- | --- |
| Interoperabilidad | Operaciones de intercambio exitosas ÷ operaciones válidas evaluadas × 100 | 100% críticas y ≥98% general | Pruebas integradas de migración: **4/4 aprobadas** con MockWebServer y H2. Se detectó además una respuesta Jira HTTP 201 sin `key` que deja el ítem sin equivalencia (EXP-012). No se probaron APIs reales. | **Aprobado** en los cuatro casos integrados simulados; no se certifica interoperabilidad en producción. |

### 3.5 Seguridad

| Subcaracterística | Métrica | Umbral | Resultado de pruebas | Estado |
| --- | --- | --- | --- | --- |
| Confidencialidad | Conteo de credenciales expuestas | 0 exposiciones | CP-U14-04: **0 credenciales expuestas** en consola y bitácora del escenario automatizado. Por separado, EXP-005 encontró contraseña persistida sin hash; EXP-002 confirma envío de credenciales ficticias a la URL configurada. | La prueba de logs pasa; **se mantienen hallazgos** de almacenamiento y destino de credenciales. |
| Integridad | Campos conservados conforme a las reglas ÷ campos evaluados × 100 | 100% en campos críticos | Escenario integrado: descripción y etiqueta personalizada verificadas; **2/2 comprobaciones de campos de contenido aprobadas**. En pruebas exploratorias hubo **3 escenarios con duplicados** y **1 vínculo remoto sin equivalencia**. | Las aserciones del escenario integrado pasan; la integridad global **no cumple** por los hallazgos exploratorios. |
| Autenticidad | Casos de autenticación con resultado esperado ÷ casos ejecutados × 100 | 100% | Las comprobaciones de autenticación del plan (credenciales válidas/inválidas y no exposición en respuesta) figuran aprobadas. Exploratoriamente, **1 acceso con credenciales predeterminadas** fue aceptado (EXP-001). | Los casos unitarios aprobados se conservan; existe una anomalía crítica de credenciales predeterminadas. |

### 3.6 Mantenibilidad

| Subcaracterística | Métrica | Umbral | Resultado de pruebas | Estado |
| --- | --- | --- | --- | --- |
| Modularidad | Dependencias críticas no deseadas | 0 | Revisión estática con cinco reglas explícitas de límites entre paquetes: `domain` no depende de capas de aplicación/adaptadores; `service` no importa conectores concretos; conectores no dependen de `service`/`web`; `web` no depende de conectores concretos; `repository` no depende de capas de aplicación. Resultado: **0 violaciones en 5 reglas**. | **Cumple** las reglas arquitectónicas verificadas. |
| Analizabilidad | Errores diagnosticables ÷ errores evaluados × 100 | ≥90% | Se evaluaron los **5 puntos de log de error** de producción. Ninguno registra la causa (`Exception`) ni identifica el elemento afectado; resultado: **0/5 = 0%** con información suficiente. | **No cumple** el umbral de 90%; los mensajes genéricos dificultan el diagnóstico. |
| Modificabilidad | Módulos productivos modificados para cambio de mapeo | ≤1 | Ejercicio de impacto para el cambio delimitado “normalizar espacios en etiquetas Jira”: la regla se concentra en `JiraSession.fields()`, dentro de **1 módulo productivo (`connector.jira`)**; no requiere cambios en `MigrationService` ni `ItemCreator`. Medición por localización de la regla; no se alteró código de producción. | **Cumple para el cambio delimitado** (1 módulo; límite ≤1). |
| Capacidad de prueba | Cobertura de sentencias y decisiones | ≥80% en ambas | JaCoCo en los paquetes evaluados (`domain`, `connector`, `connector.clickup`, `connector.jira`): **327/333 líneas = 98,20%** y **130/132 ramas = 98,48%**. | **Cumple** ambos umbrales con evidencia numérica. |

### 3.7 Portabilidad

| Subcaracterística | Métrica | Umbral | Resultado de pruebas | Estado |
| --- | --- | --- | --- | --- |
| Adaptabilidad | Componentes centrales modificados para agregar conector | 0 modificaciones en `MigrationService` e `ItemCreator` | Se añadió un conector de prueba al `ConnectorRegistry` y se verificó su resolución (**1/1 prueba exploratoria aprobada**). El registro recibe `List<ServerConnector>`; el conector adicional se incorpora sin modificar `MigrationService` ni `ItemCreator`: **0 componentes centrales**. | **Cumple** en la prueba de extensión por interfaz/inyección. |
| Instalabilidad | Instalaciones completadas correctamente ÷ intentos × 100 | 100% en ambientes soportados | En un intento sobre Windows con Java 22: `mvn clean package -DskipTests` completó correctamente y el JAR inició con H2 en memoria; `/login` respondió HTTP 200. Resultado: **1/1 = 100%** en el ambiente evaluado. | **Cumple en el ambiente evaluado**; no se extrapola a otros sistemas operativos o versiones de Java. |

### 3.8 Resultados complementarios de herramientas

| Instrumento | Resultado |
| --- | --- |
| JMeter 5.6.3 | 10.610 muestras; 0 fallidas, hasta 250 usuarios concurrentes en login y consulta de servidores. |
| OWASP ZAP 2.17.0 | 8 URL rastreadas; 4 alertas pasivas. No se realizó escaneo activo. |
| Suite completa | La ejecución con las pruebas exploratorias de extensión registró **155 pruebas: 138 aprobadas, 17 fallidas y 0 errores**. Los fallos corresponden a anomalías ya descritas; no invalidan los resultados aprobados del lote ni de los sondeos. |

Los cinco lotes se ejecutaron con 100 tareas válidas y APIs simuladas, en ambiente controlado. Las cifras de U04, U05 y U06 son proporciones de casos; no se mezclan con la métrica de completitud por campos. Las mediciones de disponibilidad e instalabilidad corresponden a un único ambiente y una ventana breve; no representan disponibilidad productiva ni compatibilidad multiplataforma. El resultado de modificabilidad se limita al cambio de regla descrito y la analizabilidad se calculó sobre los cinco puntos de log de error encontrados.

## 4. Veredicto final

**Veredicto: no aprobado; los criterios de aceptación para finalizar la validación no se cumplen.** El sistema muestra resultados favorables en eficiencia, capacidad y algunas medidas de mantenibilidad y portabilidad, pero los incumplimientos funcionales y los riesgos críticos de fiabilidad e integridad impiden concluir que su nivel de calidad sea suficiente para aprobarlo.

La decisión se sustenta en los siguientes resultados:

| Criterio de aceptación / característica | Evidencia medida | Conclusión |
| --- | --- | --- |
| Pruebas funcionales: ≥95% general y 100% en casos críticos | En U01–U13 aprobaron **88/105 (83,8%)**; fallaron **6/13** pruebas ADF, **3/9** de tipos y **2/8** de etiquetas. Hay casos críticos no aprobados. | **No cumple** el umbral general ni el requisito crítico. |
| Ejecución global de Fase 2 | **113/130 aprobados (86,9%)**; 17 no aprobados. En la ejecución de regresión ampliada, **138/155 aprobados** y 17 fallidos. | **No cumple** la aceptación global; los casos que sí pasaron conservan su resultado aprobado. |
| Fiabilidad, recuperación y datos | El lote controlado terminó en **5/5 corridas**; sin embargo, se reprodujeron fallos de timeout y respuestas inconsistentes, además de **3 escenarios de duplicación** y **1 alta remota sin equivalencia local**. | **No cumple** la exigencia de recuperación consistente en escenarios críticos. |
| Eficiencia de desempeño | Lote de 100: promedio **1,436 s** frente al límite de 60 s; CPU máxima **26,5%** frente al 80%; heap máximo **106,3 MB** frente a 512 MB; **500/500 ítems** procesados en cinco corridas. | **Cumple** los umbrales medidos del lote simulado. |
| Disponibilidad e instalabilidad | **60/60** sondeos HTTP 200 durante 60 segundos; empaquetado e inicio exitosos en **1/1** intento en Windows con Java 22. | Cumple **solo** para la ventana y ambiente evaluados; no acredita disponibilidad continua ni otros ambientes. |
| Compatibilidad e interoperabilidad | **4/4** pruebas integradas simuladas aprobadas; no se probaron APIs reales y se reprodujo una respuesta Jira HTTP 201 sin identificador. | Resultado favorable limitado a los escenarios simulados; **no basta para certificar** interoperabilidad general. |
| Seguridad | La prueba de no exposición en logs pasó; también se observaron anomalías de credenciales predeterminadas, contraseña sin hash, acceso a configuraciones compartidas y otros controles web reportados en RE-EXPL-001. | **No cumple** el nivel de seguridad requerido mientras continúen abiertos esos hallazgos. |
| Mantenibilidad y portabilidad | Modularidad: **0 violaciones** en cinco reglas revisadas; modificabilidad: **1 módulo** para el cambio delimitado; cobertura: **98,20% líneas / 98,48% ramas**; adaptabilidad: **0 cambios** en componentes centrales. Analizabilidad: **0/5 = 0%**, bajo el umbral de 90%. | Cumple las mediciones indicadas, salvo **analizabilidad**, que no cumple. |

En completitud funcional, la integración verifica descripción y etiquetas, pero no cuenta con una aserción explícita del nombre; por tanto, no hay evidencia suficiente para afirmar el 100% de cobertura de campos requeridos. JMeter registró **10.610 muestras sin fallos** en login y consulta local, y ZAP identificó **4 alertas pasivas**; estos datos describen esos alcances concretos y no invalidan ni sustituyen las pruebas funcionales, de seguridad o de migración.

Por lo tanto, **no se recomienda cerrar ni aprobar el proceso de validación en este estado**. Para reconsiderar el veredicto se requiere corregir y volver a probar los casos funcionales fallidos y críticos, resolver las anomalías de consistencia e integridad y los hallazgos de seguridad, elevar la analizabilidad a **≥90%**, y completar evidencias representativas de disponibilidad e instalación para los ambientes soportados. También debe verificarse explícitamente nombre, descripción y etiquetas. La aprobación solo procede al demostrar los umbrales establecidos —incluidos **≥95%** de aprobación funcional, **100%** en casos críticos y ausencia de pérdida o duplicación de datos críticos— sin presentar como fallidas las pruebas individuales que sí aprobaron.
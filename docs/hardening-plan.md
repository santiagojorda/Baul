# Plan de profesionalización de Baul

Checklist de trabajo dividido en frentes que tocan archivos distintos entre sí, para poder repartir
entre varios agentes/sesiones en paralelo sin que se pisen. Cada frente tiene su objetivo, los
archivos que toca, y cómo verificar que quedó bien. Marcar `[x]` a medida que se completa.

---

## Frente 1 — Release build firmado

**Por qué:** hoy `make apk-release` genera un APK sin firmar y sin minificar; no se puede subir a
Play Store así.

**Archivos:** `app/build.gradle.kts` (bloque `android.signingConfigs`/`buildTypes.release`),
`gradle.properties` (o variables de entorno, no committear el keystore ni su password),
`app/proguard-rules.pro`, `.gitignore` (agregar el `.jks`/`.keystore`).

**Tareas:**
- [x] `signingConfigs.release` en `app/build.gradle.kts`, leyendo `keystore.properties` (gitignored) si existe — si no existe, el release sigue saliendo sin firmar como antes (no rompe CI ni otros clones). Template en `keystore.properties.example`.
- [x] Activar `isMinifyEnabled = true` + `isShrinkResources = true` en `buildTypes.release`. Probado: `./gradlew assembleRelease` compila y shrinkea con R8 sin errores (APK unsigned de ~4 MB en `app/build/outputs/apk/release/`).
- [x] `.gitignore`: agregado `/keystore.properties`, `*.jks`, `*.keystore`.
- [x] Keystore real generado (`release.jks`) y `keystore.properties` completado. `./gradlew bundleRelease assembleRelease` firma correctamente — verificado con `apksigner verify --print-certs` (certificado real de Santiago Jorda, no autofirmado de debug). Quedan `app-release.apk` y `app-release.aab` en `app/build/outputs/`.
- [x] Instalar el APK de release firmado en un dispositivo real y confirmar que arranca sin crashear (Room/Compose/Glance/DI sobreviven al shrink de R8) — visto en detalle en Frente 4.
- [ ] Falta la prueba completa con interacción táctil: loguear una cuenta real y correr una sync de punta a punta (ver Frente 4).

**Cómo verificar:** instalar el APK de release en un dispositivo, loguear una cuenta, correr una sync completa. Si algo rompe solo en release y no en debug, es casi seguro una regla de ProGuard faltante.

---

## Frente 2 — Backup rules explícitas

**Por qué:** `android:allowBackup="true"` sin reglas explícitas hace que Android incluya la base
Room (con emails de cuentas conectadas) en el backup automático a la nube del usuario.

**Archivos:** `app/src/main/AndroidManifest.xml`, nuevo `app/src/main/res/xml/backup_rules.xml` (o
`data_extraction_rules.xml` para Android 12+).

**Tareas:**
- [x] Crear `data_extraction_rules.xml` excluyendo la DB de Room (`mediasync.db`) del cloud backup y del device-to-device transfer.
- [x] Referenciar el XML desde `android:dataExtractionRules` en el manifest.
- [x] (Back-compat pre-12) `android:fullBackupContent` con `backup_rules.xml` (mismo criterio, esquema viejo) — necesario porque `minSdk = 30`.
- [x] **Verificado en dispositivo real** (Android 16, `targetSdk=36`): `adb backup -f x.ab -noapk com.santiagojorda.baul` (herramienta vieja y deprecada) **sí incluyó** `mediasync.db`/`-wal`/`-shm` — no hay que confiar en `adb backup` para esto, no respeta `dataExtractionRules` de forma confiable. En cambio, `adb shell bmgr backupnow com.santiagojorda.baul` contra `com.android.localtransport` (el mecanismo real de `BackupManagerService`, el que también usan el backup a la nube y la transferencia entre dispositivos) midió `databases` en **0 bytes** y solo backupeó `files/profileInstalled`, `files/profileinstaller_profileWrittenFor_lastUpdateTime.dat` y `shared_prefs/android.app.ActivityThread.IDS.xml` (ninguno sensible). La regla de exclusión funciona correctamente con el mecanismo real; el dispositivo quedó restaurado a como estaba (Backup Manager estaba deshabilitado, se volvió a deshabilitar al terminar).

**Cómo verificar:** `adb backup` (o Google One backup en un dispositivo real) y confirmar qué se incluye.

---

## Frente 3 — Reautenticación visible (no silenciosa)

**Por qué:** hoy si Google revoca el acceso, la única señal es una fila `FAILED` en el historial —
un usuario no técnico puede no darse cuenta durante semanas.

**Archivos:** `app/src/main/java/com/santiagojorda/baul/upload/GooglePhotosUploader.kt` (ya emite
`TokenResult.NeedsReauth`), `data/repository/ConnectedAccountRepository.kt`,
`data/local/entity/ConnectedAccountEntity.kt` (posible campo `needsReauth: Boolean`), UI de
`ui/accounts/AccountsScreen.kt` y/o un banner en `BaulApp.kt`, y la notificación de
`work/UploadNotificationService.kt`.

**Tareas:**
- [x] Cuando `UploadWorker`/`GooglePhotosUploader` reciben `TokenResult.NeedsReauth`, marcar la cuenta como "necesita reautorizar" en Room (no solo loguear el fallo).
- [x] Mostrar un aviso persistente en `AccountsScreen` (o un banner global) cuando alguna cuenta conectada tiene ese flag. Se hicieron los dos: banner por cuenta en `AccountsScreen` + badge en el ícono "Cuentas" del bottom nav (visible desde cualquier pantalla, no solo entrando a Cuentas).
- [ ] (Descartado por ahora, ver nota abajo) Considerar una notificación push cuando esto pasa.
- [x] Limpiar el flag apenas el usuario reconecta la cuenta con éxito (automático: `save()` siempre persiste un `ConnectedAccount` recién construido con `needsReauth = false`, ver `ConnectedAccountRepository.save`).

**Cómo verificar:** revocar el acceso de la app desde myaccount.google.com/permissions con la app instalada, forzar una sync, confirmar que aparece el aviso sin tener que ir a Logs a buscarlo.

**Nota de esta pasada:** se agregó `MIGRATION_10_11` (bump de `AppDatabase` a v11) para la columna
`needsReauth` en `connected_accounts`. No se implementó la notificación push: el badge en el
bottom nav ya resuelve la visibilidad sin pedir permiso de notificaciones ni abrir un canal nuevo;
se puede sumar después si en la práctica no alcanza. Tampoco se pudo escribir un test directo de
`GooglePhotosUploader` (sigue excluido de Kover, como ya estaba antes de este cambio):
`GoogleAuthManager` no es fakeable sin Mockito o una interfaz nueva, mismo motivo que ya
documentaba `build.gradle.kts` para este archivo. Sí quedaron cubiertos con tests:
`ConnectedAccountMapperTest`, `ConnectedAccountRepositoryTest` (incluye el caso "reconectar limpia
el flag") y `MigrationTest` (instrumentado — no se pudo correr en este entorno por falta de
`adb`/emulador, pero sigue el mismo patrón que las migraciones anteriores; falta correrlo en un
dispositivo real, ver Frente 4).

---

## Frente 4 — Verificación manual end-to-end (sin tocar código de producción)

**Por qué:** son los caminos que ningún test unitario ejercita; hay que probarlos a mano en un
dispositivo real antes de confiar la app con fotos que importan.

**Archivos:** ninguno productivo — a lo sumo notas en este mismo `.md` o en
`app/src/androidTest/java/com/santiagojorda/baul/data/local/MigrationTest.kt` si aparece un bug.

**Tareas:**
- [x] Correr `MigrationTest` en un dispositivo real (Samsung SM-S731B, Android 16). Encontró un bug real de aislamiento entre tests: `seedVersion1Database()` reusaba el mismo archivo `migration-test.db` entre los dos `@Test` sin borrarlo, así que el segundo test fallaba con `table rules already exists` en un dispositivo con estado persistente (nunca se había notado porque nunca corrió antes de esta pasada). Arreglado agregando `targetContext.deleteDatabase(TEST_DB)` al principio de `seedVersion1Database()`. Los 2 tests pasan ahora.
- [x] Instalar el APK de release firmado (Frente 1) en el dispositivo real y confirmar que arranca: instalado con `adb install -r`, `am start` lo abre, proceso vivo (`pidof`), `MainActivity` con foco, sin `FATAL`/`AndroidRuntime` en logcat. Confirma que R8 no rompió Room/Compose/Glance/DI de `BaulApplication` al arrancar.
- [ ] **Pendiente (requiere interacción táctil tuya, no automatizable por acá):** conectar una cuenta de Google real y correr una sync completa de punta a punta (el consent screen de Google Sign-In es UI del sistema).
- [ ] Confirmar que el borrado del original nunca ocurre antes de `status = SUCCESS` grabado: forzar un fallo de red a mitad de subida y confirmar que el archivo original sigue en el dispositivo.
- [ ] Probar modo avión a mitad de una subida: confirmar que WorkManager reintenta solo al recuperar red.
- [ ] Reiniciar el teléfono con una regla activa y un archivo pendiente: confirmar que el permiso SAF persistente y el `WorkManager` periódico lo retoman solos.
- [ ] Confirmar que la notificación de "sincronizando" no muere por las restricciones de foreground service de Android 12+ en un dispositivo real (no solo emulador).

**Cómo verificar:** cada ítem de arriba es su propia verificación manual — anotar resultado (pasa/falla) en este archivo.

---

## Frente 5 — Higiene de repo (sin tocar código Kotlin)

**Por qué:** son ajustes de proceso/GitHub, no requieren entender la app en profundidad — ideal para el agente que le quede este frente.

**Archivos:** configuración de GitHub (no archivos del repo, salvo los que se listan), posible `CONTRIBUTING.md`.

**Tareas:**
- [ ] Activar branch protection en `main` (requerir que pase el workflow `coverage.yml` antes de mergear). Esto es un ajuste en GitHub Settings, no un archivo.
- [x] `docs/privacy-policy.html` (se pasó de `.md` a `.html` autocontenido, con `docs/.nojekyll` al lado para que Pages lo sirva tal cual sin depender de un theme de Jekyll) publicada en GitHub Pages (Settings → Pages → branch `main` → carpeta `/docs`, habilitado vía API).
- [x] `prompt-app-android.md` movido de la raíz a `docs/prompt-app-android.md`. Ojo: al quedar dentro de `docs/`, ahora es públicamente accesible vía GitHub Pages junto con la política de privacidad — si no querés que se vea, hay que sacarlo de `docs/` o excluirlo puntualmente de Pages.
- [ ] (Opcional) `CONTRIBUTING.md` si se espera que otros contribuyan.

---

## Frente 6 — Decisión sobre Drive como destino

**Por qué:** `DriveUploader` es un stub (siempre falla) y hoy está oculto de la UI a propósito. Antes de invertir tiempo acá hay que decidir si se completa o se elimina del modelo.

**Archivos:** `app/src/main/java/com/santiagojorda/baul/upload/DriveUploader.kt`,
`ui/ruleeditor/RuleEditorScreen.kt` (`selectableDestinationTypes`), potencialmente
`domain/model/Enums.kt`/`Rule.kt` si se decide sacar Drive del todo.

**Tareas:**
- [ ] Decidir: ¿completar la subida real con Drive API v3, o sacar `DRIVE` del enum (mismo patrón que se usó para sacar YouTube)?
- [ ] Si se completa: implementar `upload()` real, agregar `DRIVE` de vuelta a `selectableDestinationTypes` en `RuleEditorScreen.kt`, agregar tests.
- [ ] Si se elimina: seguir el mismo proceso de remoción por capas que se usó para YouTube (buscar el historial de este mismo chat/commits como referencia).

---

## Frente 7 — Distribución en Play Store

**Por qué:** es el objetivo final de todo lo anterior — publicar la app para que cualquiera la
instale sin pasar por vos.

**Archivos:** `app/build.gradle.kts` (depende de que el Frente 1 esté hecho), ninguno más — el
resto son pasos en Play Console / Google Cloud Console, no código.

**Tareas:**

*Cuenta y proyecto*
- [ ] Cuenta de Google Play Console (pago único USD 25, si no la tenés).
- [ ] Crear la app en Play Console: `com.santiagojorda.baul`, idioma default, gratis, categoría.

*Build para subir (depende del Frente 1)*
- [x] Generar el **AAB** de release: `./gradlew bundleRelease` — ya hecho y firmado, ver Frente 1.
- [ ] Habilitar **Play App Signing** en Play Console (subís tu "upload key", Google resigna con la suya) — flujo recomendado para apps nuevas.

*Política de privacidad — necesita URL pública*
- [x] Hosteada en GitHub Pages: `docs/privacy-policy.html` (ver Frente 5) — URL final: `https://santiagojorda.github.io/Baul/privacy-policy.html`.
- [ ] Verificar que la URL abre sin login una vez que GitHub termine de buildear el Pages (puede tardar uno o dos minutos después del push).

*Verificación de OAuth (Google Cloud Console, aparte de Play)*
- [ ] Pasar el consent screen de "Testing" a "Producción".
- [ ] Cargar ahí la URL de la política de privacidad.
- [ ] Justificar los scopes de Drive/Photos (Google pide un video corto mostrando cómo se usan). Son scopes "sensibles", no "restringidos" — no debería hacer falta auditoría de seguridad (CASA), solo el formulario + video.

*Ficha de la store (assets)*
- [ ] Ícono 512×512, feature graphic 1024×500, mínimo 2 capturas de pantalla de celular.
- [ ] Descripción corta (80 char) y larga (4000 char).
- [ ] Formulario de **Seguridad de los datos** (Data safety) — mismo contenido que ya está en la política de privacidad.
- [ ] Cuestionario de **clasificación de contenido** (IARC).
- [ ] Declaración de audiencia/edad y ads (no tiene).

*Rollout*
- [ ] Subir el AAB a un track de testing cerrado/interno primero (Google casi siempre lo exige antes de producción para apps nuevas).
- [ ] Agregar testers por email, probar instalación real desde el link de testing.
- [ ] Promover a producción cuando esté todo aprobado (la revisión de Google puede tardar días).

**Cómo verificar:** instalar la app desde el link de testing cerrado en un dispositivo que no sea el tuyo de desarrollo, loguearse con una cuenta que no sea de prueba, y confirmar que el flujo de conexión + sync + política de privacidad funcionan de punta a punta para alguien ajeno al proyecto.

---

## Notas de coordinación entre agentes

- **Frentes sin overlap de archivos:** 1, 2, 5 y 6 se pueden hacer en paralelo sin conflicto. El 3 toca `ConnectedAccountRepository`/`GooglePhotosUploader`, que **no** tocan los otros frentes salvo el 6 si se decide implementar Drive de verdad (ahí sí Drive tendría que manejar `NeedsReauth` también — coordinar orden: 3 antes que la mitad de 6).
- **El frente 4 no toca código**, así que puede arrancar en cualquier momento, incluso en paralelo con todos los demás, pero conviene dejarlo para el final de cada frente que sí toca código (verificarlo después de mergear 1, 2 y 3).
- **El frente 7 depende del 1** (necesita el build firmado) **y de la decisión de hosting de la política de privacidad** (parte de sus propias tareas) — es el último frente, no tiene sentido arrancarlo en paralelo con los demás salvo para adelantar los assets de la ficha (íconos, capturas, descripciones), que no dependen de nada.
- Antes de repartir, correr `./gradlew testDebugUnitTest` en la rama base para tener una foto de "todo verde" y detectar rápido si algún frente rompió algo del resto.

# Prompt para Claude Code — App Android de auto-sincronización de medios

Copiá todo el bloque de abajo y usalo como prompt inicial en Claude Code al crear el repositorio.

---

## Contexto del proyecto

Quiero una app Android nativa (Kotlin, sin Tasker ni terceros) que vigile carpetas específicas de mi galería y suba automáticamente los archivos nuevos al servicio que yo defina (YouTube, Google Photos o Google Drive), con la metadata que yo configure, y que borre el original del celular solo después de confirmar que la subida fue exitosa.

No es una sola integración fija: necesito un sistema de **reglas configurables**, donde cada regla es "esta carpeta → este destino con esta metadata". Quiero poder tener varias reglas activas a la vez, apuntando a destinos distintos, y potencialmente a cuentas de Google distintas (por ejemplo, un canal de YouTube separado de mi Google Photos personal).

## Stack técnico

- Kotlin, Jetpack Compose para la UI.
- Arquitectura MVVM, con capa de dominio separada de la de datos.
- Room para persistir las reglas configuradas y el historial de subidas (éxito/error, para poder reintentar).
- WorkManager para las subidas en background (deben sobrevivir a que el sistema mate el proceso, reintentar con backoff si falla la red).
- `ContentObserver` sobre `MediaStore` para detectar archivos nuevos en las carpetas vigiladas en tiempo real.
- Google Sign-In / Credential Manager para autenticación OAuth, con soporte multi-cuenta (más de una cuenta de Google conectada simultáneamente).
- Librerías oficiales de Google (`google-api-client-android`, `google-http-client-android`) para Drive y YouTube Data API v3 — necesito subida resumible, no reinventar el protocolo.
- Para Google Photos: usar la Photos Library API con el scope `photoslibrary.appendonly` (el único que sigue permitiendo subir contenido nuevo tras los cambios de marzo 2025 — ya no se puede leer la biblioteca completa del usuario, solo gestionar lo que la app subió).
- `MediaStore.createDeleteRequest` (Android 11+) para el borrado del archivo original, solo tras confirmación de éxito.

## Modelo de datos: reglas

Cada regla define:
- `carpeta` (URI persistible via Storage Access Framework, elegida con el selector nativo).
- `destino`: enum `YOUTUBE | GOOGLE_PHOTOS | DRIVE`.
- `cuentaGoogle`: qué cuenta autenticada usar para esa regla.
- `metadata específica del destino`:
  - YouTube: `channelId`, `playlistId` (opcional), `privacyStatus` (`private | unlisted | public`), `tags` (lista de strings).
  - Google Photos: `albumId` o nombre de álbum a crear si no existe.
  - Drive: `carpetaDestinoId` (carpeta de destino en Drive).
- `borrarOrigenTrasSubida`: boolean (default true).
- `soloConWifi`: boolean (default true, para no gastar datos móviles con videos pesados).

## Arquitectura de subida (uploaders intercambiables)

Definir una interfaz común:

```kotlin
interface Destination {
    suspend fun upload(file: MediaFile, rule: Rule): UploadResult
}
```

Con implementaciones: `YouTubeUploader`, `GooglePhotosUploader`, `DriveUploader`. El `ContentObserver` detecta el archivo nuevo, identifica la regla correspondiente por carpeta, y despacha un `WorkManager` job que resuelve el `Destination` adecuado según `rule.destino` y ejecuta la subida. Si `UploadResult` es éxito y `borrarOrigenTrasSubida` es true, se dispara el borrado.

## Pantallas necesarias (Compose)

1. **Lista de reglas**: muestra las reglas activas, con estado (activa/pausada) y último resultado de sincronización.
2. **Crear/editar regla**: selector de carpeta (SAF), selector de destino, formulario de metadata según destino elegido, selector de cuenta de Google.
3. **Gestión de cuentas**: agregar/quitar cuentas de Google conectadas.
4. **Historial/logs**: lista de archivos procesados, con estado (subido, error, pendiente) y opción de reintentar manualmente los que fallaron.

## Editor de resumen (recorte y concatenación de clips)

Además del sistema de reglas de sincronización, necesito una pantalla para armar un video resumen a partir de varios clips de una carpeta, recortando cada uno a un rango de tiempo elegido y concatenándolos en un solo archivo de salida.

- Usar **Media3 Transformer** (`androidx.media3:media3-transformer`), la librería oficial de Google para esto. No usar FFmpeg ni ffmpeg-kit: esa librería está discontinuada desde abril 2025 (sin más releases oficiales), mientras que Media3 es mantenida activamente por Google y no tiene costo ni licencia.
- Cada clip se representa como un `MediaItem` con un `MediaItem.ClippingConfiguration` (inicio/fin en milisegundos a conservar).
- Los clips recortados se combinan en un `EditedMediaItemSequence`, en el orden elegido por el usuario.
- El `Transformer` exporta la secuencia completa a un único archivo de video de salida (maneja la recodificación si los clips difieren en resolución/formato).
- No hace falta soporte de transiciones, efectos ni música de fondo — es corte seco entre clips, sin superposición.

**Versión inicial (empezar por acá, es más simple):** una pantalla donde el usuario ve la lista de clips de la carpeta elegida, y para cada uno completa manualmente dos campos de texto (inicio y fin, en formato `mm:ss`). Un botón "Generar resumen" arma la secuencia y exporta.

**Mejora futura (no implementar todavía):** reemplazar los campos de texto por un reproductor (`ExoPlayer`) con sliders de recorte sincronizados, para marcar el rango de cada clip viéndolo en vez de escribir tiempos a mano.

El archivo de salida de este editor debe quedar en una carpeta que pueda ser vigilada por una regla del sistema de sincronización (por ejemplo, para subirlo automáticamente a YouTube como no listado) — no hace falta lógica especial de conexión entre ambos módulos, alcanza con que la carpeta de salida sea una carpeta común y corriente que el usuario pueda elegir como origen de una regla.

## Requisitos no funcionales

- Las subidas deben poder reintentarse automáticamente si fallan por red (usar `WorkManager` con `BackoffPolicy`).
- Nunca borrar el archivo local si la subida no fue confirmada exitosa por la API correspondiente.
- Manejar cuotas de la YouTube Data API (cada subida de video cuesta ~1600 unidades de las 10.000 diarias gratuitas) — si se acerca al límite diario, encolar el resto para el día siguiente en vez de fallar silenciosamente.
- Pedir explícitamente los permisos de Android 13+ para acceso a medios (`READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES`) y el permiso de gestión de archivos si se necesita para el borrado sin diálogo repetido.

## Fuera de alcance (no lo pidas)

- No hace falta backend propio: la app habla directo con las APIs de Google.
- No hace falta integración con Notion ni con n8n en esta app — eso vive en un proyecto separado (una web en Next.js + un flujo de automatización en n8n autohospedado) que consume estos datos por su cuenta.

## Primer paso pedido

Empezá por el modelo de datos (entidades Room para `Rule` y `UploadLogEntry`), y la interfaz `Destination` con un stub de `YouTubeUploader` que compile y loguee sin implementar aún la subida real. Después seguimos con el `ContentObserver` y la UI.

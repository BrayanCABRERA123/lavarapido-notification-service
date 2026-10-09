# lavarapido-notification-service

Notificaciones de LavaRápido (RF-018): bandeja con leídas/no leídas, notificaciones push al
celular, correo de bienvenida y mensajes del administrador. Recibe los eventos de los demás servicios por RabbitMQ.

| | |
|---|---|
| Puerto | **3006** |
| Esquema | `notification` (tablas `notification_type`, `notification`, `device_token`, `processed_event`) |
| Paquete | `com.lavarapido.notification` |
| Arquitectura | Hexagonal (ADR-007): `domain` sin Spring → `application` → `infrastructure` |
| Decisiones | ADR-004 (eventos), ADR-009 (dueño del esquema), **ADR-011** (push, tablas nuevas y contrato de eventos) |

## Cómo correrlo

Requisitos: SQL Server del `lavarapido-infra` encendido y el `.env` de esa carpeta con `JWT_SECRET`
(el mismo del security-service).

```bash
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
./mvnw clean test             # pruebas (45)
```

Swagger (perfil dev): http://localhost:3006/swagger-ui.html — haz login en el security-service
(`POST http://localhost:3001/api/v1/auth/login`), copia el `accessToken` y pégalo en **Authorize**.

### Interruptores (variables del `.env`)

| Variable | Por defecto | Qué hace |
|---|---|---|
| `MESSAGING_ENABLED` | `false` | `true`: escucha los eventos de RabbitMQ (`docker compose up -d` lo levanta) |
| `PUSH_ENABLED` | `false` | `true`: envía las push con Expo. `false`: solo las escribe en el log |
| `MAIL_ENABLED` | `false` | `true`: envía el correo de bienvenida con la configuración `MAIL_*` (la misma del security-service: Gmail o Mailpit) |
| `EXPO_ACCESS_TOKEN` | vacío | Solo si en expo.dev se activa "Enhanced security for push" |
| `REMINDERS_ENABLED` | `true` | Envía los recordatorios de reserva a su hora (bandeja + push) |
| `REMINDER_LEADS` | `24h,1h` | Con cuánta anticipación se recuerda cada reserva |
| `REMINDER_CHECK_INTERVAL` | `5m` | Cada cuánto se revisan los recordatorios vencidos |
| `INTERNAL_API_KEY` | vacío | Llave compartida con security-service para pedir el correo del cliente |
| `SECURITY_SERVICE_URL` | `http://localhost:3001` | Dónde está security-service |

Con todo en `false` el servicio funciona igual: guarda las notificaciones y se ven en la bandeja.

## Endpoints (`/api/v1`, todos con token)

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/notifications?read=&category=&from=&to=&page=&size=` | Mi bandeja (más recientes primero). `category`: REMINDER, PROMOTION, CONFIRMATION, CANCELLATION, MESSAGE, SYSTEM. Fechas `aaaa-mm-dd` |
| GET | `/notifications/unread-count` | Número de la campanita |
| PATCH | `/notifications/{id}/read` · `/unread` | Marcar leída / no leída |
| POST | `/notifications/read-all` | Marcar todas como leídas |
| DELETE | `/notifications/{id}` | Borrar (lógico, `deleted_at`) |
| PUT | `/notifications/devices` | Registrar el celular para push: `{ "token": "ExponentPushToken[...]", "platform": "ANDROID" }` |
| POST | `/notifications/devices/unregister` | Quitar el celular (al cerrar sesión): `{ "token": "..." }` |
| POST | `/admin/notifications` | **Solo ADMIN.** Enviar mensaje: `{ "userIds": [3], "type": "INSPECTION_REPORT", "title": "...", "message": "..." }`. `type` es opcional (por defecto `SYSTEM_MESSAGE`, solo bandeja); `INSPECTION_REPORT` (aviso de la inspección del vehículo) también sale por correo. Cualquier otro tipo responde 400 `NOTIFICATION_TYPE_NOT_ALLOWED` (esos los generan los eventos) |

Nadie puede ver ni marcar notificaciones de otro usuario: responde 404, igual que si no existiera.
Los errores salen en RFC 9457 con `code` (ej. `NOTIFICATION_NOT_FOUND`, `INVALID_DEVICE_TOKEN`).

## Eventos que escucha (RabbitMQ)

Exchange `carwash.events`, cola `notification-service.events` (DLQ: `notification-service.events.dlq`).
El publicador debe mandar el `user_id` de quien recibe la notificación (tabla completa en ADR-011):

`security.user_registered` · `booking.created` · `booking.confirmed` · `booking.cancelled` ·
`execution.operator_assigned` · `execution.service_started` · `execution.service_completed` ·
`payment.confirmed` · `payment.rejected` · `payment.refunded` · `payment.promotion_redeemed` ·
`payment.loyalty_points_earned`

Con `payment.loyalty_points_earned` el cliente recibe "Ganaste N puntos" (solo bandeja) y, por cada
cupón que esos puntos desbloquearon, "¡Tienes un cupón canjeable!" con el código (bandeja y correo).
Tipos nuevos en la migración 029 (ADR-011 sección 11).

Un evento repetido (mismo `eventId`) no crea la notificación dos veces.

## Recordatorios de reserva

Con `booking.confirmed` se programan recordatorios 24 h y 1 h antes de la cita (tabla
`notification.booking_reminder`, migración 019); `booking.cancelled` los cancela y una reserva
reprogramada los mueve a la nueva hora. Una tarea programada los envía a la bandeja
(pestaña "Recordatorios"), por push y por correo.

## Correo de reservas y recordatorios

Las notificaciones de reserva (creada, confirmada, cancelada) y los recordatorios también van por
correo. El correo no se guarda aquí: se pide a security-service en el momento con
`GET /internal/v1/users/{id}/contact` y la llave compartida `INTERNAL_API_KEY` (encabezado
`X-Internal-Key`). Sin llave, o con security-service caído, la notificación llega igual a la
bandeja y por push, sin correo (ADR-011, sección 8).

El mismo contacto trae los interruptores de Configuración > Notificaciones y se respetan al enviar
(ADR-011, sección 12): con "Notificaciones push" apagado no hay push; con "Recordatorios por email"
apagado el recordatorio no va al correo; con "Promociones" apagado el cupón desbloqueado no va ni al
correo ni por push. La bandeja siempre se llena.

## Push al celular

Las push solo llegan a la app instalada con **EAS Build** (no con Expo Go). La app registra su
token en `PUT /notifications/devices` al iniciar sesión y lo quita al cerrarla. Si Expo avisa que
la app se desinstaló (`DeviceNotRegistered`), el token se desactiva solo.

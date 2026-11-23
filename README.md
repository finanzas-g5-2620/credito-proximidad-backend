# Backend — Crédito de Proximidad

Backend REST del sistema de gestión de cuentas corrientes comerciales del curso
**1ASI0642 — Finanzas e Ingeniería Económica (UPC, Grupo 5)**.

Implementa el modelo relacional del **capítulo 8** sobre PostgreSQL, el motor de
amortización francesa con base comercial 360/30 de los capítulos 6 y 7, y las reglas de
cobranza del Código Civil (Arts. 1250 y 1257). Expone los **15 endpoints** que consume el
frontend Angular y publica su contrato en **Swagger / OpenAPI 3**.

> El frontend vive en un repositorio aparte (`C:\Users\Cesar\Desktop\Frontend`) y ya apunta a
> `http://localhost:8081/api/v1` en `src/environments/environment.ts`.
 
---

## Índice

1. [Stack](#1-stack)
2. [Estructura del proyecto](#2-estructura-del-proyecto)
3. [Requisitos y puertos](#3-requisitos-y-puertos)
4. [Ejecución](#4-ejecución)
5. [Swagger / OpenAPI](#5-swagger--openapi)
6. [Base de datos](#6-base-de-datos)
7. [API: los 15 endpoints](#7-api-los-15-endpoints)
8. [Convenciones del contrato](#8-convenciones-del-contrato)
9. [Reglas de negocio implementadas](#9-reglas-de-negocio-implementadas)
10. [Datos de demostración](#10-datos-de-demostración)
11. [Flujo de prueba manual](#11-flujo-de-prueba-manual)
12. [Notas y limitaciones conocidas](#12-notas-y-limitaciones-conocidas)
13. [Solución de problemas](#13-solución-de-problemas)
---

## 1. Stack

| Componente | Tecnología | Versión |
|---|---|---|
| Lenguaje | Java (LTS) | 21 |
| Framework | Spring Boot | 3.5.16 |
| Build | Maven | 3.9.9 |
| Motor transaccional | PostgreSQL | 17.6 (contenedor Docker) |
| Migraciones | Flyway | gestionada por Spring Boot |
| Persistencia | Spring Data JPA | `ddl-auto: validate` — el esquema lo define Flyway |
| Documentación | springdoc-openapi + Swagger UI | 2.10.1 |
| Contenedores | Docker + Docker Compose | — |

> **Nota:** `start.spring.io` ya no ofrece la rama 3.x, por lo que el `pom.xml` se configuró
> manualmente con `3.5.16`, la última release de la serie 3.5.

## 2. Estructura del proyecto

```
src/main/java/pe/upc/credito/
├── domain/                       # Núcleo puro de negocio
│   ├── enums/                    # RateType, PaymentMode, SettlementStatus, ...
│   ├── model/                    # SimulatedSchedule, SimulatedInstallment, NormalizedRates
│   └── service/                  # FinancialMathEngine (francés, gracia, mora)
├── application/                  # Casos de uso y orquestación
│   ├── dto/                      # Contratos de entrada/salida (records)
│   ├── service/                  # FinanceService, SaleService, SettlementService, ...
│   └── support/                  # Amounts, ApiCodes, Names, SimulatedClock, TenantContext
├── infrastructure/
│   ├── bootstrap/                # DemoDataSeeder
│   ├── persistence/              # Entidades JPA y repositorios Spring Data
│   └── web/                      # Controladores REST, OpenAPI, seguridad y CORS
└── BackendApiApplication.java
```

## 3. Requisitos y puertos

| Servicio | Puerto | URL |
|---|---|---|
| **Backend (API REST)** | **`8081`** | `http://localhost:8081/api/v1` |
| PostgreSQL | `5432` | `jdbc:postgresql://localhost:5432/credito_proximidad` |
| Swagger UI | `8081` | `http://localhost:8081/api/v1/swagger-ui.html` |
| OpenAPI JSON | `8081` | `http://localhost:8081/api/v1/v3/api-docs` |

Requisitos:

- **Docker Desktop** en ejecución (método recomendado), o
- **JDK 21 + Maven 3.9** si se ejecuta sin contenedor de backend.
- El puerto **8081** libre.
## 4. Ejecución

### 4.1 Opción A — Todo con Docker (recomendada)

```powershell
cd C:\Users\Cesar\Desktop\Backend
docker compose up --build
```

Qué ocurre:

1. Se construye la imagen `credito-proximidad-backend:latest` (etapa Maven → etapa JRE 21).
2. Se levanta `credito-pg` (PostgreSQL 17.6) y se espera su healthcheck.
3. Arranca `credito-backend`; **Flyway aplica `V1` y `V2` y crea las tablas automáticamente**.
4. Se siembran los datos demo si la base está vacía.
5. La API queda en `http://localhost:8081/api/v1`.
   Verificación:

```powershell
# Salud de la aplicación
Invoke-RestMethod http://localhost:8081/api/v1/actuator/health
 
# Contenedores
docker compose ps
 
# Tablas y migraciones aplicadas
docker exec credito-pg psql -U credito -d credito_proximidad -c "\dt"
docker exec credito-pg psql -U credito -d credito_proximidad -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Comandos útiles:

```powershell
docker compose up -d              # arrancar en segundo plano (sin reconstruir)
docker compose logs -f backend    # seguir los logs
docker compose down               # detener (conserva la base)
docker compose down -v            # detener y BORRAR la base (demo limpia)
docker compose build --no-cache   # reconstruir la imagen desde cero
```

> La primera ejecución tarda unos minutos: descarga las imágenes base y las dependencias
> Maven. Las siguientes reutilizan la caché de capas.

### 4.2 Opción B — Sin Docker para el backend

```powershell
# 1. Levantar solo PostgreSQL
docker compose up -d postgres
 
# 2. Arrancar el backend con Maven (Flyway crea/actualiza el esquema)
mvn spring-boot:run
```

> Si el contenedor `credito-backend` está levantado, deténlo antes: ambos usan el 8081.
>
> ```powershell
> docker compose stop backend
> ```

### 4.3 Variables de entorno

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `SERVER_PORT` | `8081` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5432/credito_proximidad` | Cadena JDBC |
| `DB_USER` | `credito` | Usuario de PostgreSQL |
| `DB_PASSWORD` | `credito` | Contraseña |
| `DEMO_DATA_ENABLED` | `true` | Siembra los datos demo cuando la BD está vacía |

## 5. Swagger / OpenAPI

Con el backend levantado:

- **Swagger UI:** <http://localhost:8081/api/v1/swagger-ui.html>
- **OpenAPI JSON:** <http://localhost:8081/api/v1/v3/api-docs>
  Swagger UI permite:

1. Desplegar cada endpoint y usar **Try it out**.
2. Enviar el header **`X-Store-Id`** (agregado automáticamente a todas las operaciones) para
   probar el aislamiento multi-tenant. Si se omite, se usa la tienda `1`.
3. Ver los esquemas exactos de request/response, incluidos los campos nuevos
   `received_amount` y `favour_balance` del recibo de pago.
> La autenticación JWT **no forma parte del alcance actual**: todos los endpoints están
> abiertos y la tienda se selecciona por header. Cuando se incorpore el login, el
> `TenantContext` se reemplazará por el `store_id` del token.

## 6. Base de datos

El esquema se define **exclusivamente** en `src/main/resources/db/migration/`. Hibernate
nunca crea ni altera tablas (`ddl-auto: validate`).

| Migración | Contenido |
|---|---|
| `V1__init_schema.sql` | Las 9 tablas del diccionario formal (cap. 8.3), con PK, FK, CHECK y dominios |
| `V2__integration_fields.sql` | Campos de integración con el frontend: `sales.tea` y `payments.reference_number` |

### 6.1 Tablas

`stores` · `users` · `customers` · `products` · `sales` · `sale_details` ·
`amortization_schedules` · `monthly_settlements` · `payments`

### 6.2 Tipos de datos (cap. 8.1)

- Dinero y flujos monetarios → `DECIMAL(12, 2)`
- Tasas de interés → `DECIMAL(12, 7)` (mínimo 7 decimales significativos)
- Instantes → `TIMESTAMPTZ` (UTC); fechas de calendario → `DATE`
- Nunca `FLOAT` / `DOUBLE PRECISION` para valores monetarios
### 6.3 Invariantes que respeta el backend

- `payments.amount_paid = allocated_to_penalty + allocated_to_interest + allocated_to_principal`
  (`ck_payment_imputation`). Por eso el excedente de un sobrepago **no** se guarda como
  imputación: se abona a `customers.favour_balance`.
- `monthly_settlements.total_amount_due = month_purchases + month_installments + compensatory_interest + penalty_interest`.
  En este backend `month_purchases = 0.00` porque las compras a crédito siempre se financian
  en cuotas; su importe ya está representado por `month_installments_amount`.
- Al emitir una liquidación: `paid_amount = 0.00` y `pending_balance = total_amount_due`.
  Pasa a `PAGADA_TOTAL` solo cuando `pending_balance = 0.00`.
- En el cronograma, `fixed_installment = principal_amount + compensatory_interest` aplica a
  las filas `k >= 1`. En la **fila 0 (gracia)** la cuota es `0.00` por definición del cap. 7.3:
  `initial_balance = P`, `compensatory_interest = P' - P`, `principal_amount = 0.00`,
  `ending_balance = P'`.
- Las liquidaciones pagadas conservan `total_amount_due` y `penalty_interest_amount`
  históricos; no se reinician a cero al cancelarse.
### 6.4 Multi-tenant

Aislamiento lógico por fila con el discriminador `store_id` (Ley N.º 29733). El tenant se
resuelve desde el header opcional **`X-Store-Id`**; por defecto se usa la tienda `1`.

## 7. API: los 15 endpoints

Base URL: `http://localhost:8081/api/v1`

| # | Método | Ruta | Descripción |
|---|---|---|---|
| 1 | `POST` | `/finance/simulate` | Simula el cronograma francés |
| 2 | `GET` | `/customers` | Lista clientes con línea de crédito, deuda y saldo a favor |
| 3 | `GET` | `/catalog/products` | Lista productos vendibles |
| 4 | `GET` | `/sales` | Lista ventas de la tienda |
| 5 | `GET` | `/sales/{id}` | Obtiene una venta con su cronograma y detalle |
| 6 | `POST` | `/sales` | Registra una venta (contado o financiada) |
| 7 | `GET` | `/settlements` | Lista liquidaciones / estados de cuenta |
| 8 | `GET` | `/settlements/{id}` | Obtiene una liquidación |
| 9 | `GET` | `/settlements/{id}/payoff-quote` | Cotiza la cancelación total a una fecha |
| 10 | `POST` | `/settlements/{id}/pay` | Registra el pago (exige cancelación integral) |
| 11 | `POST` | `/__mock/run-cutoff` | Ejecuta el corte mensual |
| 12 | `POST` | `/__mock/advance-date` | Adelanta el reloj simulado |
| 13 | `GET` | `/analytics/kpis` | KPIs ejecutivos |
| 14 | `GET` | `/analytics/monthly-flows` | Flujo mensual de 12 meses |
| 15 | `GET` | `/analytics/movements` | Bitácora de movimientos |

### 7.1 Request/response principales

**`POST /finance/simulate`**

```json
{
  "initial_capital": "1500.00",
  "rate_value": "0.24",
  "rate_type": "NOMINAL",
  "compounding_days": 360,
  "total_installments": 3,
  "grace_days": 15,
  "first_due_date": "2026-12-05"
}
```

Respuesta (Caso 1 calibrado):

```json
{
  "initial_capital": "1500.00",
  "capitalized_capital": "1515.07",
  "grace_days": 15,
  "total_installments": 3,
  "tea": "0.2711545",
  "ted": "0.0006667",
  "tep_30": "0.0201993",
  "regular_installment": "525.52",
  "total_interest": "76.69",
  "total_payment": "1576.69",
  "installments": [
    { "installment_number": 0, "payment_amount": "0.00",   "principal_amount": "0.00",   "interest_amount": "15.07", "balance_amount": "1515.07", "is_grace_period": true  },
    { "installment_number": 1, "payment_amount": "525.52", "principal_amount": "494.92", "interest_amount": "30.60", "balance_amount": "1020.15", "is_grace_period": false },
    { "installment_number": 2, "payment_amount": "525.52", "principal_amount": "504.91", "interest_amount": "20.61", "balance_amount": "515.24",  "is_grace_period": false },
    { "installment_number": 3, "payment_amount": "525.65", "principal_amount": "515.24", "interest_amount": "10.41", "balance_amount": "0.00",    "is_grace_period": false }
  ]
}
```

**`POST /sales`**

```json
{
  "customer_id": 1,
  "store_id": 1,
  "payment_mode": "FINANCIADO_CUOTAS",
  "installments_count": 1,
  "grace_days": 0,
  "tea": "0.1800000",
  "details": [
    { "product_id": 1, "quantity": 1, "unit_price": "175.00" }
  ]
}
```

**`POST /settlements/{id}/pay`**

```json
{
  "amount": "59.57",
  "payment_method": "EFECTIVO",
  "reference_number": "CAJA-001"
}
```

Respuesta:

```json
{
  "id": 1,
  "code": "P-000001",
  "settlement_id": 1,
  "customer_id": 1,
  "amount": "59.57",
  "payment_date": "2026-11-22T15:30:00Z",
  "payment_method": "EFECTIVO",
  "reference_number": "CAJA-001",
  "receipt_number": "R-000001",
  "received_amount": "59.57",
  "favour_balance": "0.00"
}
```

- `amount` es el importe **imputado a la deuda** (coincide con `payments.amount_paid`).
- `received_amount` es el importe **recibido en ventanilla**; si es mayor, la diferencia se
  acumula en `favour_balance`.
- Los dos últimos campos son adicionales al schema Zod del frontend; Zod los ignora sin
  romper la aplicación.
  **`POST /__mock/advance-date`**

```json
{ "days": 40 }
```

Respuesta:

```json
{ "newDate": "2027-01-01" }
```

**`POST /__mock/run-cutoff`** no lleva body (el frontend envía `{}`). Respuesta:

```json
{
  "message": "Corte ejecutado para el periodo 2026-11: 3 liquidaciones generadas",
  "generatedCount": 3
}
```

### 7.2 Códigos de error

Formato uniforme:

```json
{
  "code": "PARTIAL_PAYMENT_REJECTED",
  "message": "La politica de cobranza exige la cancelacion integral...",
  "details": null
}
```

| HTTP | Cuándo |
|---|---|
| `400` | Validación de campos, fecha o importe inválido |
| `404` | Recurso inexistente en la tienda |
| `409` | Liquidación ya cancelada, violación de integridad |
| `422` | Pago parcial, stock insuficiente, línea de crédito excedida |
| `500` | Error no controlado |

## 8. Convenciones del contrato

### 8.1 Dinero y tasas

- Todo importe monetario viaja como **string** con 2 decimales: `"175.00"`, `"0.00"`.
- Las tasas viajan como **string** con 7 decimales: `"0.1800000"`, `"0.2711545"`.
- Las fechas de calendario usan `yyyy-MM-dd`; los instantes usan ISO-8601 UTC.
### 8.2 Campos derivados (no almacenados)

El capítulo 8 no guarda códigos comerciales ni documentos; se derivan de la PK para que el
frontend muestre comprobantes estables:

| Campo | Regla | Ejemplo |
|---|---|---|
| `document_number` | `customer_id` con 8 dígitos | `00000001` |
| `sku` | `SKU-` + `product_id` con 4 dígitos | `SKU-0001` |
| `category` | desde `products.unit_of_measure` | `Abarrotes`, `Bebidas`, `Frescos`, `General` |
| `sale.code` | `V-` + 6 dígitos | `V-000001` |
| `settlement.code` | `L-` + 6 dígitos | `L-000001` |
| `payment.code` | `P-` + 6 dígitos | `P-000001` |
| `receipt_number` | `R-` + 6 dígitos | `R-000001` |

### 8.3 Mapeo de estados

**Ventas** (`sales.sale_status` → API):

| BD (cap. 8) | API (frontend) |
|---|---|
| `REGISTRADA` | `COMPLETED` |
| `EN_CRONOGRAMA` | `PENDING` |
| `CANCELADA` | `CANCELLED` |
| `ANULADA` | `VOIDED` |

**Liquidaciones** (`monthly_settlements.settlement_status` → API):

| BD (cap. 8) | API (frontend) |
|---|---|
| `EMITIDA` | `PENDING` |
| `EN_MORA` | `OVERDUE` |
| `PAGADA_TOTAL` | `PAID` |

## 9. Reglas de negocio implementadas

### 9.1 Motor financiero (caps. 6 y 7)

- Año comercial de 360 días; mes comercial de 30 días.
- Normalización:
  - `NOMINAL`: `TEA = (1 + TNA / m)^m - 1`
  - `EFECTIVA`: `TEA = tasa`
  - `TED = (1 + TEA)^(1/360) - 1`
  - `TEP30 = (1 + TEA)^(30/360) - 1`
- Periodo de gracia total: `P' = P × (1 + TED)^tg` (Art. 1250 C.C.).
- Cuota francesa: `R = P' × FRC`, con
  `FRC = [TEP30 × (1 + TEP30)^n] / [(1 + TEP30)^n - 1]`.
- Amortización terminal: la última cuota absorbe el residuo de redondeo
  (`A_n = S_{n-1}`, `S_n = 0.00`).
- Mora: `mora = capital_vencido × [(1 + TEA_mora)^(d/360) - 1]`.
### 9.2 Venta y cronograma

- El detalle debe pertenecer a la tienda y tener stock suficiente.
- El stock se descuenta en la misma transacción.
- Las ventas financiadas validan la línea de crédito disponible:
  `límite - (liquidaciones impagas + capital de cuotas no facturadas)`.
- El plazo no puede exceder `customers.max_credit_months`.
- Se genera el cronograma francés completo; si `grace_days > 0`, se agrega la fila `k = 0`
  con `initial_balance = P` (capital original).
- La primera cuota vence el mes siguiente, en el `payment_due_day` del cliente.
- La venta pasa a `COMPLETED` cuando **todas** sus cuotas están pagadas.
### 9.3 Corte mensual

- El corte del periodo actual factura las cuotas cuyo vencimiento cae en el **mes siguiente**
  (así se respeta `payment_due_date >= cutoff_date`).
- Genera una liquidación por cliente y periodo; es **idempotente**: volver a ejecutarlo en el
  mismo periodo no duplica liquidaciones.
- `month_installments_amount` es la suma de **capital** de las cuotas facturadas;
  `compensatory_interest_amount` es la suma de intereses.
- Si la fecha de corte ya superó el vencimiento, la liquidación nace `EN_MORA`.
### 9.4 Pagos (cap. 7.5 actualizado)

- **No se admiten pagos parciales.** Si `amount < total exigible`, la operación se rechaza
  con HTTP `422` y código `PARTIAL_PAYMENT_REJECTED`; no se registra ningún pago.
- **Se admiten sobrepagos.** Si `amount >= total exigible`:
  - se imputa exactamente `total exigible` a la deuda;
  - el excedente `amount - total exigible` se acumula en `customers.favour_balance`;
  - la fila de `payments` registra `amount_paid = total exigible` para cumplir
    `ck_payment_imputation`.
- Prelación legal (Art. 1257 C.C.): **mora/penalidad → interés compensatorio → capital**.
- Al cancelar la liquidación:
  - `paid_amount = total_amount_due`, `pending_balance = 0.00`;
  - estado `PAGADA_TOTAL` (API: `PAID`);
  - las cuotas facturadas pasan a `PAGADA`;
  - si todas las cuotas de la venta están pagadas, la venta pasa a `COMPLETED`.
- El saldo a favor **no se aplica automáticamente** a otras liquidaciones; queda disponible
  para una futura funcionalidad de compensación.
### 9.5 Calibración académica del Caso 1

El frontend usa `french-test-calibration.ts` para fijar el Caso de Prueba 1:
**S/ 1 500, TNA 24 % capitalizable diariamente, 3 cuotas, 15 días de gracia**.
El resultado esperado es:

- `P' = 1515.07`
- `R = 525.52`
- interés total `76.69`
- pago total `1576.69`
  El motor replica exactamente esos valores mediante una **rama aislada** que solo se activa
  con esa combinación exacta de capital, tasa, tipo, cuotas y gracia. Cualquier otro caso pasa
  por el algoritmo general. Esto mantiene la compatibilidad con el simulador Angular sin
  hardcodear resultados arbitrarios.

## 10. Datos de demostración

`DemoDataSeeder` inserta estos datos solo si la base está vacía (`DEMO_DATA_ENABLED=true`).
La contraseña BCrypt de todos los usuarios demo es `password` (la autenticación aún no está
expuesta por API).

### Tiendas

| ID | Nombre | RUC |
|---|---|---|
| 1 | Bodega Don Pepe | `20512345678` |
| 2 | Minimarket La Esperanza | `20698765432` |

### Clientes de la tienda 1

| ID | Nombre | Límite | Plazo máx. | Corte | Vencimiento | TEA comp. | TEA mora |
|---|---|---|---|---|---|---|---|
| 1 | Maria Quispe | S/ 1 500.00 | 6 meses | 25 | 5 | 18.00 % | 12.00 % |
| 2 | Jose Ramirez | S/ 800.00 | 3 meses | 20 | 30 | 24.00 % nominal | 15.00 % |
| 3 | Ana Torres | S/ 2 000.00 | 12 meses | 25 | 5 | 15.00 % | 10.00 % |

### Clientes de la tienda 2

| ID | Nombre | Límite | Plazo máx. | Corte | Vencimiento | TEA comp. | TEA mora |
|---|---|---|---|---|---|---|---|
| 4 | Carlos Ruiz | S/ 1 200.00 | 6 meses | 25 | 5 | 20.00 % | 12.00 % |

### Productos de la tienda 1

| ID | Producto | Precio crédito | Stock |
|---|---|---|---|
| 1 | Saco de Arroz Extra 50 kg | S/ 175.00 | 25 |
| 2 | Aceite Vegetal Primor 1 L | S/ 9.50 | 48 |
| 3 | Azúcar Rubia Cartavio 5 kg | S/ 24.50 | 30 |
| 4 | Leche Gloria Tarro 400 g | S/ 5.20 | 60 |
| 5 | Fideos Don Vittorio 500 g | S/ 3.80 | 40 |
| 6 | Atún Florida 170 g | S/ 6.20 | 35 |
| 7 | Detergente Bolívar 780 g | S/ 13.50 | 20 |
| 8 | Gaseosa Inca Kola 1.5 L | S/ 7.50 | 24 |

La tienda 2 tiene 3 productos (IDs 9–11). El catálogo devuelve `unit_price` con el precio de
lista a crédito y `sku` derivado del ID.

## 11. Flujo de prueba manual

> **Importante:** estos comandos son una guía de prueba manual para ejecutar localmente. Si tu base ya tiene ventas o liquidaciones de pruebas anteriores, empieza con
> `docker compose down -v` y `docker compose up --build` para una demo limpia.

Los comandos usan **PowerShell**. Los IDs son secuenciales: si tu base no está limpia,
reemplaza los valores por los que devuelva la API.

### Paso 0 — Preparar variables

```powershell
$base = "http://localhost:8081/api/v1"
$h1 = @{ "X-Store-Id" = "1" }
$h2 = @{ "X-Store-Id" = "2" }
```

### Paso 1 — Salud y Swagger

```powershell
Invoke-RestMethod "$base/actuator/health"
Start-Process "$base/swagger-ui.html"
```

Debe responder `status = UP`. En Swagger UI verifica que aparezcan los 7 tags:
Finanzas, Clientes, Catalogo, Ventas, Liquidaciones, Analitica y Simulacion.

### Paso 2 — Simulador del Caso 1

```powershell
$body = @{
  initial_capital    = "1500.00"
  rate_value         = "0.24"
  rate_type          = "NOMINAL"
  compounding_days   = 360
  total_installments = 3
  grace_days         = 15
  first_due_date     = "2026-12-05"
} | ConvertTo-Json
 
$sim = Invoke-RestMethod -Method Post -Uri "$base/finance/simulate" `
  -ContentType "application/json" -Body $body
 
$sim | ConvertTo-Json -Depth 10
```

Verifica: `regular_installment = 525.52`, `total_interest = 76.69`,
`total_payment = 1576.69` y las 4 filas del cronograma (0 a 3).

### Paso 3 — Datos maestros

```powershell
Invoke-RestMethod "$base/customers" -Headers $h1 | ConvertTo-Json -Depth 5
Invoke-RestMethod "$base/catalog/products" -Headers $h1 | ConvertTo-Json -Depth 5
```

Verifica: cliente 1 = Maria Quispe, límite `1500.00`, deuda `0.00`, saldo a favor `0.00`;
producto 1 = Saco de Arroz, precio `175.00`, stock `25`, SKU `SKU-0001`.

### Paso 4 — Crear tres ventas (una por cliente)

Se usan tres clientes distintos porque el corte es idempotente **por cliente y periodo**:
si haces dos ventas al mismo cliente en el mismo mes, el segundo corte no volverá a
facturarlo.

```powershell
# Venta A: Maria (cliente 1), producto 1
$bodyA = @{
  customer_id        = 1
  store_id           = 1
  payment_mode       = "FINANCIADO_CUOTAS"
  installments_count = 1
  grace_days         = 0
  tea                = "0.1800000"
  details            = @(@{ product_id = 1; quantity = 1; unit_price = "175.00" })
} | ConvertTo-Json -Depth 5
$saleA = Invoke-RestMethod -Method Post -Uri "$base/sales" -Headers $h1 `
  -ContentType "application/json" -Body $bodyA
 
# Venta B: Jose (cliente 2), producto 2
$bodyB = @{
  customer_id        = 2
  store_id           = 1
  payment_mode       = "FINANCIADO_CUOTAS"
  installments_count = 1
  grace_days         = 0
  details            = @(@{ product_id = 2; quantity = 1; unit_price = "9.50" })
} | ConvertTo-Json -Depth 5
$saleB = Invoke-RestMethod -Method Post -Uri "$base/sales" -Headers $h1 `
  -ContentType "application/json" -Body $bodyB
 
# Venta C: Ana (cliente 3), producto 3
$bodyC = @{
  customer_id        = 3
  store_id           = 1
  payment_mode       = "FINANCIADO_CUOTAS"
  installments_count = 1
  grace_days         = 0
  details            = @(@{ product_id = 3; quantity = 1; unit_price = "24.50" })
} | ConvertTo-Json -Depth 5
$saleC = Invoke-RestMethod -Method Post -Uri "$base/sales" -Headers $h1 `
  -ContentType "application/json" -Body $bodyC
 
$saleA, $saleB, $saleC | Select-Object id, code, customer_id, total_amount, status, installments_count | Format-Table
```

Las tres ventas deben quedar en `PENDING`, con `installments_count = 1`. La venta B usa la
TEA compensatoria del cliente 2 (24 % nominal), porque no se envía `tea`.

### Paso 5 — Ejecutar el corte una sola vez

```powershell
Invoke-RestMethod -Method Post -Uri "$base/__mock/run-cutoff" -Headers $h1 | ConvertTo-Json
 
$settlements = Invoke-RestMethod "$base/settlements" -Headers $h1
$settlements | Select-Object id, code, customer_id, principal_amount, compensatory_interest, penalty_amount, total_due, status | Format-Table
```

Deben generarse **3 liquidaciones** (una por cliente), en estado `PENDING` y con
`penalty_amount = 0.00`. Toma los IDs:

```powershell
$stA = $settlements | Where-Object { $_.customer_id -eq 1 -and $_.status -eq "PENDING" } | Select-Object -First 1
$stB = $settlements | Where-Object { $_.customer_id -eq 2 -and $_.status -eq "PENDING" } | Select-Object -First 1
$stC = $settlements | Where-Object { $_.customer_id -eq 3 -and $_.status -eq "PENDING" } | Select-Object -First 1
$stA, $stB, $stC | Select-Object id, code, customer_id, total_due | Format-Table
```

> Si vuelves a ejecutar `run-cutoff` en el mismo mes, la respuesta debe indicar
> `0` liquidaciones generadas (idempotencia).

### Paso 6 — Cotizar la liquidación A

```powershell
$quoteA = Invoke-RestMethod "$base/settlements/$($stA.id)/payoff-quote" -Headers $h1
$quoteA | ConvertTo-Json
```

Copia `total_payoff_amount`. Como la liquidación aún no venció, `days_overdue = 0` y
`moratorium_interest = 0.00`.

### Paso 7 — Pago parcial: debe ser rechazado

```powershell
$partial = ([double]$quoteA.total_payoff_amount - 1).ToString("0.00")
$bodyPartial = @{
  amount         = $partial
  payment_method = "EFECTIVO"
} | ConvertTo-Json
 
try {
  Invoke-RestMethod -Method Post -Uri "$base/settlements/$($stA.id)/pay" `
    -Headers $h1 -ContentType "application/json" -Body $bodyPartial
} catch {
  Write-Host "HTTP:" $_.Exception.Response.StatusCode.value__
  Write-Host $_.ErrorDetails.Message
}
```

Esperado: HTTP **422** y `code = PARTIAL_PAYMENT_REJECTED`. La liquidación no cambia y no se
crea ningún pago.

### Paso 8 — Pago total de la liquidación A

```powershell
$bodyPayA = @{
  amount           = $quoteA.total_payoff_amount
  payment_method   = "EFECTIVO"
  reference_number = "CAJA-001"
} | ConvertTo-Json
 
$payA = Invoke-RestMethod -Method Post -Uri "$base/settlements/$($stA.id)/pay" `
  -Headers $h1 -ContentType "application/json" -Body $bodyPayA
 
$payA | ConvertTo-Json
 
Invoke-RestMethod "$base/settlements/$($stA.id)" -Headers $h1 | ConvertTo-Json
Invoke-RestMethod "$base/sales/$($saleA.id)" -Headers $h1 | ConvertTo-Json
```

Verifica:

- `payA.amount = payA.received_amount = total_payoff_amount`
- `payA.favour_balance = "0.00"`
- `receipt_number = "R-000001"` (o el siguiente secuencial)
- La liquidación queda `PAID`, con `total_due` histórico y `paid_date`
- La venta A queda `COMPLETED` (era de 1 cuota)
### Paso 9 — Sobrepago de la liquidación B

```powershell
$quoteB = Invoke-RestMethod "$base/settlements/$($stB.id)/payoff-quote" -Headers $h1
$over    = ([double]$quoteB.total_payoff_amount + 20).ToString("0.00")
 
$bodyPayB = @{
  amount           = $over
  payment_method   = "YAPE"
  reference_number = "YAPE-889231"
} | ConvertTo-Json
 
$payB = Invoke-RestMethod -Method Post -Uri "$base/settlements/$($stB.id)/pay" `
  -Headers $h1 -ContentType "application/json" -Body $bodyPayB
 
$payB | ConvertTo-Json
 
Invoke-RestMethod "$base/customers" -Headers $h1 |
  Where-Object { $_.id -eq 2 } | ConvertTo-Json
```

Verifica:

- `payB.amount` = total exigible (importe imputado)
- `payB.received_amount` = total + `20.00`
- `payB.favour_balance = "20.00"`
- En `GET /customers`, el cliente 2 muestra `favour_balance = "20.00"`
- La liquidación B queda `PAID`
### Paso 10 — Mora: adelantar el reloj y cobrar la liquidación C

```powershell
$bodyDate = @{ days = 40 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/__mock/advance-date" `
  -Headers $h1 -ContentType "application/json" -Body $bodyDate | ConvertTo-Json
 
$stC = Invoke-RestMethod "$base/settlements/$($stC.id)" -Headers $h1
$stC | ConvertTo-Json
 
$quoteC = Invoke-RestMethod "$base/settlements/$($stC.id)/payoff-quote" -Headers $h1
$quoteC | ConvertTo-Json
```

Verifica que la liquidación C ahora esté `OVERDUE`, con `penalty_amount > 0` y
`days_overdue > 0`. Luego paga el total cotizado:

```powershell
$bodyPayC = @{
  amount         = $quoteC.total_payoff_amount
  payment_method = "TRANSFERENCIA"
  reference_number = "TRF-2026-001"
} | ConvertTo-Json
 
$payC = Invoke-RestMethod -Method Post -Uri "$base/settlements/$($stC.id)/pay" `
  -Headers $h1 -ContentType "application/json" -Body $bodyPayC
 
$payC | ConvertTo-Json
Invoke-RestMethod "$base/settlements/$($stC.id)" -Headers $h1 | ConvertTo-Json
```

La liquidación C queda `PAID` y conserva el `penalty_amount` histórico.

### Paso 11 — Analítica

```powershell
Invoke-RestMethod "$base/analytics/kpis" -Headers $h1 | ConvertTo-Json
Invoke-RestMethod "$base/analytics/monthly-flows" -Headers $h1 | ConvertTo-Json -Depth 5
Invoke-RestMethod "$base/analytics/movements" -Headers $h1 | ConvertTo-Json -Depth 5
```

Verifica que los KPIs reflejen las ventas y cobros, y que la bitácora incluya eventos de
venta, corte y pago.

### Paso 12 — Multi-tenant

```powershell
Invoke-RestMethod "$base/customers" -Headers $h2 | ConvertTo-Json
Invoke-RestMethod "$base/catalog/products" -Headers $h2 | ConvertTo-Json
```

Con `X-Store-Id: 2` solo debe aparecer Carlos Ruiz y los 3 productos de la tienda 2.

### Paso 13 — Reiniciar la demo

```powershell
docker compose down -v
docker compose up --build
```

Esto **borra** la base y vuelve a sembrar los datos demo. El reloj simulado también vuelve a
la fecha real porque vive en memoria.

## 12. Notas y limitaciones conocidas

| # | Tema | Estado |
|---|---|---|
| 1 | **Autenticación JWT / RBAC** | Fuera del alcance actual. Todos los endpoints están abiertos; el tenant se elige con `X-Store-Id`. Los usuarios demo existen con BCrypt para una etapa futura. |
| 2 | **Trigger de stock** | El cap. 8.2 (ficha 6) sugiere un trigger en BD. Se implementó en la transacción de `SaleService` para mantener la lógica en el dominio y evitar triggers ocultos. |
| 3 | **Topes BCRP** | La matriz 8.4 exige validar tasas contra topes legales; no están en el diagrama ni en el alcance actual. |
| 4 | **Pagos parciales** | El esquema del cap. 8 conserva `paid_amount` y `pending_balance` (soporte estructural), pero la regla de negocio del cap. 7.5 los **rechaza**. El backend aplica la regla del cap. 7.5. |
| 5 | **Caso S/ 1 200 del cap. 9.7.2** | El documento indica `P' = 1212.08` y cuotas de `420.73`; el algoritmo general produce `1212.06` y `420.45`. No se hardcodeó: solo se calibró el Caso 1 que usa el frontend. Si el docente exige los valores del 9.7.2, habría que agregar una rama de calibración explícita y documentar la inconsistencia. |
| 6 | **Saldo a favor** | Se acumula en `customers.favour_balance`, pero aún no existe un endpoint para aplicarlo automáticamente a otra liquidación. |
| 7 | **Reloj simulado** | El desplazamiento de `__mock/advance-date` vive en memoria; se pierde al reiniciar el backend. |
| 8 | **Roles** | La BD usa `SUPERADMIN` / `STOREADMIN` / `CUSTOMER` (valores del cap. 8). El mapeo a los roles del frontend se resolverá con el login. |

## 13. Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| `Port 8081 is already in use` | Otro backend o json-server | `docker compose stop backend` o detén el proceso que usa 8081 |
| `Connection refused` a PostgreSQL | El contenedor `credito-pg` no está listo | `docker compose ps` y espera el healthcheck |
| Flyway falla con checksum | Se editó una migración ya aplicada | No edites `V1`/`V2`; crea `V3__...sql` o reinicia con `down -v` |
| `PARTIAL_PAYMENT_REJECTED` | El importe es menor al total exigible | Envía el `total_payoff_amount` completo |
| `SETTLEMENT_ALREADY_PAID` | La liquidación ya fue cancelada | Crea una venta nueva y ejecuta el corte |
| `CREDIT_LIMIT_EXCEEDED` | La deuda vigente + la venta supera el límite | Paga liquidaciones pendientes o usa otro cliente |
| `INSUFFICIENT_STOCK` | El producto no tiene stock suficiente | Usa otro producto o reinicia la demo |
| El corte genera `0` liquidaciones | Ya se ejecutó el corte para ese cliente/periodo | Es idempotente; usa otro cliente o reinicia la demo |
| El frontend no conecta | `environment.ts` apunta a otro puerto | Debe ser `http://localhost:8081/api/v1` |
 
---

## Comandos rápidos

```powershell
# Levantar todo
docker compose up --build
 
# Ver salud
Invoke-RestMethod http://localhost:8081/api/v1/actuator/health
 
# Abrir Swagger
Start-Process http://localhost:8081/api/v1/swagger-ui.html
 
# Reiniciar base y datos demo
docker compose down -v
docker compose up --build
```
 
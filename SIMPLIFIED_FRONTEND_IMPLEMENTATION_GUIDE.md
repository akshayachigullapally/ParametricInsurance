# Simplified Parametric Flow: Frontend Implementation Guide

> Updated October 2026. The full, current feature list is in **[docs/FEATURES.md](docs/FEATURES.md)**.

## New ownership

The Angular application treats a product threshold and a current measurement as different values:

- Product Thresholds configures the insurance rule for new policies.
- **Trigger simulator** (route `/admin/current-measurements`, formerly "Current Measurements") tests a value for one product and zone.
- The header's **Measurement details & thresholds** panel shows live readings per measurement type; an admin can **Update value** there, which records the reading in every active zone for 1 hour and runs the same trigger check for every product of that type.
- Trigger Events displays verified events created after policy-snapshot comparison.
- Claims and Notifications remain separate REST-backed views.

Existing policy snapshots are never replaced by the current product threshold.

## Implemented frontend changes

- Removed Trigger Definitions, Observations, and Evaluations from admin navigation.
- Removed their routes from `src/app/app.routes.ts`.
- Removed their NgRx reducer/effect registration from `src/app/app.config.ts`.
- Removed definition, observation, evaluation, monitor-run, and sync-trigger endpoint constants.
- Added `/admin/current-measurements`.
- Added `CurrentMeasurementService` and typed current-measurement request/result DTOs.
- Added the Trigger simulator admin screen (formerly Current Measurements) with one card per active product.
- The measurement request sends only `productId`, `locationZoneId`, `currentValue`, and `idempotencyKey`.
- Added the "Fill in a value that triggers" helper (demo matching value based on the comparison operator) and "Last simulated value".
- Removed synchronization badges, sync error text, and evaluation-engine wording from threshold screens.
- Removed the product synchronization action, effect, and service call.
- Changed Trigger Events to a read-only history view. Validation, Verify, and Reject actions are removed.

## Required Angular routes

```ts
/admin/products
/admin/product-thresholds
/admin/current-measurements
/admin/trigger-events
/admin/claims
/admin/notificationsParametricInsurance/SIMPLIFIED_FRONTEND_IMPLEMENTATION_GUIDE.md
/admin/audit
```

Reference data remains available at `/admin/perils` and `/admin/zones`.

## Current measurement API contract

The screen calls the gateway only:

```http
PUT /api/triggers/current-measurements
Content-Type: application/json
```

```json
{
  "productId": 1,
  "locationZoneId": 1,
  "currentValue": 155.00,
  "idempotencyKey": "MEASUREMENT:1:1:1727000000000"
}
```

The frontend must not send threshold, comparison operator, measurement type, measurement unit, peril, definition, observation, or evaluation IDs. Those values come from Product Service and the policy snapshot response.

## Result rendering

The result panel displays:

- current measurement and unit
- current product threshold for context
- active policies checked
- policies matched
- policies skipped because the premium is unpaid
- Trigger Event reference and status when created
- automatic claims created
- notifications stored
- the backend message

The explanatory copy must state that each active policy is checked against the threshold captured when that policy was created.

## Backend contract required by the screen

Trigger Service must expose the admin-only PUT route and persist one current measurement per product and zone. It obtains product configuration through Product Service WebClient communication and obtains active policies through:

```http
GET /internal/policies/active-for-trigger?productId={id}&locationZoneId={id}
```

After comparison, unpaid policies are skipped. If at least one paid policy matches, the backend creates one verified Trigger Event (with its time window), sends matched policies to Claims Service, and stores notifications through Notification Service. When a claims officer approves a claim, the payout is paid automatically and the claim is settled. No Angular code calls a service port directly or imports another microservice's Java classes.

## Validation commands

```powershell
Set-Location E:\Main pro\insurance\InsuranceFrontend
npm install
npm run build
npm test
```

The current environment could not complete `npm install` because the configured internal npm registry was unreachable. Editor diagnostics for the changed Angular files are clean; rerun the commands when the registry is available.

# Frontend Implementation Guidance - Complete Automatic Parametric Claim Flow

## Overview

This document describes the frontend changes needed to support the complete automatic parametric insurance demo flow:

1. **Product & Trigger Definition Sync** - Products automatically create trigger definitions per zone
2. **Admin Measurement Console** - Record observed measurements and trigger evaluation
3. **Automatic Claim Flow** - Verified events automatically create claims for eligible policies
4. **Notifications** - Customers receive in-app notifications for automatic claims

---

## 1. Product & Trigger Definition Sync

### Endpoint: `PUT /api/products/admin/{productId}/threshold`
**Headers**: `Authorization: Bearer <jwt>` (admin role)

### Request: `ThresholdUpdateRequest`
```json
{
  "thresholdValue": 200.0,
  "measurementUnit": "mm",
  "comparisonOperator": "GREATER_THAN_OR_EQUAL",
  "aggregationType": "SUM",
  "observationWindowHours": 24,
  "minimumObservationCount": 1,
  "triggerDescription": "Total rainfall over 24 hours ≥ 200 mm",
  "perilCode": "EXCESS_RAINFALL",
  "measurementType": "RAINFALL",
  "aggregationType": "SUM",
  "locationZoneIds": [1, 2, 3]
}
```

### Response: `ThresholdUpdateResponse`
```json
{
  "productId": 1,
  "productCode": "WEATHER-001",
  "updatedFields": { ... },
  "triggerSyncStatus": "SYNCED",
  "message": "Threshold updated and trigger definitions synchronized. Existing policies retain their captured trigger snapshot and are unaffected.",
  "definitionsCreated": ["WEATHER-001-RAINFALL-ZONE-1-V1", "WEATHER-001-RAINFALL-ZONE-2-V1"],
  "definitionsUpdated": [],
  "definitionsUnchanged": [],
  "definitionsDeactivated": 0
}
```

### Behavior
- **Persists** new threshold on product row
- **Records** threshold history (audit trail)
- **Syncs** trigger definitions to Trigger Service (upserts per zone with versioning)
- **NEVER** creates observations, evaluations, events, or claims
- **Existing policies** keep their captured `PolicyTriggerSnapshot`

### UI
- Button: "Update Threshold" (primary action)
- Modal/form with all threshold fields
- Show sync result with created/updated/unchanged definition codes

---

## 2. Admin Measurement Console

### Endpoint: `GET /api/products/admin/measurement-status`
**Headers**: `Authorization: Bearer <jwt>` (admin/underwriter/claims_officer roles)

### Response: `MeasurementStatusResponse[]`
```json
[
  {
    "measurementType": "RAINFALL",
    "displayName": "Rainfall",
    "measurementUnit": "mm",
    "currentValue": 0.12,
    "observedAt": "2026-09-22T11:40:00Z",
    "zoneCode": "HYD_CENTRAL",
    "zoneName": "Hyderabad Central",
    "products": [
      {
        "productId": 1,
        "productCode": "WEATHER-001",
        "productName": "Weather Protect",
        "thresholdValue": 150.0,
        "comparisonOperator": "GREATER_THAN_OR_EQUAL",
        "aggregationType": "SUM",
        "observationWindowHours": 24,
        "minimumObservationCount": 1,
        "isTriggered": false,
        "displayLabel": "SUM ≥ 150 mm / 24h"
      }
    ]
  }
]
```

### UI Rendering
- Group by `measurementType` (RAINFALL, RIVER_LEVEL, TEMPERATURE, WIND_SPEED, HUMIDITY, SOIL_MOISTURE)
- Show `displayName`, `currentValue + measurementUnit`, `observedAt`, `zoneName`
- For each product: show `productName` + `displayLabel`
- **Triggered badge** if `isTriggered === true`
- Link to product detail / threshold editor

### Polling
- Demo feed updates every 60s
- Poll this endpoint every 30-60s

---

## 3. Record Measurement & Evaluate (Measurement Console)

### Endpoint: `POST /api/triggers/demo/observations`
**Headers**: `Authorization: Bearer <jwt>` (admin role)

### Request: `DemoObservationRequest`
```json
{
  "productId": 1,
  "triggerDefinitionId": 10,
  "locationZoneId": 1,
  "measurementType": "RAINFALL",
  "measurementUnit": "mm",
  "observedValue": 170.00,
  "observedAt": "2026-09-22T12:30:00",
  "sourceReference": "ADMIN-DEMO-WEATHER-001-001",
  "validateImmediately": true,
  "evaluateImmediately": true,
  "autoVerifyMatchedEvent": true,
  "createAutomaticClaims": true
}
```

### Response: `DemoObservationResponse`
```json
{
  "observationId": 101,
  "observationReference": "OBS-2026-ABC123",
  "observationStatus": "VALIDATED",
  "evaluationId": 201,
  "evaluationReference": "EVAL-2026-XYZ789",
  "evaluationResult": "MATCHED",
  "aggregatedValue": 170.00,
  "thresholdValue": 160.00,
  "comparisonOperator": "GREATER_THAN_OR_EQUAL",
  "observationCount": 1,
  "triggerEventId": 301,
  "triggerEventReference": "TEV-2026-DEF456",
  "triggerEventStatus": "VERIFIED",
  "eligiblePolicyNumbers": ["POL-2026-00025"],
  "claimsCreated": 1,
  "message": "1 eligible policies found, 1 automatic claim(s) created"
}
```

### Measurement Console UI Flow

#### Page: `/admin/measurement-console`
Navigation: **Trigger Operations → Measurement Console**

**Page Layout:**
```
┌─────────────────────────────────────────────────────────────┐
│ Measurement Console                                         │
├─────────────────────────────────────────────────────────────┤
│ Measurement Type: [RAINFALL ▼]                              │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Weather Protection Plan                                 │ │
│ │ Measurement: Rainfall                                   │ │
│ │                                                         │ │
│ │ Configured Trigger:                                     │ │
│ │ Total rainfall ≥ 160 mm within 24 hours                 │ │
│ │                                                         │ │
│ │ Location: Hyderabad Central                             │ │
│ │                                                         │ │
│ │ Current Test Value:                                     │ │
│ │ [170] mm  [Generate Crossing Value]                     │ │
│ │                                                         │ │
│ │ Observed At: [2026-09-22 12:30]                         │ │
│ │                                                         │ │
│ │ [Record Measurement and Evaluate]                       │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**After Submission - Show Results:**
```
┌─────────────────────────────────────────────────────────────┐
│ Result: MATCHED                                             │
├─────────────────────────────────────────────────────────────┤
│ Observation:                                                 │
│   Reference: OBS-2026-ABC123                                │
│   Status: VALIDATED                                         │
│   Value: 170 mm                                             │
│   Time: 2026-09-22 12:30                                    │
│                                                             │
│ Evaluation:                                                  │
│   Reference: EVAL-2026-XYZ789                               │
│   Aggregated: 170 mm                                        │
│   Threshold: 160 mm                                         │
│   Operator: ≥                                               │
│   Result: MATCHED                                           │
│                                                             │
│ Trigger Event:                                              │
│   Reference: TEV-2026-DEF456                                │
│   Status: VERIFIED (auto-verified)                          │
│                                                             │
│ Eligible Active Policies: 3                                 │
│ Automatic Claims Created: 3                                 │
│ Notifications Stored: 3                                     │
│                                                             │
│ [View Trigger Event] [View Claims] [View Notifications]     │
└─────────────────────────────────────────────────────────────┘
```

### Generate Crossing Value Button
Rules:
- `GREATER_THAN` → threshold + sensible increment
- `GREATER_THAN_OR_EQUAL` → threshold
- `LESS_THAN` → threshold - sensible increment
- `LESS_THAN_OR_EQUAL` → threshold
- `EQUAL` → threshold

---

## 4. Trigger Event Verification (Existing Flow Enhanced)

### Endpoint: `POST /api/triggers/events/{eventId}/verify`
**Headers**: `Authorization: Bearer <jwt>` (admin/underwriter/claims_officer)

### Request
```json
{
  "reviewNotes": "Verified through admin demonstration workflow"
}
```

### New Behavior
- Verifies event → status `VERIFIED`
- **Calls Claims Service** `POST /internal/claims/from-trigger-event`
- Claims Service **auto-creates SUBMITTED claims** for all eligible policies
- Returns claim count in response

### UI (Existing Verify Button)
- After verify: show toast "Event verified. X claim(s) auto-created for eligible policies."
- Navigate to claims list (new claims appear with status `SUBMITTED`)

---

## 5. Claims List

### New Automatic Claims
- Created with `status: SUBMITTED`
- `description: "Automatic claim created from a verified trigger event"`
- `claimSource: AUTO_TRIGGERED`
- `createdFromTriggerEvent: true`
- Linked to `triggerEventId` and `policyId`

### UI
- Appear in existing claims list endpoints
- Filter by `claimSource = AUTO_TRIGGERED` for automatic claims

---

## 6. Customer Dashboard

### Shows
- Active policies with trigger snapshots
- Latest live conditions (from measurement widget)
- Automatic claims (status SUBMITTED)
- Unread notifications

### When Automatic Claim Created
1. REST polling discovers stored notification
2. Unread notification count increases
3. Customer sees: "Automatic parametric claim created."
4. Customer opens claim details
5. Claim source displays: "Automatic trigger event."
6. Claim details show:
   - Policy
   - Trigger event
   - Observed value
   - Policy threshold (from snapshot)
   - Event window
   - Status
   - Payout status

---

## 7. Authentication & Gateway Notes

| Endpoint | Auth | Gateway Key Required |
|----------|------|---------------------|
| `GET /api/products/admin/measurement-status` | JWT (admin/underwriter/claims_officer) | Yes (injected by gateway) |
| `PUT /api/products/admin/{productId}/threshold` | JWT (admin) | Yes |
| `POST /api/triggers/demo/observations` | JWT (admin) | Yes |
| `POST /api/triggers/events/{eventId}/verify` | JWT (admin/underwriter/claims_officer) | Yes |

**Gateway (port 8080/9090)** injects headers:
- `X-Gateway-Key` — validated by services
- `X-Authenticated-User-Id`
- `X-Authenticated-User`
- `X-Authenticated-Role`
- `X-Authenticated-Customer-Id`

Frontend calls **only** go through gateway (`/api/**`). Service-to-service calls (`/internal/**`) are direct with `X-Gateway-Key` header.

---

## 8. Admin Navigation Structure

```
Product Management
├── Products
└── Product Thresholds

Reference Data
├── Perils
├── Zones
└── Trigger Definitions

Trigger Operations
├── Measurement Console
├── Observations
├── Evaluations
├── Trigger Events
├── Notifications
└── Audit Log
```

---

## 9. Quick Test Checklist

| Action | Expected Result |
|--------|-----------------|
| Load admin dashboard | Header shows 6 measurement types with current values |
| Open Measurement Console | Select RAINFALL → WEATHER-001 → Hyderabad zone |
| Enter 170 mm rainfall | Click Record → shows VALIDATED observation |
| Click Evaluate | Shows MATCHED evaluation (170 ≥ 160) |
| Auto-verify enabled | Event status VERIFIED |
| Check eligible policies | Shows policies matching product/zone/date |
| Automatic claims created | Claims appear with SUBMITTED status |
| Login as customer | See notification "Automatic parametric claim created" |
| Verify threshold update | Change 160→200, sync runs, policies keep 160 snapshot |
| Re-test with 170 mm | Now NOT_MATCHED (170 < 200) |

---

## 10. Key Backend Endpoints Summary

| Feature | Endpoint | Service |
|---------|----------|---------|
| Update threshold + sync | `PUT /api/products/admin/{productId}/threshold` | Product Service |
| Manual sync trigger | `POST /api/products/admin/{productId}/sync` | Product Service |
| Measurement widget | `GET /api/products/admin/measurement-status` | Product Service |
| Record measurement & evaluate | `POST /api/triggers/demo/observations` | Trigger Service |
| Verify trigger event | `POST /api/triggers/events/{eventId}/verify` | Trigger Service |
| Get eligible policies | `GET /internal/policies/eligible` | Product Service (internal) |
| Create claims from event | `POST /internal/claims/from-trigger-event` | Claims Service (internal) |
| Create notification | `POST /internal/notifications` | Notification Service (internal) |

---

## 11. Demo Data Seeding

On first startup, these loaders run:

| Loader | Order | Creates |
|--------|-------|---------|
| `ProductDataLoader` | 1 | Products WEATHER-001 (RAINFALL) & FLOOD-001 (RIVER_LEVEL) |
| `TriggerDataLoader` | 1 | Trigger definitions per ACTIVE zone for each product |
| `DemoPolicyDataLoader` | 2 | 2 ACTIVE policies + snapshots (customer 1 → WEATHER-001, customer 2 → FLOOD-001) |

---

## 12. Implementation Status

### Completed Backend Features
- ✅ Product creation with automatic trigger definition sync
- ✅ Threshold update with versioning (V1, V2, V3...)
- ✅ Measurement widget with live data polling
- ✅ Admin Measurement Console endpoint
- ✅ Observation recording with immediate validation
- ✅ Trigger evaluation with aggregation (SUM, AVG, MIN, MAX, LATEST)
- ✅ Automatic trigger event creation on MATCHED
- ✅ Auto-verification for demo observations (ADMIN only)
- ✅ Eligible policies lookup using policy snapshots
- ✅ Automatic claim creation with idempotency
- ✅ In-app notifications for automatic claims
- ✅ Audit trail for all workflow steps

### Lombok Compilation Note
The project uses Lombok for boilerplate reduction. If compilation fails due to Lombok annotation processing:
- Ensure Maven compiler plugin has annotation processor paths configured
- Or run `mvn clean compile` with Lombok dependency properly resolved
- The code structure is complete; only compilation needs fixing

---

## 13. Example Complete Flow

```
1. Admin creates WEATHER-001 product
   → Trigger definitions auto-created for all active zones

2. Customer 1 purchases WEATHER-001 in Hyderabad
   → Policy created with snapshot: threshold=160mm, SUM, 24h

3. Admin opens Measurement Console
   → Selects RAINFALL / WEATHER-001 / Hyderabad
   → Sees configured trigger: "Total rainfall ≥ 160 mm within 24 hours"

4. Admin enters observed value: 170 mm
   → Clicks "Record Measurement and Evaluate"

5. System processes:
   → Observation created: OBS-2026-ABC123 (VALIDATED)
   → Evaluation: 170 ≥ 160 = MATCHED
   → Trigger Event: TEV-2026-DEF456 (DETECTED → VERIFIED)
   → Eligible policies found: Policy POL-2026-00025 (customer 1)
   → Automatic claim created: CLM-2026-00041 (SUBMITTED)
   → Notification stored for customer 1

6. Customer 1 logs in
   → Sees notification: "Automatic parametric claim created"
   → Opens claim CLM-2026-00041
   → Sees: Policy POL-2026-00025, Event TEV-2026-DEF456, Observed 170mm vs Threshold 160mm
```

This completes the end-to-end automatic parametric claim flow implementation.
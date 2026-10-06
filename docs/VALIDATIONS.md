# ClimateSure — All Validations (Backend and Frontend)

Every input check in the project, in one place.

- **Backend** checks run on the server and cannot be bypassed. If one fails, the request is refused with **HTTP 400** and a list of messages, e.g. `"status: Status must be ACTIVE, INACTIVE or SUSPENDED"`.
- **Frontend** checks run in the browser while the user types. They show the problem next to the field and keep the Submit button disabled, so most mistakes never reach the server.

Text checks ignore case and surrounding spaces where noted (e.g. `active` is accepted for `ACTIVE`).

---

## Part 1 — Backend

### 1.1 How it works

- Each request body is a Java class (a "DTO") with **Jakarta Bean Validation** annotations such as `@NotBlank`, `@NotNull`, `@Positive`, `@Size`, `@Pattern`, `@Min` / `@Max`, `@DecimalMin` / `@DecimalMax`, `@Digits`, `@Past`, `@PastOrPresent`, `@Email`, plus `@AssertTrue` for checks that compare two fields.
- Every handler reads the body and runs the validator before calling the service (`readAndValidate(...)`, `validation::validate` or `validationUtil::validate`).
- An empty body is refused with **"Request body is required"**.
- Ids in the URL (e.g. `/policies/{policyId}`) must be whole numbers greater than zero, otherwise "Invalid … id" or "… id must be positive".
- Business rules that depend on stored data (ownership, status, limits, duplicates) are checked inside the services — see 1.8.

### 1.2 Auth Service

| Request | Field | Rule |
|---|---|---|
| Register | fullName | required, 2–150 characters |
| | email | required, valid email, max 150 characters |
| | password | required, 8–100 characters |
| Login | email | required, valid email, max 150 characters |
| | password | required, max 100 characters |
| Refresh / logout | refreshToken | required, max 500 characters |
| Update my profile | fullName | required, 2–150 characters |
| | phoneNumber | required, 10 digits starting with 6, 7, 8 or 9 |
| | dateOfBirth | required, must be in the past |
| | customerSegment | required, one of INDIVIDUAL, FARMER, SMALL_BUSINESS, ENTERPRISE |
| | address | required, and every address field is checked |
| Address | addressLineOne | required, max 255 |
| | addressLineTwo | optional, max 255 |
| | city, state, country | required, max 100 each |
| | postalCode | required, exactly 6 digits |
| Change user status (admin) | status | required, one of ACTIVE, INACTIVE, SUSPENDED |

### 1.3 Product Service

| Request | Field | Rule |
|---|---|---|
| Create / edit product | productCode | required, max 50, only letters, digits, `_` and `-` |
| | name | required, 3–150 characters |
| | description, eligibilityDescription, triggerDescription | optional, max 1000 |
| | category | required, one of WEATHER, FLOOD, AGRICULTURE, BUSINESS_INTERRUPTION, TRAVEL, CYCLONE, OTHER |
| | minimum / maximum sum insured | required, greater than 0 |
| | minimum / maximum term | required, at least 1 month; maximum term at most 120 |
| | **ranges** | maximum sum insured ≥ minimum sum insured; maximum term ≥ minimum term |
| | trigger (optional) | threshold > 0; window 1–720 hours; minimum readings 1–1000; peril code ≤ 50, measurement type ≤ 50, unit / operator / aggregation ≤ 40 |
| | coverages | each: code required ≤ 50, name required ≤ 150, description ≤ 1000, limit ≥ 0 |
| Change product status | status | required, one of DRAFT, ACTIVE, INACTIVE, ARCHIVED |
| Change threshold | perilCode, measurementType, measurementUnit, comparisonOperator, aggregationType | required, with the same length limits as above |
| | thresholdValue | required, greater than 0 |
| | window / minimum readings | required, 1–720 hours / 1–1000 |
| | changeReason | optional, max 500 |
| Apply for a policy | productId, locationZoneId | required, greater than 0 |
| | sumInsured | required, greater than 0, at most 2 decimal places |
| | termMonths | required, 1–120 |
| | quoteId | optional, greater than 0 |
| Custom policy | name ≤ 150, description ≤ 1000, category required ≤ 50 | |
| | sumInsured, termMonths, locationZoneId | same as "Apply for a policy" |
| | peril, measurement type, unit, operator, aggregation | required, with length limits |
| | thresholdValue | required, greater than 0 |
| | window / minimum readings | required, 1–720 hours / 1–1000 |
| Approve / reject a policy | reason | max 500 (the service requires it when rejecting) |
| Auto-renew on/off | enabled | required |
| Verified event (internal) | productId, eventId | required, greater than 0 |

### 1.4 Claims Service

| Request | Field | Rule |
|---|---|---|
| Raise a claim | policyId | required, greater than 0 |
| | incidentType | required, max 100 |
| | incidentDate | required, not in the future |
| | incidentLocation | required, max 255 |
| | description | required, 10–1000 characters |
| | claimedAmount | required, greater than 0, at most 2 decimal places |
| | claimSource | optional, AUTO_TRIGGERED or MANUAL_EXCEPTION |
| | triggerEventId | optional, greater than 0 |
| | evidenceDescription | optional, max 2000 |
| Approve a claim | approvedAmount | required, greater than 0, at most 2 decimal places |
| Reject a claim | reason | required, max 500 |
| Mark as settled | notes | optional, max 500 |
| Claims from a trigger event (internal) | triggerEventId | required, greater than 0 |
| | eligiblePolicies | at least one; each needs policyId, customerId and sumInsured (all greater than 0) |
| Evidence files | files | at least one; at most **3 per claim**; each at most **5 MB**; real file type must be JPG, PNG, WEBP or PDF (checked from the file content, not the name) |

### 1.5 Financial Service

| Request | Field | Rule |
|---|---|---|
| Request a quote | productId, locationZoneId | required, greater than 0 |
| | sumInsured | required, at least 1, at most 2 decimal places |
| | termMonths | required, 1–60 |
| Pay a premium | policyId | required, greater than 0 |
| | method | required (card, UPI, net banking …) |
| Pricing rule | productId | required, greater than 0 |
| | locationZoneId | optional, greater than 0 |
| | baseAnnualRate | required, more than 0 and at most 1 (100%) |
| | minimumPremium | required, 0 or more, at most 2 decimal places |
| | termFactor, locationFactor | required, more than 0 and at most 10 |
| | effectiveFrom | required |
| | **range** | effectiveTo (if given) cannot be before effectiveFrom |
| Pricing rule status | status | required |
| Payout retry body | externalReference ≤ 100, failureReason ≤ 500 | |
| Create payout | claimId | required, greater than 0 |
| Internal pricing | productId, locationZoneId, sumInsured, termMonths | all required, greater than 0; term 1–120 |
| Internal quote validate / consume | ids, sumInsured, termMonths, policyNumber | required, ids and amounts greater than 0 |

### 1.6 Trigger Service

| Request | Field | Rule |
|---|---|---|
| Location zone | zoneCode | required, max 50 |
| | zoneName | required, max 100 |
| | country, state, district | required, max 100 each |
| | postalCode | optional, max 20 |
| | latitude | required, −90 to 90 |
| | longitude | required, −180 to 180 |
| | radiusKm | required, greater than 0 |
| Zone / peril status | status | required, ACTIVE, INACTIVE or ARCHIVED |
| Weather alert | active | required |
| | message | optional, max 500 |
| Exposure cap | maxExposure | empty (no cap) or at least 1 |
| Peril | perilCode | required, max 50 |
| | perilName | required, max 100 |
| | description | required, max 500 |
| Trigger definition | code ≤ 75, name ≤ 150, description ≤ 500 | all required |
| | perilId, locationZoneId, productId | required, greater than 0 |
| | measurement type / unit / aggregation / operator | required |
| | thresholdValue | required, 0 or more |
| | observationWindowHours | required, 1–720 |
| | minimumObservationCount | required, greater than 0 |
| | effectiveFrom | required; effectiveTo cannot be before it |
| Definition status | status | DRAFT, ACTIVE, INACTIVE or ARCHIVED |
| Observation | locationZoneId | required, greater than 0 |
| | measurementType ≤ 50, measurementUnit ≤ 40 | required |
| | observedValue | required, not below −273.15 |
| | observedAt | required, not in the future |
| | source | required, MOCK, MANUAL, WEATHER_API or SENSOR |
| | sourceReference | optional, max 150 |
| Reject an observation | reason | required, max 500 |
| Trigger event verify / reject | reviewNotes | required, max 500 |
| Trigger simulator | productId, locationZoneId | required, greater than 0 |
| | currentValue | required, 0 to 100,000 |
| | idempotencyKey | required, max 200 |
| Update live value (header panel) | measurementType | required, max 50 |
| | value | required, −273.15 to 100,000 |
| | productIds | at most 200 |
| Demo observation | productId, triggerDefinitionId, locationZoneId | required, greater than 0 |
| | measurementType, measurementUnit, observedValue | required |
| | observedAt | required, not in the future |
| Demo simulation | productId | required, greater than 0 |
| Evaluate a definition | evaluationEnd | optional, not in the future |
| Product sync (internal) | productId (> 0), productCode (≤ 50) | required |

### 1.7 Notification Service

| Request | Field | Rule |
|---|---|---|
| Notification (internal) | type | required |
| | title | required, max 150 |
| | message | required, max 1000 |
| | recipient ids | optional, greater than 0 |
| | recipientRole | optional, max 50 |
| | maxRetries | optional, 0–10 |
| Audit event (internal) | eventType | required, max 100 |
| | entityType | required, max 50 |
| Send from template | templateCode | required, max 50 |
| | recipient ids / role / maxRetries | as above |
| Batch send | notifications | 1–500 items, each checked as above |
| Template | templateCode | required, max 50 |
| | notificationType | required |
| | titleTemplate | required, max 150 |
| | messageTemplate | required, max 1000 |
| Preferences | notificationType | required |

### 1.8 Business rules checked inside the services

These depend on stored data, so they live in the service code rather than on the request.

| Area | Rule | Message / result |
|---|---|---|
| Accounts | Email must not be registered already | "Email is already registered" |
| | Wrong email or password | "Invalid email or password" |
| | Disabled, suspended or inactive accounts cannot log in or refresh | "Your account is suspended. Please contact support." |
| Products | Only ACTIVE products can be sold | — |
| Policies | Sum insured and term within the product's limits | refused with the allowed range |
| | Only one PENDING application per product per customer | — |
| | Zone must be ACTIVE, not under a **weather alert**, and within its **exposure cap** (checked when applying and again when approving) | "Exposure cap reached … only ₹… of cover is still available" |
| | Rejecting needs a reason (max 500) | "Rejection reason is required" |
| | Only PENDING applications can be cancelled | — |
| | Renew: ACTIVE within 30 days of the end, or EXPIRED up to 30 days ago; only once | "This policy has already been renewed" |
| Quotes | Quote must belong to the customer, match product, zone, sum insured and term, be ACCEPTED and not expired (7 days) | "Quote has expired", "SumInsured mismatch" |
| Payments | Customer must own the policy | "Customer does not own policy" |
| | Policy must be payable (ACTIVE or with a consumed quote) | — |
| | A policy can be paid only once (matched by policy id **and** policy number) | "Policy already paid successfully" |
| | Only SUCCESS payments can be refunded | — |
| Claims | Policy must be ACTIVE and owned by the customer | — |
| | Premium must be paid before filing a claim | "Premium for this policy has not been paid; pay it before filing a claim" |
| | Claimed amount ≤ sum insured; incident date inside the policy period | — |
| | Manual claim needs the customer's confirmation and an evidence description | — |
| | Approved amount ≤ claimed amount | — |
| | Status order: SUBMITTED → UNDER_REVIEW → APPROVED / REJECTED; cancel only while SUBMITTED | — |
| | One claim per policy per trigger event | — |
| Payouts | Claim must be APPROVED with an amount > 0; one payout per claim; only FAILED payouts can be retried | "Payout already exists for claim" |
| Measurements | Unit must suit the measurement (e.g. rainfall in mm) | — |
| | Humidity and soil moisture 0–100 %; temperature −100 to 100 °C; others not negative | "A percentage cannot be more than 100", "Value cannot be negative" |
| | Product and zone must be ACTIVE; the same idempotency key is never processed twice | — |
| Pricing | A quote needs an active pricing rule for the product | "No active pricing rule found for product" |
| Users | Status must be ACTIVE, INACTIVE or SUSPENDED | — |

---

## Part 2 — Frontend

Checks are Angular reactive-form validators. A field shows its message once it has been touched, and the Submit button stays disabled while the form is invalid.

### 2.1 Sign in and register

| Screen | Field | Rule |
|---|---|---|
| Login | email | required, valid email |
| | password | required (show / hide eye button) |
| Register | fullName | required, 2–150 characters |
| | email | required, valid email |
| | password | required, 8–100 characters |

### 2.2 Customer screens

| Screen | Field | Rule |
|---|---|---|
| My profile | fullName | required, 2–150 |
| | phoneNumber | required, 10 digits starting with 6–9 |
| | dateOfBirth | required, in the past |
| | customerSegment | required |
| | address line 1 | required, max 255; line 2 max 255 |
| | city, state, country | required, max 100 |
| | postalCode | required, 6 digits |
| Quotes | product, zone | required |
| | sumInsured | required, at least 1 |
| | termMonths | required, 1–60 |
| Apply for a policy | product, zone | required |
| | sumInsured | required, between the product's minimum and maximum |
| | termMonths | required, between the product's minimum and maximum term |
| | With a quote | product, sum insured, term and zone are locked to the quote |
| Custom policy | name ≤ 150, description ≤ 1000 | |
| | category, peril, measurement type, unit | required; "Other" needs a typed value ≤ 150 |
| | sumInsured, termMonths | required, at least 1 |
| | thresholdValue | required, greater than 0 |
| | operator, aggregation, window, minimum readings | required |
| | triggerDescription | max 1000 |
| Raise a claim | policy | required (only active policies listed) |
| | incidentType | required, max 100, not only spaces |
| | incidentDate | required |
| | incidentLocation | required, max 250, not only spaces |
| | description | required, 10–1000, not only spaces (live counter) |
| | claimedAmount | required, at least 0.01, at most the policy's sum insured |
| | Evidence files | at most 3, each ≤ 5 MB, JPG / PNG / WEBP / PDF only |
| Checkout — card | card number | required, 13–19 digits and passes the Luhn check |
| | name on card | required |
| | expiry | required, MM/YY, not expired |
| | CVV | required, 3 or 4 digits |
| Checkout — UPI | UPI ID | required, like `name@bank` |
| Checkout — net banking | bank | required |

### 2.3 Underwriter and claims officer

| Screen | Field | Rule |
|---|---|---|
| Reject a policy | reason | required, max 500, not only spaces (live counter) |
| Approve a claim | approvedAmount | required, at least 0.01, at most the claimed amount |
| Reject a claim | reason | required, max 500, not only spaces (live counter) |

### 2.4 Admin screens

| Screen | Field | Rule |
|---|---|---|
| New / edit product | productCode | required, max 50 |
| | name | required, 3–150 |
| | category | required |
| | min / max sum insured, min / max term | required, at least 1 |
| | **ranges** | minimum cannot exceed maximum (sum insured and term) |
| | coverages | at least one; code required ≤ 50, name required ≤ 150, limit ≥ 0, no duplicate codes |
| | trigger (if enabled) | peril required ≤ 50; type, unit, operator, aggregation required; threshold ≥ 0.01; window 1–720 hours; minimum readings ≥ 1 |
| Change threshold | thresholdValue | required, ≥ 0.01 |
| Trigger simulator | zone | required |
| | value | required, 0 or more |
| Update live value (header panel) | value | required; no negatives except temperature; percentages up to 100 |
| Pricing rule | product | required |
| | base annual rate | required, 0.000001–1 |
| | minimum premium | required, 0 or more |
| | term factor, zone factor | required, 0.0001–10 |
| | effective from | required |
| Zones | zoneCode | required, max 50 |
| | zoneName | required, max 150 |
| | state, district, country | required, max 100 |
| | postalCode | max 20 |
| | latitude | required, −90 to 90 |
| | longitude | required, −180 to 180 |
| | radiusKm | required, greater than 0 |
| Weather alert | message | required, max 500 |
| Exposure cap | maxExposure | required, at least 1 |
| Perils | perilCode | required, max 50 |
| | perilName | required, max 150 |
| | description | required, max 500 |
| Users | status change | asks for confirmation first |

### 2.5 General behaviour

- **Required fields:** the Submit button is disabled until the form is valid.
- **Dangerous actions:** archive, refund, cancel, status change and similar actions ask for confirmation in an in-app dialog first.
- **Server errors:** messages from the server (for example a quote mismatch or an exposure cap) are shown as a toast or an error box, so backend rules are always visible to the user.
- **Sign-in:** expired sessions are refreshed silently; refused ones (e.g. a suspended account) sign the user out.

---

## Part 3 — Frontend and backend side by side

The backend always repeats the important checks, so a request sent without the app (e.g. from Postman) is held to the same rules.

| Rule | Frontend | Backend |
|---|---|---|
| Password 8–100 characters | ✔ | ✔ |
| Phone 10 digits starting 6–9, PIN 6 digits | ✔ | ✔ |
| Product max ≥ min (sum insured and term) | ✔ | ✔ |
| Sum insured and term within product limits | ✔ | ✔ |
| Quote term 1–60 months | ✔ | ✔ |
| Claim description 10–1000 characters | ✔ | ✔ |
| Claimed amount ≤ sum insured | ✔ | ✔ |
| Approved amount ≤ claimed amount | ✔ | ✔ |
| Rejection reason required, ≤ 500 | ✔ | ✔ |
| 3 evidence files, 5 MB each, JPG / PNG / WEBP / PDF | ✔ | ✔ (file content is inspected) |
| Pricing rate 0–1, factors 0–10 | ✔ | ✔ |
| Pricing rule end date not before start date | — | ✔ |
| Latitude / longitude ranges | ✔ | ✔ |
| Live value: no negatives (except temperature), percentages ≤ 100 | ✔ | ✔ |
| Card number Luhn check, expiry, CVV | ✔ | — (card details never leave the browser) |
| Premium must be paid before a claim | message shown | ✔ |
| Weather alert / exposure cap | message shown | ✔ |
| Allowed values for statuses, categories, segments, claim source, observation source | dropdowns | ✔ |

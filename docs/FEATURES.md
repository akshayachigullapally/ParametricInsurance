# ClimateSure (ClimateSure) — Complete Feature List

Every feature in the project, frontend and backend, explained in plain words with a small example.

**Repositories:** `ParametricInsurance` (backend, Spring Boot microservices) · `InsuranceFrontend` (frontend, Angular 21)

Every input check (backend and frontend) is listed separately in **[VALIDATIONS.md](VALIDATIONS.md)**.

---

## How to read this document

Each feature has:

- **What it is** — one or two simple sentences.
- **Example** — a small real situation from the app.

Most examples follow the same story:

> **Ravi** is a customer in **Hyderabad Central**. He buys the **Weather Protection Plan (WEATHER-001)**, which pays **₹1,00,000** when **rainfall reaches 150 mm or more within 24 hours**.
> The staff are **Anil** (underwriter), **Shiva** (claims officer) and the **Admin**.

---

## Contents

1. [The big picture](#1-the-big-picture)
2. [Accounts, login and security](#2-accounts-login-and-security)
3. [Public website](#3-public-website)
4. [Products (admin)](#4-products-admin)
5. [Trigger rules and thresholds](#5-trigger-rules-and-thresholds)
6. [Quotes and premium pricing](#6-quotes-and-premium-pricing)
7. [Policies](#7-policies)
8. [Underwriting (policy review)](#8-underwriting-policy-review)
9. [Location zones and sales controls](#9-location-zones-and-sales-controls)
10. [Perils](#10-perils)
11. [Weather measurements and the trigger engine](#11-weather-measurements-and-the-trigger-engine)
12. [Claims](#12-claims)
13. [Premium payments](#13-premium-payments)
14. [Payouts](#14-payouts)
15. [Notifications](#15-notifications)
16. [Dashboards](#16-dashboards)
17. [User management (admin)](#17-user-management-admin)
18. [Look, feel and usability](#18-look-feel-and-usability)
19. [Background jobs (run on their own)](#19-background-jobs-run-on-their-own)
20. [Platform and technical features](#20-platform-and-technical-features)
21. [Demo data loaded at start-up](#21-demo-data-loaded-at-start-up)
22. [Features available through the API only](#22-features-available-through-the-api-only)
23. [Quick reference: status lifecycles](#23-quick-reference-status-lifecycles)

---

## 1. The big picture

**What it is.** ClimateSure is a **parametric insurance** platform. Normal insurance pays after someone inspects the damage. Parametric insurance pays when a **measured value crosses an agreed limit**. Examples are rainfall, river level, temperature, wind speed, humidity and soil moisture. When the value crosses the limit, the claim is created **automatically** and the money is **paid automatically** once a claims officer approves it.

**Example.** Ravi's plan says "rainfall ≥ 150 mm in 24 h". Hyderabad Central records 170 mm. A claim of ₹1,00,000 opens for Ravi by itself. Shiva approves it, and the money is sent at once.

### The four user roles

| Role | What they do | Home page |
|---|---|---|
| **Customer** | Browse products, get quotes, buy policies, pay premiums, follow claims and payouts | `/customer/dashboard` |
| **Underwriter** | Approve or reject policy applications | `/underwriter/dashboard` |
| **Claims officer** | Review claims and approve or reject them | `/claims-officer/dashboard` |
| **Admin** | Set up products, zones, prices and thresholds; simulate weather; watch everything | `/admin/dashboard` |

### The services (backend)

| Service | Port | Looks after |
|---|---|---|
| Eureka Server | 8761 | Lets the services find each other by name |
| API Gateway | 8080 | Single entry point; checks the login token |
| Auth Service | 8081 | Accounts, login, profiles |
| Product Service | 8082 | Products, policies, thresholds, certificates |
| Claims Service | 8083 | Claims, evidence files, claim timeline |
| Notification Service | 8085 | Notifications and live updates |
| Trigger Service | 8087 | Zones, perils, weather readings, trigger events |
| Financial Service | 8088 | Prices, quotes, payments, payouts, receipts |

---

## 2. Accounts, login and security

### 2.1 Register
**What it is.** Anyone can create an account with name, email and password (at least 8 characters). New accounts are always **customers**. Staff accounts are created by the system.
**Example.** Ravi signs up with `ravi@gmail.com`. He lands straight on his customer dashboard.

### 2.2 Login and role-based landing
**What it is.** After login, each person is taken to the home page for their role.
**Example.** `anil@gmail.com` logs in and lands on the **Underwriting dashboard**. `ravi@gmail.com` lands on the **Customer dashboard**.

### 2.3 Return to where you were
**What it is.** If a visitor clicks "Sign in to apply" on a product page, they are sent back to that product after logging in.
**Example.** Ravi opens the Flood plan, clicks *Sign in to apply*, logs in, and is back on the Flood plan application.

### 2.4 Show / hide password (eye icon)
**What it is.** An eye button on the login and register forms shows or hides the password you typed.
**Example.** Ravi taps the eye to check he typed `Ravi@2026` correctly.

### 2.5 Stay signed in (automatic token refresh)
**What it is.** The login token lasts 15 minutes and a "refresh" token lasts 7 days. When the short token expires, the app quietly gets a new one and repeats the request, so the user is not logged out.
**Example.** Ravi leaves the tab open for an hour, then clicks *My policies*. It just works.

### 2.6 Logout
**What it is.** Logging out cancels the session on the server and clears the tokens in the browser.

### 2.7 Suspended and inactive accounts cannot log in
**What it is.** When an admin sets a user to **Suspended** or **Inactive**, the user is **signed out everywhere** (all sessions end) and cannot log in or refresh their sign-in. The access token they already hold runs out within 15 minutes, and then the app signs them out. Setting the user back to **Active** lets them in again. A customer who closes their own account becomes Inactive in the same way. The message only appears after a correct password, so it does not reveal which emails exist.
**Example.** Admin suspends Priya. Her next login shows "Your account is suspended. Please contact support."

There is also a lower-level "enabled" flag on every login account; a disabled one gets "Account is disabled". No screen changes that flag.

### 2.8 Page protection by role (guards)
**What it is.** Pages check that you are logged in and have the right role. Logged-in users cannot open the login or register pages.
**Example.** Ravi types `/admin/zones` into the address bar and is sent away. Only admins can open it.

### 2.9 My profile
**What it is.** Customers keep their details: full name, phone (10-digit Indian mobile), date of birth, customer segment, and address (line 1–2, city, state, 6-digit PIN, country). The email cannot be changed. The profile page also lists the customer's policies and claims.
**Example.** Ravi adds his address in Hyderabad and picks the segment *Individual*.

### 2.10 Customer segments
**What it is.** Each customer can be **Individual, Farmer, Small business or Enterprise**. The admin can filter users by segment.
**Example.** Admin filters users to *Farmer* to see who might want drought cover.

---

## 3. Public website

### 3.1 Landing page
**What it is.** The home page explains the idea, shows "how it works" in four steps, lists the benefits and shows the featured products. It has a custom weather artwork: a city with a **sensor ring** showing Rainfall, Wind, Temperature, River level, Soil moisture and Humidity.
**Example.** A visitor reads "Insurance that pays out when the weather turns" and clicks *Explore products*.

### 3.2 Product catalogue with category filter
**What it is.** Lists all active products, with chips to filter by category (Weather, Flood, Agriculture, Business interruption, Travel, Cyclone, Other). Each card shows the trigger in plain English, the sum-insured range and the term.
**Example.** A card reads "Total rainfall of 150 mm or more within 24 hours · ₹10,000 – ₹5,00,000 · 1–12 months".

### 3.3 Product detail page
**What it is.** Shows "How the payout triggers", key information, what is covered and the steps of the cover. It changes depending on who is looking:
- **Visitors** get *Create an account to apply*.
- **Customers** get *Apply* and *Get a priced quote first*. They also see if they already hold this product or have an application waiting.
- **Admins** get *Edit product* and *Change trigger threshold*.
- **Other staff** see a note that only customers can apply.

**Example.** Ravi sees "You already have an application for this product waiting for underwriter review".

### 3.4 Footer
**What it is.** Product links, company links, contact details and an "All systems operational" note.

---

## 4. Products (admin)

### 4.1 Create a product
**What it is.** The admin enters:
- the code, category, name and description;
- the sum-insured and term limits, and who is eligible;
- one or more **coverages** (code, name, limit, mandatory/active);
- optionally, a **trigger**.

**Example.** Admin creates `RAIN-LOW`, "Rainfall Protection", ₹10,000–₹5,00,000, 1–12 months, with coverage "Base Coverage".

### 4.2 Edit a product
**What it is.** Change any product detail. Saving replaces the full coverage list.

### 4.3 Product status (active / inactive) and archive
**What it is.** Only **active** products can be sold. Archiving removes a product from sale; existing policies are not affected. A styled confirmation dialog asks first.
**Example.** Admin archives an old travel product. It disappears from the catalogue.

### 4.4 Product list for admins
**What it is.** A table with code, name, category (shown nicely, e.g. "Business interruption"), coverage limits, status and actions.

---

## 5. Trigger rules and thresholds

### 5.1 Trigger rule on each product
**What it is.** A rule has these parts:
- **peril** (e.g. Excess rainfall) and **measurement** (rainfall, water level, river level, temperature, wind speed, humidity, soil moisture);
- **unit** (mm, m, cm, °C, K, km/h, m/s, %);
- **operator** (>, ≥, <, ≤, =) and **threshold value**;
- **how readings are combined** (total, average, minimum, maximum, latest) over an **observation window** of 1–720 hours;
- the **minimum number of readings** needed.

**Example.** WEATHER-001: *total rainfall ≥ 150 mm within 24 hours*.

### 5.2 Plain-English trigger sentences
**What it is.** The app turns the rule into a sentence people understand. Windows of whole days over 24 hours are shown in days.
**Example.** "Maximum temperature of 45 °C or more within 3 days" instead of "MAX TEMPERATURE ≥ 45 CELSIUS 72h".

### 5.3 Change a threshold (with history)
**What it is.** On **Product thresholds** the admin changes the advertised threshold. Every change is saved in a **change history** (old → new, who, when).
**Example.** Admin changes WEATHER-001 from 150 mm to 200 mm. The history shows "150 → 200".

### 5.4 Snapshot protection (your contract never changes)
**What it is.** When a policy is created, its trigger rule is **copied and locked** into the policy. Later threshold changes only affect **new** policies.
**Example.** Ravi bought at 150 mm. Priya bought after the change, at 200 mm. Rainfall is 180 mm: Ravi is paid and Priya is not.

### 5.5 Trigger definitions sync
**What it is.** Each time a product's rule changes, the trigger engine automatically gets an updated (versioned) definition per zone.

---

## 6. Quotes and premium pricing

### 6.1 Request a quote
**What it is.** A customer picks a product, zone, sum insured and term (1–60 months) and gets a price before applying.
**Example.** Ravi asks for ₹1,00,000 for 12 months in Hyderabad Central and gets **₹3,500**.

### 6.2 "How is this calculated?" breakdown
**What it is.** Each quote shows the working: annual rate × term × term factor × zone risk factor. If the product's minimum premium kicks in, it says so.
**Example.** 1,00,000 × 3.5% × 12/12 × 1.0 × 1.0 = **₹3,500**.

### 6.3 Premium formula
**What it is.**
```
premium = sum insured × annual rate × (term months ÷ 12) × term factor × zone factor
premium = the larger of (premium, minimum premium)
```
**Example.** ₹10,000 for 1 month at 3.5% = ₹29.17, so the **₹250 minimum** applies.

### 6.4 Accept, cancel, expire, use
**What it is.** A quote can be **accepted** (locks the price), **cancelled**, or it **expires after 7 days**. When a policy is created with it, the quote becomes **consumed**. The policy must match the quote exactly.
**Example.** Ravi accepts his quote and clicks *Apply for policy*. The form opens pre-filled and locked to ₹3,500. If he changes the sum insured, the quote is refused.

### 6.5 Pricing rules (admin)
**What it is.** The admin sets the price rules per product:
- a **default rule** for all zones, or a **zone-specific rule**;
- the annual rate (0–1), minimum premium, term factor and zone factor;
- the dates the rule is effective from and to.

A zone rule beats the default rule. The rules can be edited, switched on or off, and archived.
**Example.** Admin adds a Mumbai rule with zone factor 1.20. A ₹1,00,000 policy there costs ₹4,200 instead of ₹3,500.

### 6.6 Default price rules created automatically
**What it is.** Every active product automatically gets a default rule by category: Weather 3.5%, Flood 4.5%, Cyclone 5%, Agriculture 4%, Business interruption 3%, Travel 2%, others 3.5%. All have a minimum of ₹250.
**Example.** The admin creates a new Flood product and it can be quoted right away at 4.5%.

---

## 7. Policies

### 7.1 Apply for a policy
**What it is.** The customer picks a product, sum insured, term and zone. The app checks:
- the product is active and the values are within its limits;
- there is no other pending application for the same product;
- the zone is open for sales (see section 9).

The policy is saved as **PENDING** with a number like `POL-2026-XXXXXXXX`. The form shows an estimated premium; the real price comes from the pricing rules.
**Example.** Ravi applies for WEATHER-001, ₹1,00,000, 12 months, Hyderabad Central. Status: *Pending*.

### 7.2 Custom policy (build your own trigger)
**What it is.** A customer can design their own cover. They set the category, peril, measurement, unit, operator, threshold, how readings are combined, the window and the minimum readings. The system creates a private product and the usual review follows.
**Example.** A farmer asks for "Average soil moisture below 15% within 7 days".

### 7.3 Cancel an application
**What it is.** A customer can withdraw an application while it is still pending.
**Example.** Ravi applied for the wrong zone. He cancels and applies again.

### 7.4 Policy detail page
**What it is.** Shows the product, sum insured, premium, status, cover dates, the **locked trigger snapshot**, the rejection reason (if any) and the renewal options.

### 7.5 Policy certificate (PDF)
**What it is.** Active policies have a downloadable **certificate of insurance** PDF. Staff can download it too.
**Example.** Ravi downloads `POL-2026-…-certificate.pdf` to show his bank.

### 7.6 Renew
**What it is.** An active policy can be renewed from **30 days before it ends**. An expired policy can be renewed up to **30 days after**. Renewal creates a new application linked to the old policy. The new cover starts when the old one ends, so there is no gap.
**Example.** Ravi's policy ends on 1 Oct 2027. *Renew* appears on 1 Sep 2027.

### 7.7 Auto-renew
**What it is.**
- **On:** the renewal application is created by itself 14 days before the end date.
- **Off:** the customer gets reminders at 14 and 3 days before the end date.

**Example.** Ravi switches auto-renew on and forgets about it. The renewal is filed for him.

### 7.8 Automatic expiry
**What it is.** Every night, active policies past their end date become **EXPIRED** and the customer is told.

### 7.9 Policy history
**What it is.** Every status change (pending → active → expired …) is recorded with who did it and why.

### 7.10 Payment status on the dashboard
**What it is.** Each active policy shows **✓ Premium paid** or **Payment due · Pay now →**.

---

## 8. Underwriting (policy review)

### 8.1 Pending applications queue
**What it is.** Underwriters see every pending application, with requested cover, premium, term and zone. The queue also shows totals: number in queue, total cover asked for, and average premium.

### 8.2 Review a policy
**What it is.** The review page shows the product rules, the customer's request, the trigger snapshot and a review checklist.

### 8.3 Approve
**What it is.** Approving makes the policy **ACTIVE**. Cover starts today and ends after the term. The customer is notified. Approval is blocked if the zone is under a **weather alert** or over its **exposure cap**.
**Example.** Anil approves Ravi's policy on 1 Oct 2026, so the cover runs 1 Oct 2026 → 1 Oct 2027.

### 8.4 Reject with a reason
**What it is.** The underwriter must give a reason (up to 500 characters). The customer sees it on the policy page.
**Example.** "Address is outside the insured zone."

### 8.5 Review history
**What it is.** Every decision the underwriter made, with search and filter, plus totals (decisions, cover approved, approval rate).

---

## 9. Location zones and sales controls

### 9.1 Location zones
**What it is.** The geographic areas you can insure. Each has:
- a code and name;
- the state, district and PIN code;
- the latitude and longitude of its centre, and a radius in km;
- a status (active / inactive / archived).

The admin can create, edit, search and archive zones.
**Example.** `HYDERABAD_CENTRAL`, Telangana, 25 km radius.

### 9.2 Weather alert — pause sales
**What it is.** The admin can **raise an alert** on a zone with a message. While it is on, **no new policies can be sold or approved there**, so nobody can buy cover after a storm is announced. Existing policies stay covered.
**Example.** Admin raises "Cyclone warning issued by IMD" on Chennai. A customer who tries to buy there is told sales are paused.

### 9.3 Exposure cap — limit total risk per zone
**What it is.** The admin can set a **maximum total cover** for a zone. Active and pending policies count towards it. When a new policy would go over the cap, it is refused and the message says how much room is left. With no cap set, there is no limit.
**Example.** Cap ₹5,00,000 and ₹4,00,000 already used. A ₹1,50,000 policy is refused: "Exposure cap reached for Hyderabad Central … only ₹100000 of cover is still available".

### 9.4 Zone exposure page
**What it is.** For every zone, a table shows cover used, a utilisation bar (% of cap), active and pending policies, and whether new sales are open. Totals at the top show cover in force, zones under alert and zones closed to sales.
**Example.** Hyderabad shows "45% used · Sales open". Chennai shows "Alert · Sales closed".

### 9.5 Zone map
**What it is.** An interactive map (Leaflet) showing every zone as an area. Each zone shows its current condition and live readings, and the map refreshes every 30 seconds. Customers and admins can open it.
**Example.** Ravi opens the map and sees "Rainfall 0.11 mm · Wind 12 km/h" over Hyderabad.

---

## 10. Perils

### 10.1 Peril catalogue
**What it is.** The list of insurable dangers that products and triggers use. The admin can create, edit, search, change status and archive perils.
**Example.** Pre-loaded perils: Excess rainfall, Drought, Flood, Extreme temperature, High wind, Cyclone.

---

## 11. Weather measurements and the trigger engine

### 11.1 Live demo weather feed
**What it is.** Every 60 seconds the trigger service creates a sample reading for each active zone and each measurement type. The values are kept in a safe, normal range (e.g. rainfall 0–0.15 mm), so the feed never triggers claims by itself. It back-fills the last 24 hours at start-up and deletes readings older than 3 days.

### 11.2 Measurement details & thresholds panel
**What it is.** A gauge button in the header opens a live panel. It refreshes every 10 seconds and contains:
- a summary of sensors reporting, products monitored, how many are near their trigger and how many have triggered;
- one card per measurement, with its **own icon and colour** (rain cloud, droplet, waves, thermometer, wind, sprout) and the **current value** in large type;
- for each product, "Triggers at ≥ 150 mm" and a **progress bar** ("85% of trigger").

Bars turn **amber** at 80% of the threshold and **red** when it is crossed.
**Example.** Rainfall 132 mm vs a 156 mm trigger shows "85% of trigger" in amber with the status "Approaching threshold".

### 11.3 Update the current value (admin)
**What it is.** In that panel the admin clicks **Update value** on a measurement. The new value is recorded for **every active zone** and kept for **1 hour**, then the live feed takes over again. A "Set by admin" tag shows on the value. If the value crosses any policy threshold, **automatic claims are created**, just like the trigger simulator. The value is checked first: no negatives (except temperature) and no percentages over 100.
**Example.** Admin sets Rainfall to 160 mm. A toast says "⚡ Trigger fired: WEATHER-001 — 2 automatic claims created, 1 unpaid policy skipped".

### 11.4 Trigger simulator (admin)
**What it is.** A page with one card per product. The admin picks a zone, types a value and clicks **Simulate reading**. The system checks every active policy of that product in that zone against **its locked threshold**. The result shows:
- policies checked and policies matched;
- policies skipped because the premium is unpaid;
- automatic claims and notifications created.

*Fill in a value that triggers* fills in a value that just crosses the threshold, and *Last simulated value* shows the last value tested.
**Example.** 120 mm → "No policy matched". 170 mm → "Trigger matched · 1 automatic claim".

### 11.5 Only paid policies are paid out
**What it is.** When a trigger fires, policies whose premium is **not paid** are skipped and counted separately.
**Example.** Two policies cross 150 mm, but one never paid its premium, so only one claim is created.

### 11.6 Trigger events
**What it is.** Each time a reading matches at least one policy, a **trigger event** (`TEV-…`) is saved. It records the product, zone, measured value, threshold and the **time window** of the readings. The admin's *Trigger events* page lists them with search.
**Example.** "TEV-3f2a… · WEATHER-001 · Hyderabad · 170 mm vs 150 mm · Verified".

### 11.7 No duplicates (idempotency)
**What it is.** Each reading is sent with a unique key. If the same reading arrives twice (double click or network retry), the first result is returned and nothing is created twice. A policy can only get one claim per event.

### 11.8 Readings combined over a window
**What it is.** The engine can combine many readings in the observation window into one number — the total, average, minimum, maximum or latest — before comparing it with the threshold. If there are too few readings, the result is "insufficient observations".
**Example.** Readings 40, 60 and 55 mm in 24 h total **155 mm**, which is ≥ 150, so the rule is matched.

### 11.9 Reading validation
**What it is.** Each type has allowed units and ranges, e.g. humidity 0–100%, temperature −100 to 100 °C, rainfall ≥ 0. Readings outside these are refused.

### 11.10 Claims-officer "Trigger engine · Live" widget
**What it is.** The claims dashboard shows recent trigger evaluations ("value vs threshold") so officers can see what is happening.

---

## 12. Claims

### 12.1 Automatic claims
**What it is.** When a trigger fires, each matching, **paid**, active policy gets a claim (`CLM-2026-…`). It is marked **Automatic trigger event**. The claimed amount is the full sum insured, and the claim stores the reading, threshold and time window. The customer and the claims officers are notified.
**Example.** Ravi's claim CLM-2026-A1B2 for ₹1,00,000 appears with "observed 170 mm vs threshold 150 mm".

### 12.2 Manual claim
**What it is.** For a real loss that the trigger did not catch, the customer files a claim by hand. They give:
- the active policy;
- the incident type, date and location;
- the amount (up to the sum insured) and a description (up to 1000 characters).

The date must be inside the policy period. A claims officer reviews it.
**Example.** Ravi's shop flooded although the rain stayed under 150 mm. He files a ₹40,000 manual claim.

### 12.3 Claims need a paid premium
**What it is.** A manual claim is refused if the policy's premium has not been paid.
**Example.** "Premium for this policy has not been paid; pay it before filing a claim."

### 12.4 Evidence files
**What it is.** Up to **3 files per claim**, 5 MB each: photos (JPG, PNG, WEBP) or PDF. Customers upload them, and officers view and download them.
**Example.** Ravi uploads two photos of the water damage.

### 12.5 Cancel a claim
**What it is.** A customer can cancel a claim before review starts.

### 12.6 Claims queue (officer)
**What it is.** Officers see submitted claims (automatic and manual), with totals: the number in the queue, the total claimed and the average claim.

### 12.7 Start review → approve or reject
**What it is.** The officer clicks *Start review* (status **Under review**). Then they either:
- **approve** an amount between ₹1 and the claimed amount (pre-filled with the claimed amount), or
- **reject** with a reason of up to 500 characters that the customer can read.

**Example.** Shiva approves ₹35,000 of Ravi's ₹40,000 manual claim.

### 12.8 Automatic settlement
**What it is.** As soon as a claim is approved, the payout is made and the claim becomes **Settled** automatically (see section 14).

### 12.9 Manual "Mark as settled"
**What it is.** A backup button for officers, if a payment was made outside the system.

### 12.10 Claim timeline
**What it is.** Every step (submitted, review started, approved, settled …) is listed with time and person.

### 12.11 "Why was I paid?" explanation
**What it is.** On an automatic claim, the customer sees their locked trigger and the reading that crossed it, explained in plain words.

### 12.12 Officer review history
**What it is.** Every claim the officer handled, with search, an action filter and totals (handled, approved, rejected, settled, amount approved).

---

## 13. Premium payments

### 13.1 Checkout (simulated)
**What it is.** A payment-style checkout with **Card**, **UPI** or **Net banking**. It checks the card number (Luhn check), expiry, CVV, UPI ID format and bank choice. The payment settles instantly. Card details never leave the browser — this is test mode and no real money moves.
**Example.** Ravi pays ₹3,500 with card 4242 4242 4242 4242 and gets reference `PAY-…`.

### 13.2 Test decline cards
**What it is.** Special card numbers fake a failure so it can be shown in a demo.
**Example.** 4000 0000 0000 0002 → "card declined".

### 13.3 One payment per policy
**What it is.** A policy cannot be paid twice, and only the owner can pay it.

### 13.4 Payments & payouts page (customer)
**What it is.** Two tabs: **Payments** (premiums paid) and **Payouts** (money received), with totals. "Pay now" appears for policies waiting for payment.

### 13.5 Refunds (admin)
**What it is.** The admin can refund a successful payment after a confirmation dialog.
**Example.** "Refund ₹3,500? Payment PAY-2026-… will be refunded to the customer."

### 13.6 Premium reminders
**What it is.** Every day at 09:00, customers get "premium due" (7–29 days after the last payment) or "overdue" (30+ days) reminders.

---

## 14. Payouts

### 14.1 Fully automatic payouts
**What it is.** The moment a claim is approved, the payout is created **and paid** — **no admin step**. The system then:
1. records the transfer reference (`AUTO-…`) and the time;
2. notifies the customer;
3. settles the claim;
4. closes the trigger event;
5. records the payment in the ledger.

**Example.** Shiva approves ₹1,00,000. Ravi instantly sees "Payout processed" and the claim shows **Settled**.

### 14.2 Payout history (admin)
**What it is.** A read-only list of payouts with the transfer reference, time and status.

### 14.3 Retry failed payouts
**What it is.** If an old payout failed, the admin can click **Retry** and it is paid straight away. A background job also retries failed payouts (up to 3 times), and every 5 minutes pays any payout still waiting.

### 14.4 Payout receipt (PDF)
**What it is.** Customers and admins can download a receipt PDF for a successful payout.

### 14.5 Payment never undone by side steps
**What it is.** If, for example, settling the claim fails after the money is sent, the payment still stands. The problem is logged so it can be fixed with "Mark as settled".

---

## 15. Notifications

### 15.1 In-app notifications
**What it is.** Every important event sends a notification. Examples include policy submitted, approved, rejected, expired or renewal due; claim created, approved, rejected or settled; payment success; payout processed; and trigger events.
**Example.** "Automatic parametric claim created — Rainfall of 170 mm crossed your 150 mm threshold."

### 15.2 Notification bell with live updates
**What it is.** The bell in the header shows the unread count and a drop-down list. You can **mark all read**, delete items and refresh. New notifications arrive **live** (Server-Sent Events) and also pop up as a toast.
**Example.** Ravi's bell changes from 0 to 1 the moment his claim is created, without reloading.

### 15.3 Notifications page
**What it is.** The full list with an unread filter, mark read, mark all read and delete. Customers and staff each have their own page.

### 15.4 Notification preferences
**What it is.** Customers choose how they want to be told: **In-app, Email or SMS**. Email and SMS are simulated and logged.

### 15.5 Admin notifications page
**What it is.** Every notification on the platform, with search by title, message or recipient, plus mark read and delete.

### 15.6 Delivery tracking and retry
**What it is.** Each send attempt is stored. Failed deliveries are retried every 5 minutes, and old read or failed notifications are cleaned up after 90 days.

### 15.7 Templates and broadcast (API)
**What it is.** Message templates can be created, edited, activated or deactivated, and messages can be sent to many users at once (batch).

---

## 16. Dashboards

### 16.1 Customer dashboard
**What it is.**
- Greeting and quick actions.
- Totals: active policies, sum insured, claims submitted, premiums paid, payouts received.
- Recent policies (clickable, with paid / payment-due tags) and recent claims (clickable).
- A "Live monitoring active" note.

### 16.2 Underwriter dashboard
**What it is.**
- Totals: pending applications, active policies, premium in force, approval rate.
- The application queue and a review checklist.
- Counts of reference data (products, zones, perils, trigger definitions).

**Example.** Approval rate: 8 approved and 2 rejected gives **80.0%**.

### 16.3 Claims officer dashboard
**What it is.** Claims to review, manual claims, total claimed, average claim, the live trigger-engine widget and the settlement rules.

### 16.4 Admin dashboard
**What it is.** Registered users, active policies, live products and total payouts, plus tiles linking to every admin area and the product catalogue.

### 16.5 Financial metrics
**What it is.** Totals for premiums collected and payouts paid, and counts of quotes, payments and payouts by status, shown on the admin dashboard.

---

## 17. User management (admin)

### 17.1 Users list
**What it is.** Every user with role, segment, contact, location and join date. You can search by name, email, role, phone or location, and filter by status or segment.

### 17.2 Change a user's status
**What it is.** Set a profile to **Active, Inactive or Suspended**, after a confirmation dialog that explains the effect. **Inactive and Suspended users are signed out and cannot log in** (see 2.7); **Active** lets them sign in again. The status can also be used as a filter.
**Example.** Admin suspends a customer. The dialog says "They will be signed out and cannot sign in until an admin makes them active again."

### 17.3 User detail
**What it is.** One customer's record: profile, address, all their policies and all their claims.

---

## 18. Look, feel and usability

### 18.1 In-app dialogs (no browser pop-ups)
**What it is.** All confirmations and questions use styled dialogs instead of the browser's "localhost says…" box. They have a blurred background, red buttons for dangerous actions, and close with Esc or a click outside.
**Example.** "Archive this product? It will no longer be available to customers."

### 18.2 Toast messages
**What it is.** Small pop-ups at the top right confirm success or explain errors. They close on their own after a few seconds.

### 18.3 Status badges
**What it is.** Coloured pills with a dot and readable text: green for good, amber for in progress, red for rejected or failed.
**Example.** "● Under review" instead of `UNDER_REVIEW`.

### 18.4 Indian money and date format
**What it is.** Amounts use Indian grouping everywhere, and dates are written day-month-year.
**Example.** ₹1,50,000 (not ₹150000). 2 Oct 2026.

### 18.5 Page titles in the browser tab
**What it is.** Each page has its own tab title.
**Example.** "My claims · ClimateSure".

### 18.6 Open new pages at the top
**What it is.** Opening a new page starts at the top. The back button returns you to where you were.

### 18.7 Custom illustration
**What it is.** A hand-made SVG weather scene on the landing page and the dashboard banners, with a pulsing sensor ring.

### 18.8 Loading, empty and error states
**What it is.** Every list shows a spinner while loading, a friendly message when empty (with a next step) and a clear error message if something fails.

### 18.9 Search, filters and pagination
**What it is.** Most lists can be searched and filtered (by status, role, segment, state and so on). Long lists are split into pages.

### 18.10 Tables that fit the screen
**What it is.** Compact table spacing, with related columns merged so wide tables fit without side-scrolling.
**Example.** "₹1,00,000 / Approved ₹1,00,000" sits in one *Amount* column.

### 18.11 Works on phones
**What it is.** Layouts adapt to small screens: cards stack, menus collapse and the measurement panel becomes one column.

### 18.12 Reduced motion
**What it is.** For users who ask their device for less motion, animations are switched off.

### 18.13 Header navigation by role
**What it is.** Each role gets its own menu, with a "More" drop-down for extra pages (Reference data, Finance, Operations) and an account menu.

---

## 19. Background jobs (run on their own)

| Job | When | What it does | Example |
|---|---|---|---|
| Demo weather feed | Every 60 s | Adds normal sample readings for every zone | Rainfall 0.08 mm in Mumbai |
| Admin-reading hold | Each feed run | Leaves an admin-set value alone for 1 hour | Admin's 160 mm stays until 1 h later |
| Old reading cleanup | Hourly | Deletes demo readings older than 3 days | — |
| Monitor scan | Every 60 s | Checks active trigger definitions against recent readings | Records "matched / not matched" |
| Policy expiry | Daily 01:00 | Active policies past end date → Expired | Policy ended 30 Sep → Expired on 1 Oct |
| Policy renewal | Daily 01:30 | Auto-renew applications and renewal reminders | Reminder 14 and 3 days before end |
| Premium reminders | Daily 09:00 | "Premium due" / "overdue" notifications | — |
| Payout sweep | 30 s after start, then every 5 min | Pays waiting payouts and retries failed ones (max 3) | — |
| Reconciliation | Daily 03:30 | Checks every payment and payout has a ledger entry | "10 checked, 0 mismatches" |
| Default price rules | Every 60 s | Gives any new product a default price rule | — |
| Notification retry | Every 5 min | Re-sends failed deliveries | — |
| Notification cleanup | Daily 03:00 | Removes read or failed items older than 90 days | — |

---

## 20. Platform and technical features

### 20.1 Microservices with their own databases
**What it is.** Each service has its own H2 database file and can be run on its own. Services never share tables.

### 20.2 API Gateway as the single door
**What it is.** The browser only talks to the gateway on port 8080. The gateway checks the login token, removes any fake identity headers sent by the browser, adds trusted user and role headers and a secret gateway key, and forwards the request.
**Example.** Someone sends `X-Authenticated-Role: ROLE_ADMIN` from the browser. The gateway strips it.

### 20.3 Services trust only the gateway
**What it is.** Every service rejects requests without the secret gateway key. Each service also has its own role rules, and anything not listed is denied.

### 20.4 Service discovery (Eureka)
**What it is.** Services register by name and call each other by name (e.g. `CLAIMS-SERVICE`), not by fixed address.

### 20.5 Internal service-to-service APIs
**What it is.** Paths under `/internal/...` are only for service-to-service calls. For example: the trigger service asks the product service for active policies, and the claims service asks the financial service whether a premium is paid.

### 20.6 Reactive, non-blocking backend
**What it is.** The backend uses Spring WebFlux and R2DBC, so many requests are handled with few threads.

### 20.7 CORS
**What it is.** The gateway allows the Angular app's address to call the API from the browser.

### 20.8 Request logging and logs
**What it is.** The gateway logs every request. Each service writes rolling log files.

### 20.9 Health checks
**What it is.** Each service has an actuator health endpoint, reachable through the gateway.
**Example.** `http://localhost:8080/trigger/actuator/health` returns `UP`.

### 20.10 Uniform error messages
**What it is.** Every error comes back in the same JSON shape (status, message, path), and the frontend shows the message to the user.

### 20.11 Money stored exactly
**What it is.** Amounts use exact decimals (`BigDecimal`), rounded to 2 places.

### 20.12 PDF generation
**What it is.** A built-in PDF writer creates policy certificates and payout receipts.

### 20.13 Automated tests
**What it is.** Unit and integration tests cover the backend (JUnit, Mockito, StepVerifier) and the frontend (Vitest).

---

## 21. Demo data loaded at start-up

| What | Details |
|---|---|
| Staff accounts | Admin `admin@gmail.com` / `admin@123`; underwriters `anil@gmail.com`, `sandhya@gmail.com`; claims officers `shiva@gmail.com`, `pallavi@gmail.com` (password `12345678`) |
| Products | WEATHER-001 Weather Protection Plan (total rainfall ≥ 150 mm / 24 h); FLOOD-001 Flood Protection Plan (max water level ≥ 5 m / 6 h); both ₹10,000–₹5,00,000, 1–12 months |
| Zones | Hyderabad Central (active), Mumbai Central (active), Pune Test (inactive) |
| Perils | Excess rainfall, Drought, Flood, Extreme temperature, High wind, Cyclone |
| Demo policies | Two active sample policies in Hyderabad Central (WEATHER-001 ₹1,50,000 and FLOOD-001 ₹2,50,000) |
| Price rules | Default rule per product, by category |
| Notification templates | Default message templates |

Customers are not pre-loaded; they register themselves.

---

## 22. Features available through the API only

These work in the backend but have no screen yet. They can be shown with an API client such as Postman.

| Feature | What it does |
|---|---|
| Observations | Record raw readings, then validate or reject them one by one or all at once |
| Monitor runs | See each automatic scan and start one by hand |
| Trigger definitions and evaluations | View, create and evaluate per-zone trigger definitions |
| Trigger event workflow | Move an event through validation → verified / rejected → processed |
| Demo observation and simulation | Record a demo reading and evaluate it, or generate readings that cross a threshold |
| Notification templates and batch send | Manage templates and broadcast to many users |
| Delivery attempts | See every send attempt of a notification |
| Policy history and snapshot | Read a policy's status history and its locked trigger |

---

## 23. Quick reference: status lifecycles

| Item | Lifecycle |
|---|---|
| Policy | Pending → Active → Expired · Pending → Rejected · Pending → Cancelled |
| Quote | Created → Accepted → Consumed · Created/Accepted → Cancelled · Created → Expired (7 days) |
| Payment | Success → Refunded |
| Claim | Submitted → Under review → Approved → Settled (automatic) · Under review → Rejected · Submitted → Cancelled |
| Payout | Created → Success (instantly) · Failed → retried → Success |
| Trigger event | Verified on creation → Processed when the payout succeeds |
| Zone | Active ↔ Inactive · Archived → Restore → Inactive · weather alert on/off · cap set/removed |
| Peril | Active ↔ Inactive · Archived → Restore → Inactive |
| Pricing rule | Draft → Active ↔ Inactive · Inactive → Archived → Restore → Inactive |
| User profile | Active ↔ Inactive ↔ Suspended (admin) · Inactive and Suspended cannot log in · a customer closing their account becomes Inactive |

---

*Covers the code as of October 2026.*

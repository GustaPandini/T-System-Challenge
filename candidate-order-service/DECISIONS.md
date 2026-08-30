# Engineering Decisions

## Problem summary

The service used to price orders from a catalogue stored inside itself, and confirmed every order immediately. The price must now come from the Pricing API, which is operated by another company, is not reliable and can be unavailable for several minutes — and the store still has to be able to register orders during that time.

The hard part is not calling the API. It is deciding what the system does when the API does not behave as expected.

---

## Guiding concerns

Three concerns shaped the whole solution:

- **The user experience is the priority.** My main concern was what would give the best experience to the person operating the system, not what would be the best solution technically.

- **Support counts as a user too.** `CHANGE_REQUEST.md` itself treats the two together in item 6 of the requested behaviour: *"a store/support user"*. That is why I made a point of recording every piece of information the support team would need in order to fix the problems the system can run into.

- **Rejecting orders means losing sales.** I was careful to reject an order only when there is genuinely no way to process it.

---

## Assumptions I made

The brief left several decisions open. These are the answers I gave:

1. **An order is never rejected because of the Pricing API.** If the API fails, the order is still accepted and stored, without a price. Rejecting it would force the store to type everything again, which is exactly what the customer asked us to avoid.

2. **An unpriced order must never be treated as confirmed.** Not in the API, not on the screen.

3. **A new attempt is always triggered by a person.** There is no background retry.

4. **There is no maximum time an order may wait for a price.** It is the user who closes the case, not an automatic deadline. I explain why below.

5. **Data stays in memory.** Restarting the application loses every order. I chose to keep the starter application's original approach rather than add implementations the challenge does not require.

6. **The first valid quote that arrives is the one that counts.** I do not compare it against previous requests.

---

## Key decisions and trade-offs

### 1. The order is stored before the price is requested

The flow is: receive the order, store it with a stable ID, only then call the Pricing API and update it according to the result.

If I fetched the price first and only stored the order afterwards, a Pricing API outage would mean the order simply never existed, and the store would have to enter it all over again.

### 2. Three possible states

| State (code) | On screen | Meaning | Retry button? |
|---|---|---|---|
| `CONFIRMED` | *Priced* | Price obtained and validated. | No |
| `PENDING_PRICE` | *Awaiting price* | Transient failure: repeating the request may work | **Yes** |
| `PRICING_FAILED` | *Needs attention* | Permanent failure: repeating will not work without a change on the Pricing API side | No |

`PRICING_FAILED` matters because it is a kind of error that cannot be solved by trying again without changing the request or without something changing on the provider's side.

The split comes from a classification I built by probing the Pricing API (see the next section).

### 3. Record the reason, not just the state

The customer asked for "enough information to understand later why an order remained unconfirmed", and the state alone does not answer that. Every failure stores a `PricingFailure` alongside the order, holding: the classified reason (`reason`), the `X-Request-Id` returned by the Pricing API, the accumulated number of attempts (`attempts`), the timestamp of the last one (`lastAttemptAt`), and the original error message. The `X-Request-Id` is what lets support correlate with the provider's own logs.

### 4. Retrying is an explicit action, not an automatic one

`CHANGE_REQUEST.md` gave me three options for how retrying should work, in its list of deliberately open questions — *"whether retrying should happen synchronously, in the background, or through an explicit action"*: retry several times inside the request itself, run a background retry that re-attempts the pending orders, or let a person press a button.

**I chose: one automatic attempt when the order is created, and everything after that by button.**

Why:

- retrying inside the request itself does not solve the problem: the outage lasts minutes, and every attempt keeps a server thread occupied while the HTTP client waits. It would trade a failure for serious slowness;
- a background retry would solve it, but it requires a scheduler and brings concurrency control and more moving parts that could take up a lot of the time available for my solution.

This was a difficult trade-off. The better option for the customer would probably be the automatic retry, but it would add considerable complexity and implementation time; I consider it an **important future improvement**.

To partly compensate for the absence of an automatic retry, I added a "retry all pending" button: during an outage of a few minutes a store can accumulate several orders, and resolving them all at once improves usability a great deal, at a much lower implementation cost.

### 5. The effect of having no automatic deadline

Because there is no background retry, there is no automatic deadline moving an order from `PENDING_PRICE` to `PRICING_FAILED`. That simplified the design:

- **`PENDING_PRICE`** = a failure that may improve, therefore it has a button;
- **`PRICING_FAILED`** = a failure that will not improve (without intervention), therefore it has no button.

The two states ended up with meanings that do not overlap. The price is that an order can wait indefinitely if nobody looks at it.

### 6. A confirmed price is never changed

If someone presses the button twice, or presses it on an already confirmed order, the system does not call the Pricing API again — it returns the order as it stands.

The screen only shows the button on `PENDING_PRICE` orders, but the page shows the state of the moment it was loaded, and there are four routes to an order that has already changed state: the double click; two open tabs; the browser back button; and a direct call to the REST endpoint.

The Pricing API has a product whose price alternates on every request (11.99, then 13.49). Without this guard, two accidental clicks would change the price of an order that was already closed.

### 7. Creating an order always returns status 201

Even when no price was obtained. The reasoning is: even if pricing failed, the order was created with a stable ID, and that is what the status code describes.

I considered using `202 Accepted` for pending orders, but 202 states that processing continues on its own, which does not happen with my manual retry.

I also added the `Location: /api/orders/{id}` header, which states which URL the newly created resource lives at. That way the stable ID appears in the protocol, and not only in the response body.

### 8. The local catalogue was deleted

The Pricing API is now the single source of price. There is no point in keeping an old, outdated price source that would sit unused.

**Side effect of my solution compared to the old local catalogue:** requesting a non-existent product used to return an immediate `400`. The order is now accepted and ends up in `PRICING_FAILED`. That is a change in API behaviour, and it follows from assumption 1.

### 9. A 200 response does not mean success

Every successful response still goes through validation before it becomes a price. The Pricing API returns `200` in situations that cannot be used. There are six checks: an empty or unreadable body, a missing `amount`, a non-positive `amount`, a currency different from the one requested, a product different from the one requested, and a `validUntil` that is missing or already expired. Each of them produces a classified reason rather than a generic exception. The observed cases are detailed in the next section.

---

## What I found by probing the Pricing API

Before writing the integration, I needed to know which failures the Pricing API can produce — there was no way to assume that from the contract alone. I specified a probe, product by product, repeating requests, and the AI suggested comparing a fresh container against one already in use. The AI ran the probe, and the result was decisive for almost every decision I made.

### Failures that disappear if you repeat the request

- **provider temporarily unavailable** (`503`);
- **too many requests** (`429`) — accompanied by a `Retry-After` header asking for 2 seconds;
- **connection closed with no response at all** — this never becomes an HTTP status code, it becomes a network error.

### Failures that never disappear

- **product not found** (`404`);
- **invalid request** (`400`, missing country or currency).

### "Successful" responses that cannot be used

- one product **omits the amount field on alternating requests**;
- another **returns a different currency than the one requested**: ask for euro, get dollar;
- another **alternates the price** on every request.

### A slow product

One of the products responds in 3.5 seconds, consistently, while every other product responds in about 6 milliseconds.

That number is what defines the read timeout. Any limit below 3.5 seconds turns a valid product into one that can never be priced. The configured value is 5 seconds.

I ran into trouble with this product in the first version of the system; there is more detail in the section on AI-assisted work.

### A behaviour that differs between the probe and the application

`SKU-1008` behaves differently depending on which client calls it. Through `curl`, the first request drops the connection. Through the application, the same order is confirmed on the first attempt, with a price, and with no failure recorded in the log.

I chose not to investigate the cause of that difference, so as not to consume time from the challenge. In a real application this case would certainly be investigated. The practical effect is documented: the reproduction table in the README describes the behaviour observed through the application, not through `curl`.

---

## What the screen shows and what it hides

The screen has two audiences.

**The system's operator** needs to know what to do. They see:

- the state spelled out in words — *Priced*, *Awaiting price*, *Needs attention*. Colour is only a reinforcement;
- guidance in plain language that changes according to the kind of problem:
  - order data → *"check the data and create a corrected order"*;
  - Pricing API down → *"there is nothing wrong with this order, try again shortly"*;
  - odd response from the Pricing API → *"this will not fix itself, contact support with the order ID"*;
- how many attempts have failed, and the wait requested by `Retry-After` when it is sent;
- the retry button, only on orders in `PENDING_PRICE`. On the other rows the column states why no action is available, instead of leaving the cell empty.

**Support** needs technical detail. It sits inside a collapsible block — a `<details>` element, native HTML — holding the technical reason, the Pricing API's `X-Request-Id`, the time of the last attempt and the original error message.

**What the screen deliberately does not show by default:** provider error names, technical identifiers and internal messages. That is unnecessary for the operator, and it exposes the internals of a third-party provider.

One important message on the screen is the one for an accepted but unpriced order: "do not submit this order again", with the ID highlighted.

---

## How I tested it

There are 43 automated tests, and one testing decision worth explaining.

The HTTP client tests run against a real HTTP server, started by the test itself (`com.sun.net.httpserver`, which ships with the JDK), rather than against a mocked `RestClient` (`MockRestServiceServer`). The reason is that two of the failure modes observed in the Pricing API are not status codes: the connection closed without a response, and the read timeout. A mock cannot produce either of them (and this needed no new dependency).

The tests cover every error code, the `200` responses that cannot be used, the timeout, the dropped connection, the three states on screen, recovery via the button, and the confirmed-price guard.

**One test arrived broken in the starter project:** it searched for the word "Invalid" in the HTML, but that word is only Thymeleaf prototyping text — at render time `th:errors` always replaces it with the real Bean Validation message. The test could never pass. It was fixed by asserting on the validation result itself (the `BindingResult`) instead of searching for text in the page.

---

## Known limitations

1. **Data lives in memory.** There is no database: restarting the application loses every order.

2. **Two simultaneous attempts on the same order can collide.** The confirmed-price guard protects against clicks in sequence, but not against two requests arriving at exactly the same moment: both read the `PENDING_PRICE` state and both proceed. Solving this properly requires concurrency control, far more complex than the problem justifies here.

3. **An order can stay in `PENDING_PRICE` forever** if nobody presses the button.

4. **The Pricing API's response time becomes the user's response time.** Measuring the application, an order for the slowest product takes 4.17 seconds to respond, with a server thread occupied throughout.

5. **`Retry-After` is displayed but not honoured automatically** — there is no scheduler to honour it.

6. **An unstable price is not detected.** If the Pricing API returns 11.99 on one request and 13.49 on the next, I accept the first. Detecting it would mean comparing requests, and there is no objective criterion for deciding which of the two is correct.

7. **The `quoteId` is not stored on success** — I only record a tracing identifier when something goes wrong. Storing it in both cases would allow complete traceability.

8. **Form validation messages are the Bean Validation defaults**, and some of them show a regular expression to the end user. It works, but it is not ideal.

---

## What I would change for production

1. **A real database.** It is the first item, and a prerequisite for almost all the others.

2. **A background retry** re-attempting the pending orders. It would be the better option for the customer's experience.

3. **Move the Pricing API call out of the user's request.** If traffic grew a hundredfold: with the Pricing API slow or down, every order holds a server thread for seconds.

4. **Concurrency control** — order versioning, or an idempotency key on the retry action.

5. **Metrics and alerting per failure type.** Today there is only log output. An operations team would need to see the success rate, the provider's latency and how many orders are stuck in `PENDING_PRICE` without having to read logs.

6. **Separate what the API exposes.** Today it returns the full technical detail to any caller. That could become a leak of provider information.

---

## AI-assisted work and how I validated it

I used an AI assistant throughout the implementation — for discussion, code generation, tests, review and documentation. Four concrete examples of validation follow.

**1. A problem with the timeout.** One of the lines in the probe report, the one summarising the Pricing API's behaviour, read "healthy latency, ~4ms". Based on that figure, the AI set the read timeout to 3 seconds. A valid product then became impossible to price, because it consistently responds in 3.5 seconds. I found out during my own testing with the application running, and raised the timeout above 3.5 seconds.

**2. Differences between testing through curl and testing through the application.** The probe report written by the AI stated that `SKU-1008` closed the connection with no HTTP response on the first call and worked normally from the second onwards. The automated tests also passed without problems. However, in my manual testing — done directly against the application rather than through curl — I noticed that this same SKU always priced correctly on the very first request, including after restarting the Pricing API directly through Docker. I chose to correct only the documentation; in a real application, and with more time available, I would certainly have investigated this case further.

**3. The AI wanted to implement something I would not be able to validate.** While the AI suggested the automatic retry, which would require a scheduler, I chose not to have one. Even accepting that it is a technically superior solution, I have not worked with that component yet and do not know enough about it to validate whether the implementation generated by the AI would be correct.

**4. A discussion about blocking retries on an already confirmed order.** The AI recommended blocking a retry on an order whose price was already confirmed, citing the example of two open tabs. I judged it unnecessary for this solution, because in my view that example was misuse of the system. But since I had not considered that it could happen at all, I asked the AI to list the situations in which the block would be useful — and it showed that there were more routes to a retry on an already priced order than I had imagined. I agreed with the arguments and decided to implement it.

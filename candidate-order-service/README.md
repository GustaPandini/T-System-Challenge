# T-Systems International Developer Challenge

---

# Running this solution

Requires Java 21+, Maven 3.9+ and Docker.

```bash
# 1. start the external Pricing API
docker run --rm --name pricing-api -p 8090:8080 eduardosassegdcbrazil/tsystems-pricing-api:1.0

# 2. in another terminal, start the application
mvn spring-boot:run
```

Dashboard: <http://localhost:8080/> · REST API: `/api/orders`

Tests do not need Docker — the Pricing API is stubbed and the HTTP client is tested against a
local server started by the test itself:

```bash
mvn test
```

## Reproducing each pricing state

The Pricing API is deterministic: the behaviour depends on the product **and on how many times it
has already been called**. Restart the container to reset it and replay any scenario.

| Submit this product | You get | Because |
|---|---|---|
| `SKU-1001` | **Priced** | always answers correctly |
| `SKU-1003` | **Priced**, after ~3.5s | this product is deliberately slow |
| `SKU-1002` | **Awaiting price** → *Retry pricing* twice → **Priced** | fails the first two calls with `503` |
| `SKU-1004` | **Awaiting price** → *Retry pricing* twice → **Priced** | rate-limits the first two calls with `429` |
| `SKU-1008` | **Priced** | drops the connection on the first call when probed with `curl`, but the application still prices it — see `DECISIONS.md` |
| `SKU-1007` | alternates **Awaiting price** / **Priced** on every attempt | omits the required `amount` field every other call |
| `SKU-1005` | **Needs attention** | answers in a different currency than the one requested |
| `SKU-9999` | **Needs attention** | product does not exist |

For a full outage, stop the container (`docker stop pricing-api`) and submit an order: it is still
accepted with a stable ID and lands in **Awaiting price**.

## Endpoints added to the starter

| Endpoint | Purpose |
|---|---|
| `POST /api/orders/{id}/retry` | retry pricing an order; no-op unless it is awaiting price |
| `POST /ui/orders/{id}/retry` | same action from the dashboard (plain HTML form) |
| `POST /ui/orders/retry-pending` | retry every order awaiting price at once |

`POST /api/orders` still returns `201` — now with a `Location` header — and the pricing state
travels in the response body, never in the status code.

## Configuration

| Property | Default | |
|---|---|---|
| `pricing.api.url` | `http://localhost:8090` | override with the `PRICING_API_URL` env var |
| `pricing.api.connect-timeout` | `2s` | |
| `pricing.api.read-timeout` | `5s` | must stay above `SKU-1003`'s ~3.5s response |

Design decisions, assumptions and known limitations are in [`DECISIONS.md`](DECISIONS.md).

---

## Context

You have joined a team that maintains an Order Management service for an international retail customer. The application already exists and is in use by other developers. Your task is to understand the codebase, introduce a new integration, and make engineering decisions when requirements and dependencies are imperfect.

This is **not** a speed-coding exercise. We are interested in how you analyze a system, validate assumptions, make trade-offs, test your solution, and explain your decisions.

## Timebox

Please spend approximately **4-6 hours** on the exercise. We do not award extra points for adding a large number of features. If you run out of time, document what you would do next and why.

## AI tools

You are welcome to use coding assistants and LLMs such as GitHub Copilot, Codex, Claude Code, Cursor, or similar tools.

We are **not** evaluating how much code you can type without assistance. We are evaluating whether you understand the problem and the solution you submit. You should be prepared to explain, challenge, and modify code produced with AI assistance.

## The existing application

The starter service is a small Java 21 / Spring Boot application.

It currently exposes:

- `POST /api/orders` - create an order;
- `GET /api/orders/{id}` - retrieve an order;
- `GET /api/orders` - list orders;
- `GET /` - a small server-rendered order dashboard.

The current implementation uses an in-process local price catalog and confirms an order immediately.

Example request:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "customer-42",
    "productId": "SKU-1001",
    "quantity": 2,
    "country": "DE",
    "currency": "EUR"
  }'
```

The in-memory repository and simple domain model are intentional starter constraints. You may change them if your solution requires it, but do not redesign the entire application without a clear reason.

## Browser UI

The starter includes a deliberately small browser interface at `http://localhost:8080/`. It is rendered on the server with Thymeleaf and contains only HTML and CSS on the client side.

The screen lets a store user:

- submit an order without using `curl`;
- see the stable order ID returned by the application;
- see whether an order is confirmed or needs attention;
- see whether a price has been captured;
- refresh the page to observe later state changes.

The UI is intentionally incomplete for the new integration. Evolve it together with your domain model so that a user can understand whether pricing succeeded, is still pending, failed, or recovered according to the decisions you make.

### Frontend constraints

For this challenge, keep the browser side intentionally simple:

- use **HTML and CSS only** in the browser;
- do **not** add JavaScript or TypeScript;
- do **not** add a frontend framework or CSS framework;
- server-side rendering with the supplied Thymeleaf setup is allowed and expected;
- keep forms and status information usable with semantic HTML, associated labels, keyboard navigation, and a narrow/mobile viewport;
- do not communicate an important status by color alone.

We are not looking for elaborate visual design. We want to see that core HTML/CSS and usability fundamentals are present, and that the UI makes the behavior of the backend integration observable.

## New customer requirement

The local price catalog is no longer the source of truth.

For every new order, the application must use the **external Pricing API** to obtain the unit price before treating that order as successfully priced.

The Pricing API is operated by another company. You do not control it, and you should not assume that it always behaves as expected.

Your goal is to integrate with it in a way that you consider appropriate for a production-oriented application, while keeping the scope appropriate for this exercise.

Some requirements are intentionally underspecified. Make reasonable assumptions and document them.

## Running the external Pricing API

You will receive the name of a Docker image published by T-Systems for this challenge.

Run it locally with:

```bash
docker run --rm --name pricing-api -p 8090:8080 eduardosassegdcbrazil/tsystems-pricing-api:1.0
```

Or create a `.env` file from `.env.example` and run:

```bash
docker compose up pricing-api
```

Check that it is running:

```bash
curl http://localhost:8090/health
```

The API contract is available in [`docs/pricing-api.openapi.yaml`](docs/pricing-api.openapi.yaml).

The provider exposes `GET /v1/products` with product IDs that can be used during development. Do not assume that all valid products or all requests behave identically.

Treat the Docker image as a **third-party black box**. Reverse engineering or depending on its internal implementation is outside the scope of the challenge. Your solution should rely only on observable behavior and the supplied API contract.

## Your task

Implement a solution that:

1. replaces the local catalog as the pricing source for new orders;
2. integrates the existing application with the Pricing API;
3. handles dependency behavior and failures in a way you can justify;
4. preserves or deliberately evolves the existing Order API;
5. evolves the supplied HTML/CSS dashboard so pricing state and recovery behavior are understandable to a user;
6. adds or updates automated tests for the behavior you consider important;
7. documents assumptions, important decisions, and known limitations;
8. addresses the additional customer request in [`CHANGE_REQUEST.md`](CHANGE_REQUEST.md).

There is no single expected architecture. We will accept different approaches when the decisions are coherent and well explained.

## What we are deliberately not specifying

We are not giving you a checklist of every failure mode or production concern. Investigate the dependency, decide what matters, and show us how your application behaves.

You are not required to:

- introduce a specific resilience library;
- use a specific HTTP client;
- add a database, queue, or cloud service;
- solve every possible production concern;
- preserve every starter-code decision.

Prefer the smallest design that clearly supports the decisions you want to demonstrate.

## Running the starter application

Requirements:

- Java 21+
- Maven 3.9+
- Docker, for the external Pricing API

Run the tests:

```bash
mvn test
```

Run the application:

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

Open `http://localhost:8080/` in a browser to use the supplied HTML/CSS dashboard. The REST API remains available under `/api/orders`.

## Submission

Please provide:

1. a link to your Git repository containing the source code;
2. a concise README section describing how to run your solution;
3. your assumptions and important engineering decisions;
4. a link to an **8-12 minute video in English or German**. A private/unlisted YouTube link is fine.

Do not include secrets or credentials in your repository.

## Video discussion points

Use the video to show the application working, but focus on reasoning rather than a feature tour. Please cover all of the following:

1. Which part of the existing codebase did you need to understand before making your change?
2. What behavior of the Pricing API influenced your design the most?
3. Show one failure scenario and explain how your application reacts.
4. Describe one ambiguous requirement and the assumption you made.
5. What is one technical decision in your solution that you are not completely satisfied with?
6. If traffic increased by 100x, what would you reconsider first?
7. Which parts of the solution were influenced by an AI assistant, and how did you validate or challenge its suggestions?
8. How did `CHANGE_REQUEST.md` affect your original design?
9. Use the browser UI to demonstrate at least one successfully priced order and one non-happy-path pricing situation. Explain what the screen tells the user and what it deliberately does not expose.

## What we evaluate

We look at the submission as a whole. A smaller solution with strong reasoning can score higher than a larger solution with more infrastructure.

The main dimensions are:

- problem understanding and assumptions;
- engineering decisions and trade-offs;
- correctness and code quality;
- testing and validation;
- resilience and edge-case thinking;
- ability to evolve existing code rather than simply replacing it;
- HTML/CSS fundamentals and whether the screen makes backend state understandable;
- communication in English or German;
- clarity about AI-assisted work and how it was verified.

Good luck, and have fun investigating the system.

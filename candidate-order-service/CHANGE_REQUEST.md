# Change Request CR-002 - Orders must survive pricing outages

## Message from the customer

The first version of the pricing integration assumed that a price can be obtained while the order request is being processed.

We have now learned that the external pricing provider can be unavailable for several minutes. Our stores must still be able to submit orders during that period.

## Requested behavior

1. A temporary problem with the Pricing API must not force the store to submit the same order again.
2. The order must have a stable ID as soon as it is accepted by our application.
3. The API must make it clear when an order has not yet been successfully priced.
4. Once a valid price is available, the same order should be able to move to a confirmed state.
5. We need enough information to understand later why an order remained unconfirmed.
6. The supplied browser screen must make it clear to a store/support user whether the accepted order is already priced, still awaiting pricing, or needs attention according to your chosen model.
7. A pricing outage must not be represented as a successful confirmed order simply because the HTTP request to our own application returned successfully.

## Deliberately open questions

The customer has **not** specified:

- which provider failures are temporary and which are permanent;
- whether retrying should happen synchronously, in the background, or through an explicit action;
- how many times the application should retry;
- how long an order may remain unconfirmed;
- the required persistence technology.

Make reasonable decisions for the scope of this exercise. Document your assumptions and explain what you would change for a production system.

## Browser acceptance notes

The current dashboard is intentionally minimal. Adapt the supplied server-rendered HTML/CSS as needed so that the state introduced by this change request is visible and understandable. Do not add JavaScript, TypeScript, a frontend framework, or a CSS framework.

A manual page refresh is acceptable for observing a background state transition. If your design uses an explicit recovery/retry action, it may be implemented as a normal HTML form submission.

We care more about clear semantics and useful feedback than visual polish.

## Scope guidance

You are not expected to build a distributed messaging platform or a production-grade scheduler. A small, coherent solution that exposes your reasoning is more valuable than a large amount of infrastructure.

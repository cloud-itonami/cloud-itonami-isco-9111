# cloud-itonami-isco-9111

Open Occupation Blueprint for **ISCO-08 9111**: Domestic Cleaners and Helpers.

This repository designs a forkable OSS business for an independent domestic cleaner: a cleaning-assist robot performs vacuuming and surface-cleaning tasks under a governor-gated actor, so the practice keeps its own service and access records instead of renting a closed home-service SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a cleaning-assist robot performs vacuuming, surface cleaning and restocking of household supplies under an actor that proposes
actions and an independent **Domestic Cleaning Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating in occupied rooms, near children/pets, or handling chemical cleaning agents) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
client consent + service scope + access schedule
        |
        v
Service Advisor -> Domestic Cleaning Governor -> clean, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `9111`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation

`src/domestic_cleaning/{store,governor}.cljc` is a minimal but real
implementation of the Core Contract above (pure cljc, no external deps):

- `domestic-cleaning.store` — `Store` protocol + `MemStore`: households,
  visits, chemical-use events. A visit/chemical-use event can only be
  recorded against a registered (consented) household (household
  provenance).
- `domestic-cleaning.governor` — `DomesticCleaningGovernor`: `assess`
  gates a proposal against the household env. Hard invariants force
  `:hold` (no household, direct-write instead of `:propose`); a
  chemical-use event in a household with children or pets present
  **always** requires `:high`+ safety-class and thus `:human-approval` —
  it can never be auto-approved; low-confidence proposals also escalate.

```bash
kbb -M:test   # 8 tests, 14 assertions, green
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation) —
the 14th `cloud-itonami-isco-*` occupation to reach that tier, after
`cloud-itonami-isco-6112`, `-2221`, `-7126`, `-4321`, `-9312`, `-5322`,
`-8332`, `-1321`, `-3253`, `-6210`, `-5223`, `-7231` and `-8121`
(ADR-2607012000).

## License

AGPL-3.0-or-later.

# sequeless-automation

Automation for Sequeless: trigger evaluation, the actions a fired transition runs, timers, and the
Store-event ingress that feeds them. It is moved out of `sequeless-prototype` by spec 15
(`docs/extraction/spec-15-sequeless-automation.md`, issue #43). The move is not finished: this
bootstrap commit has no modules yet.

The automation contract (the definitions of triggers, actions and Observables) is authored in
`sequeless-ontology` and read through its `automation-contract` module. This repo never depends on
`sequeless-core` or any Store module.

## Local setup

The parent POM comes from GitHub Packages, so export a token with `read:packages` as
`GITHUB_PACKAGES_TOKEN` before any `mvn` run. The `github` server in `~/.m2/settings.xml` reads it
as `<password>${env.GITHUB_PACKAGES_TOKEN}</password>`; `sequeless-build`'s README has the full entry.

If `spotless:check` fails at `verify`, run `mvn spotless:apply`, then `mvn verify` again.

## Layout

See `PROVENANCE.md` for where each file came from, and `docs/adr/` for decisions. The first ADR,
`docs/adr/0001-outbox-holds-only-store-events.md`, arrives with the first release.

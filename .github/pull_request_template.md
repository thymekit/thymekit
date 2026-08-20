## What changes and why

<!-- The reasoning, not the diff: the diff is visible below. -->

## Checks

- [ ] `./gradlew build` passes
- [ ] `./gradlew pitest` stays at 100% — a surviving mutant is a missing test or unjustified code
- [ ] A new element comes as a triple (Java factory, fragment, CSS) and is added to the contract test
- [ ] Commits are signed off (`git commit -s`)

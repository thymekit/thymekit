# Contributing

## The shape of a change

An element is a triple: a Java factory, a Thymeleaf fragment and a CSS file. All three come together or
not at all — `ElementContractTest` walks every element and fails the build when one of them drifts. Add
your element to its sample list and the contract is checked from then on.

Guards live in the builder: a null argument fails with the name of the argument, an invalid state fails
on `build()`. Appearance lives in `--tk-*` handles, never in hard-coded values.

## Running the checks

```
./gradlew build     # compile, tests, javadoc, jars
./gradlew pitest    # mutation gate, expected to stay at 100%
```

The mutation gate is not decoration: it is what tells us a test asserts the behaviour rather than the
line. A surviving mutant is either a missing test or code nobody can justify.

## Sign your work

Commits are signed off under the [Developer Certificate of Origin](https://developercertificate.org/):

```
git commit -s
```

That certifies where the code came from. It is not a contributor agreement — nobody signs their rights
away, and the project cannot be relicensed behind your back.

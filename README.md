# datomic-pullups

Merge Datomic Pull syntax via union and intersection.

[![Clojars Project](https://img.shields.io/clojars/v/lab79/datomic-pullups.svg)](https://clojars.org/lab79/datomic-pullups)
[![Build Status](https://travis-ci.org/lab-79/datomic-pullups.svg?branch=master)](https://travis-ci.org/lab-79/datomic-pullups)
[![codecov](https://codecov.io/gh/lab-79/datomic-pullups/branch/master/graph/badge.svg)](https://codecov.io/gh/lab-79/datomic-pullups)

## Usage

```clojure
(require '[lab79.datomic-pullups :as dp])
```

Given two pull patterns, e.g.:

```clojure
(def pull1 [:foo :bar])
(def pull2 [:foo :qux :cow])
```

We can get the union of them:

```clojure
(dp/compose-pull-patterns [pull1 pull2])
```

Result:

```clojure
(:foo :bar :qux :cow)
```

We can also get the intersection:

```clojure
(dp/intersect-pull-patterns pull1 pull2)
```

Result:

```clojure
[:foo]
```

It works on deeply nested patterns too:

```clojure
;; union

(dp/compose-pull-patterns
  [[{:user/access-groups
     [{:access-group/members
       [:person.id/ssn
        {:person/name [:person.name/family]}]}]}]
   [{:user/access-groups
     [{:access-group/members
       [{:person/name [:person.name/given]}]}]}]])

;; =>

(#:user{:access-groups
  (#:access-group{:members
    (:person.id/ssn #:person{:name (:person.name/family :person.name/given)})})})

;; intersection

(dp/intersect-pull-patterns
  [{:user/access-groups
     [{:access-group/members
       [:person.id/ssn
        {:person/name [:person.name/given :person.name/family]}]}]}]
   [{:user/access-groups
     [{:access-group/members
       [{:person/name [:person.name/given]}]}]}])

;; =>

[#:user{:access-groups
  [#:access-group{:members
    [#:person{:name [:person.name/given]}]}]}]
```

See the [tests](test/lab79/datomic_pullups_test.clj) for more examples.

## Limitations

It does not currently support the full range of Datomic syntax:

- does not explicitly support `*`
- does not correctly merge attributes with function invocation syntax

These are captured in commented-out tests.

## License

Copyright © 2017 Lab79, Inc.

Distributed under the [MIT License](LICENSE).

(ns lab79.datomic-pullups)

;; Pull Union helpers

(defn element-contains?
  "Checks if a map inside a sequence contains a key"
  [s k]
  (not-empty (filter (fn [c] (and (map? c) (contains? c k))) s)))

(declare compose-pull-patterns)

(defn merge-pull-components
  "Take two pull components and merge them. If they share the same key,
   merge-via compose-pull-patterns."
  [c1 c2]
  (merge-with
    (fn [x y]
      (compose-pull-patterns [x y]))
    c1
    c2))

(defn merge-component-into-pull
  "Take a pull pattern and a single component from a pull pattern and merge."
  [pull-pattern pattern-component]
  ;; merge pattern-component into pull-pattern
  ;; examples:
  ;;   [{:bar [:baz :qux]}] {:bar [:quux]}
  ;;   [:baz :qux] :quux
  ;; in order to merge a component into a sequence we either:
  ;; - merge it into another component that shares the same key
  ;; - conj it on when there are no other components that share a key
  ;; decide which to do up front.
  (if (map? pattern-component)
    (let [merge-key (-> pattern-component keys first)]
      (if (element-contains? pull-pattern merge-key)
        ;; map over pull-pattern and alter the component with the matching
        ;; key
        (map (fn [pull-pattern-component]
               (if (and (map? pull-pattern-component)
                        (contains? pull-pattern-component merge-key))
                 ;; found the shared key, merge into this component
                 (merge-pull-components pull-pattern-component pattern-component)
                 ;; otherwise return the pull-pattern-component unchanged
                 pull-pattern-component))
             pull-pattern)
        ;; if there wasn't a shared key just conj it on
        (conj pull-pattern pattern-component)))
    ;; if pattern-component is not a map just conj it on
    (distinct (conj pull-pattern pattern-component))))

;;
;; Pull Union
;;

(defn compose-pull-patterns
  "Compose multiple pull patterns into a single pattern via union. All patterns
   must be relative to the same entity ID.

   `patterns` is a seq of seqs.

   A pull pattern is an array of potentially-nested queries.

   Merge rules:

   1. * cancels out all top level (non-map) attributes
   2. nested queries with shared top-level keys must be combined via recursive
      merge

   Examples:

   1. [[:foo/name :foo/age]
       [*]
       [{:foo/friend [:foo/name]}]]

      becomes:

      [* {:foo/friend [:foo/name]}]

   2. [[:foo/name :foo/age]
       [*]
       [{:foo/friend [:foo/name]}]
       [{:foo/friend [:foo/dob]}]]

      becomes:

      [* {:foo/friend [:foo/name :foo/dob]}]"
  [patterns]
  (distinct (reduce
              (fn [acc-pattern pattern-to-merge]
                ;; - acc-pattern is the accumulated pattern up to this point
                ;; - pattern-to-merge is the pattern to merge into acc-pattern
                ;; Merge the components of pattern-to-merge one at a time. How
                ;; it gets merged depends on its type.
                (reduce
                  (fn [acc-pattern pattern-component]
                    (condp #(%1 %2) pattern-component
                      ;; simple case is a top level keyword that can be conj'd on
                      keyword? (conj acc-pattern pattern-component)
                      ;; TODO: likely need to get smarter about how this is
                      ;; merged but for now just conj it on.
                      list? (conj acc-pattern pattern-component)
                      ;; maps need to be merged
                      map? (merge-component-into-pull acc-pattern pattern-component)))
                  acc-pattern
                  pattern-to-merge))
              []
              patterns)))

;; Pull Intersection helpers

(defn search-for-shared-component
  "Find pattern-component inside pattern.  If pattern-component is:
   - a keyword, simply find the same keyword
   - a map, find a map with the same keyword in it (ignore values)"
  [pattern pattern-component]
  (not-empty
    (filter
      (fn [candidate-pattern-component]
        (cond
          (keyword? pattern-component) (= candidate-pattern-component pattern-component)
          (map? pattern-component) (let [k (-> pattern-component keys first)]
                                     (and
                                       (map? candidate-pattern-component)
                                       (contains? candidate-pattern-component k)))))
      pattern)))

;; Pull Intersection

(defn intersect-pull-patterns
  "Intersect two patterns. For example, in access control:
   - left pattern could specify the attributes a user has access to
   - right pattern could be a requested pattern from a client
   The result is the intersection of left and right.
   Order doesn't matter (unless using star, which we don't by convnetion);
   intersection is commutative."
  [left right]
  (reduce
    (fn [acc pull-component]
      (let [search-result (search-for-shared-component right pull-component)]
        (if search-result
          ;; if found, intersect pull patterns on shared map keys or simply
          ;; keep if a keyword in the simple case
          (cond
            (keyword? pull-component) (conj acc pull-component)
            (map? pull-component) (let [k (-> pull-component keys first)]
                                    (conj acc
                                          {k
                                           (intersect-pull-patterns
                                             (-> pull-component vals first)
                                             (-> search-result first k))})))
          ;; no shared component found; elide this segment
          acc)))
    []
    left))

(defn intersect-pull-with-tx
  "Incomplete implementation. Intent is to merge a pull pattern with a Datomic
   tx map (which could be nested)."
  [pull tx]
  ;; TODO handle deep pulls and tx maps
  (select-keys tx pull)
  ;; (reduce
  ;;   (fn [acc pull-component]
  ;;     (let [search-result (search-for-shared-pull-component-in-map right pull-component)
  )

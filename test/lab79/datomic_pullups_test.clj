(ns lab79.datomic-pullups-test
  "Datomic Pullups unit tests"
  (:require
    [clojure.test :refer :all]
    [lab79.datomic-pullups :as dp]))

(deftest merge-map-into-pull-test
  (testing "Merging a map into an existing pull pattern"
    (is
      (=
       (dp/merge-component-into-pull [{:foo [:bar]}] {:foo [:baz]})
       [{:foo [:bar :baz]}]))))

(deftest simple-pull-compose-test
  (testing "Composition of simple pull patterns"
    (let [pull1 [:foo :bar]
          pull2 [:foo :qux :cow]
          expected [:foo :bar :qux :cow]]
      (is (= (dp/compose-pull-patterns [pull1 pull2]) expected)))))

(deftest pull-patterns-compose-test
  (testing "Composition of pull patterns all rooted at the same entity"
     (let [expected-result [{:user/access-groups
                             [{:access-group/members
                               [:patient.id/medicare-number
                                :person/person.id/uuid
                                :person/person.id/ssn
                                {:person/name '[*]}]}]}]

           pull1 [{:user/access-groups
                   [{:access-group/members
                     [:patient.id/medicare-number
                      :person/person.id/uuid]}]}]

           pull2 [{:user/access-groups
                   [{:access-group/members
                     [:person/person.id/ssn
                      {:person/name '[*]}]}]}]]

       (is (= (dp/compose-pull-patterns [pull1 pull2]) expected-result)
           "Pulls should correctly compose"))

     (is (= (dp/compose-pull-patterns
              (vector
                ;; first rule
                [{:user/access-groups
                  [{:access-group/members
                    [{:person/name [:person.name/given]}]}]}]
                ;; second rule
                [{:user/access-groups
                  [{:access-group/members
                    [{:person/name [:person.name/family]}]}]}]))
            ;; composition
            [{:user/access-groups
              [{:access-group/members
                [{:person/name [:person.name/given :person.name/family]}]}]}])
         "The first rule gives access to the patient's given name.
          The second rule gives access to the patient's family name.
          The composition gives access to both.")

     (is (= (dp/compose-pull-patterns
              (vector [{:foo [{:ten [:nine]}]}]
                      [{:foo [{:bar [:aa :bb :cc]}]}]))
         [{:foo [{:ten [:nine]} {:bar [:aa :bb :cc]}]}])
         "Both map components")

     (is (= (dp/compose-pull-patterns
              (vector [{:foo [:qux]}]
                      [{:foo [{:bar [:aa :bb :cc]}]}]))
            [{:foo [:qux {:bar [:aa :bb :cc]}]}]))

     (is (= (dp/compose-pull-patterns
              (vector [{:foo [{:bar [:aa :bb :cc]}]}]
                      [{:foo [:qux]}]))
            [{:foo [{:bar [:aa :bb :cc]} :qux]}])
         "Reverse of the previous test")

     (is (= (dp/compose-pull-patterns
              (vector [{:foo [:qux]}]
                      [{:foo [:baz]}]))
            [{:foo [:qux :baz]}]))

     (is (= (dp/compose-pull-patterns
              (vector [:qux {:foo [{:ten [:nine]}]}]
                      [:cow {:foo [{:bar [:aa :bb :cc]}]}]))

            [:qux {:foo [{:ten [:nine]} {:bar [:aa :bb :cc]}]} :cow]
            )))

  (testing "Pull patterns with shared keys"
    (is (= (dp/compose-pull-patterns
              (vector [:qux :foo :bar]
                      [:cow :foo :bar]))
           [:qux :foo :bar :cow])
        ":foo :bar should be distinct keys"))

  (testing "Composition of pull patterns with lists (limits, defaults)"

    (is (= (dp/compose-pull-patterns
             (vector '[(limit :track/_artists 10)]
                     '[:foo]))
           [['limit :track/_artists 10] :foo])
        ":foo :bar should be distinct keys")

    (is (= true false) "TODO: implement if we decide to go all-in on pull composition"))

  (testing "Star * should cancel out all other keywords in a pattern vector"
    (is (= true false) "TODO: implement if we decide to go all-in on pull composition")))


;; intersection

(deftest search-for-shared-component-test
  (testing "Simple case where keyword exists"
    (is (not-empty (dp/search-for-shared-component [:foo :bar :baz] :baz))))
  (testing "Simple case where keyword doesn't exist"
    (is (empty? (dp/search-for-shared-component [:foo :bar :baz] :qux))))
  (testing "Search for map with shared key"
    (is (= (dp/search-for-shared-component
             [:foo {:m [:bar]} :baz] {:m [:ok]})
           [{:m [:bar]}])))
  (testing "Search for map with no shared key"
    (is (= (dp/search-for-shared-component
             [:foo {:m [:bar]} :baz] {:no [:ok]})
           nil)))
  (testing "TODO: attribute in a request should be normalized to its name.
            Access control does not care about limit or default expressions on
            attributes."
    (is (not-empty
          (dp/search-for-shared-component
            ;; access rule allowing acces to :foo
            [:foo]
            ;; request includes a limit
            '(limit :foo 10))))))

(deftest pull-patterns-intersect-test

  (testing "Simple keyword intersection case"
    (is (= (dp/intersect-pull-patterns
             [:foo :bar :baz]
             [:qux :bar :nop])
           [:bar])))

  (testing "Intersection with star (doesn't work yet)"
    (is (= (dp/intersect-pull-patterns
             ['*]
             [:qux :bar :nop])
           ['*])))

  (testing "Nested maps intersection"
    (is (= (dp/intersect-pull-patterns
             ;; first rule
             [{:user/access-groups
               [{:access-group/members
                 [:person/person.id/ssn
                  {:person/name [:person.name/family]}]}]}]
             ;; second rule
             [{:user/access-groups
               [{:access-group/members
                 [{:person/name [:person.name/given :person.name/family]}]}]}])
           ;; intersection
           [{:user/access-groups
             [{:access-group/members
               [{:person/name [:person.name/family]}]}]}]))))

(deftest intersect-pull-with-tx-test

  (testing "Flat pull and tx"
    (def flat-pull [:patient.id/medicare-number :person.id/uuid])
    (def flat-tx {:db/id "foo"
                  :patient.id/medicare-number "123"
                  :person.id/ssn "555-555-5555"})
    (is (= (dp/intersect-pull-with-tx flat-pull flat-tx)
           {:patient.id/medicare-number "123"})
        "Only medicare-number should remain"))

  #_(testing "Nested pull and tx"
      (def nested-pull
        [:patient.id/medicare-number
         {:person/name [:person.name/given]}])
      (def nested-tx {:db/id "foo"
                      :patient.id/medicare-number "123"
                      :person/name [{:db/id "name1-id"
                                     :person.name/given "foo"}
                                    {:db/id "name2-id"
                                     :person.name/family "bar"}]})
      (is (= (dp/intersect-pull-with-tx nested-pull nested-tx)
             {:patient.id/medicare-number "123"
              :person/name [{:db/id "name1-id"
                             :person.name/given "foo"}
                            ;; nothing to update on name2 because user does not
                            ;; have permission to update family name
                            {:db/id "name2-id"}]})))

  )

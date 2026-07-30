(ns domestic-cleaning.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [domestic-cleaning.store :as store]
            [domestic-cleaning.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-household! st {:household-id "hh-1" :has-children? false :has-pets? false :consented-at "2026-01-01"})
    (store/register-household! st {:household-id "hh-2" :has-children? true :has-pets? false :consented-at "2026-01-01"})
    st))

(deftest proceeds-on-clean-visit
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :visit :household-id "hh-1" :safety-class :low
                   :effect :propose :confidence 0.9}]
    (is (= :proceed (:decision (governor/assess env proposal))))))

(deftest holds-on-unregistered-household
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :visit :household-id "no-such-household" :safety-class :low
                   :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-household (:rule %)) (:violations result)))))

(deftest holds-on-no-actuation-violation
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :visit :household-id "hh-1" :safety-class :low
                   :effect :direct-write :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-actuation (:rule %)) (:violations result)))))

(deftest proceeds-on-chemical-use-in-household-without-children-or-pets
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :chemical-use :household-id "hh-1" :safety-class :low
                   :effect :propose :confidence 0.9}]
    (is (= :proceed (:decision (governor/assess env proposal))))))

(deftest holds-on-chemical-use-in-household-with-children-without-high-safety-class
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :chemical-use :household-id "hh-2" :safety-class :medium
                   :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :chemical-exposure-safety (:rule %)) (:violations result)))))

(deftest human-approval-on-chemical-use-in-household-with-children-with-high-safety-class
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :chemical-use :household-id "hh-2" :safety-class :high
                   :effect :propose :confidence 0.9}]
    (is (= :human-approval (:decision (governor/assess env proposal))))))

(deftest human-approval-on-low-confidence
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :visit :household-id "hh-1" :safety-class :none
                   :effect :propose :confidence 0.2}
        result (governor/assess env proposal)]
    (is (= :human-approval (:decision result)))
    (is (= :low-confidence (:reason result)))))

(deftest store-records-append-only
  (let [st (fresh-store)]
    (store/record-visit! st {:visit-id "v1" :household-id "hh-1" :tasks [:vacuum]})
    (store/record-chemical-use! st {:event-id "c1" :household-id "hh-1" :chemical "surface-cleaner" :area "kitchen"})
    (is (= 1 (count (store/visits-of st "hh-1"))))
    (is (= 1 (count (store/chemical-uses-of st "hh-1"))))
    (is (empty? (store/visits-of st "hh-2")))))

(deftest a-proposal-without-confidence-does-not-proceed
  (testing "確信度を言っていない提案は、確信していると言っていないので auto-proceed
            させない。この既定は 2026-07-30 まで 1.0 で、:confidence を持たない提案が
            :proceed していた（ADR-2607309100）。fleet の boolean 方言 346 件はすべて
            0.0 既定で、うち isco-5419 はそれを明示的にテストしている。"
    (let [st (fresh-store)
          env (governor/env-for-store st)
          proposal {:kind :visit :household-id "hh-1" :safety-class :low :effect :propose}
          result (governor/assess env proposal)]
      (is (= 0.0 (:confidence result))
          "欠落した :confidence は 0.0 であって 1.0 ではない")
      (is (not= :proceed (:decision result))
          "確信度不明の提案が自動で通ってはならない"))))

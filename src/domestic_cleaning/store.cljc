(ns domestic-cleaning.store
  "SSoT for the ISCO-08 9111 independent domestic-cleaning sole-proprietor
  actor, behind a `Store` protocol so the backend is a swap (MemStore
  default ‖ a real Datomic/kotoba-server backend, per the itonami actor
  pattern).

  Domain = independent domestic cleaning practice:

    household        — a consented client household (householdId,
                       hasChildren? boolean, hasPets? boolean,
                       consentedAt)
    visit            — a scheduled/completed cleaning visit (visitId,
                       householdId, tasks)
    chemical-use     — a chemical-cleaning-agent use event (eventId,
                       householdId, chemical, area)

  The append-only records are the operating ledger: a visit or
  chemical-use event must reference a registered (consented) household,
  and visits/chemical-use events are never mutated in place, only
  appended.")

(defprotocol Store
  (household [st household-id])
  (visits-of [st household-id])
  (chemical-uses-of [st household-id])
  (register-household! [st household])
  (record-visit! [st visit])
  (record-chemical-use! [st chemical-use]))

(defrecord MemStore [state]
  Store
  (household [_ household-id]
    (get-in @state [:households household-id]))
  (visits-of [_ household-id]
    (filter #(= household-id (:household-id %)) (:visits @state)))
  (chemical-uses-of [_ household-id]
    (filter #(= household-id (:household-id %)) (:chemical-uses @state)))
  (register-household! [_ household]
    (swap! state assoc-in [:households (:household-id household)] household))
  (record-visit! [_ visit]
    (swap! state update :visits (fnil conj []) visit))
  (record-chemical-use! [_ chemical-use]
    (swap! state update :chemical-uses (fnil conj []) chemical-use)))

(defn mem-store
  ([] (mem-store {}))
  ([seed]
   (->MemStore (atom (merge {:households {} :visits [] :chemical-uses []} seed)))))

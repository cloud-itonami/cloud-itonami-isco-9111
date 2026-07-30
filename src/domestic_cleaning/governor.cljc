(ns domestic-cleaning.governor
  "DomesticCleaningGovernor — the independent safety/traceability layer
  for the ISCO-08 9111 independent domestic-cleaning actor. The Cleaning
  Advisor proposes actions (visit, chemical-use); it has no notion of
  household provenance or chemical-exposure risk, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD — the
  itonami-actor pattern (independent Governor gates a proposing actor)
  applied to this occupation.

  Charter (mirrors ADR-2607011000 robotics premise + ADR-2607012000
  cloud-itonami-isco): the actor never dispatches a robot action or writes an
  operating record the governor refuses. A chemical-use event in a
  household with children or pets present ALWAYS requires human sign-off
  — it can never be auto-approved.

  HARD invariants for :cleaning/propose:
    1. Household provenance — a visit or chemical-use event must
       reference a registered (consented) household.
    2. No-actuation         — the proposal must not directly mutate a
       visit or chemical-use record outside the record-visit!/
       record-chemical-use! path (effect must be :propose, never a raw
       store write).
    3. Chemical-exposure safety — a chemical-use event in a household
       with `has-children?` or `has-pets?` true always requires :high or
       higher safety-class, forcing human sign-off; it is never
       auto-approved regardless of confidence.
  SOFT:
    4. Confidence floor → escalate."
  (:require [domestic-cleaning.store :as store]))

(def confidence-floor 0.6)
(def safety-classes [:none :low :medium :high :safety-critical])

(defn- safety-rank [safety-class]
  (let [idx (.indexOf safety-classes safety-class)]
    (if (neg? idx) 0 idx)))

(defn- exposure-risk? [found-household proposal]
  (and (= :chemical-use (:kind proposal))
       found-household
       (or (:has-children? found-household) (:has-pets? found-household))))

(defn- hard-violations [{:keys [household-fn]} proposal]
  (let [{:keys [household-id safety-class effect]} proposal
        found-household (household-fn household-id)]
    (cond-> []
      (nil? found-household)
      (conj {:rule :no-household :detail (str "未登録/未同意 household " household-id)})

      (not= :propose effect)
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and (exposure-risk? found-household proposal)
           (< (safety-rank (or safety-class :none)) (safety-rank :high)))
      (conj {:rule :chemical-exposure-safety
             :detail "children/pets のいる household での chemical-use は :high 以上の safety-class が必須"}))))

(defn assess
  "Assess a proposal against `env` (a map with `:household-fn` lookup,
  decoupled from any concrete Store so this stays pure). Returns
  `{:decision :proceed|:hold|:human-approval :violations [...] :confidence n}`."
  [env proposal]
  (let [violations (hard-violations env proposal)
        safety-class (or (:safety-class proposal) :none)
        confidence (or (:confidence proposal) 0.0)]
    (cond
      (seq violations)
      {:decision :hold :violations violations :confidence confidence}

      (>= (safety-rank safety-class) (safety-rank :high))
      {:decision :human-approval :violations [] :confidence confidence}

      (< confidence confidence-floor)
      {:decision :human-approval :violations [] :confidence confidence
       :reason :low-confidence}

      :else
      {:decision :proceed :violations [] :confidence confidence})))

(defn env-for-store
  "Build the decoupled env map `assess` needs from a concrete
  `domestic-cleaning.store/Store` implementation."
  [store]
  {:household-fn #(store/household store %)})

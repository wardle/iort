(ns com.eldrix.iort.impl.cdm
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(s/def ::id string?)
(s/def ::tables string?)
(s/def ::fields string?)
(s/def ::cdm
  (s/keys :req-un [::id]
          :opt-un [::tables ::fields]))

(s/def ::registry (s/coll-of ::cdm))

(def supported-cdm-versions
  "Registry of supported versions."
  [{:id     "5.4"
    :fields "OMOP_CDMv5.4_Field_Level.csv"
    :tables "OMOP_CDMv5.4_Table_Level.csv"}])

(def cdm-version-by-id
  (reduce (fn [acc {:keys [id] :as x}] (assoc acc id x)) {} supported-cdm-versions))

(when-not (s/valid? ::registry supported-cdm-versions)
  (throw (ex-info "invalid CDM registry" (s/explain-data ::registry supported-cdm-versions))))

(def default-cdm-version "5.4")

(s/def ::cdm-version (into #{} (map :id) supported-cdm-versions))
(s/def ::config (s/keys :opt-un [::cdm-version]))

(s/fdef cdm
  :args (s/cat :v (s/? ::cdm-version)))
(defn cdm
  "Returns information about a specific CDM version, or the default if omitted.
  For example,
  ```
  (cdm \"5.4\")
  ```"
  ([]
   (cdm nil))
  ([v]
   (if v
     (cdm-version-by-id v)
     (cdm default-cdm-version))))

(defn column-title->kw
  "Convert column title to a keyword. 
  Almost all columns are camelCase, but they've added a column 'unique DQ identifiers' 
  which we convert."
  [s] (-> s (str/replace #" " "") keyword))

(defn read-csv
  [f]
  (with-open [reader (io/reader f)]
    (let [csv-data (csv/read-csv reader)]
      (mapv zipmap
            (->> (first csv-data)
                 (map column-title->kw)
                 repeat)
            (rest csv-data)))))

(s/fdef model-structures
  :args (s/cat :config (s/? ::config)))
(defn model-structures
  "Return OMOP model structure definitions grouped by table name. 
  Returns a map of CDM table name to the table definition. Each definition
  is a close representation of the source Table_Level.csv but with an 
  additional key ':fields' with data from Field_Level.csv for that table."
  [{:keys [cdm-version] :as config}]
  (let [{:keys [tables fields]} (cdm cdm-version)]
    (if (and tables fields)
      (let [fields-by-table (group-by :cdmTableName (read-csv (io/resource fields)))]
        (reduce (fn [acc {:keys [cdmTableName] :as table}]
                  (assoc acc cdmTableName (assoc table :fields (get fields-by-table cdmTableName))))
                {} (read-csv (io/resource tables))))
      (throw (ex-info "invalid CDM version" config)))))

(comment
  (require '[clojure.spec.test.alpha :as stest])
  (stest/instrument)
  supported-cdm-versions
  (cdm "5.4")
  (def all-table-fields
    (let [fields (read-csv (io/resource (:fields (cdm "5.4"))))]
      (group-by :cdmTableName fields))))

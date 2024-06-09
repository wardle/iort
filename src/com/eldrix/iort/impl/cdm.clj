(ns com.eldrix.iort.impl.cdm
  (:require
   [charred.api :as csv]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; CDM registry = supported CDM versions
;;

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

(when-not (s/valid? ::registry supported-cdm-versions)
  (throw (ex-info "invalid CDM registry" (s/explain-data ::registry supported-cdm-versions))))

(def default-cdm-version "5.4")

(def cdm-version-by-id
  (reduce (fn [acc {:keys [id] :as x}] (assoc acc id x)) {} supported-cdm-versions))

(s/fdef cdm
  :args (s/cat :v (s/? ::cdmVersion)))
(defn cdm
  "Returns information from the registry about a specific CDM version, or the
  default if omitted. For example,
  ```
  (cdm \"5.4\")
  ```
  =>
  {:id     \"5.4\"
    :fields \"OMOP_CDMv5.4_Field_Level.csv\"
    :tables \"OMOP_CDMv5.4_Table_Level.csv\"}"
  ([]
   (cdm nil))
  ([v]
   (if v
     (cdm-version-by-id v)
     (cdm default-cdm-version))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CDM model specification
;;
;;

(s/def ::cdmVersion (into #{} (map :id) supported-cdm-versions))
(s/def ::cdmTableName string?)
(s/def ::schema string?)
(s/def ::isRequired #{"Yes" "No"})
(s/def ::conceptPrefix string?)
(s/def ::cdmFields (s/coll-of ::cdmField))
(s/def ::cdmTableModel (s/keys :req-un [::cdmTableName ::schema ::isRequired ::conceptPrefix ::cdmFields]))
(s/def ::cdmModel (s/map-of string? ::cdmTableModel))

;; CDM configuration specification

(s/def ::config (s/keys :opt-un [::cdmModel ::cdmVersion ::schemas]))

;;
;; CDM models 
;;

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

(defn parse-yes-no [x]
  (= "Yes" x))

(defn parse-str [x]
  (when-not (= "NA" x) x))

(defn parse-int [x]
  (when-not (= "NA" x) (parse-long x)))

(def parsers
  {:isPrimaryKey              parse-yes-no
   :isForeignKey              parse-yes-no
   :isRequired                parse-yes-no
   :measurePersonCompleteness parse-yes-no})

(defn parse-model [m]
  (reduce-kv
   (fn [acc k v]
     (assoc acc k ((get parsers k parse-str) v)))
   {} m))

(defn model-structures*
  "Return CDM structures as a map of table name to table definition, with each
  table definition containing :cdmFields with a sequence of fields.
  Parameters: config - a map containing keys:
  - :cdmVersion - version needed, may be omitted
  - :schemas - a set of strings of schema names to limit result."
  [{:keys [cdmVersion schemas] :as config}]
  (let [{:keys [tables fields]} (cdm cdmVersion)]
    (if (and tables fields)
      (let [table-filter    (if (seq schemas) #(schemas (:schema %)) (constantly true))
            tables#         (->> (read-csv (io/resource tables))
                                 (filter table-filter)
                                 (map parse-model))
            table-names     (into #{} (map :cdmTableName) tables#)
            fields#         (->> (read-csv (io/resource fields))
                                 (filter #(table-names (:cdmTableName %)))
                                 (map parse-model))
            fields-by-table (group-by :cdmTableName fields#)]
        (reduce (fn [acc {:keys [cdmTableName] :as table}]
                  (assoc acc cdmTableName (assoc table :cdmFields (get fields-by-table cdmTableName))))
                {}
                tables#))
      (throw (ex-info "invalid CDM version" config)))))

(defn available-schemas
  ([]
   (available-schemas nil))
  ([cdmVersion]
   (let [model (model-structures* {:cdmVersion cdmVersion})]
     (into #{} (map :schema) (vals model)))))

(s/fdef with-model-structures
  :args (s/cat :config (s/? ::config))
  :ret  ::config)
(defn with-model-structures
  "Updates the configuration with OMOP model structures using key :cdmModel.
  For convenience, returns its argument if cdmModel already defined.
  The model specification is a map of CDM table name to the table definition. 
  Each definition is a close representation of the source Table_Level.csv but 
  with an  additional key ':cdmFields' with data from Field_Level.csv for 
  that table. Some source data are parsed such as 'NA' to 'null', and 
  'Yes' and 'No' to boolean."
  [{:keys [cdmModel] :as config}]
  (if cdmModel config (assoc config :cdmModel (model-structures* config))))

(comment
  (require '[clojure.spec.test.alpha :as stest])
  (available-schemas)
  (map parse-model (read-csv (io/resource (:tables (cdm)))))
  (read-csv (io/resource (:fields (cdm))))
  (map :cdmTableName (vals (:cdmModel (with-model-structures {:schemas #{"CDM"}}))))
  (stest/instrument)
  supported-cdm-versions
  (cdm "5.4")
  (def all-table-fields
    (let [fields (read-csv (io/resource (:fields (cdm "5.4"))))]
      (group-by :cdmTableName fields))))

(ns com.eldrix.iort.impl.models
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [honey.sql :as sql]))

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
     (first (filter (fn [{:keys [id]}] (= v id)) supported-cdm-versions))
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

(def datatypes
  "A map of OMOP CDM datatypes to SQL types"
  {"datetime" "timestamp"})

(defn create-field-sql
  "Given a table field specification, return the :with-columns clauses for
  a :create-table operation."
  [{:keys [cdmFieldName isRequired isPrimaryKey cdmDatatype]}]
  (remove nil?
          [(keyword cdmFieldName)
           (keyword (get datatypes cdmDatatype cdmDatatype))
           (when (= isPrimaryKey "Yes") [:primary-key])
           (if (= isRequired "Yes") [:not nil] :null)]))

(defn create-table-sql
  [table-fields]
  (let [table-name (:cdmTableName (first table-fields))]
    (when (not-every? #(= table-name (:cdmTableName %)) table-fields)
      (throw (ex-info "all fields must relate to the same table" {:fields table-fields})))
    {:create-table (keyword table-name)
     :with-columns (mapv create-field-sql table-fields)}))

(defn drop-table-sql
  [table-name]
  {:drop-table (keyword table-name)})

;; register new honey sql clauses that clone the usage of the built-in modify-coumn clause
(sql/register-clause! :add-constraint :modify-column :modify-column)
(sql/register-clause! :drop-constraint :modify-column :modify-column)

(defn fk-constraint-id
  "Generate a suitable identifier for a foreign key constraint."
  [{:keys [cdmTableName cdmFieldName]}]
  (keyword (str "fpk_" cdmTableName "_" cdmFieldName)))

(defn add-fk-constraints-sql
  [{:keys [cdmTableName cdmFieldName isForeignKey fkTableName fkFieldName] :as field}]
  (when-not (= isForeignKey "Yes")
    (throw (ex-info "cannot generate a foreign key constraint" field)))
  {:alter-table    (keyword cdmTableName)
   :add-constraint [(fk-constraint-id field)
                    [:foreign-key (keyword cdmFieldName)]
                    [:references (keyword fkTableName) (keyword fkFieldName)]]})

(defn drop-fk-constraints-sql
  [{:keys [cdmTableName isForeignKey] :as field}]
  (when-not (= isForeignKey "Yes")
    (throw (ex-info "cannot generate a foreign key constraint" field)))
  {:alter-table     (keyword cdmTableName)
   :drop-constraint [(fk-constraint-id field)]})

(defn add-table-constraints-sql
  [table-fields]
  (->> table-fields
       (filter #(= "Yes" (:isForeignKey %)))
       (map add-fk-constraints-sql)))

(defn drop-table-constraints-sql
  [table-fields]
  (->> table-fields
       (filter #(= "Yes" (:isForeignKey %)))
       (map drop-fk-constraints-sql)))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-tables-sql
  "Return a sequence of SQL DDL statements to create the tables for the CDM 
  version specified, or the default version."
  ([] (create-tables-sql {}))
  ([config] (mapv create-table-sql (map :fields (vals (model-structures config))))))

(defn drop-tables-sql
  ([] (drop-tables-sql {}))
  ([config] (mapv drop-table-sql (map :cdmTableName (vals (model-structures config))))))

(s/fdef add-constraints-sql
  :args (s/cat :opts (s/? ::config)))
(defn add-constraints-sql
  "Returns a sequence of DDL statements to add the necessary foreign key
  constraints for the CDM version specified, or the default if omitted."
  ([] (add-constraints-sql {}))
  ([config] (mapcat add-table-constraints-sql (map :fields (vals (model-structures config))))))

(s/fdef drop-constraints-sql
  :args (s/cat :opts (s/? ::config)))
(defn drop-constraints-sql
  "Returns a sequence of DDL statements to remove foreign key constraints for 
  the CDM version specified, or the default if omitted."
  ([] (drop-constraints-sql {}))
  ([config] (mapcat drop-table-constraints-sql (map :fields (vals (model-structures config))))))
(comment
  (require '[clojure.spec.test.alpha :as stest])
  (stest/instrument)
  supported-cdm-versions
  (cdm "5.4")
  (def all-table-fields (let [fields (read-csv (io/resource (:fields (cdm "5.4"))))]
                          (group-by :cdmTableName fields)))
  (sql/format (create-table-sql (get all-table-fields "observation")))
  (mapv sql/format (create-tables-sql))
  (mapv sql/format (add-table-constraints-sql (get all-table-fields "observation")))
  (mapv sql/format (add-constraints-sql))
  (mapv sql/format (drop-constraints-sql {:cdm-version "5.3"}))
  (mapv sql/format (drop-tables-sql))

  (time (get (group-by :cdmFieldName (:fields (get (model-structures {}) "person"))) "person_id")))

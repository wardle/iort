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

(def default-cdm-version "5.4")

(when-not (s/valid? ::registry supported-cdm-versions)
  (throw (ex-info "invalid CDM registry" (s/explain-data ::registry supported-cdm-versions))))

(s/fdef cdm-version
  :args (s/cat :v string?))
(defn cdm-version
  "Returns information about a specific CDM version.
  For example,
  ```
  (cdm-version \"5.4\")
  ```"
  [v]
  (first (filter (fn [{:keys [id]}] (= v id)) supported-cdm-versions)))

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

(defn foreign-key-sql
  [{:keys [cdmFieldName isForeignKey fkTableName fkFieldName]}]
  (when (= isForeignKey "Yes")
    [[:foreign-key (keyword cdmFieldName)] [:references (keyword fkTableName) (keyword fkFieldName)]]))

(defn foreign-keys-sql
  [table-fields]
  (->> table-fields
       (filter #(= "Yes" (:isForeignKey %)))
       (map foreign-key-sql)))

(defn create-table-sql
  [table-fields]
  (let [table-name (:cdmTableName (first table-fields))]
    (when (not-every? #(= table-name (:cdmTableName %)) table-fields)
      (throw (ex-info "all fields must relate to the same table" {:fields table-fields})))
    {:create-table (keyword table-name)
     :with-columns (into (mapv create-field-sql table-fields)
                         (foreign-keys-sql table-fields))}))

(defn create-tables-sql
  "Return a sequence of create table SQL DDL statements for the CDM version
  specified, or the default version."
  ([]
   (create-tables-sql {}))
  ([{:keys [version-string]}]
   (let [cdm (cdm-version (or version-string default-cdm-version))
         fields (read-csv (io/resource (:fields cdm)))
         fields-by-table (group-by :cdmTableName fields)]
     (mapv create-table-sql (vals fields-by-table)))))

(comment
  supported-cdm-versions
  (cdm-version "5.4")
  (def all-table-fields (let [fields (read-csv (io/resource (:fields (cdm-version "5.4"))))]
                          (group-by :cdmTableName fields)))
  (sql/format (create-table-sql (get all-table-fields "observation")))
  (foreign-keys-sql (get all-table-fields "observation"))
  (mapv sql/format (create-tables-sql)))

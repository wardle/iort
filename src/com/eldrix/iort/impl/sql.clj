(ns com.eldrix.iort.impl.sql
  (:refer-clojure :exclude [format])
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.eldrix.iort.impl.cdm :as cdm]
   [honey.sql :as sql])
  (:import
   (java.time.format DateTimeFormatter)))

(def supported-dialects
  "These are the databases that will be tested; it is likely other databases
  with JDBC drivers would work. Any dialect-specific tweaks are provided 
  within this namespace via multimethods."
  #{:sqlite
    :postgresql
    :oracle
    :duckdb
    :snowflake
    :sqlserver})

(def default-datatypes
  "A map of OMOP CDM datatypes to SQL types"
  {"datetime"     "timestamp"
   "varchar(MAX)" "text"})

(defn parse-cdm-date [s]
  (java.time.LocalDate/parse s DateTimeFormatter/BASIC_ISO_DATE))

(def cdmDatatype->parser
  {"float"    parse-double
   "Integer"  parse-long                   ;; unfortunately the CDM datatypes are inconsistent on case
   "integer"  parse-long
   "date"     parse-cdm-date})

(defn parsers
  "Based on table fields, return a vector of parsers for import."
  [table-fields]
  (reduce (fn [acc {:keys [cdmDatatype]}]
            (conj acc (get cdmDatatype->parser cdmDatatype identity)))
          [] table-fields))

(defn foreign-key-sql
  [{:keys [cdmFieldName isForeignKey fkTableName fkFieldName]}]
  (when isForeignKey
    [[:foreign-key (keyword cdmFieldName)] [:references (keyword fkTableName) (keyword fkFieldName)]]))

(defn foreign-keys-sql
  [table-fields]
  (->> table-fields
       (filter :isForeignKey)
       (map foreign-key-sql)))

(defn create-field-sql
  "Given a table field specification, return the :with-columns clauses for
  a :create-table operation."
  [{:keys [datatypes]} {:keys [cdmFieldName isRequired isPrimaryKey cdmDatatype]}]
  (remove nil?
          [(keyword cdmFieldName)
           (keyword (get (or datatypes default-datatypes) cdmDatatype cdmDatatype))
           (when isPrimaryKey [:primary-key])
           (if isRequired [:not nil] :null)]))

(defn create-table-sql
  "Given a CDM table specification, return SQL statements.
  Parameters:
  opts: a map with :-
        - constraints : true or false whether to add foreign key constraints
        - datatypes   : map of CDM datatypes to SQL datatypes to use "
  [{:keys [constraints] :as opts} table-fields]
  (let [table-name (:cdmTableName (first table-fields))
        fields (mapv #(create-field-sql opts %) table-fields)]
    (when (not-every? #(= table-name (:cdmTableName %)) table-fields)
      (throw (ex-info "all fields must relate to the same table" {:cdmFields table-fields})))
    {:create-table (keyword table-name)
     :with-columns (if constraints ;; add foreign key constraints if required 
                     (into fields (foreign-keys-sql table-fields))
                     fields)}))

(defn drop-table-sql
  [table-name]
  {:drop-table (keyword table-name)})

;; register new honey sql clauses that clone the usage of the built-in modify-column clause
(sql/register-clause! :add-constraint :modify-column :modify-column)
(sql/register-clause! :drop-constraint :modify-column :modify-column)

(defn fk-constraint-id
  "Generate a suitable identifier for a foreign key constraint."
  [{:keys [cdmTableName cdmFieldName]}]
  (keyword (str "fpk_" cdmTableName "_" cdmFieldName)))

(defn add-fk-constraints-sql
  [{:keys [cdmTableName cdmFieldName isForeignKey fkTableName fkFieldName] :as field}]
  (when-not isForeignKey
    (throw (ex-info "cannot generate a foreign key constraint" field)))
  {:alter-table    (keyword cdmTableName)
   :add-constraint [(fk-constraint-id field)
                    [:foreign-key (keyword cdmFieldName)]
                    [:references (keyword fkTableName) (keyword fkFieldName)]]})

(defn drop-fk-constraints-sql
  [{:keys [cdmTableName isForeignKey] :as field}]
  (when-not isForeignKey
    (throw (ex-info "cannot generate a foreign key constraint" field)))
  {:alter-table     (keyword cdmTableName)
   :drop-constraint [(fk-constraint-id field)]})

(defn add-table-constraints-sql
  [table-fields]
  (->> table-fields
       (filter :isForeignKey)
       (map add-fk-constraints-sql)))

(defn drop-table-constraints-sql
  [table-fields]
  (->> table-fields
       (filter :isForeignKey)
       (map drop-fk-constraints-sql)))

(defn idx-name
  [{:keys [cdmTableName cdmFieldName]}]
  (keyword (str "idx_" cdmTableName "_" cdmFieldName)))

(defn add-idx-sql
  [{:keys [cdmTableName cdmFieldName] :as  field}]
  {:create-index [(idx-name field) [(keyword cdmTableName) (keyword cdmFieldName)]]})

(defn drop-idx-sql
  [field]
  {:drop-index (idx-name field)})

(defn add-table-indices
  [table-fields]
  (->> table-fields
       (filter (fn [{:keys [cdmFieldName]}] (str/ends-with? cdmFieldName "_id")))
       (map add-idx-sql)))

(defn drop-table-indices
  [table-fields]
  (->> table-fields
       (filter (fn [{:keys [cdmFieldName]}] (str/ends-with? cdmFieldName "_id")))
       (map drop-idx-sql)))

(defn upsert-sql
  [{:keys [cdmFields cdmTableName] :as table}]
  (let [field-names (map :cdmFieldName cdmFields)
        field-names# (str/join "," field-names)
        primary-keys (into #{} (comp (filter :isPrimaryKey) (map :cdmFieldName)) cdmFields)
        primary-keys# (str/join "," primary-keys)
        non-primary-keys (set/difference (set field-names) primary-keys)
        non-primary-keys (str/join "," (map #(str % "=excluded." %) non-primary-keys))
        placeholders (str/join "," (repeat (count field-names) "?"))]
    (when (empty? primary-keys)
      (throw (ex-info (str "cannot 'upsert' into table '" cdmTableName "' as no primary keys") table)))
    [(str "INSERT INTO " cdmTableName
          " (" field-names# ") VALUES (" placeholders ") "
          " ON CONFLICT (" primary-keys# ") DO UPDATE SET " non-primary-keys)]))

(defn insert-sql*
  [{:keys [cdmFields cdmTableName] :as table}]
  (when table
    (let [field-names (map :cdmFieldName cdmFields)
          field-names# (str/join "," field-names)
          placeholders (str/join "," (repeat (count field-names) "?"))]
      [(str "INSERT INTO " cdmTableName
            " (" field-names# ") VALUES (" placeholders ")")])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti create-tables-sql
  "Return SQL DDL statements to create all CDM database tables."
  :dialect)
(defmulti drop-tables-sql
  "Return SQL DDL statements to drop all CDM database tables."
  :dialect)
(defmulti add-constraints-sql
  "Return SQL DDL statements to add all necessary foreign key constraints."
  :dialect)
(defmulti drop-constraints-sql
  "Return SQL DDL statements to drop all foreign key constraints."
  :dialect)
(defmulti add-indices-sql
  "Return SQL DDL statements to add all indices to a CDM database."
  :dialect)
(defmulti drop-indices-sql
  "Return SQL DDL statements to drop all indices from a CDM database."
  :dialect)

(defmulti insert-sql
  "Return SQL insert statement to insert data into the table specified.
  Returns 'nil' when no table definition for table specified."
  (fn [{:keys [dialect]} _table-name] dialect))

;;
;;
;;

(defmethod create-tables-sql :default
  [{:keys [cdmModel] :as config}]
  (when-not cdmModel (throw (ex-info "missing :cdmModel in config" config)))
  (mapv (fn [{:keys [cdmFields]}]
          (create-table-sql {:constraints false, :datatypes default-datatypes} cdmFields))
        (vals cdmModel)))

(defmethod create-tables-sql :sqlite
  [{:keys [cdmModel] :as config}]
  (when-not cdmModel (throw (ex-info "missing :cdmModel in config" config)))
  (mapv (fn [{:keys [cdmFields]}]
          (create-table-sql {:constraints true, :datatypes default-datatypes} cdmFields))
        (vals cdmModel)))

(defmethod drop-tables-sql :default
  [{:keys [cdmModel] :as config}]
  (when-not cdmModel (throw (ex-info "missing :cdmModel in config" config)))
  (mapv drop-table-sql (map :cdmTableName (vals cdmModel))))

(defmethod add-constraints-sql :default
  [{:keys [cdmModel] :as config}]
  (when-not cdmModel (throw (ex-info "missing :cdmModel in config" config)))
  (mapcat add-table-constraints-sql (map :cdmFields (vals cdmModel))))

(defmethod add-constraints-sql :sqlite   ;; In SQLite, constraints are defined in table creation, so turn on FK support
  [_config]
  ["PRAGMA foreign_keys=1"])

(defmethod drop-constraints-sql :default
  [{:keys [cdmModel] :as config}]
  (when-not cdmModel (throw (ex-info "missing :cdmModel in config" config)))
  (mapcat drop-table-constraints-sql (map :cdmFields (vals cdmModel))))

(defmethod drop-constraints-sql :sqlite
  [_config]
  ["PRAGMA foreign_keys=0"])

(defmethod add-indices-sql :default
  [{:keys [cdmModel] :as config}]
  (when-not cdmModel (throw (ex-info "missing :cdmModel in config" config)))
  (mapcat add-table-indices (map :cdmFields (vals cdmModel))))

(defmethod drop-indices-sql :default
  [{:keys [cdmModel] :as config}]
  (when-not cdmModel (throw (ex-info "missing :cdmModel in config" config)))
  (mapcat drop-table-indices (map :cdmFields (vals cdmModel))))

(defmethod insert-sql :default
  [{:keys [cdmModel] :as config} table-name]
  (when-not cdmModel (throw (ex-info "missing :cdmModel in config" config)))
  (insert-sql* (get cdmModel table-name)))

(defn format
  [x]
  (cond
    (string? x) (vector x)
    (map? x)    (sql/format x)))

(comment
  (def config (cdm/with-model-structures {}))
  (keys config)
  (keys (:cdmModel config))
  (insert-sql config "concept")
  (insert-sql config "observation")
  (into #{} (map :cdmDatatype) (mapcat :cdmFields (vals (:cdmModel (cdm/with-model-structures {})))))

  (def config (cdm/with-model-structures {}))
  (create-tables-sql {})
  (map format (create-tables-sql config))
  (map format (create-tables-sql (assoc config :dialect :sqlite)))
  (map format (add-constraints-sql config))
  (map format (add-constraints-sql (assoc config :dialect :sqlite)))
  (map format (drop-constraints-sql config))
  (map format (drop-constraints-sql (assoc config :dialect :sqlite)))
  (map format (add-indices-sql config))
  (map format (drop-tables-sql config)))

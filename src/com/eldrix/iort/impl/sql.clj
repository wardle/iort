(ns com.eldrix.iort.impl.sql
  (:refer-clojure :exclude [format])
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.eldrix.iort.impl.cdm :as cdm]
   [honey.sql :as sql]))

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

(defn foreign-key-sql
  [{:keys [cdmFieldName isForeignKey fkTableName fkFieldName]}]
  (when (= isForeignKey "Yes")
    [[:foreign-key (keyword cdmFieldName)] [:references (keyword fkTableName) (keyword fkFieldName)]]))

(defn foreign-keys-sql
  [table-fields]
  (->> table-fields
       (filter #(= "Yes" (:isForeignKey %)))
       (map foreign-key-sql)))

(defn create-field-sql
  "Given a table field specification, return the :with-columns clauses for
  a :create-table operation."
  [{:keys [datatypes]} {:keys [cdmFieldName isRequired isPrimaryKey cdmDatatype]}]
  (remove nil?
          [(keyword cdmFieldName)
           (keyword (get (or datatypes default-datatypes) cdmDatatype cdmDatatype))
           (when (= isPrimaryKey "Yes") [:primary-key])
           (if (= isRequired "Yes") [:not nil] :null)]))

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
      (throw (ex-info "all fields must relate to the same table" {:fields table-fields})))
    {:create-table (keyword table-name)
     :with-columns (if constraints ;; add foreign key constraints if required 
                     (into fields (foreign-keys-sql table-fields))
                     fields)}))

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

(defn prepared-upsert-sql
  [{:keys [fields cdmTableName] :as table}]
  (let [field-names (map :cdmFieldName fields)
        field-names# (str/join "," field-names)
        primary-keys (into #{} (comp (filter (fn [{:keys [isPrimaryKey]}] (= "Yes" isPrimaryKey))) (map :cdmFieldName)) fields)
        primary-keys# (str/join "," primary-keys)
        non-primary-keys (set/difference (set field-names) primary-keys)
        non-primary-keys (str/join "," (map #(str % "=excluded." %) non-primary-keys))
        placeholders (str/join "," (repeat (count field-names) "?"))]
    (when (empty? primary-keys)
      (throw (ex-info (str "cannot 'upsert' into table '" cdmTableName "' as no primary keys") table)))
    [(str "INSERT INTO " cdmTableName
          " (" field-names# ") VALUES (" placeholders ") "
          " ON CONFLICT (" primary-keys# ") DO UPDATE SET " non-primary-keys)]))

(defn prepared-insert-sql
  [{:keys [fields cdmTableName]}]
  (let [field-names (map :cdmFieldName fields)
        field-names# (str/join "," field-names)
        placeholders (str/join "," (repeat (count field-names) "?"))]
    [(str "INSERT INTO " cdmTableName
          " (" field-names# ") VALUES (" placeholders ")")]))

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
  "Return SQL insert statement to insert data into the table specified. "
  (fn [{:keys [dialect]} _table-name] dialect))
;;
;;
;;

(defmethod create-tables-sql :default
  [config]
  (mapv (fn [{:keys [fields]}] (create-table-sql {:constraints false, :datatypes default-datatypes} fields))
        (vals (cdm/model-structures config))))

(defmethod create-tables-sql :sqlite
  [config]
  (mapv (fn [{:keys [fields]}] (create-table-sql {:constraints true, :datatypes default-datatypes} fields))
        (vals (cdm/model-structures config))))

(defmethod drop-tables-sql :default
  [config]
  (mapv drop-table-sql (map :cdmTableName (vals (cdm/model-structures config)))))

(defmethod add-constraints-sql :default
  [config]
  (mapcat add-table-constraints-sql (map :fields (vals (cdm/model-structures config)))))

(defmethod add-constraints-sql :sqlite   ;; In SQLite, constraints are defined in table creation, so turn on FK support
  [_config]
  ["PRAGMA foreign_keys=1"])

(defmethod drop-constraints-sql :default
  [config]
  (mapcat drop-table-constraints-sql (map :fields (vals (cdm/model-structures config)))))

(defmethod drop-constraints-sql :sqlite
  [_config]
  ["PRAGMA foreign_keys=0"])

(defmethod add-indices-sql :default
  [config]
  (mapcat add-table-indices (map :fields (vals (cdm/model-structures config)))))

(defmethod drop-indices-sql :default
  [config] (mapcat drop-table-indices (map :fields (vals (cdm/model-structures config)))))

(defmethod insert-sql :default
  [config table-name]
  (some-> (get (cdm/model-structures config) table-name)
          (prepared-insert-sql)))

(defn format
  [x]
  (cond
    (string? x) (vector x)
    (map? x)    (sql/format x)))

(comment
  (insert-sql {} "concept")
  (insert-sql {} "observation")
  (cdm/model-structures {})
  (map format (create-tables-sql {}))
  (map format (create-tables-sql {:dialect :sqlite}))
  (map format (add-constraints-sql {}))
  (map format (add-constraints-sql {:dialect :sqlite}))
  (map format (drop-constraints-sql {}))
  (map format (drop-constraints-sql {:dialect :sqlite}))
  (map format (add-indices-sql {}))
  (map format (drop-tables-sql {})))

(ns com.eldrix.iort.core
  "iort: interoperable outcomes research tools"
  (:require
   [clojure.spec.alpha :as s]
   [com.eldrix.iort.impl.sql :as cdm-sql]
   [clojure.spec.test.alpha :as stest]
   [com.eldrix.iort.impl.cdm :as cdm]))

(defn cdm-versions
  "Return a set of supported versions.
  e.g. 
  ```
  (cdm-versions)
  =>
  #{\" 5.4 \"}
  ```"
  []
  (set (map :id cdm/supported-cdm-versions)))

(def dialects
  "Return a set of supported dialects."
  cdm-sql/supported-dialects)

(s/fdef create-tables-sql
  :args (s/cat :config ::config))

(defn create-tables-sql
  "Return SQL DDL statements to create the OMOP CDM database tables. 
  Parameters: an options map with:
  - :cdm-version - e.g. \"5.4\"."
  [config]
  (map cdm-sql/format (cdm-sql/create-tables-sql config)))

(s/fdef drop-tables-sql
  :args (s/cat :config ::config))

(defn drop-tables-sql
  "Return a sequence of SQL DDL statements to drop OMOP CDM database tables.
  Parameters: a configuration map with:
  - :cmd-version - e.g. \"5.4\"."
  [config]
  (map cdm-sql/format (cdm-sql/drop-tables-sql config)))

(s/fdef add-constraints-sql
  :args (s/cat :config ::config))

(defn add-constraints-sql
  "Return a sequence of SQL DDL statements to add foreign key constraints. 
  Parameters: options map with:
  - :cdm-version - e.g. \"5.4\"."
  [config]
  (map cdm-sql/format (cdm-sql/add-constraints-sql config)))

(s/fdef drop-constraints-sql
  :args (s/cat :config ::config))

(defn drop-constraints-sql
  "Return a sequence of SQL DDL statements to drop OMOP CDM constraints. 
  Parameters: an options map with:
  - :cdm-version - e.g. \"5.4\"."
  [config]
  (map cdm-sql/format (cdm-sql/drop-constraints-sql config)))

(s/fdef add-indices-sql
  :args (s/cat :config ::config))

(defn add-indices-sql
  "Return a sequence of SQL DDL statements to add indices. As per the CDM 
  advice, fields ending in '_id' are indexed. It is usually faster to defer 
  creation of indices until AFTER large imports of data iff there is confidence
  in the integrity of that source information, such as the OMOP CDM 
  vocabularies dataset.
  Parameters: a configuration map with:
  - :cmd-version - e.g. \"5.4\"."
  [config]
  (map cdm-sql/format (cdm-sql/add-indices-sql config)))

(s/fdef drop-indices-sql
  :args (s/cat :config ::config))

(defn drop-indices-sql
  "Return a sequence of SQL DDL statements to drop OMOP CDM indices.
  Parameters: a configuration map with:
  - :cmd-version - e.g. \"5.4\"."
  [config]
  (map cdm-sql/format (cdm-sql/drop-indices-sql config)))

(defn create-database-sql
  [config]
  (concat (create-tables-sql config)
          (add-constraints-sql config)
          (add-indices-sql config)))

(defn drop-database-sql
  [config]
  (concat (drop-indices-sql config)
          (drop-constraints-sql config)
          (drop-tables-sql config)))

(comment
  (clojure.spec.test.alpha/instrument)
  (cdm-versions)
  (create-tables-sql {})
  (create-tables-sql {:cdm-version "5.3" :dialect :postgresql})
  (create-tables-sql {:cdm-version "5.4" :dialect :postgresql})
  (create-tables-sql {:cdm-version "5.4" :dialect :sqlite})
  (add-constraints-sql {})
  (add-indices-sql {})
  (drop-constraints-sql {})
  (drop-tables-sql {})
  (drop-indices-sql {})
  (create-database-sql {:dialect :sqlite})
  (drop-database-sql {}))


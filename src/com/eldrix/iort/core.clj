(ns com.eldrix.iort.core
  "iort: interoperable outcomes research tools"
  (:require
   [clojure.spec.alpha :as s]
   [com.eldrix.iort.impl.models :as models]
   [clojure.spec.test.alpha :as stest]
   [honey.sql :as sql]))

(s/def ::config ::models/config)

(s/fdef create-tables-sql
  :args (s/cat :config ::config))
(defn create-tables-sql
  "Return SQL DDL statements to create the OMOP CDM database tables. 
  Parameters: an options map with:
  - :cdm-version - e.g. \"5.4\"
  Returns SQL as Clojure data structures suitable for [[honey.sql/format]]."
  [config]
  (models/create-tables-sql config))

(s/fdef drop-tables-sql
  :args (s/cat :config ::config))
(defn drop-tables-sql
  "Return a sequence of SQL DDL statements to drop OMOP CDM database tables.
  Parameters: a configuration map with:
  - :cmd-version - e.g. \"5.4\"
  Returns SQL as Clojure data structures suitable for [[honey.sql/format]]. "
  [config]
  (models/drop-tables-sql config))

(s/fdef add-constraints-sql
  :args (s/cat :config ::config))
(defn add-constraints-sql
  "Return a sequence of SQL DDL statements to add foreign key constraints. 
  Parameters: options map with:
  - :cdm-version - e.g. \"5.4\".
  Returns SQL as Clojure data structures suitable for [[honey.sql/format]]."
  [config]
  (models/add-constraints-sql config))

(s/fdef drop-constraints-sql
  :args (s/cat :config ::config))
(defn drop-constraints-sql
  "Return a sequence of SQL DDL statements to drop OMOP CDM constraints. 
  Parameters: an options map with:
  - :cdm-version - e.g. \"5.4\"
  Returns SQL as Clojure data structures suitable for [[honey.sql/format]]."
  [config]
  (models/drop-constraints-sql config))

(s/fdef add-indices-sql
  :args (s/cat :config ::config))
(defn add-indices-sql
  "Return a sequence of SQL DDL statements to add indices. As per the CDM 
  advice, fields ending in '_id' are indexed. It is usually faster to defer 
  creation of indices until AFTER large imports of data iff there is confidence
  in the integrity of that source information, such as the OMOP CDM 
  vocabularies dataset.
  Parameters: a configuration map with:
  - :cmd-version - e.g. \"5.4\"
  Returns SQL as Clojure data structures suitable for [[honey.sql/format]]. "
  [config]
  (models/add-indices-sql config))

(s/fdef drop-indices-sql
  :args (s/cat :config ::config))
(defn drop-indices-sql
  "Return a sequence of SQL DDL statements to drop OMOP CDM indices.
  Parameters: a configuration map with:
  - :cmd-version - e.g. \"5.4\"
  Returns SQL as Clojure data structures suitable for [[honey.sql/format]]. "
  [config]
  (models/drop-indices-sql config))

(comment
  (clojure.spec.test.alpha/instrument)
  (mapv sql/format (create-tables-sql {:cdm-version "5.4"}))
  (mapv sql/format (add-constraints-sql {}))
  (mapv sql/format (add-indices-sql {}))
  (mapv sql/format (drop-constraints-sql {}))
  (mapv sql/format (drop-tables-sql {}))
  (mapv sql/format (drop-indices-sql {})))

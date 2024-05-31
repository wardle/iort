(ns com.eldrix.iort.database-test
  (:require [clojure.test :refer [deftest is run-test]]
            [com.eldrix.iort.core :as iort]
            [next.jdbc :as jdbc]))

(defn test-up-down
  [db config]
  (let [conn (jdbc/get-connection db)]
    (run! #(jdbc/execute! conn %) (iort/create-tables-sql config))
    (run! #(jdbc/execute! conn %) (iort/add-constraints-sql config))
    (run! #(jdbc/execute! conn %) (iort/add-indices-sql config))
    (run! #(jdbc/execute! conn %) (iort/drop-indices-sql config))
    (run! #(jdbc/execute! conn %) (iort/drop-constraints-sql config))
    (run! #(jdbc/execute! conn %) (iort/drop-tables-sql config))))

(deftest test-sqlite
  (test-up-down "jdbc:sqlite:test.db" {:dialect :sqlite}))

(deftest test-postgresql
  (test-up-down {:dbtype "postgresql" :dbname "omop_cdm"} {:dialect :postgresql}))

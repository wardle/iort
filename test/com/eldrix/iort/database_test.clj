(ns com.eldrix.iort.database-test
  (:require [clojure.test :refer [deftest is]]
            [com.eldrix.iort.core :as iort]
            [next.jdbc :as jdbc]))

(defn test-up-down
  [db config]
  (let [conn (jdbc/get-connection db)]
    (run! #(jdbc/execute! conn %) (iort/create-database-sql config))
    (run! #(jdbc/execute! conn %) (iort/drop-database-sql config))))

(deftest test-unsupported-version
  (is (thrown? clojure.lang.ExceptionInfo (test-up-down "jdbc:sqlite:test.db" {:dialect :sqlite :cdm-version "invalid"}))))

(deftest ^:sqlite test-sqlite
  (test-up-down "jdbc:sqlite:test.db" {:dialect :sqlite}))

(deftest ^:postgresql test-postgresql
  (test-up-down {:dbtype "postgresql" :dbname "omop_cdm"} {:dialect :postgresql}))

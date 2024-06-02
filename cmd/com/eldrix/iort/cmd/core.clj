(ns com.eldrix.iort.cmd.core
  (:gen-class)
  (:require [clojure.string :as str]
            [com.eldrix.iort.core :as iort]
            [com.eldrix.iort.cmd.cli :as cli]
            [next.jdbc :as jdbc]))

(defn exit [status-code msg]
  (println msg)
  (System/exit status-code))

(defn print-sql [stmts]
  (println (str/join \newline (map first stmts))))

(defn execute-sql [jdbc-url stmts]
  (with-open [conn (jdbc/get-connection jdbc-url)]
    (run! #(jdbc/execute! conn %) stmts)))

(defn -main [& args]
  (let [{:keys [options usage errors arguments]} (cli/parse-opts args)]
    (let [{:keys [jdbc-url create drop create-tables drop-tables add-constraints drop-constraints
                  add-indexes drop-indexes]} options]
      (cond
        (:help options)
        (println usage)
        (seq errors)
        (exit 1 (str (str/join \newline (map #(str "ERROR:" %) errors)) "\n\n" usage))
        (and jdbc-url create)
        (execute-sql jdbc-url (iort/create-database-sql options))
        (and jdbc-url drop)
        (execute-sql jdbc-url (iort/drop-database-sql options))
        (and jdbc-url create-tables)
        (execute-sql jdbc-url (iort/create-tables-sql options))
        (and jdbc-url drop-tables)
        (execute-sql jdbc-url (iort/drop-tables-sql options))
        (and jdbc-url add-constraints)
        (execute-sql jdbc-url (iort/add-constraints-sql options))
        (and jdbc-url drop-constraints)
        (execute-sql jdbc-url (iort/drop-constraints-sql options))
        (and jdbc-url add-indexes)
        (execute-sql jdbc-url (iort/add-indices-sql options))
        (and jdbc-url drop-indexes)
        (execute-sql jdbc-url (iort/drop-indices-sql options))
        create
        (print-sql (iort/create-database-sql options))
        drop
        (print-sql (iort/drop-database-sql options))
        create-tables
        (print-sql (iort/create-tables-sql options))
        drop-tables
        (print-sql (iort/drop-tables-sql options))
        add-constraints
        (print-sql (iort/add-constraints-sql options))
        drop-constraints
        (print-sql (iort/drop-constraints-sql options))
        add-indexes
        (print-sql (iort/add-indices-sql options))
        drop-indexes
        (print-sql (iort/drop-indices-sql options))))))

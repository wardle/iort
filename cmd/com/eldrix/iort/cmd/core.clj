(ns com.eldrix.iort.cmd.core
  (:gen-class)
  (:require [clojure.string :as str]
            [com.eldrix.iort.core :as iort]
            [com.eldrix.iort.impl.vocabulary :as vocab]
            [com.eldrix.iort.cmd.cli :as cli]
            [next.jdbc :as jdbc]
            [com.eldrix.iort.impl.cdm :as cdm]))

(defn exit [status-code msg]
  (println msg)
  (System/exit status-code))

(defn print-sql [stmts]
  (println (str/join \newline (map first stmts))))

(defn execute-sql
  ([jdbc-url stmts]
   (execute-sql jdbc-url stmts {:atomic? false}))
  ([jdbc-url stmts {:keys [atomic?]}]
   (with-open [conn (jdbc/get-connection jdbc-url)]
     (if atomic?
       ;; run all statements atomically, and return error if one fails
       (try
         (jdbc/with-transaction [txn conn]
           (run! #(jdbc/execute! txn %) stmts))
         (catch Exception e
           (println (ex-message e)) [e]))
       ;; run each statement independently collecting any errors
       (loop [stmts stmts
              errors []]
         (if (seq stmts)
           (do
             (println "Executing" (first (first stmts)))
             (let [[stmts' errors']  ;; either success or failure returns what should be recurred
                   (try (jdbc/execute! conn (first stmts)) [(rest stmts) errors]
                        (catch Exception e
                          (println (ex-message e)) [(rest stmts) (conj errors e)]))]
               (recur stmts' errors')))
           errors))))))

(defn import-vocabulary [{:keys [jdbc-url] :as config} dir]
  (with-open [conn (jdbc/get-connection jdbc-url)]
    (vocab/import-cdm-fixtures conn config dir)))

(defn -main [& args]
  (let [{:keys [options usage errors arguments]} (cli/parse-opts args)
        {:keys [jdbc-url create-tables drop-tables add-constraints drop-constraints
                add-indexes drop-indexes vocab]} options
        config (cdm/with-model-structures options)]
    (cond
      (:help options)
      (println usage)
      (seq errors)
      (exit 1 (str (str/join \newline (map #(str "ERROR:" %) errors)) "\n\n" usage))
      jdbc-url
      (do
        (when drop-indexes
          (println "dropping indexes" options)
          (execute-sql jdbc-url (iort/drop-indices-sql config)))
        (when drop-constraints
          (println "dropping constraints" options)
          (execute-sql jdbc-url (iort/drop-constraints-sql config)))
        (when drop-tables
          (println "dropping tables" options)
          (execute-sql jdbc-url (iort/drop-tables-sql config) {:atomic? true}))
        (when create-tables
          (println "Creating tables" options)
          (execute-sql jdbc-url (iort/create-tables-sql config) {:atomic? true}))
        (when vocab
          (println "Importing vocabulary" vocab)
          (import-vocabulary config vocab))
        (when add-constraints
          (println "Adding constraints" options)
          (execute-sql jdbc-url (iort/add-constraints-sql config)))
        (when add-indexes
          (println "Adding indexes" options)
          (execute-sql jdbc-url (iort/add-indices-sql config))))

      drop-tables
      (print-sql (iort/drop-tables-sql config))
      create-tables
      (print-sql (iort/create-tables-sql config))
      drop-constraints
      (print-sql (iort/drop-constraints-sql config))
      add-constraints
      (print-sql (iort/add-constraints-sql config))
      drop-indexes
      (print-sql (iort/drop-indices-sql config))
      add-indexes
      (print-sql (iort/add-indices-sql config)))))

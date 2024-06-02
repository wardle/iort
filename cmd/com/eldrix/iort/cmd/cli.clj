(ns com.eldrix.iort.cmd.cli
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [com.eldrix.iort.core :as iort]))

(defn usage [summary]
  (str/join "\n"
            ["Usage: clj -M:run <options>"
             "or     java -jar iort.jar <options>"
             ""
             summary
             ""
             " (*) --create is equivalent to '--create-tables --add-constraints --add-indexes'"
             " (*) --drop   is equivalent to '--drop-tables --drop-constraints --drop-indexes'"
             ""
             (str/join " " (into ["Supported CDM versions:"] (sort (iort/cdm-versions))))]))

(def cli-options
  [["-m" "--cdm-version VERSION" (str "OMOP CDM version; eg. " (first (iort/cdm-versions)))
    :validate [(iort/cdm-versions) (str "Supported CDM versions: " (str/join " " (sort (iort/cdm-versions))))]]
   ["-u" "--jdbc-url URL" "Database URL; eg. jdbc:sqlite:my-cdm.db"]
   [nil "--dialect DIALECT" "Database dialect; usually optional when URL provided"
    :parse-fn keyword
    :validate [(set iort/dialects) (str "Supported dialects: " (str/join " " (map name (sort iort/dialects))))]]
   ["-c" "--create" "Create database tables, constraints and indexes (*)"]
   ["-d" "--drop" "Drop database tables, constraints and indexes (*)"]
   [nil "--create-tables" "Create database tables"]
   [nil "--add-constraints" "Add database constraints"]
   [nil "--add-indexes" "Add database indexes"]
   [nil "--drop-tables" "Drop database tables"]
   [nil "--drop-constraints" "Drop database constraints"]
   [nil "--drop-indexes" "Drop database indexes"]
   ["-h" "--help"]])

(defn parse-opts
  [args]
  (let [{:keys [options arguments errors summary] :as parsed} (cli/parse-opts args cli-options)]
    (cond-> (assoc parsed :usage (usage summary))
      (:create options)
      (-> (assoc-in [:options :create-tables] true)
          (assoc-in [:options :add-constraints] true)
          (assoc-in [:options :add-indexes] true)))))

(comment
  (parse-opts (str/split "--dialect sqlite --cdm-version 5.4 --create" #"\s"))
  (let [{:keys [options arguments errors summary] :as parsed} (cli/parse-opts (str/split "--dialect sqlite --cdm-version 5.4 --create" #"\s") cli-options)]

    (clojure.pprint/pprint options)
    (println "errors:" errors)
    (println (usage summary))))

(ns com.eldrix.iort.impl.vocabulary
  "Functions to read the OMOP CDM vocabulary files. "
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [charred.api :as csv]
   [com.eldrix.iort.impl.sql :as cdm-sql]
   [next.jdbc :as jdbc]))

(def cdm-vocabulary-csv-files
  ["CONCEPT.csv"
   "CONCEPT_RELATIONSHIP.csv"
   "RELATIONSHIP.csv"
   "CONCEPT_ANCESTOR.csv"
   "CONCEPT_SYNONYM.csv"
   "VOCABULARY.csv"
   "CONCEPT_CLASS.csv"
   "DOMAIN.csv"
   "CONCEPT_CPT4.csv"
   "DRUG_STRENGTH.csv"])

(defn csv-filename->table-name [s]
  (str/lower-case (first (str/split s #"\." 2))))

(defn stream-batched-csv
  "Stream the contents of CSV file 'f' in batches to the 
  `clojure.core.async` channel 'ch'. Each batch is returned as a map of the 
  metadata with additional keys ':headers' and ':data'.
  - :headers  - a vector of CSV headings
  - :data     - a sequence of vectors representing the CSV data "
  [f ch {:keys [metadata batch-size close?] :or {metadata {}, batch-size 5000, close? false}}]
  (with-open [reader (io/reader f)]
    (try
      (let [[headers & data] (csv/read-csv reader :separator \tab :quote \.)
            batches (partition-all batch-size data)]
        (loop [batches# (map #(assoc metadata :headers headers :data %) batches)]
          (if (and (seq batches#) (async/>!! ch (first batches#)))
            (recur (rest batches#))
            (when close? (async/close! ch)))))
      (catch Exception e (.printStackTrace e)))))

(defn cdm-file [config f]
  (let [table-name (csv-filename->table-name (.getName f))]
    (when-let [insert-sql (cdm-sql/insert-sql config table-name)]
      {:f f
       :table-name table-name
       :insert-sql insert-sql})))

(defn cdm-file-seq
  "Returns a sequence of maps containing the CDM vocabulary files in the
  specified directory."
  [config dir]
  (->> (file-seq (io/file dir))
       (map #(cdm-file config %))
       (remove nil?)))

(defn import-cdm-fixtures
  [conn config dir]
  (let [ch (async/chan)]
    (async/thread
      (doseq [{:keys [f table-name insert-sql] :as cdm-file} (cdm-file-seq config dir)]
        (println "Importing " table-name "from" f)
        (stream-batched-csv f ch {:metadata cdm-file}))
      (async/close! ch))
    (loop [{:keys [insert-sql headers data] :as batch} (async/<!! ch)]
      (when batch
        (jdbc/with-transaction [txn conn]
          (jdbc/execute-batch! txn (first insert-sql) data {}))
        (recur (async/<!! ch))))))

(comment
  (def dir "/Users/mark/Downloads/vocabulary_download_v5_{6f5de1f1-ac8b-4332-92f2-9872830b1281}_1716899271991")
  (def f "/Users/mark/Downloads/vocabulary_download_v5_{6f5de1f1-ac8b-4332-92f2-9872830b1281}_1716899271991/CONCEPT.csv")
  (def ch (async/chan 1))
  (csv-filename->table-name "CONCEPT.csv")
  (cdm-file-seq {}  dir)
  (import-cdm-fixtures (jdbc/get-connection "jdbc:sqlite:test3.db") {} dir)
  (async/thread (stream-batched-csv f ch {:metadata {:type :concept} :batch-size 10 :close? true}))
  (async/<!! ch))





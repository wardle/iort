(ns com.eldrix.iort.impl.vocabulary
  "Functions to read the OMOP CDM vocabulary files. "
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [charred.api :as csv]
   [com.eldrix.iort.impl.sql :as cdm-sql]
   [next.jdbc :as jdbc]
   [com.eldrix.iort.impl.cdm :as cdm]))

(defn csv-filename->table-name [s]
  (str/lower-case (first (str/split s #"\." 2))))

(defn parse-row
  [parsers row]
  (map (fn [f x] (f x)) parsers row))

(defn parse-rows
  [parsers rows]
  (map #(parse-row parsers %) rows))

(defn stream-batched-csv
  "Stream the contents of CSV file 'f' in batches to the 
  `clojure.core.async` channel 'ch'. Each batch is returned with the metadata 
  passed in supplemented with keys ':headers' and ':data'.
  - :headers  - a vector of CSV headings
  - :data     - a sequence of vectors representing the CSV data "
  [f ch {:keys [metadata parsers batch-size close?]
         :or {metadata {}, batch-size 5000, close? false}}]
  (with-open [reader (io/reader f)]
    (try
      (let [[headers & data] (csv/read-csv reader :separator \tab :quote \")
            data' (parse-rows parsers data)
            batches (partition-all batch-size data')]
        (loop [batches# (map #(assoc metadata :headers headers :data %) batches)]
          (if (and (seq batches#) (async/>!! ch (first batches#)))
            (recur (rest batches#))
            (when close? (async/close! ch)))))
      (catch Exception e (.printStackTrace e)))))

(defn cdm-file [{:keys [cdmModel] :as config} f]
  (let [table-name (csv-filename->table-name (.getName f))
        table-model (get cdmModel table-name)
        insert-sql (cdm-sql/insert-sql config table-name)]
    (when table-model
      {:f          f
       :table-name table-name
       :insert-sql insert-sql
       :parsers    (cdm-sql/parsers (:cdmFields table-model))})))

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
      (doseq [{:keys [f parsers table-name insert-sql] :as cdm-file} (cdm-file-seq config dir)]
        (println "Importing" table-name "from" f)
        (stream-batched-csv f ch {:metadata cdm-file :parsers parsers}))
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
  (def fns [identity identity parse-long identity])
  (def data ["hi" "there" "5" "bob"])
  (map (fn [f x] (f x)) fns data)
  (import-cdm-fixtures (jdbc/get-connection "jdbc:sqlite:test3.db") {} dir)
  (async/thread (stream-batched-csv f ch {:metadata {:type :concept} :batch-size 10 :close? true}))
  (async/<!! ch))





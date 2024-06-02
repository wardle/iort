(ns com.eldrix.iort.cmd.core
  (:gen-class)
  (:require [clojure.string :as str]
            [com.eldrix.iort.cmd.cli :as cli]))

(defn exit [status-code msg]
  (println msg)
  (System/exit status-code))

(defn -main [& args]
  (let [{:keys [options usage errors arguments]} (cli/parse-opts args)]
    (cond
      (:help options)
      (println usage)
      (seq errors)
      (exit 1 (str (str/join \newline (map #(str "ERROR:" %) errors)) "\n\n" usage)))))

(ns blog-backup.core
  (:require [cljs.core.async :refer [go <!]]
            [clojure.tools.cli :refer [parse-opts]]
            [goog.string :as gs]
            [goog.string.format]
            [blog-backup.logging :refer [debug! info! error! set-level!]]
            [blog-backup.type :refer [new-blog <print-all-posts]]
            [blog-backup.util :as u]))

(enable-console-print!)

(def cli-options
  [["-o" "--out-dir DIR" "output dir"
    :default "/tmp/blog"]
   ["-w" "--who WHO" "whose blog to print"]
   ["-p" "--proxy PROXY" "http proxy"]
   ["-v" "--verbose"]
   ["-h" "--help"]])

(declare print-pdf)
(defn -main [& args]
  (let [{:keys [options summary errors] :as opts} (parse-opts args cli-options)
        {:keys [out-dir who help verbose proxy]} options]
    (when verbose
      (set-level! :debug))
    (debug! (str "\n" (u/pretty-str opts)))
    (cond
      (not (nil? errors)) (error! errors)
      help (info! (str "\n" summary))
      :else (print-pdf out-dir who proxy))))

(defn print-pdf [dir who proxy]
  (u/init-dir! dir)
  (go
    (let [browser (<! (u/<new-browser :devtools false
                                      :args (if proxy
                                              [(gs/format "--proxy-server=%s" proxy)]
                                              [])))
          blog (<! (new-blog {:who who} browser))]
      (try
        (<! (<print-all-posts browser blog dir))
        (catch js/Error e (error! e))
        (finally
          (.close browser))))))

(set! *main-cli-fn* -main)

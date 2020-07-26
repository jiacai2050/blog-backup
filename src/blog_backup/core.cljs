(ns blog-backup.core
  (:require [cljs.core.async :refer [go <!]]
            [clojure.tools.cli :refer [parse-opts]]
            [goog.string.format]
            [blog-backup.logging :refer [debug! info! error! set-level!]]
            [blog-backup.type :refer [new-blog <print-all-posts]]
            [blog-backup.single-page :refer [print-page]]
            [blog-backup.util :as u]))

(enable-console-print!)

(def cli-options
  [["-o" "--out-dir DIR" "output dir" :default "/tmp/blog"]
   ["-w" "--who WHO" "whose blog to print"]
   ["-a" "--addr Address" "print a single page"]
   ["-p" "--proxy PROXY" "http proxy"]
   ;; https://pptr.dev/#?product=Puppeteer&version=v5.2.1&show=api-puppeteerlaunchoptions
   ["-P" "--puppeteer-opts OPTS" "options to set on the browser. format: a=b;c=d"]
   ["-v" "--verbose"]
   ["-h" "--help"]])

(defn print-pdf [dir who pp-opts]
  (u/init-dir! dir)
  (go
    (let [browser (<! (u/<new-browser pp-opts))
          blog (<! (new-blog {:who who} browser))]
      (try
        (<! (<print-all-posts browser blog dir))
        (catch js/Error e (error! e))
        (finally
          (.close browser))))))

(defn -main [& args]
  (let [{:keys [options summary errors] :as opts} (parse-opts args cli-options)
        {:keys [out-dir who addr help verbose proxy puppeteer-opts]} options]
    (when verbose
      (set-level! :debug))
    (debug! (str "\n" (u/pretty-str opts)))
    (cond
      (not (nil? errors)) (error! errors)
      help (info! (str "\n" summary))
      addr (print-page addr out-dir (u/prepare-options proxy puppeteer-opts))
      :else (let [opts (u/prepare-options proxy puppeteer-opts)]
              (print-pdf out-dir who opts)))))

(set! *main-cli-fn* -main)

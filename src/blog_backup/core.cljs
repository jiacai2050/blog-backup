(ns blog-backup.core
  (:require [cljs.core.async :refer [go <!]]
            [clojure.tools.cli :refer [parse-opts]]
            [cljs.reader :as reader]
            [goog.string.format]
            [blog-backup.logging :refer [debug! info! error! set-level!]]
            [blog-backup.type :refer [new-blog <print-all-posts new-static-blog]]
            [blog-backup.single-page :refer [print-page]]
            [blog-backup.util :as u]))

(enable-console-print!)

(def cli-options
  [["-o" "--out-dir DIR" "output dir" :default "/tmp/blog"]
   ["-w" "--who WHO" "whose blog to print"]
   ["-a" "--addr Address" "print a single page"]
   ["-c" "--conf Conf" "config file" :default (str u/home-dir "/.blogbackup.edn")]
   ["-p" "--proxy PROXY" "http proxy"]
   ;; https://pptr.dev/#?product=Puppeteer&version=v5.2.1&show=api-puppeteerlaunchoptions
   ["-P" "--puppeteer-opts OPTS" "options to set on the browser. format: a=b;c=d"]
   ["-v" "--verbose"]
   ["-h" "--help"]])


(defonce static-blogs (atom {}))

(defn print-pdf [dir who pp-opts]
  (u/init-dir! dir)
  (go
    (let [browser (<! (u/<new-browser pp-opts))
          blog (if-let [blog-item (@static-blogs who)]
                 (new-static-blog blog-item browser)
                 (<! (new-blog {:who who} browser)))]
      (try
        (<! (<print-all-posts browser blog dir))
        (catch js/Error e (error! e))
        (finally
          (.close browser))))))

(defn parse-conf! [conf-file]
  (if-let [conf (u/slurp conf-file)]
    (let [conf (reader/read-string conf)]
      (doseq [{:keys [id]
               :as blog-item} (:blogs conf)]
        (swap! static-blogs assoc id (dissoc blog-item :id)))
      (debug! @static-blogs))
    (debug! (u/format-str "conf file %s not exist." conf-file))))

(defn -main [& args]
  (let [{:keys [options summary errors] :as opts} (parse-opts args cli-options)
        {:keys [out-dir who addr conf help verbose proxy puppeteer-opts]} options]
    (when verbose
      (set-level! :debug))
    (debug! (str "\n" (u/pretty-str (dissoc opts :summary))))
    (when conf
      (parse-conf! conf))

    (cond
      (not (nil? errors)) (error! errors)
      help (info! (str "\n" summary))
      addr (print-page addr out-dir (u/prepare-options proxy puppeteer-opts))
      :else  (let [opts (u/prepare-options proxy puppeteer-opts)]
               (print-pdf out-dir who opts)))))

(set! *main-cli-fn* -main)

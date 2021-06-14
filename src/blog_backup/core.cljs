(ns blog-backup.core
  (:require [cljs.core.async :refer [go]]
            [async-error.core :refer-macros [<?]]
            [clojure.tools.cli :refer [parse-opts]]
            [cljs.reader :as reader]
            [goog.string.format]
            [blog-backup.logging :refer [debug! info! error! set-level!]]
            [blog-backup.chromium :as c]
            [blog-backup.pdf :refer [<print-all-posts]]
            [blog-backup.protocol :refer [new-blog new-static-blog]]
            [blog-backup.single-page :refer [print-page]]
            [blog-backup.util :as u]))

(enable-console-print!)

(def cli-options
  [["-o" "--out-dir DIR" "output dir" :default u/default-out-dir]
   ["-w" "--who WHO" "whose blog to print"]
   ["-a" "--addr Address" "print a single page"]
   ["-c" "--conf Config" "config file" :default u/default-conf-file]
   ["-p" "--proxy Proxy" "http proxy"]
   ;; https://pptr.dev/#?product=Puppeteer&version=v5.2.1&show=api-puppeteerlaunchoptions
   ["-P" "--puppeteer-opts OPTS" "options to set on the browser. format: a=b;c=d"]
   ["-m" "--media Media" "media type" :default "print"]
   ["-u" "--user-agent UserAgent" "UserAgent"]
   ["-v" "--verbose"]
   ["-V" "--version"]
   ["-h" "--help"]])


(defonce static-blogs (atom {}))
(defonce version-str "v0.4.1")

(defn print-pdf [dir who pp-opts]
  (u/init-dir! dir)
  (go
   (let [browser (<? (c/<new-browser pp-opts))]
     (try
       (let [blog (if-let [blog-item (@static-blogs who)]
                    (new-static-blog blog-item browser)
                    (<? (new-blog {:who who} browser)))]
         (<? (<print-all-posts browser blog dir)))
       (catch js/Error e (error! "print pdf failed" e))
       (finally
         (.close browser))))))

(defn parse-conf! [conf-file]
  (if-let [conf (u/slurp conf-file)]
    (let [conf (reader/read-string conf)]
      (doseq [{:keys [id]
               :as blog-item} (:blogs conf)]
        (swap! static-blogs assoc id (dissoc blog-item :id))))
    (throw (js/Error. (u/format-str "%s not exist" conf-file)))))

(defn -main [& args]
  (let [{:keys [options summary errors] :as opts} (parse-opts args cli-options)
        {:keys [out-dir who version addr conf help verbose proxy puppeteer-opts
                media user-agent]} options]
    (when verbose
      (set-level! :debug))

    (debug! (u/format-str "default-config-file:%s, default-data-dir:%s" u/default-conf-file u/default-data-dir))
    (debug! (str "\n" (u/pretty-str (dissoc opts :summary))))

    (when help
      (println summary)
      (.exit js/process 0))
    (when version
      (println version-str)
      (.exit js/process 0))

    (when conf
      (parse-conf! conf))
    (when media
      (reset! c/media media))
    (when user-agent
      (reset! c/user-agent user-agent))

    (cond
      (not (nil? errors)) (error! errors)
      help (println summary)
      addr (print-page addr out-dir (u/prepare-options proxy puppeteer-opts))
      who  (let [opts (u/prepare-options proxy puppeteer-opts)]
             (print-pdf out-dir who opts))
      :else (println summary))))

(set! *main-cli-fn* -main)

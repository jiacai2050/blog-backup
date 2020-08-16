(ns blog-backup.util
  (:require [cljs.pprint :refer [pprint]]
            [blog-backup.logging :refer [debug!]]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as cs]
            [goog.string :as gs]
            [goog.string.format]
            ;; nodejs module
            [fs]
            [os])
  (:import [goog.i18n DateTimeFormat]))

(defonce home-dir (.homedir os))

(defn pretty-str [x]
  (with-out-str
    (pprint (js->clj x))))

(defonce ^DateTimeFormat time-format (DateTimeFormat. "yyyy-MM-dd_HH:mm:ss"))

(defn pretty-time [t]
  (.format time-format t))

(defn format-str [tmpl & args]
  (apply gs/format tmpl args))

(defn prepare-options [proxy puppeteer-opts]
  (as-> (->> (cs/split puppeteer-opts #"[;]")
             (filter #(not (cs/blank? %)))
             ;; can't use cs/split with limit
             ;; https://clojure.atlassian.net/browse/CLJS-2528
             (map #(js->clj (gs/splitLimit % "=" 2)))
             (map (fn [[k v]] [(cs/trim (csk/->camelCase k)) (cs/trim v)]))
             (into {}))
      $
    (update $ "args" (fnil #(js->clj (js/JSON.parse %)) "[]"))
    (if proxy
      (update $ "args" #(conj % (str "--proxy-server=" proxy)))
      $)
    (update $ "userDataDir" #(or % (str home-dir "/.blog_backup")))))

(defn slurp [filepath]
  (when (.existsSync fs filepath)
    (fs/readFileSync filepath (clj->js {:encoding "utf8"}))))

(defn init-dir! [dir]
  (let [dir (or dir "/tmp/blog")]
    (if (.existsSync fs dir)
      (when-not (zero? (count (.readdirSync fs dir)))
        (debug! (format-str "output dir %s not empty" dir))
        ;; (throw (js/Error "output dir not empty" {}))
        )
      (do
        (debug! (str "create dir " dir))
        (.mkdirSync fs dir)))
    (debug! (str "output dir set to " dir))))

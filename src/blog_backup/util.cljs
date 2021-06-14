(ns blog-backup.util
  (:require [cljs.pprint :refer [pprint]]
            [blog-backup.logging :refer [debug!]]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as cs]
            [goog.string :as gs]
            [goog.object :as go]
            [goog.string.format]
            ;; nodejs module
            [fs]
            [path]
            [os])
  (:import [goog.i18n DateTimeFormat]))

(defn ensure-dir! [dir]
  (when-not (.existsSync fs dir)
    (debug! (str "create dir " dir))
    (.mkdirSync fs dir)))

(defonce home-dir (.homedir os))
(defonce default-conf-file
  (or (let [f (apply (.-join path) #js [home-dir ".blogbackup.edn"])]
        (when (.existsSync fs f)
          f))
      (when-let [xdg-conf (or (go/get (.-env js/process) "XDG_CONFIG_HOME")
                              (apply (.-join path) #js [home-dir ".config"]))]
        (when (.existsSync fs xdg-conf)
          (let [conf-dir (apply (.-join path) #js [xdg-conf "blogbackup"])]
            (ensure-dir! conf-dir)
            (apply (.-join path) #js [conf-dir "config.edn"]))))))
(defonce default-data-dir (or (let [dir (apply (.-join path) #js [home-dir ".blog_backup"])]
                                (when (.existsSync fs dir)
                                  dir))
                              (when-let [xdg-data (or (go/get (.-env js/process) "XDG_DATA_HOME")
                                                      (apply (.-join path) #js [home-dir ".local" "share"]))]
                                (when (.existsSync fs xdg-data)
                                  (apply (.-join path) #js [xdg-data "blogbackup"])))))

(defonce default-out-dir "/tmp/blog-backup")

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
    (update $ "userDataDir" #(or % default-data-dir))))

(defn slurp [filepath]
  (when (.existsSync fs filepath)
    (fs/readFileSync filepath (clj->js {:encoding "utf8"}))))

(defn init-dir! [dir]
  (let [dir (or dir default-out-dir)]
    (ensure-dir! dir)
    (when-not (zero? (count (.readdirSync fs dir)))
      (let [err-msg (format-str "output dir %s not empty" dir)]
        (debug! err-msg)))
    (debug! (str "output dir set to " dir))))

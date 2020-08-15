(ns blog-backup.util
  (:require-macros [hiccups.core :as hiccups :refer [html]])
  (:require [cljs.core.async.interop :refer-macros [<p!]]
            [async-error.core :refer-macros [go-try]]
            [cljs.pprint :refer [pprint]]
            [blog-backup.logging :refer [debug! info! error!]]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as cs]
            [goog.string :as gs]
            [goog.string.format]
            ;; nodejs module
            [fs]
            [os]
            [puppeteer])
  (:import [goog.i18n DateTimeFormat]))

(defonce home-dir (.homedir os))

(def page-header (html [:div {:style "width:100%; text-align:right; font-size:10px;"}
                        [:span.date]]))
(def page-footer (html [:div {:style "width:90%; text-align:left; font-size:10px;"}
                        [:span.url]]
                       [:div {:style "width:10%; text-align:right; font-size:10px;"}
                        [:span.pageNumber] "/" [:span.totalPages]]))

;; https://github.com/puppeteer/puppeteer/blob/v5.2.0/docs/api.md#pagegotourl-options
(def openpage-opts (clj->js {:timeout (* 60000 3) :waitUntil "load"}))

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

(defn <new-browser [pp-opts]
  (debug! pp-opts)
  (go-try (<p! (.launch puppeteer (clj->js pp-opts)))))

(defn <save-as-pdf [browser filepath {:keys [page url] :as param}]
  (go-try
   (if (.existsSync fs filepath)
     (info! (format-str "%s already exist, skip. param: %s" filepath param))
     (let [_ (info! (format-str "save %s..." filepath ))
           page (or page (let [np (<p! (.newPage browser))
                               resp (<p! (.goto np url openpage-opts))
                               code (.status resp)]
                           (if (>= code 400)
                             (throw (ex-info "open page" {:url url
                                                          :code code
                                                          :headers (pretty-str (.headers resp))}))
                             np)))]
       (<p! (.emulateMediaType page "print"))
       (<p! (.pdf page (clj->js {:path filepath
                                 :printBackground false
                                 :displayHeaderFooter true
                                 :margin  {:bottom 100
                                           :top 50
                                           :right 10
                                           :left 10}
                                 :headerTemplate page-header
                                 :footerTemplate page-footer
                                 })))))))

(defn <eval-in-page [browser url selector vanilla-js-fn]
  (debug! (format-str "[eval-in-page] url: %s, selector: %s" url selector))
  (go-try (let [page (<p! (.newPage browser))]
            (try
              (let [resp (<p! (.goto page url openpage-opts))
                    code (.status resp)]
                ;; (<p! (.evaluate page (fn [] (.scrollBy js/window 0 (.. js/document -body -scrollHeight)))))
                ;; (<p! (.evaluate page (fn [] (.scrollBy js/window 0 (.. js/window -innerHeight)))))
                (if (>= code 400)
                  (throw (ex-info "goto failed" {:url url :code code :headers  (pretty-str (.headers resp))}))
                  (<p! (.$$eval page selector vanilla-js-fn))))
              (finally (<p! (.close page)))))))

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

(defn format-name [out-dir url title seq-num]
  (format-str "%s/%s-%s.pdf" out-dir
              (or (some-> (re-find #"\d+/\d+/\d+" url) (cs/replace "/" "-"))
                  (let [sep (cs/last-index-of url "/")]
                    (str (gs/format "%03d" seq-num)
                         "-"
                         (subs url (inc sep)))))
              (if (empty? title)
                (last (cs/split url "/"))
                (cs/replace title "/" "-"))))

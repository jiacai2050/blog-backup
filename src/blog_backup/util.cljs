(ns blog-backup.util
  (:require-macros [hiccups.core :as hiccups :refer [html]])
  (:require [cljs.core.async :refer [go]]
            [cljs.pprint :refer [pprint]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [blog-backup.logging :refer [debug! info! error!]]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as cs]
            [goog.string :as gs]
            [goog.string.format]
            ;; nodejs module
            [fs]
            [os]
            [puppeteer]))

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

(defn <new-browser [& {:as opts}]
  (let [pp-opts (-> opts
                    (update :user-data-dir #(or % (str home-dir "/pp")))
                    (clj->js :keyword-fn (comp csk/->camelCase name)))]
    (debug! (.stringify js/JSON pp-opts))
    (go (<p! (.launch puppeteer pp-opts)))))

(defn <save-as-pdf [browser filepath {:keys [page url]}]
  (go
    (let [page (or page (let [np (<p! (.newPage browser))
                              resp (<p! (.goto np url openpage-opts))
                              code (.status resp)]
                          (if (>= code 400)
                            (throw (ex-info "open page failed" {:url url
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
                                }))))))

(defn <eval-in-page [browser url selector vanilla-js-fn]
  (debug! (gs/format "[eval-in-page] url: %s, selector: %s" url selector))
  (go (let [page (<p! (.newPage browser))
            resp (<p! (.goto page url openpage-opts))
            code (.status resp)]
        (try
          (if (>= code 400)
            (throw (ex-info "goto failed" {:url url :code code :headers  (pretty-str (.headers resp))}))
            (<p! (.$$eval page selector vanilla-js-fn)))
          (finally (<p! (.close page)))))))


(defn init-dir! [dir]
  (let [dir (or dir "/tmp/blog")]
    (if (.existsSync fs dir)
      (when-not (zero? (count (.readdirSync fs dir)))
        (throw (js/Error "output dir not empty" {})))
      (do
        (info! (str "create dir " dir))
        (.mkdirSync fs dir)))
    (info! (str "output dir set to " dir))))

(defn format-name [out-dir url title seq-num]
  (gs/format "%s/%s-%s.pdf" out-dir
             (or (some-> (re-find #"\d+/\d+/\d+" url) (cs/replace "/" "-"))
                 (let [sep (cs/last-index-of url "/")]
                   (str (gs/format "%03d" seq-num)
                        "-"
                        (subs url (inc sep)))))
             (cs/replace title "/" "-")))

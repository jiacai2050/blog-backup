(ns blog-backup.chromium
  (:require-macros [hiccups.core :as hiccups :refer [html]])
  (:require [async-error.core :refer-macros [go-try <?]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [goog.string.format]
            [blog-backup.logging :refer [debug! info!]]
            [blog-backup.util :as u]
            ;; nodejs
            [puppeteer]
            [fs]))

(def page-header (html [:div {:style "width:100%; text-align:right; font-size:10px;"}
                        [:span.date]]))
(def page-footer (html [:div {:style "width:90%; text-align:left; font-size:10px;"}
                        [:span.url]]
                       [:div {:style "width:10%; text-align:right; font-size:10px;"}
                        [:span.pageNumber] "/" [:span.totalPages]]))

;; https://github.com/puppeteer/puppeteer/blob/v5.2.0/docs/api.md#pagegotourl-options
(def openpage-opts (clj->js {:timeout (* 60000 3) :waitUntil ["load"]}))
(def media (atom "print"))
(def user-agent (atom ""))

(defn <new-browser [pp-opts]
  (debug! pp-opts)
  (go-try (<p! (.launch puppeteer (clj->js pp-opts)))))

(defn <open-page [browser url]
  (go-try
   (debug! (u/format-str "goto %s, opts: %s" url (u/pretty-str openpage-opts)))
   (let [np (<p! (.newPage browser))
         _ (let [ua @user-agent]
             (when-not (empty? ua)
               (debug! (u/format-str "user-agent: %s" ua))
               (<p! (.setUserAgent np ua))))
         resp (<p! (.goto np url openpage-opts))
         ;; _ (debug! (u/format-str "goto %s done, metrics: %s" url (u/pretty-str (<p! (.metrics np)))))
         code (.status resp)]
     (if (>= code 400)
       (throw (ex-info "open page" {:url url
                                    :code code
                                    :headers (u/pretty-str (.headers resp))}))
       np))))

(defn <save-as-pdf [filepath page]
  (go-try
   (if (.existsSync fs filepath)
     (info! (u/format-str "%s already exist, skip." filepath))
     (do
       (info! (u/format-str "save %s..." filepath))
       (<p! (.emulateMediaType page @media))
       (<p! (.pdf page (clj->js {:path filepath
                                 :printBackground false
                                 :displayHeaderFooter true
                                 :margin  {:bottom 100
                                           :top 50
                                           :right 10
                                           :left 10}
                                 :headerTemplate page-header
                                 :footerTemplate page-footer})))))))

(defn <eval-in-page [browser url selector vanilla-js-fn]
  (debug! (u/format-str "[eval-in-page] url: %s, selector: %s" url selector))
  (go-try (let [page (<p! (.newPage browser))]
            (try
              (let [resp (<p! (.goto page url openpage-opts))
                    code (.status resp)]
                ;; (<p! (.evaluate page (fn [] (.scrollBy js/window 0 (.. js/document -body -scrollHeight)))))
                ;; (<p! (.evaluate page (fn [] (.scrollBy js/window 0 (.. js/window -innerHeight)))))
                (if (>= code 400)
                  (throw (ex-info "goto failed" {:url url :code code :headers (u/pretty-str (.headers resp))}))
                  (<p! (.$$eval page selector vanilla-js-fn))))
              (finally (<p! (.close page)))))))

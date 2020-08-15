(ns blog-backup.single-page
  (:require [async-error.core :refer-macros [go-try <?]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as str]
            [blog-backup.util :as u]
            [blog-backup.logging :refer [debug! info! error!]]
            [puppeteer]))

(def title-re #"<title>(.+)</title>")

(defn print-page [url out-dir pp-opts]
  (info! (u/format-str "begin print %s..." url))
  (go-try
   (let [browser (<? (u/<new-browser pp-opts))]
     (try
       (let [page (<p! (.newPage browser))
             resp (<p! (.goto page url u/openpage-opts))
             code (.status resp)]
         (if (>= code 400)
           (throw (ex-info "open page" {:url url
                                        :code code
                                        :headers (u/pretty-str (.headers resp))}))
           (let [body (<p! (.text resp))
                 title (if-let [title (re-find title-re body)]
                         (second title)
                         (str/replace url "/" "-"))
                 out-name (u/format-str "%s/%s-%s.pdf" out-dir title (u/pretty-time (js/Date.)))]
             (<? (u/<save-as-pdf browser out-name {:page page})))))
       (finally
         (<p! (.close browser)))))))

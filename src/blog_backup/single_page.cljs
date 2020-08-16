(ns blog-backup.single-page
  (:require [async-error.core :refer-macros [go-try <?]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as str]
            [blog-backup.util :as u]
            [blog-backup.logging :refer [debug! info! error!]]
            [blog-backup.chromium :as c]
            [puppeteer]))

(defn print-page [url out-dir pp-opts]
  (info! (u/format-str "begin print %s..." url))
  (go-try
   (let [browser (<? (c/<new-browser pp-opts))]
     (try
       (let [page (<? (c/<open-page browser url))
             title (as-> (<p! (.title page))
                       $ (if (empty? $)
                           (str/replace url "/" "-")
                           $))
             out-name (u/format-str "%s/%s.pdf" out-dir title)]
         (<? (c/<save-as-pdf out-name page)))
       (catch js/Error e
         (error! e))
       (finally
         (<p! (.close browser)))))))

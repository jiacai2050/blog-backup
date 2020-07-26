(ns blog-backup.single-page
  (:require [cljs.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as str]
            [blog-backup.util :as u]
            [blog-backup.logging :refer [debug! info! error! set-level!]]
            [goog.string :as gs]
            [goog.string.format]
            [puppeteer]))

(enable-console-print!)
(def title-re #"<title>(.+)</title>")

(defn print-page [url out-dir pp-opts]
  (info! (gs/format "begin print %s..." url))
  (go
    (let [browser (<! (u/<new-browser pp-opts))]
      (try
        (let [page (<p! (.newPage browser))
              resp (<p! (.goto page url u/openpage-opts))
              code (.status resp)]
          (if (>= code 400)
            (error! (gs/format "goto %s failed, status: %d, header: %s" url code (u/pretty-str (.headers resp))))
            (let [body (<p! (.text resp))
                  title (if-let [title (re-find title-re body)]
                          (second title)
                          (str/replace url "/" "-"))
                  out-name (gs/format "%s/%s-%s.pdf" out-dir title (u/pretty-time (js/Date.)))]
              (info! (gs/format "save to %s..." out-name))
              (<! (u/<save-as-pdf browser out-name {:page page})))))
        (catch js/Error err (error! err))
        (finally
          (<p! (.close browser)))))))

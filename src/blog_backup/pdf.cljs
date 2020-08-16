(ns blog-backup.pdf
  (:require [async-error.core :refer-macros [go-try <?]]
            [blog-backup.protocol :as prot]
            [blog-backup.chromium :as c]
            [blog-backup.logging :refer [debug! info! error!]]
            [blog-backup.util :as u]))


(defn <print-all-posts [browser blog out-dir]
  (go-try
   (let [num-print (atom 0)]
     (loop [has-more (prot/page-down! blog)]
       (if has-more
         (do
           (when-let [posts (try (<? (prot/current-posts blog))
                                 (catch js/Error e
                                   (error! (u/format-str "failed to save %d page, skip to next" (prot/current-page blog)) e)))]
             (debug! (u/pretty-str posts))
             (debug! (count posts))
             (doseq [{:keys [title url]} posts]
               (let [out-name (u/format-name out-dir url title (swap! num-print inc))]
                 (try
                   (<? (c/<save-as-pdf browser out-name {:url url}))
                   (catch js/Error e
                     (error! (u/format-str "failed to save %s, skip to next..." url) e)))))
             (debug! "pagedown.."))
           (recur (prot/page-down! blog)))
         (debug! "no more pages."))))))

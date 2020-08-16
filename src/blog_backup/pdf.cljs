(ns blog-backup.pdf
  (:require [async-error.core :refer-macros [go-try <?]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as cs]
            [blog-backup.protocol :as prot]
            [blog-backup.chromium :as c]
            [blog-backup.logging :refer [debug! info! error!]]
            [blog-backup.util :as u]))

(defn- format-name [out-dir url title seq-num]
  (u/format-str "%s/%s-%s.pdf"
                out-dir
                (or (some-> (re-find #"\d+/\d+/\d+" url) (cs/replace "/" "-"))
                    (u/format-str "%03d" seq-num))
                (if (empty? title)
                  (last (cs/split url "/"))
                  (cs/replace title "/" "-"))))

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
               (let [page (<? (c/<open-page browser url))]
                 (try
                   (let [title (if (empty? title)
                                 (<p! (.title page))
                                 title)
                         out-name (format-name out-dir url title (swap! num-print inc))]
                     (<? (c/<save-as-pdf out-name page)))
                   (catch js/Error e
                     (error! (u/format-str "failed to save %s, skip to next..." url) e))
                   (finally
                     (<p! (.close page))))))
             (debug! "pagedown.."))
           (recur (prot/page-down! blog)))
         (debug! "no more pages."))))))

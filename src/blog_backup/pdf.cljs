(ns blog-backup.pdf
  (:require [async-error.core :refer-macros [go-try <?]]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as cs]
            [blog-backup.protocol :as prot]
            [blog-backup.chromium :as c]
            [blog-backup.logging :refer [debug! info! error!]]
            [blog-backup.util :as u]

            [fs]
            [path]))

(defonce pdfjs (js/require "pdfjs"))
(defonce Document (.-Document pdfjs))
(defonce ExternalDocument (.-ExternalDocument. pdfjs))
(defonce merged-file "merged.pdf")

(defn- format-name [out-dir url title seq-num]
  (u/format-str "%s/%s-%s.pdf"
                out-dir
                (or (some-> (re-find #"\d+/\d+/\d+" url) (cs/replace "/" "-"))
                    (u/format-str "%03d" seq-num))
                (if (empty? title)
                  (last (cs/split url "/"))
                  (cs/replace title "/" "-"))))

(defn <merge-to-one [indir out-file]
  (let [files (fs/readdirSync indir)
        files (filter #(and (.endsWith % "pdf")
                            (not= % merged-file)) (js->clj files))
        doc (Document.)]
    (when (zero? (count files))
      (throw (ex-info "no pdfs found" {:input-dir indir})))
    (doseq [f files]
      (let [src (path/join indir f)
            ext (ExternalDocument. (fs/readFileSync src))]
        (.addPagesOf doc ext)))
    (go-try
     (.pipe doc (fs/createWriteStream out-file))
     (<p! (.end doc)))))

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
               (try
                 (let [page (<? (c/<open-page browser url))]
                   (try
                     (let [title (if (empty? title)
                                   (<p! (.title page))
                                   title)
                           out-name (format-name out-dir url title (swap! num-print inc))]
                       (<? (c/<save-as-pdf out-name page)))
                     (finally
                       (<p! (.close page)))))
                 (catch js/Error e
                   (error! (u/format-str "failed to save %s, skip to next..." url) e))))
             (debug! "pagedown.."))
           (recur (prot/page-down! blog)))
         (do
           (debug! "no more pages.")
           (info! "Generate merged.pdf")
           (<merge-to-one out-dir (path/join out-dir merged-file))))))))

(defn merge-pdfs [input-dir out-dir]
  (go
    (try
      (u/ensure-dir! out-dir)
      (let [out-file (path/join out-dir merged-file)]
        (info! (u/format-str "Merge PDFs in %s to %s" input-dir out-file))
        (<? (<merge-to-one input-dir out-file)))
      (catch js/Error e
        (error! "merge failed" e)))))

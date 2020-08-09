(ns blog-backup.type
  (:require  [cljs.core.async :refer [go <!]]
             [cljs.core.async.interop :refer-macros [<p!]]
             [clojure.string :as cs]
             [goog.string :as gs]
             [goog.string.format]
             [blog-backup.logging :refer [debug! info! error!]]
             [blog-backup.util :as u]))

(defprotocol Blog
  (page-down! [this])
  (current-posts [this]))

(defn- new-blog-inner
  "selector: pass to document.querySelectorAll to find all posts on current page.
  pager: construct the url of next page"
  [browser selector pager total-page]
  (let [current-page (atom 0)
        total-page (js/parseInt total-page)]
    (reify Blog
      (page-down! [this]
        (if (< @current-page total-page)
          (do (swap! current-page inc)
              true)
          false))

      (current-posts [this]
        (go
          (let [url (pager @current-page)]
            (as-> (<! (u/<eval-in-page browser url selector
                                       (fn [links]
                                         ;; this callback get executed in chrome, only vanilla JS allowable
                                         (.map links (fn [link]
                                                       #js {"url" (.-href link)
                                                            "title" (.trim (.-textContent link))})))))
                $
              (js->clj $ :keywordize-keys true)
              (filter #(cs/starts-with? (:url %) "http") $))))))))

(defn <print-all-posts [browser blog out-dir]
  (go
    (loop [has-more (page-down! blog)]
      (let [num-print (atom 0)]
        (if has-more
          (let [posts (<! (current-posts blog))]
            (debug! (u/pretty-str posts))
            (debug! (count posts))
            (doseq [{:keys [title url]} posts]
              (let [out-name (u/format-name out-dir url title (swap! num-print inc))]
                (info! out-name)
                (try
                  (<! (u/<save-as-pdf browser out-name {:url url}))
                  (catch js/Error e
                    (error! e)))))
            (debug! "pagedown..")
            (recur (page-down! blog)))
          (debug! "no more pages."))))))

(defmulti new-blog :who)

(defmethod new-blog "ljc" [_ browser]
  (let [archive-url "https://liujiacai.net/archives"]
    (go (new-blog-inner browser
                        "ul.listing > li > a"
                        (fn [page-num]
                          (if (== page-num 1)
                            archive-url
                            (gs/format "%s/page/%d" archive-url page-num)))
                        (<! (u/<eval-in-page browser
                                             archive-url
                                             "nav.page-navigator > a"
                                             (fn [links] (.-innerHTML (aget links 2)))))))))

(defmethod new-blog "yw" [_ browser]
  (let [archive-url "http://www.yinwang.org/"]
    (go (new-blog-inner browser
                        "ul.list-group > li > a"
                        (constantly archive-url)
                        1))))

(defmethod new-blog "yw-wp" [_ browser]
  (let [archive-url "https://yinwang0.wordpress.com/author/yinwang0/"]
    (go (new-blog-inner browser
                        "div#posts-wrapper > article > a"
                        (constantly archive-url)
                        1))))

(defmethod new-blog "nathan" [_ browser]
  (let [archive-url "http://nathanmarz.com/archives/"]
    (go (new-blog-inner browser
                        "div.journal-archive-set > ul > li > a"
                        (constantly archive-url)
                        1))))

(defmethod new-blog :default [{:keys [who]} _]
  (throw (ex-info (gs/format "%s's blog doesn't support yet. " who) {})))

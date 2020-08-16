(ns blog-backup.protocol
  (:require [async-error.core :refer-macros [go-try <?]]
            [clojure.string :as cs]
            [cljstache.core :refer [render]]
            [goog.string :as gs]
            [goog.string.format]
            [blog-backup.chromium :as c]
            [blog-backup.logging :refer [debug! info! error!]]))

(defprotocol Blog
  (page-down! [this])
  (current-page [this])
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

      (current-page [this]
        @current-page)

      (current-posts [this]
        (go-try
         (let [url (pager @current-page)]
           (as-> (<? (c/<eval-in-page browser url selector
                                      (fn [links]
                                        ;; this callback get executed in chrome, only vanilla JS allowable
                                        (.map links (fn [link]
                                                      #js {"url" (.-href link)
                                                           "title" (.trim (.-textContent link))})))))
               $
             (js->clj $ :keywordize-keys true)
             (filter #(cs/starts-with? (:url %) "http") $))))))))

(defmulti new-blog :who)

(defn new-static-blog [{:keys [base-url posts-selector page-tmpl total-page]
                        :as blog-item}
                       browser]
  (debug! (str "use static blog: " blog-item))
  (new-blog-inner browser
                  posts-selector
                  (fn [page-num]
                    (render page-tmpl {:first-page (== 1 page-num)
                                       :base-url base-url
                                       :page-num page-num}))
                  total-page))

(defmethod new-blog :default [{:keys [who]} _]
  (throw (ex-info (gs/format "%s's blog doesn't support yet. " who) {})))

(defmethod new-blog "ljc" [_ browser]
  (let [archive-url "https://liujiacai.net/archives"]
    (go-try (new-blog-inner browser
                            "ul.listing > li > a"
                            (fn [page-num]
                              (if (== page-num 1)
                                archive-url
                                (gs/format "%s/page/%d" archive-url page-num)))
                            (<? (c/<eval-in-page browser
                                                 archive-url
                                                 "nav.page-navigator > a"
                                                 (fn [links] (.-innerHTML (aget links 2)))))))))

;; use static blog config instead
;; (defmethod new-blog "yw" [_ browser]
;;   (let [archive-url "http://www.yinwang.org/"]
;;     (go (new-blog-inner browser
;;                         "ul.list-group > li > a"
;;                         (constantly archive-url)
;;                         1))))

;; (defmethod new-blog "yw-wp" [_ browser]
;;   (let [archive-url "https://yinwang0.wordpress.com/author/yinwang0/"]
;;     (go (new-blog-inner browser
;;                         "div#posts-wrapper > article > a"
;;                         (constantly archive-url)
;;                         1))))

;; (defmethod new-blog "nathan" [_ browser]
;;   (let [archive-url "http://nathanmarz.com/archives/"]
;;     (go (new-blog-inner browser
;;                         "div.journal-archive-set > ul > li > a"
;;                         (constantly archive-url)
;;                         1))))

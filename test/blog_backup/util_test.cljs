(ns blog-backup.util-test
  (:require [blog-backup.util :as u]
            [goog.object :as gobj]
            [clojure.test :refer [is are deftest testing]]))

(deftest prepare-options-test []
  (testing "default data dir"
      (let [default-dir (str u/home-dir "/.blog_backup")]
        (are [proxy opts expected] (= (assoc expected "userDataDir" default-dir)
                                      (u/prepare-options proxy opts))
          ;; normal case
          nil nil  {"args" []}
          nil "a=b;c=d;"  {"a" "b", "c" "d" "args" []}

          nil "executablePath=/Applications/chrome"  {"executablePath" "/Applications/chrome", "args" []}
          ;; clojure-case -> camelCase
          nil "executable-path=/Applications/chrome"  {"executablePath" "/Applications/chrome", "args" []}
          ;; opts contains whitespace
          nil "a = b ; c = d ;" {"a" "b", "c" "d" "args" []}
          ;; proxy not nil
          "socks5://127.0.0.1:13659" "a=b;c=d" {"a" "b", "c" "d" "args" ["--proxy-server=socks5://127.0.0.1:13659"]}
          "socks5://127.0.0.1:13659" nil {"args" ["--proxy-server=socks5://127.0.0.1:13659"]}
          ;; proxy merge into existing args
          "socks5://127.0.0.1:13659" "a=b;args=[\"--disable-gpu\"]" {"a" "b", "args" ["--disable-gpu" "--proxy-server=socks5://127.0.0.1:13659"]})))

  (testing "update data dir"
    (is (= (u/prepare-options nil "user-data-dir=/tmp") {"userDataDir" "/tmp", "args" []}))))

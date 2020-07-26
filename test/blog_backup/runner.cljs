(ns blog-backup.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [blog-backup.util-test]))


;; (doo.runner/doo-all-tests)

(doo-tests 'blog-backup.util-test)

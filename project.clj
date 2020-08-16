(defproject blog-backup "0.3.1"
  :description "Backup blog posts to PDF for offline storage"
  :url "https://github.com/jiacai2050/blog-backup/"
  :license {:name "MIT"
            :url "https://liujiacai.net/license/MIT.html?year=2020"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 [org.clojure/core.async "1.2.603"]
                 ;; https://github.com/alexanderkiel/async-error
                 [org.clojars.akiel/async-error "0.3"
                  :exclusions [org.clojure/core.async
                               org.clojure/clojure]]
                 ;; [funcool/promesa "5.1.0"]
                 [camel-snake-kebab "0.4.1"]
                 [hiccups "0.3.0"]
                 [cljstache "2.0.6"]
                 [org.clojure/tools.cli "1.0.194"]
                 ]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-pprint "1.3.2"]
            [lein-doo "0.1.10"]]
  :npm {:dependencies [[source-map-support "0.5.19"]
                       [puppeteer "5.2.0"]]}
  :source-paths ["src" "target/classes"]
  :clean-targets [:target-path "bin"]
  :target-path "target"
  :profiles {:dev {:dependencies [[cider/piggieback "0.5.0"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}
             :release {:cljsbuild {:builds {:main {:compiler {:optimizations :simple
                                                              :pretty-print false}}}}}
             :test {:cljsbuild {:builds {:test {:source-paths ["src" "test"]
                                                :compiler {:output-to "target/js/test.js"
                                                           :main blog-backup.runner
                                                           :target :nodejs
                                                           :optimizations :none}}}}}}
  :cljsbuild {:builds {:main {:source-paths ["src"]
                              :compiler {:output-to "bin/main.js"
                                         :closure-defines {"goog.log.ENABLED" true}
                                         :main blog-backup.core
                                         :optimizations :none
                                         :target :nodejs
                                         :pretty-print true}}}}
  :aliases {"dev" ["cljsbuild" "auto" "main"]
            "test" ["with-profile" "test" "doo" "node" "test" "auto"]
            "release" ["with-profile" "release" "do"
                       ["clean"]
                       ["cljsbuild" "once" "main"]]})

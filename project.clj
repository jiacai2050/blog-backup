(defproject blog-backup "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 [org.clojure/core.async "1.2.603"]
                 [camel-snake-kebab "0.4.1"]
                 [hiccups "0.3.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 ]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.2"]
            [lein-cljsbuild "1.1.8"]]
  :npm {:dependencies [[source-map-support "0.5.19"]
                       [puppeteer "5.2.0"]]}
  :source-paths ["src" "target/classes"]
  :clean-targets [:target-path "bin"]
  :target-path "target"
  :profiles {:dev {:dependencies [[cider/piggieback "0.5.0"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :cljsbuild {:builds [{:id "main"
                        :source-paths ["src"]
                        :compiler {:output-to "bin/main.js"
                                   :closure-defines {"goog.log.ENABLED" true}
                                   :main blog-backup.core
                                   :optimizations :none
                                   :target :nodejs
                                   :pretty-print true}}
                       {:id "single-page"
                        :source-paths ["src"]
                        :compiler {:output-to "bin/single-page.js"
                                   :closure-defines {"goog.log.ENABLED" true}
                                   :main blog-backup.single-page
                                   :optimizations :none
                                   :target :nodejs
                                   :pretty-print true}}]}
  :aliases {"single-page" ["cljsbuild" "once" "single-page"]
            "main" ["cljsbuild" "once" "main"]}
  )

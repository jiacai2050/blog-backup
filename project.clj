(defproject blog-backup "0.1.0-SNAPSHOT"
  :description "Backup blog posts to PDF for offline storage"
  :url "https://github.com/jiacai2050/blog-backup/"
  :license {:name "MIT"
            :url "https://liujiacai.net/license/MIT.html?year=2020"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 [org.clojure/core.async "1.2.603"]
                 [camel-snake-kebab "0.4.1"]
                 [hiccups "0.3.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 ]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.2"]
            [lein-cljsbuild "1.1.8"]
            [lein-pprint "1.3.2"]]
  :npm {:dependencies [[source-map-support "0.5.19"]
                       [puppeteer "5.2.0"]]}
  :source-paths ["src" "target/classes"]
  :clean-targets [:target-path "bin"]
  :target-path "target"
  :profiles {:dev {:dependencies [[cider/piggieback "0.5.0"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}
             :release {:cljsbuild {:builds {:main {:compiler {:optimizations :simple
                                                              :pretty-print false}}}}}}
  :cljsbuild {:builds {:main {:source-paths ["src"]
                              :compiler {:output-to "bin/main.js"
                                         :closure-defines {"goog.log.ENABLED" true}
                                         :main blog-backup.core
                                         :optimizations :none
                                         :target :nodejs
                                         :pretty-print true}}}}
  :aliases {"dev" ["cljsbuild" "auto" "main"]
            "release" ["with-profile" "release" "do"
                       ["clean"]
                       ["cljsbuild" "once" "main"]]})

(ns blog-backup.logging
  (:require [goog.log :as glog])
  (:require-macros [blog-backup.logging])
  (:import [goog.log Logger LogRecord]
           [goog.i18n DateTimeFormat]
           [goog.date DateTime]
           [goog.debug.Logger Level]))

(defonce ^DateTimeFormat time-format (DateTimeFormat. "yyyy-MM-dd HH:mm:ss"))

(def level-mapping
  {:debug Level/FINE
   :info Level/INFO
   :warn Level/WARNING
   :error Level/SEVERE})

(def level-mapping2
  {Level/FINE "DEBUG"
   Level/INFO "INFO"
   Level/WARNING "WARN"
   Level/SEVERE "ERROR"})


(defn- ^Logger get-logger [name & [level]]
  (let [level (or level :info)]
    (doto (glog/getLogger name (level-mapping level))
      (.addHandler (fn [^LogRecord log-record]
                     (let [time (.getMillis log-record)
                           msg (.getMessage log-record)
                           log-name (.getLoggerName log-record)]
                       (println (.format time-format (DateTime/fromTimestamp time))
                                (level-mapping2 (.getLevel log-record))
                                [(str log-name ":" (.getSequenceNumber log-record))]
                                (or msg ""))
                       (when-let [ex (.getException log-record)]
                         (println (.-stack ex) "\n" (ex-data ex))
                         (loop [cause (ex-cause ex)]
                           (when cause
                             (println "caused by:" (.-stack cause) "\n" (ex-data cause))
                             (recur (ex-cause cause))))
                         )))))))


(defn make-log-record ^LogRecord [ns-name line ^Level level message exception]
  ;; hack
  ;; line = opt_sequenceNumber, loggerName = ns-name
  (let [record (LogRecord. level message ns-name nil line)]
    (when exception
      (.setException record exception))
    record))

(let [root-logger (get-logger "ROOT")]
  (defn log
    ([ns line lvl message]
     (log ns line lvl message nil))
    ([ns line lvl message exception]
     (when glog/ENABLED
       (.logRecord root-logger
                   (make-log-record ns line
                                    (level-mapping lvl) message
                                    exception)))))
  (defn set-level! [level]
    (.setLevel root-logger (level-mapping level))))

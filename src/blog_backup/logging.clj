(ns blog-backup.logging)

(defn do-log [form level msg ex]
  (let [{:keys [line]} (meta form)
        ns (name cljs.analyzer/*cljs-ns*)]
    `(if (instance? js/Error ~msg)
       (blog-backup.logging/log ~ns ~line ~level nil ~msg)
       (blog-backup.logging/log ~ns ~line ~level ~msg ~ex))))

(defmacro debug!
  [msg & [ex]]
  (do-log &form :debug msg ex))

(defmacro info!
  [msg & [ex]]
  (do-log &form :info msg ex))

(defmacro warn!
  [msg & [ex]]
  (do-log &form :warn msg ex))

(defmacro error!
  [msg & [ex]]
  (do-log &form :error msg ex))

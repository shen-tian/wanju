(ns wanju.core
  (:require [cider.nrepl :refer [cider-middleware]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nrepl.core :as nrepl]
            [nrepl.middleware.sideloader :as sideloader]
            [nrepl.misc :refer [uuid]]))

(defn string->content
  [str]
  (-> str
      (.getBytes "UTF-8")
      java.io.ByteArrayInputStream.
      sideloader/base64-encode))

(defn file->content
  [file]
  (if file
    (-> file
        io/input-stream
        sideloader/base64-encode)
    ""))


(defn handle-sideloader
  [client {:keys [id session type name]}]
  (let [content (case type
                  "resource"
                  (if-let [res (io/resource name)]
                    (string->content (slurp res))
                    "")
                  "class"
                  (let [file (io/resource (str (str/replace name "." "/") ".class"))]
                    (file->content file)))]
    (client {:id      id
             :session session
             :op      "sideloader-provide"
             :content content
             :type    type
             :name    name})))

(defn start-sideloader!
  [client-session]
  (let [sideloader-id (uuid)]

    (future (->> (client-session {:id sideloader-id
                                  :op "sideloader-start"})
                 (filter #(= (:id %) sideloader-id))
                 (run! (fn [{:keys [status] :as msg}]
                         (when (contains? (set (map keyword status))
                                          :sideloader-lookup)
                           (handle-sideloader client-session
                                              msg)))))
            :done)))

(def ^:dynamic *port* 9998)

(defn message-in-session
  [session msg]
  (->> (nrepl/message session msg)
       (map #(dissoc % :id :session))
       nrepl/combine-responses))

(defn eval-in-session
  [session code]
  (message-in-session session {:op "eval" :code code}))

(defn message-with-sideloader
  [client msg]
  (let [session (nrepl/client-session client)]
    (start-sideloader! session)
    (message-in-session session msg)))

(defn prep-for-cider []
  (let [extra-ns ["cider.nrepl.middleware.content-type"
                  "cider.nrepl.middleware.slurp"
                  "cider.nrepl.middleware.version"
                  "cider.nrepl.middleware.apropos"
                  "cider.nrepl.middleware.classpath"
                  "cider.nrepl.middleware.complete"
                  "cider.nrepl.middleware.debug"
                  "cider.nrepl.middleware.enlighten"
                  "cider.nrepl.middleware.format"
                  "cider.nrepl.middleware.info"
                  "cider.nrepl.middleware.inspect"
                  "cider.nrepl.middleware.macroexpand"
                  "cider.nrepl.middleware.ns"
                  "cider.nrepl.middleware.out"
                  "cider.nrepl.middleware.profile"
                  "cider.nrepl.middleware.refresh"
                  "cider.nrepl.middleware.resource"
                  "cider.nrepl.middleware.spec"
                  "cider.nrepl.middleware.stacktrace"
                  "cider.nrepl.middleware.test"
                  "cider.nrepl.middleware.trace"
                  "cider.nrepl.middleware.track-state"
                  "cider.nrepl.middleware.undef"
                  "cider.nrepl.middleware.version"
                  "cider.nrepl.middleware.xref"
                  "cider.nrepl.middleware.clojuredocs"

                  "refactor-nrepl.ns.resolve-missing"
                  "refactor-nrepl.find.find-symbol"
                  "refactor-nrepl.artifacts"
                  "refactor-nrepl.ns.clean-ns"
                  "refactor-nrepl.ns.pprint"
                  "refactor-nrepl.find.find-locals"
                  "refactor-nrepl.analyzer"
                  "refactor-nrepl.find.find-macros"
                  "refactor-nrepl.extract-definition"
                  "refactor-nrepl.rename-file-or-dir"
                  "refactor-nrepl.find.find-used-publics"]]
    (with-open [transport (nrepl/connect :port *port*)]
      (let [client  (nrepl/client transport 5000)
            session (nrepl/client-session client)]
        (start-sideloader! session)
        (Thread/sleep 100)

        (prn (message-in-session session {:op               "add-middleware"
                                          :middleware       (vec (conj (mapv str cider-middleware)
                                                                       "refactor-nrepl.middleware/wrap-refactor"))
                                          :extra-namespaces extra-ns}))
        (prn (message-in-session session {:op "cider-version"}))))))


(defn y []
  (with-open [transport (nrepl/connect :port *port*)]
    (let [client  (nrepl/client transport 5000)
          session (nrepl/client-session client)]
      (start-sideloader! session)
      (Thread/sleep 100)

      (prn (eval-in-session session "(require 'cider.nrepl.middleware.version)")))))

(comment
  (with-open [transport (nrepl/connect :port *port*)]
    (let [client  (nrepl/client transport 1000)
          session (nrepl/client-session client)]
      (->> (message-with-sideloader session {:op         "add-middleware"
                                             :middleware ["wanju.ping/wrap-ping"]})
           (map #(dissoc % :id :session))
           doall)))

  (with-open [transport (nrepl/connect :port *port*)]
    (let [client (nrepl/client transport 1000)
          resp   (->> (nrepl/message client {:op "ls-middleware"})
                      (map #(dissoc % :id :session))
                      nrepl/combine-responses)]
      (-> resp
          :middleware)))

  (with-open [transport (nrepl/connect :port *port*)]
    (let [client (nrepl/client transport 1000)]
      (->> (nrepl/message client {:op "ping"})
           (map #(dissoc % :id :session))
           doall))))

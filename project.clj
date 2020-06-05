(defproject wanju "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [nrepl "0.7.0"]
                 [cider/cider-nrepl "0.24.0"]
                 [cider/piggieback "0.4.2"]
                 [refactor-nrepl "2.5.0"]]
  :repl-options {:init-ns wanju.core})

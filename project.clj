(defproject tard-server "0.1.0-SNAPSHOT"
 :description "A dashboard that shows how happy the world is."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"] ; May use any v1.5.1+
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.taoensso/sente "1.2.0"]
                 [com.taoensso/timbre "3.3.1"]
                 [http-kit "2.1.19"]
                 [compojure "1.3.1"] ; Or routing lib of your choice
                 [ring "1.3.2"]
                 [ring/ring-defaults "0.1.2"]
                 [com.cognitect/transit-clj "0.8.259"]]
  
  :main tard-server.core)

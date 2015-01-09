(ns tard-server.core
  (:require
    [clojure.string :as str]
    [compojure.core :as comp :refer (defroutes GET POST)]
    [compojure.route :as route]
    [compojure.handler :as handler]
    [ring.middleware.defaults]
    [ring.middleware.cors :refer [wrap-cors]]
    [hiccup.core :as hiccup]
    [org.httpkit.server :as http-kit-server]
    [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
    [taoensso.timbre :as timbre]
    [taoensso.sente :as sente]
    [taoensso.sente.packers.transit :as sente-transit]))

(defn- logf [fmt & xs] (println (apply format fmt xs)))

(def packer
  (sente-transit/get-flexi-packer :edn))

(let [{:keys [ch-recv send-fn ajax-post-fn 
              ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! {:packer packer})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(defn login! [ring-request]
  (let [{:keys [session params]} ring-request
        {:keys [username password]} params]
    (logf "Login request: %s" params)
    ;; todo: Check username/password against database
    {:status 200 :session (assoc session :uid username)}))

(defroutes routes
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (POST "/login" req (login! req)))

(def cors-routes (wrap-cors routes :access-control-allow-origin #".*"
                                         :access-control-allow-methods [:get :put :post :delete]))

(defmulti event-msg-handler :id)

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (logf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defmethod event-msg-handler :chsk/recv [{:as ev-msg :keys [?data]}]
  (logf "Push event from server: %s" ?data))

(defmethod event-msg-handler :tard-web.core/button2 [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (println "button2")
  (when ?reply-fn
    (?reply-fn {:you-clicked "button2"})))

(defonce http-server_ (atom nil))

(defn stop-http-server! []
  (when-let [stop-f @http-server_]
    (stop-f :timeout 100)))

(defn start-http-server! []
  (stop-http-server!)
  (let [s (http-kit-server/run-server (handler/site cors-routes) {:port 8080})
        uri (format "http://localhost:%s/" (:local-port (meta s)))]
    (reset! http-server_ s)
    (logf "Http-kit server is running at `%s`" uri)))

(defonce router_ (atom nil))

(defn stop-router! [] 
  (when-let [stop-f @router_] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

(defn -main []
  (start-router!)
  (start-http-server!))
  
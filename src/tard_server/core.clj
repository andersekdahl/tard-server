(ns tard-server.core
  (:require
    [clojure.string :as str]
    [compojure.core :as comp :refer (defroutes GET POST)]
    [compojure.route :as route]
    [ring.middleware.defaults]
    [hiccup.core :as hiccup]
    [org.httpkit.server :as http-kit-server]
    [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
    [taoensso.timbre :as timbre]
    [taoensso.sente :as sente]
    [ring.middleware.anti-forgery :as ring-anti-forgery]
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

(defroutes routes
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req)))

(def ring-handler
  (let [ring-defaults-config
        (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
          {:read-token (fn [req] (-> req :params :csrf-token))})]
    (ring.middleware.defaults/wrap-defaults routes ring-defaults-config)))

(defmulti event-msg-handler :id)

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (logf "Event: %s" event)
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (logf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defonce http-server_ (atom nil))

(defn stop-http-server! []
  (when-let [stop-f @http-server_]
    (stop-f :timeout 100)))

(defn start-http-server! []
  (stop-http-server!)
  (let [s (http-kit-server/run-server (var ring-handler) {:port 0})
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
  
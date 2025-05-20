(ns main
  (:require [clojure.data.json :as json]
            [io.pedestal.connector :as conn]
            [io.pedestal.http.http-kit :as hk]
            [io.pedestal.http.content-negotiation :as content-negotiation])
  (:gen-class))

(defn ok [body]
  {:status 200 :body body})

(defn not-found []
  {:status 404 :body "Not found\n"})

(defn greeting-for [greet-name]
  (cond
    (empty? greet-name) "Hello, world!\n"
    :else (str "Hello, " greet-name "\n")))

(defn greet-handler [request]
  (let [greet-name (get-in request [:query-params :name])
        message    (greeting-for greet-name)]
    (if message
      (ok message)
      (not-found))))

(def supported-types ["text/html"
                      "application/edn"
                      "application/json"
                      "text/plain"])

(def content-negotiation-interceptor
  (content-negotiation/negotiate-content supported-types))

(defn accepted-type
  [context]
  (get-in context [:request :accept :field] "text/plain"))

(defn transform-content
  [body content-type]
  (case content-type
    "text/html" body
    "text/plain" body
    "application/edn" (pr-str body)
    "application/json" (json/write-str body)))

(defn coerce-to
  [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(defn missing-response-content-type?
  [context]
  (nil? (get-in context [:response :headers "Content-Type"])))

(def coerce-body-interceptor
  {:name ::coerce-body
   :leave
   (fn [context]
     (cond-> context
       (missing-response-content-type? context)
       (update :response coerce-to (accepted-type context))))})

(def routes
  #{["/" :get [coerce-body-interceptor
               content-negotiation-interceptor
               greet-handler]
     :route-name :index]})

(defn create-connector []
  (-> (conn/default-connector-map "0.0.0.0" 8080)
      (conn/with-default-interceptors)
      (conn/with-routes routes)
      (hk/create-connector nil)))

(defn -main [& _args]
  (conn/start! (create-connector)))

;; For interactive development
(defonce *connector (atom nil))

(defn start []
  (reset! *connector
          (conn/start! (create-connector))))

(defn stop []
  (conn/stop! @*connector)
  (reset! *connector nil))

(defn restart []
  (stop)
  (start))

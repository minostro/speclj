(ns speclj.stub
  (:require ;cljs-macros
            [speclj.platform :refer [throw-error]])
  (:require [speclj.platform :refer [endl]]))

(declare ^:dynamic *stubbed-invocations*)

(defn- check-recording []
  ;cljs-ignore->
  (when-not (bound? #'*stubbed-invocations*)
    ;<-cljs-ignore
    ;cljs-include (when-not *stubbed-invocations*
    (throw-error "Stub recoding not bound.  Please add (with-stubs) to the decribe/context.")))

(defn -record-invocation [name args]
  (check-recording)
  (let [args (if (= nil args) [] (vec args))]
    (swap! *stubbed-invocations* conj [name args])))

(defn- invoke-delegate [name delegate args]
  (try
    (apply delegate args)
    ;cljs-ignore->
    (catch clojure.lang.ArityException e
      (throw-error (str "Stub " name " was invoked with " (.-actual e) " arguments, but the :invoke fn has a different arity")))
    ;<-cljs-ignore
    ))

(defn stub
  ([name] (stub name {}))
  ([name options]
    (let [delegate (:invoke options)]
      (when (and delegate (not (ifn? delegate)))
        (throw-error "stub's :invoke argument must be an ifn"))
      (fn [& args]
        (-record-invocation name args)
        (let [result (if delegate (invoke-delegate name delegate args) nil)]
          (when-let [throwable (:throw options)]
            (throw throwable))
          (if (contains? options :return)
            (:return options)
            result))))))

(defn invocations-of
  "Returns a list of argument lists representing each invocation of the specified stub."
  [name]
  (map second
    (filter #(= name (first %))
      @*stubbed-invocations*)))

(defn first-invocation-of
  "Returns the list of arguments passed into the first invocation of the specified stub, nil if it was never invoked."
  [name]
  (first (invocations-of name)))

(defn last-invocation-of
  "Returns the list of arguments passed into the last invocation of the specified stub, nil if it was never invoked."
  [name]
  (last (invocations-of name)))

(defn params-match? [expected actual]
  (and
    (sequential? expected)
    (sequential? actual)
    (= (count expected) (count actual))
    (every? true?
      (map
        (fn [e a]
          (cond
            (= :* e) true
            (fn? e) (or (= e a) (e a))
            :else (= e a)))
        expected actual))))


(ns frontend.components.select
  "Generic component for fuzzy searching items to select an item. See
  select-config to add a new use or select-type for this component. To use the
  new select-type, set :ui/open-select to the select-type. See
  :select-graph/open command for an example."
  (:require [frontend.modules.shortcut.core :as shortcut]
            [frontend.context.i18n :as i18n]
            [frontend.search :as search]
            [frontend.state :as state]
            [frontend.ui :as ui]
            [frontend.util :as util]
            [frontend.db :as db]
            [frontend.text :as text]
            [rum.core :as rum]
            [frontend.config :as config]
            [reitit.frontend.easy :as rfe]))

(rum/defc render-item
  [{:keys [id value]} chosen?]
  [:div.inline-grid.grid-cols-4.gap-x-4.w-full
   {:class (when chosen? "chosen")}
   [:span.col-span-3 value]
   [:div.col-span-1.justify-end.tip.flex
    (when id
      [:code.opacity-20.bg-transparent id])]])

(rum/defcs select <
  (shortcut/disable-all-shortcuts)
  (rum/local "" ::input)
  {:will-unmount (fn [state]
                   (state/set-state! [:ui/open-select] nil)
                   state)}
  [state {:keys [items limit on-chosen empty-placeholder prompt-key]
          :or {limit 100
               prompt-key :select/default-prompt}}]
  (rum/with-context [[t] i18n/*tongue-context*]
    (let [input (::input state)]
      [:div.cp__select.cp__select-main
       [:div.input-wrap
        [:input.cp__select-input.w-full
         {:type        "text"
          :placeholder (t prompt-key)
          :auto-focus  true
          :value       @input
          :on-change   (fn [e] (reset! input (util/evalue e)))}]]

       [:div.item-results-wrap
        (ui/auto-complete
         (search/fuzzy-search items @input :limit limit :extract-fn :value)
         {:item-render render-item
          :class       "cp__select-results"
          :on-chosen   (fn [x]
                         (state/close-modal!)
                         (on-chosen x))
          :empty-placeholder (empty-placeholder t)})]])))

(defn select-config
  "Config that supports multiple types (uses) of this component. To add a new
  type, add a key with the value being a map with the following keys:

  * :items-fn - fn that returns items with a :value key that are used for the
    fuzzy search and selection. Items can have an optional :id and are displayed
    lightly for a given item.
  * :on-chosen - fn that is given item when it is chosen.
  * :empty-placeholder - fn that returns hiccup html to render if no matched graphs found.
  * :prompt-key - dictionary keyword that prompts when components is first open.
    Defaults to :select/default-prompt."
  []
  {:select-graph
   {:items-fn (fn []
                (->>
                 (state/get-repos)
                 (remove (fn [{:keys [url]}]
                           (or (config/demo-graph? url)
                               (= url (state/get-current-repo)))))
                 (map (fn [{:keys [url]}]
                        {:value (text/get-graph-name-from-path
                                 ;; TODO: Use helper when a common one is refactored
                                 ;; from components.repo
                                 (if (config/local-db? url)
                                   (config/get-local-dir url)
                                   (db/get-repo-path url)))
                         :id (config/get-repo-dir url)
                         :graph url}))))
    :prompt-key :select.graph/prompt
    :on-chosen #(state/pub-event! [:graph/switch (:graph %)])
    :empty-placeholder (fn [t]
                         [:div.px-4.py-2
                          [:div.mb-2 (t :select.graph/empty-placeholder-description)]
                          (ui/button
                           (t :select.graph/add-graph)
                           :href (rfe/href :repo-add)
                           :on-click state/close-modal!)])}})

(rum/defc select-modal < rum/reactive
  []
  (when-let [select-type (state/sub [:ui/open-select])]
    (let [select-type-config (get (select-config) select-type)]
      (state/set-modal!
       #(select (-> select-type-config
                    (select-keys [:on-chosen :empty-placeholder :prompt-key])
                    (assoc :items ((:items-fn select-type-config)))))
       {:fullscreen? false
        :close-btn?  false}))
    nil))

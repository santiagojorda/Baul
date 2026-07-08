package com.santiagojorda.baul.ui.navigation

object BaulDestinations {
    const val RULE_LIST = "rule_list"
    const val HISTORY = "history"
    const val ACCOUNTS = "accounts"
    const val EXCLUDED_FOLDERS = "excluded_folders"

    const val RULE_EDITOR_ARG_RULE_ID = "ruleId"
    const val RULE_EDITOR_ROUTE = "rule_editor?$RULE_EDITOR_ARG_RULE_ID={$RULE_EDITOR_ARG_RULE_ID}"

    /** [ruleId] null significa "crear regla nueva". */
    fun ruleEditorRoute(ruleId: Long? = null) = "rule_editor?$RULE_EDITOR_ARG_RULE_ID=${ruleId ?: -1L}"
}

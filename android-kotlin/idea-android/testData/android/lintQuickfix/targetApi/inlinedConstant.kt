// INTENTION_TEXT: Add @TargetApi(KITKAT) Annotation
// INSPECTION_CLASS: com.android.tools.idea.lint.inspections.AndroidLintInlinedApiInspection

class Test {
    fun foo(): Int {
        return android.R.attr.<caret>windowTranslucentStatus
    }
}
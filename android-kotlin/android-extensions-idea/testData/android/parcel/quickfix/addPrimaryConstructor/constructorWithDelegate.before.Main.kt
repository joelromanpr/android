// "Add empty primary constructor" "true"
// ERROR: 'Parcelable' should have a primary constructor
// WITH_STDLIB

package com.myapp.activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class <caret>Test : Parcelable {
    constructor(s: String)
    constructor(s: String, i: Int) : this(s)
}
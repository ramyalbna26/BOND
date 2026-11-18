package com.sanad.bond.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import kotlin.reflect.KClass

/**
 * A single entry in the Settings home screen.
 *
 * @param id            stable identifier (used as fragment tag & backstack key)
 * @param titleRes      string resource for the row title
 * @param iconRes       drawable resource for the row icon
 * @param fragmentClass the fragment that opens when this row is tapped
 * @param enabled       whether the row is currently tappable
 */
data class SettingsSection(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val fragmentClass: KClass<out Fragment>,
    val enabled: Boolean = true
)
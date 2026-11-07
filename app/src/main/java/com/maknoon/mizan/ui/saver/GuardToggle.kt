package com.maknoon.mizan.ui.saver

/**
 * What pressing the Guard switch must do, given the two preconditions it depends on.
 *
 * The switch used to be *disabled* whenever a precondition was missing, and a disabled Compose
 * switch swallows the press without a word: on a fresh install the primary action of the screen
 * looked broken, because nothing had been selected yet. Pressing it now always resolves to one
 * of these, so the operator gets a visible consequence for every tap.
 */
internal enum class GuardToggleAction {
    /** Turn the tunnel off. */
    STOP,

    /** Both preconditions hold: turn the tunnel on. */
    START,

    /** Nothing is blocked yet, so there is no tunnel to build: open the app picker. */
    CHOOSE_APPS,

    /** Apps are chosen but the system VPN consent is missing: ask for it. */
    REQUEST_CONSENT,
}

/**
 * Resolves a press on the switch into an action.
 *
 * [enabled] is the state the operator asked for, not the current one. The app picker is offered
 * before the consent dialog on purpose: choosing apps is local and cheap, while the system
 * dialog is the one step the operator may refuse, so it is asked for only when it is about to be
 * used.
 */
internal fun guardToggleAction(
    enabled: Boolean,
    blockedCount: Int,
    consentGranted: Boolean,
): GuardToggleAction = when {
    !enabled -> GuardToggleAction.STOP
    blockedCount <= 0 -> GuardToggleAction.CHOOSE_APPS
    consentGranted -> GuardToggleAction.START
    else -> GuardToggleAction.REQUEST_CONSENT
}

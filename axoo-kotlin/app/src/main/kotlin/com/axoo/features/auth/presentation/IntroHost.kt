package com.axoo.features.auth.presentation

import android.content.Context
import android.widget.FrameLayout
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route

internal class IntroHost(
    context: Context,
    private val onNavigateToLogin: () -> Unit,
) : FrameLayout(context), NativeScreen {
    override val route: Route = Route.Intro

    private val view = IntroView(context) { onNavigateToLogin() }

    init {
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}

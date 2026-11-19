package com.axoo.features.auth.presentation

import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.auth.data.dto.SessionBootstrapResponse

internal class LoginHost(
    context: Context,
    viewModel: LoginViewModel,
    lifecycleOwner: LifecycleOwner,
    private val onLoginSuccess: (SessionBootstrapResponse) -> Unit,
) : FrameLayout(context), NativeScreen, BackHandler {
    override val route: Route = Route.Login

    private val view = LoginView(
        context = context,
        viewModel = viewModel,
        lifecycleOwner = lifecycleOwner,
        onLoginSuccess = onLoginSuccess,
    )

    init {
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    override fun onBackPressed(): Boolean = view.onBackPressed()
}

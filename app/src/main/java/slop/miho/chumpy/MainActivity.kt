package slop.miho.chumpy

import android.Manifest
import android.content.Intent
import android.graphics.Rect as ScreenRect
import android.os.Bundle
import slop.miho.chumpy.ui.components.ControllerActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import slop.miho.chumpy.sub.SubscriptionLink
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import slop.miho.chumpy.ui.MainViewModel
import slop.miho.chumpy.ui.components.SystemBarsIconsDark
import slop.miho.chumpy.ui.main.MainShell
import slop.miho.chumpy.ui.theme.ChumpyTheme
import slop.miho.chumpy.ui.theme.LocalDarkTheme
import slop.miho.chumpy.ui.welcome.WelcomeScreen
import kotlin.math.hypot

class MainActivity : ControllerActivity() {
    private var pendingSubscription by mutableStateOf<String?>(null)
    private var importingSubscription by mutableStateOf(false)
    private var subscriptionImportError by mutableStateOf<String?>(null)

    companion object {
        /** The update notification's tap: hand the downloaded APK to the installer. */
        const val EXTRA_INSTALL_UPDATE = "install_update"

        /** The orb widget's tap: the app reveals itself through the circle — [OrbMorph]. */
        const val EXTRA_FROM_ORB = "orb_widget"
    }

    /** True while the orb widget's opening morph is still running; re-fired per tap. */
    private val orbOpening = mutableStateOf(false)

    /** The tapped widget's own bounds, if the launcher handed them over. */
    private var orbSource by mutableStateOf<ScreenRect?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        orbOpening.value = intent?.getBooleanExtra(EXTRA_FROM_ORB, false) == true
        orbSource = intent?.getSourceBounds()
        pendingSubscription = savedInstanceState?.getString("pending_subscription")
        if (savedInstanceState == null) receiveSubscription(intent)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            // The notifications permission rides the first config's landing — asked once,
            // ever. The ask belongs to the activity, not the shell: a fresh install adds
            // its first config from the welcome screen, where the shell is not yet alive.
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            val notificationsAsk by viewModel.notificationsAsk.collectAsStateWithLifecycle()
            LaunchedEffect(notificationsAsk) {
                if (notificationsAsk) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.consumeNotificationsAsk()
                }
            }
            ChumpyTheme(settings = settings) {
                pendingSubscription?.let { url ->
                    AlertDialog(
                        onDismissRequest = { if (!importingSubscription) pendingSubscription = null },
                        title = { Text(stringResource(R.string.subscription_link_title)) },
                        text = {
                            Text(subscriptionImportError ?: if (importingSubscription) {
                                getString(R.string.phone_import_importing)
                            } else url)
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !importingSubscription,
                                onClick = {
                                    importingSubscription = true
                                    subscriptionImportError = null
                                    withLocalNetworkPermission {
                                        viewModel.addSubscription(url) { error ->
                                            importingSubscription = false
                                            subscriptionImportError = error
                                            if (error == null) pendingSubscription = null
                                        }
                                    }
                                },
                            ) { Text(stringResource(R.string.phone_import_confirm)) }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !importingSubscription,
                                onClick = { pendingSubscription = null },
                            ) { Text(stringResource(R.string.subscription_cancel)) }
                        },
                    )
                }

                Surface(color = MaterialTheme.colorScheme.surface) {
                    val motion = MaterialTheme.motionScheme
                    // setupDone stays null while the database answers for the first time:
                    // the app holds on the theme until it knows which screen to show.
                    val setupDone by viewModel.setupDone.collectAsStateWithLifecycle()
                    // The orb widget's opening morph, when the tap came from it: the
                    // shell reveals itself through a circle growing out of the middle.
                    val opening by orbOpening
                    val source = orbSource
                    OrbMorph(
                        active = opening,
                        source = source,
                        onDone = { orbOpening.value = false },
                    ) {
                        AnimatedContent(
                            targetState = setupDone,
                            transitionSpec = {
                                if (initialState == null) {
                                    // The app coming open: the hold's empty slot is zero-sized, and
                                    // growing it out with the clip would unfold the window out of
                                    // a corner. The system's own window fade has the launch
                                    // covered — the shell lands whole, at once.
                                    ContentTransform(
                                        targetContentEnter = fadeIn(snap()),
                                        initialContentExit = fadeOut(snap()),
                                        sizeTransform = SizeTransform(sizeAnimationSpec = { _, _ -> snap() }),
                                    )
                                } else {
                                    // Onboarding answered and the shell takes over: the slow
                                    // fade with the settle-down from a touch shy of full.
                                    (fadeIn(motion.slowEffectsSpec()) +
                                        scaleIn(motion.slowSpatialSpec(), initialScale = 0.92f))
                                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                                }
                            },
                            label = "onboarding",
                        ) { onboarded ->
                            if (onboarded == true) {
                                MainShell(viewModel)
                            } else if (onboarded == false) {
                                // No wallpaper here — the bars answer to the theme alone. The
                                // main shell takes over the window the moment it composes.
                                SystemBarsIconsDark(dark = LocalDarkTheme.current)
                                WelcomeScreen(
                                    onAddSubscription = { url, name, done ->
                                        withLocalNetworkPermission { viewModel.addSubscription(url, name, done) }
                                    },
                                    onImport = viewModel::addManualProfiles,
                                )
                            }

                        }
                    }
                }
            }
        }
        // The update notification's tap on a cold start: the request rides the launching
        // intent, and the view model reads the downloaded file from its store.
        handleInstallRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A warm open from the widget fires the morph again — the hand-off belongs to
        // the tap, not to the activity's birth.
        orbOpening.value = intent.getBooleanExtra(EXTRA_FROM_ORB, false)
        orbSource = intent.getSourceBounds()
        setIntent(intent)
        if (!importingSubscription) receiveSubscription(intent)
        handleInstallRequest(intent)
    }

    /** The update notification's tap: re-held by the live instance when one exists. */
    private fun handleInstallRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_INSTALL_UPDATE, false) != true) return
        ViewModelProvider(this)[MainViewModel::class.java].installUpdate()
    }

    override fun onStart() {
        super.onStart()
        // The same instance the compose tree holds: the stats loops sample once a second
        // while this is true.
        ViewModelProvider(this)[MainViewModel::class.java].setAppInForeground(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!importingSubscription) outState.putString("pending_subscription", pendingSubscription)
        super.onSaveInstanceState(outState)
    }

    private fun receiveSubscription(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        pendingSubscription = intent.dataString?.let(SubscriptionLink::parse)
        subscriptionImportError = null
        intent.data = null
    }

    override fun onStop() {
        super.onStop()
        // The activity leaving the screen is the app going background: the loops take
        // their slow stride and the history keeps trickling in behind the scenes.
        ViewModelProvider(this)[MainViewModel::class.java].setAppInForeground(false)
    }
}

/**
 * The orb widget's opening morph: the app reveals itself through the widget's own
 * circle — the main card, not the widget's whole rectangular box — growing out of
 * the place on the screen the launcher says the widget sits. While it runs, the
 * circle's rim glides outward and everything still outside it is the theme's plain
 * background; the shell shows through only where the circle has already passed.
 */
@Composable
private fun OrbMorph(
    active: Boolean,
    source: ScreenRect?,
    onDone: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!active) {
        content()
        return
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // The widget's circle settles whole, at its own stride.
        progress.animateTo(1f, tween(550, easing = CubicBezierEasing(0.22f, 0.8f, 0.2f, 1f)))
        onDone()
    }
    val view = LocalView.current
    val screenOffset = remember { IntArray(2) }
    val surround = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                view.getLocationOnScreen(screenOffset)
                // The widget's own circle: centered in the source bounds, sized to
                // their short side — the main card, never the whole widget box.
                val originX = source?.let { it.exactCenterX() - screenOffset[0] }
                    ?: size.width / 2f
                val originY = source?.let { it.exactCenterY() - screenOffset[1] }
                    ?: size.height / 2f
                val startRadius = source?.let { minOf(it.width(), it.height()) / 2f } ?: 0f
                // The radius that finally covers the screen's farthest corner from
                // that origin, a hair past it so the clip ends clean.
                val cover = 1.02f * maxOf(
                    hypot(originX, originY),
                    hypot(size.width - originX, originY),
                    hypot(originX, size.height - originY),
                    hypot(size.width - originX, size.height - originY),
                )
                val radius = startRadius + progress.value * (cover - startRadius)
                val circle = Path().apply {
                    addOval(
                        Rect(originX - radius, originY - radius, originX + radius, originY + radius),
                    )
                }
                // The shell shows through only where the circle has already passed;
                // everything still outside it is the theme's plain background.
                clipPath(circle) { this@drawWithContent.drawContent() }
                clipPath(circle, clipOp = ClipOp.Difference) { drawRect(surround) }
            },
    ) {
        content()
    }
}

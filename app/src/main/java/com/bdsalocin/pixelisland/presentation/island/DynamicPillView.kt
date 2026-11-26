package com.bdsalocin.pixelisland.presentation.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdsalocin.pixelisland.core.util.HapticFeedbackHelper
import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import com.bdsalocin.pixelisland.presentation.island.components.ApplePlayPauseIcon
import com.bdsalocin.pixelisland.presentation.island.components.AppleSkipNextIcon
import com.bdsalocin.pixelisland.presentation.island.components.AppleSkipPreviousIcon
import com.bdsalocin.pixelisland.presentation.island.components.EqualizerBars
import com.bdsalocin.pixelisland.presentation.island.components.FaceUnlockScanner
import com.bdsalocin.pixelisland.presentation.theme.rememberDeviceAccentColor
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

enum class IslandStage {
    FACE_UNLOCK,
    EXPANDED_CARD,
    COMPACT_MUSIC,
    HIDDEN
}

@Composable
fun DynamicPillView(
    viewModel: IslandViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val deviceAccentColor = rememberDeviceAccentColor(themeRefreshVersion = state.themeVersion)

    // Vérifie si une action requiert l'affichage de l'îlot
    val isVisible = state.isMusicActive || state.isFaceUnlockActive || state.isExpanded

    // Bornes supérieure et inférieure de l'îlot.
    // Au repos, la pilule permanente style iPhone 18 (90dp x 36dp, centrée sur y=20dp) reste visible autour du poinçon.
    val targetTop = when {
        state.isFaceUnlockActive -> 0.dp
        state.isExpanded -> 0.dp
        state.isMusicActive -> 0.dp
        else -> 2.dp
    }

    val targetBottom = when {
        state.isFaceUnlockActive -> 96.dp  // Format carré arrondi type iPhone (96dp)
        state.isExpanded -> 238.dp        // Carte agrandie généreuse pour loger la grande pochette et le scrubbing
        state.isMusicActive -> 40.dp      // Hauteur compacte 40dp centrée sur le poinçon
        else -> 38.dp                     // Hauteur permanente 36dp (de 2dp à 38dp, centrée à 20dp) style iPhone 18
    }

    val targetWidth = when {
        state.isFaceUnlockActive -> 96.dp  // Format carré 96dp sur 96dp
        state.isExpanded -> 384.dp         // Carte étendue allant presque sur les côtés de l'écran (~14dp de marge latérale)
        state.isMusicActive -> 180.dp     // 180dp pour la pilule musique compacte
        else -> 90.dp                     // Pilule permanente compacte et affinée 90dp style iPhone 18
    }

    // Détermination de l'état d'affichage pour la transition douce
    val currentStage = when {
        state.isFaceUnlockActive -> IslandStage.FACE_UNLOCK
        state.isExpanded -> IslandStage.EXPANDED_CARD
        state.isMusicActive && state.mediaTrack != null -> IslandStage.COMPACT_MUSIC
        else -> IslandStage.HIDDEN
    }

    val targetCornerRadius = when {
        state.isFaceUnlockActive -> 28.dp
        state.isExpanded -> 40.dp
        state.isMusicActive -> 20.dp
        else -> 18.dp
    }

    // Ressorts organiques calibrés style iOS Dynamic Island à 120 FPS :
    // Largeur légèrement en avance sur la hauteur à l'ouverture pour un déploiement liquide et naturel
    val musicCompactWidthSpring = remember {
        spring<Float>(dampingRatio = 0.78f, stiffness = 340f)
    }
    val musicCompactVerticalSpring = remember {
        spring<Float>(dampingRatio = 0.82f, stiffness = 360f)
    }
    val musicExpandWidthSpring = remember {
        spring<Float>(dampingRatio = 0.80f, stiffness = 380f)
    }
    val musicExpandHeightSpring = remember {
        spring<Float>(dampingRatio = 0.80f, stiffness = 310f)
    }
    val musicExpandCornerSpring = remember {
        spring<Float>(dampingRatio = 0.82f, stiffness = 320f)
    }
    val musicCollapseSpring = remember {
        spring<Float>(dampingRatio = 0.92f, stiffness = 460f)
    }
    val musicHideSpring = remember {
        spring<Float>(dampingRatio = 0.88f, stiffness = 380f)
    }
    val faceOpenSpringSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)
    }

    val pillTopAnim = remember { Animatable(targetTop.value) }
    val pillBottomAnim = remember { Animatable(targetBottom.value) }
    val pillWidthAnim = remember { Animatable(targetWidth.value) }
    val pillCornerAnim = remember { Animatable(targetCornerRadius.value) }
    val bounceScaleX = remember { Animatable(1f) }
    val bounceScaleY = remember { Animatable(1f) }
    var lastHandledPulse by remember { mutableIntStateOf(state.bouncePulseCount) }

    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    var isDraggingCard by remember { mutableStateOf(false) }
    var wasExpanded by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }

    // Remise à zéro immédiate à l'extinction de l'écran pour éviter qu'une animation interrompue
    // par la coupure VSYNC ne laisse une boîte noire figée qui sursaute au prochain appui sur Power.
    DisposableEffect(context) {
        val screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    viewModel.onScreenOff()
                    val current = viewModel.uiState.value
                    coroutineScope.launch {
                        if (current.isMusicActive) {
                            pillTopAnim.snapTo(0f)
                            pillBottomAnim.snapTo(40f)
                            pillWidthAnim.snapTo(180f)
                            pillCornerAnim.snapTo(20f)
                        } else {
                            pillTopAnim.snapTo(2f)
                            pillBottomAnim.snapTo(38f)
                            pillWidthAnim.snapTo(90f)
                            pillCornerAnim.snapTo(18f)
                        }
                        bounceScaleX.snapTo(1f)
                        bounceScaleY.snapTo(1f)
                        wasExpanded = false
                        isDraggingCard = false
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenOffReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(screenOffReceiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(screenOffReceiver)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(targetTop.value, targetBottom.value, targetWidth.value, targetCornerRadius.value) {
        if (!isDraggingCard) {
            val isInteractive = powerManager?.isInteractive != false
            if (!isInteractive) {
                pillTopAnim.snapTo(targetTop.value)
                pillBottomAnim.snapTo(targetBottom.value)
                pillWidthAnim.snapTo(targetWidth.value)
                pillCornerAnim.snapTo(targetCornerRadius.value)
                wasExpanded = false
                return@LaunchedEffect
            }
            val isOpeningExpandedCard = state.isExpanded && targetWidth.value >= 380f
            val isCollapsingCard = (wasExpanded || pillWidthAnim.value > 180.5f) &&
                    targetWidth.value == 180f && targetBottom.value == 40f
            val isOpeningCompactMusic = !state.isExpanded && !state.isFaceUnlockActive &&
                    state.isMusicActive && pillWidthAnim.value < 179.5f

            val topSpec = when {
                state.isFaceUnlockActive -> faceOpenSpringSpec
                isOpeningExpandedCard -> musicExpandHeightSpring
                isCollapsingCard -> musicCollapseSpring
                isOpeningCompactMusic -> musicCompactVerticalSpring
                else -> musicHideSpring
            }
            val bottomSpec = when {
                state.isFaceUnlockActive -> faceOpenSpringSpec
                isOpeningExpandedCard -> musicExpandHeightSpring
                isCollapsingCard -> musicCollapseSpring
                isOpeningCompactMusic -> musicCompactVerticalSpring
                else -> musicHideSpring
            }
            val widthSpec = when {
                state.isFaceUnlockActive -> faceOpenSpringSpec
                isOpeningExpandedCard -> musicExpandWidthSpring
                isCollapsingCard -> musicCollapseSpring
                isOpeningCompactMusic -> musicCompactWidthSpring
                else -> musicHideSpring
            }
            val cornerSpec = when {
                state.isFaceUnlockActive -> faceOpenSpringSpec
                isOpeningExpandedCard -> musicExpandCornerSpring
                isCollapsingCard -> musicCollapseSpring
                isOpeningCompactMusic -> musicCompactVerticalSpring
                else -> musicHideSpring
            }

            launch { pillTopAnim.animateTo(targetTop.value, topSpec) }
            launch { pillBottomAnim.animateTo(targetBottom.value, bottomSpec) }
            launch { pillWidthAnim.animateTo(targetWidth.value, widthSpec) }
            launch { pillCornerAnim.animateTo(targetCornerRadius.value, cornerSpec) }
            wasExpanded = state.isExpanded
        }
    }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            wasExpanded = true
        } else if (isDraggingCard) {
            isDraggingCard = false
            val isCollapsingCard = (wasExpanded || pillWidthAnim.value > 180.5f) &&
                    targetWidth.value == 180f && targetBottom.value == 40f
            val spec = if (isCollapsingCard) musicCollapseSpring else musicHideSpring
            launch { pillTopAnim.animateTo(targetTop.value, spec) }
            launch { pillBottomAnim.animateTo(targetBottom.value, spec) }
            launch { pillWidthAnim.animateTo(targetWidth.value, spec) }
            launch { pillCornerAnim.animateTo(targetCornerRadius.value, spec) }
            wasExpanded = false
        }
    }

    LaunchedEffect(state.isPillPressed, state.bouncePulseCount, state.isExpanded, state.isFaceUnlockActive) {
        if (state.isExpanded || state.isFaceUnlockActive) {
            lastHandledPulse = state.bouncePulseCount
            launch { bounceScaleX.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 500f)) }
            launch { bounceScaleY.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 500f)) }
            return@LaunchedEffect
        }
        if (state.bouncePulseCount != lastHandledPulse) {
            lastHandledPulse = state.bouncePulseCount
            // Effet iPhone Dynamic Island : léger gonflement organique puis retour élastique soyeux
            val puffX = launch { bounceScaleX.animateTo(1.14f, tween(80, easing = FastOutSlowInEasing)) }
            val puffY = launch { bounceScaleY.animateTo(1.10f, tween(80, easing = FastOutSlowInEasing)) }
            joinAll(puffX, puffY)
            launch { bounceScaleX.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f)) }
            launch { bounceScaleY.animateTo(1f, spring(dampingRatio = 0.46f, stiffness = 420f)) }
        } else if (state.isPillPressed) {
            // Micro gonflement tactile immédiat sous le doigt
            launch { bounceScaleX.animateTo(1.06f, spring(dampingRatio = 0.72f, stiffness = 560f)) }
            launch { bounceScaleY.animateTo(1.05f, spring(dampingRatio = 0.72f, stiffness = 560f)) }
        } else {
            launch { bounceScaleX.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 440f)) }
            launch { bounceScaleY.animateTo(1f, spring(dampingRatio = 0.50f, stiffness = 440f)) }
        }
    }

    // Lecture différée dans graphicsLayer pour éviter toute recomposition lors du fondu
    val contentAlphaState = animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 200 else 150,
            easing = FastOutSlowInEasing
        ),
        label = "ContentAlphaAnimation"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Conteneur morphing 100% différé (phases Layout et Draw/Layer uniquement) :
        // aucune recomposition de l'arbre Compose pendant les 120 images par seconde d'animation
        Box(
            modifier = modifier
                .layout { measurable, constraints ->
                    val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
                    val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
                    val topPx = pillTopAnim.value.dp.roundToPx().coerceIn(0, maxH)
                    val bottomPx = pillBottomAnim.value.dp.roundToPx().coerceIn(topPx, maxH)
                    val widthPx = pillWidthAnim.value.dp.roundToPx().coerceIn(0, maxW)
                    val heightPx = (bottomPx - topPx).coerceAtLeast(0)
                    val placeable = measurable.measure(
                        Constraints.fixed(widthPx, heightPx)
                    )
                    layout(widthPx, topPx + heightPx) {
                        placeable.place(0, topPx)
                    }
                }
                .graphicsLayer {
                    val radiusDp = pillCornerAnim.value.coerceAtLeast(0f).dp
                    scaleX = bounceScaleX.value
                    scaleY = bounceScaleY.value
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    shape = RoundedCornerShape(radiusDp)
                    clip = true
                }
                .background(Color.Black),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlphaState.value }
            ) {
                AnimatedContent(
                    targetState = currentStage,
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        when {
                            initialState == IslandStage.HIDDEN && targetState == IslandStage.COMPACT_MUSIC -> {
                                (fadeIn(animationSpec = tween(210, delayMillis = 55, easing = FastOutSlowInEasing)) +
                                        scaleIn(
                                            initialScale = 0.80f,
                                            transformOrigin = TransformOrigin(0.5f, 0.5f),
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f)
                                        ))
                                    .togetherWith(fadeOut(animationSpec = tween(60, easing = FastOutSlowInEasing)))
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            }
                            initialState == IslandStage.COMPACT_MUSIC && targetState == IslandStage.HIDDEN -> {
                                fadeIn(animationSpec = tween(80))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(110, easing = FastOutSlowInEasing)) +
                                                scaleOut(
                                                    targetScale = 0.80f,
                                                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                                                    animationSpec = tween(110, easing = FastOutSlowInEasing)
                                                )
                                    )
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            }
                            initialState == IslandStage.COMPACT_MUSIC && targetState == IslandStage.EXPANDED_CARD -> {
                                (fadeIn(animationSpec = tween(210, delayMillis = 65, easing = FastOutSlowInEasing)) +
                                        scaleIn(
                                            initialScale = 0.82f,
                                            transformOrigin = TransformOrigin(0.5f, 0.08f),
                                            animationSpec = spring(dampingRatio = 0.84f, stiffness = 340f)
                                        ))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(75, easing = FastOutSlowInEasing)) +
                                                scaleOut(
                                                    targetScale = 0.88f,
                                                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                                                    animationSpec = tween(75, easing = FastOutSlowInEasing)
                                                )
                                    )
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            }
                            initialState == IslandStage.EXPANDED_CARD && targetState == IslandStage.COMPACT_MUSIC -> {
                                (fadeIn(animationSpec = tween(150, delayMillis = 115, easing = FastOutSlowInEasing)) +
                                        scaleIn(
                                            initialScale = 0.86f,
                                            transformOrigin = TransformOrigin(0.5f, 0.5f),
                                            animationSpec = tween(170, delayMillis = 115, easing = FastOutSlowInEasing)
                                        ))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(85, easing = FastOutSlowInEasing)) +
                                                scaleOut(
                                                    targetScale = 0.90f,
                                                    transformOrigin = TransformOrigin(0.5f, 0.05f),
                                                    animationSpec = tween(85, easing = FastOutSlowInEasing)
                                                )
                                    )
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            }
                            initialState == IslandStage.FACE_UNLOCK && (targetState == IslandStage.COMPACT_MUSIC || targetState == IslandStage.EXPANDED_CARD) -> {
                                (fadeIn(animationSpec = tween(180, delayMillis = 140, easing = FastOutSlowInEasing)) +
                                        scaleIn(
                                            initialScale = 0.84f,
                                            transformOrigin = TransformOrigin(0.5f, 0.5f),
                                            animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f)
                                        ))
                                    .togetherWith(
                                        scaleOut(
                                            targetScale = 0.2f,
                                            transformOrigin = TransformOrigin(0.5f, 0.21f),
                                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                                        ) + fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing))
                                    )
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            }
                            initialState == IslandStage.FACE_UNLOCK && targetState == IslandStage.HIDDEN -> {
                                fadeIn(animationSpec = tween(100))
                                    .togetherWith(
                                        scaleOut(
                                            targetScale = 0.1f,
                                            transformOrigin = TransformOrigin(0.5f, 0.21f),
                                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                                        ) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                                    )
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            }
                            targetState == IslandStage.FACE_UNLOCK -> {
                                fadeIn(animationSpec = tween(150, delayMillis = 35, easing = FastOutSlowInEasing))
                                    .togetherWith(fadeOut(animationSpec = tween(80)))
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            }
                            else -> {
                                fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing))
                                    .togetherWith(fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing)))
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            }
                        }
                    },
                    label = "IslandContentTransition"
                ) { stage ->
                    when (stage) {
                        // Vue Déverrouillage Visage (Carré arrondi 96dp fixe sans rebond ni compression interne)
                        IslandStage.FACE_UNLOCK -> {
                            Box(
                                modifier = Modifier
                                    .wrapContentSize(align = Alignment.TopCenter, unbounded = true)
                                    .size(96.dp)
                                    .padding(top = 34.dp, bottom = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FaceUnlockScanner(
                                    status = state.biometricStatus,
                                    isUnlocked = (state.biometricStatus == BiometricStatus.SUCCESS),
                                    accentColor = deviceAccentColor
                                )
                            }
                        }

                        // Vue Musique Détaillée (Dimensions fixes 384x238dp sans écrasement ni reflow de texte à l'ouverture)
                        IslandStage.EXPANDED_CARD -> {
                            val track = state.mediaTrack
                            val albumBitmap = remember(track?.albumArt) {
                                track?.albumArt?.asImageBitmap()
                            }

                            Box(
                                modifier = Modifier
                                    .wrapContentSize(align = Alignment.TopCenter, unbounded = true)
                                    .requiredSize(width = 384.dp, height = 238.dp)
                                    .graphicsLayer {
                                        if (isDraggingCard) {
                                            val dragFraction = ((238f - pillBottomAnim.value) / 198f).coerceIn(0f, 1f)
                                            val dragScale = (1f - dragFraction * 0.12f).coerceIn(0.88f, 1f)
                                            val dragAlpha = (1f - dragFraction * 0.45f).coerceIn(0f, 1f)
                                            scaleX = dragScale
                                            scaleY = dragScale
                                            alpha = dragAlpha
                                        } else {
                                            scaleX = 1f
                                            scaleY = 1f
                                            alpha = 1f
                                        }
                                        transformOrigin = TransformOrigin(0.5f, 0f)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(state.isExpanded) {
                                            if (!state.isExpanded) return@pointerInput
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    velocityTracker.resetTracking()
                                                    isDraggingCard = true
                                                },
                                                onDragEnd = {
                                                    val velocityY = velocityTracker.calculateVelocity().y
                                                    val displacementDp = 238f - pillBottomAnim.value
                                                    val shouldDismiss = (displacementDp >= 50f && velocityY < 800f) || velocityY <= -800f

                                                    coroutineScope.launch {
                                                        if (shouldDismiss) {
                                                            isDraggingCard = false
                                                            HapticFeedbackHelper.performConfirmationFeedback(context, view)
                                                            viewModel.collapseIsland()
                                                        } else {
                                                            val bJob = launch {
                                                                pillBottomAnim.animateTo(
                                                                    targetValue = 238f,
                                                                    animationSpec = spring(
                                                                        dampingRatio = 0.76f,
                                                                        stiffness = 380f
                                                                    )
                                                                )
                                                            }
                                                            val wJob = launch {
                                                                pillWidthAnim.animateTo(
                                                                    targetValue = 384f,
                                                                    animationSpec = spring(
                                                                        dampingRatio = 0.76f,
                                                                        stiffness = 380f
                                                                    )
                                                                )
                                                            }
                                                            val cJob = launch {
                                                                pillCornerAnim.animateTo(
                                                                    targetValue = 40f,
                                                                    animationSpec = spring(
                                                                        dampingRatio = 0.76f,
                                                                        stiffness = 380f
                                                                    )
                                                                )
                                                            }
                                                            joinAll(bJob, wJob, cJob)
                                                            isDraggingCard = false
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    coroutineScope.launch {
                                                        val bJob = launch {
                                                            pillBottomAnim.animateTo(
                                                                targetValue = 238f,
                                                                animationSpec = spring(
                                                                    dampingRatio = 0.76f,
                                                                    stiffness = 380f
                                                                )
                                                            )
                                                        }
                                                        val wJob = launch {
                                                            pillWidthAnim.animateTo(
                                                                targetValue = 384f,
                                                                animationSpec = spring(
                                                                    dampingRatio = 0.76f,
                                                                    stiffness = 380f
                                                                )
                                                            )
                                                        }
                                                        val cJob = launch {
                                                            pillCornerAnim.animateTo(
                                                                targetValue = 40f,
                                                                animationSpec = spring(
                                                                    dampingRatio = 0.76f,
                                                                    stiffness = 380f
                                                                )
                                                            )
                                                        }
                                                        joinAll(bJob, wJob, cJob)
                                                        isDraggingCard = false
                                                    }
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                    change.consume()
                                                    val dragDp = with(density) { dragAmount.toDp().value }
                                                    val newBottom = (pillBottomAnim.value + dragDp).coerceIn(40f, 238f)
                                                    val progress = ((238f - newBottom) / 198f).coerceIn(0f, 1f)
                                                    val newWidth = 384f - (204f * progress)
                                                    val newCorner = 40f - (20f * progress)
                                                    coroutineScope.launch {
                                                        pillBottomAnim.snapTo(newBottom)
                                                        pillWidthAnim.snapTo(newWidth)
                                                        pillCornerAnim.snapTo(newCorner)
                                                    }
                                                }
                                            )
                                        }
                                        .padding(top = 42.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // En-tête : Grande pochette (64dp), Titre agrandi (18sp), Artiste (14sp) et Égaliseur à droite
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.onOpenMusicAppClicked()
                                                }
                                        ) {
                                            if (albumBitmap != null) {
                                                Image(
                                                    bitmap = albumBitmap,
                                                    contentDescription = "Pochette d'album",
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("♪", color = Color.White, fontSize = 28.sp)
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = track?.title ?: "Média en cours",
                                                    color = Color.White,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (!track?.artist.isNullOrBlank()) track?.artist ?: "" else track?.appName ?: "",
                                                    color = Color(0xFF9CA3AF),
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Box(
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                viewModel.toggleExpand()
                                            }
                                        ) {
                                            EqualizerBars(
                                                isPlaying = track?.isPlaying == true,
                                                barColor = deviceAccentColor
                                            )
                                        }
                                    }

                                    // Barre de progression tactile interactive avec scrubbing au doigt en temps réel
                                    val durationMs = track?.durationMs ?: 0L
                                    val positionMs = track?.positionMs ?: 0L

                                    MediaProgressBarScrubber(
                                        positionMs = positionMs,
                                        durationMs = durationMs,
                                        onSeek = { targetMs ->
                                            viewModel.onSeek(targetMs)
                                        }
                                    )

                                    // Commandes multimédias Apple : Précédent (48dp), Bouton central blanc Lecture / Pause (56dp), Suivant (48dp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Piste précédente style Apple
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.onSkipPreviousClicked()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppleSkipPreviousIcon(tint = Color.White)
                                        }

                                        Spacer(modifier = Modifier.width(36.dp))

                                        // Bouton central blanc Apple pour Lecture / Pause
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.onPlayPauseClicked()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ApplePlayPauseIcon(
                                                isPlaying = track?.isPlaying == true,
                                                tint = Color.Black,
                                                sizeDp = 22.dp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(36.dp))

                                        // Piste suivante style Apple
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.onSkipNextClicked()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppleSkipNextIcon(tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // Vue Musique Compacte (Dimensions fixes 180x40dp sans écrasement des ailes pendant le déploiement)
                        IslandStage.COMPACT_MUSIC -> {
                            val compactBitmap = remember(state.mediaTrack?.albumArt) {
                                state.mediaTrack?.albumArt?.asImageBitmap()
                            }

                            Row(
                                modifier = Modifier
                                    .wrapContentSize(align = Alignment.TopCenter, unbounded = true)
                                    .requiredSize(width = 180.dp, height = 40.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                HapticFeedbackHelper.performTapticFeedback(context, view)
                                                viewModel.expandIsland()
                                            },
                                            onLongPress = {
                                                HapticFeedbackHelper.performConfirmationFeedback(context, view)
                                                viewModel.onOpenMusicAppClicked()
                                            }
                                        )
                                    }
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Côté gauche : Aile de la pochette d'album
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (compactBitmap != null) {
                                        Image(
                                            bitmap = compactBitmap,
                                            contentDescription = "Pochette miniature",
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("♪", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }

                                // Centre : Espace neutre réservé pour le poinçon de la caméra frontale
                                Spacer(modifier = Modifier.width(36.dp))

                                // Côté droit : Aile des barres de son (égaliseur)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    EqualizerBars(
                                        isPlaying = state.mediaTrack?.isPlaying == true,
                                        barColor = deviceAccentColor
                                    )
                                }
                            }
                        }

                        IslandStage.HIDDEN -> {
                            Spacer(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/**
 * Barre de progression multimédia avec contrôle tactile et scrubbing direct au doigt en temps réel.
 */
@Composable
private fun MediaProgressBarScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0.30f
    }

    val displayProgress = if (isScrubbing) scrubFraction else currentProgress
    val displayPositionMs = if (isScrubbing) {
        (scrubFraction * durationMs).toLong()
    } else {
        positionMs
    }

    val positionStr = formatMediaTime(displayPositionMs)
    val durationStr = if (durationMs > 0L) formatMediaTime(durationMs) else "--:--"

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(durationMs) {
                    if (durationMs <= 0L) return@pointerInput
                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val barWidth = size.width.toFloat()
                            if (barWidth > 0f) {
                                isScrubbing = true
                                scrubFraction = (down.position.x / barWidth).coerceIn(0f, 1f)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    change.consume()
                                    val currentFrac = (change.position.x / barWidth).coerceIn(0f, 1f)
                                    scrubFraction = currentFrac
                                }
                                val finalTargetMs = (scrubFraction * durationMs).toLong()
                                onSeek(finalTargetMs)
                            }
                        } finally {
                            isScrubbing = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                val w = size.width
                val h = size.height
                val trackHeight = if (isScrubbing) 6.dp.toPx() else 4.5f.dp.toPx()
                val trackRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                val trackTop = (h - trackHeight) / 2f
                val activeWidth = (displayProgress * w).coerceIn(0f, w)

                // Piste de fond
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(w, trackHeight),
                    cornerRadius = trackRadius
                )

                // Piste active blanche
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, trackTop),
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = trackRadius
                    )
                }

                // Curseur Apple
                val thumbRadius = if (isScrubbing) 7.dp.toPx() else 4.5f.dp.toPx()
                val thumbCenterX = activeWidth.coerceIn(thumbRadius, w - thumbRadius)
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbCenterX, h / 2f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = positionStr,
                color = if (isScrubbing) Color.White else Color(0xFF9CA3AF),
                fontSize = 12.sp,
                fontWeight = if (isScrubbing) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = durationStr,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp
            )
        }
    }
}

private fun formatMediaTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val secStr = if (sec < 10) "0$sec" else "$sec"
    return "$min:$secStr"
}

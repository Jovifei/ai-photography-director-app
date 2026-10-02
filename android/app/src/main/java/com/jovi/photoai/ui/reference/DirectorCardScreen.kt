package com.jovi.photoai.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.jovi.photoai.reference.DirectorCard
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

private val PreparationSaver = listSaver<DirectorPreparation, Any>(
    save = { listOf(it.scope, it.completed and 31) },
    restore = { DirectorPreparation(it[0] as String, (it[1] as Int) and 31) },
)

/** Preparation stays local to this screen. It never changes provenance or capture eligibility. */
@Composable
fun DirectorCardScreen(
    card: DirectorCard,
    sourceLabel: String,
    onBack: () -> Unit,
    onEnterCameraDirector: () -> Unit,
) {
    val scope = directorPreparationScope(card, sourceLabel)
    var preparation by rememberSaveable(scope, stateSaver = PreparationSaver) {
        mutableStateOf(DirectorPreparation(scope))
    }
    val mask = preparation.maskFor(scope)
    fun toggle(step: PreparationStep) { preparation = preparation.toggle(scope, step) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.PagePadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = AppDimensions.Space12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回分析") }
            GlassPill(text = sourceLabel)
        }
        Spacer(Modifier.height(AppDimensions.Space16))
        Text("摄影导演卡", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(DIRECTOR_REFERENCE_NOTICE, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextSecondary)
        Spacer(Modifier.height(AppDimensions.Space12))
        Text("拍摄准备 · ${preparation.countFor(scope)} / 5", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("director-preparation-progress"))
        Text("清单可选；离开本页后不作为项目历史保存。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { preparation = DirectorPreparation(scope) }) { Text("重置准备清单") }
        PreparationSection("现场安全", "由拍摄者自行确认",
            "选择稳固、允许拍摄且不妨碍通行的位置；移动前先看路，不倒退进入车流或攀爬。",
            PreparationStep.LOCATION, mask) { toggle(PreparationStep.LOCATION) }
        Spacer(Modifier.height(AppDimensions.Space12))
        PreparationSection("环境", "检查场景、光线与构图", card.environment,
            PreparationStep.ENVIRONMENT, mask) { toggle(PreparationStep.ENVIRONMENT) }
        Spacer(Modifier.height(AppDimensions.Space12))
        PreparationSection("人物", "确认姿态与主体意图", card.subject,
            PreparationStep.SUBJECT, mask) { toggle(PreparationStep.SUBJECT) }
        Spacer(Modifier.height(AppDimensions.Space12))
        PreparationSection("情绪", "确认想表达的状态", card.emotion,
            PreparationStep.EMOTION, mask) { toggle(PreparationStep.EMOTION) }
        Spacer(Modifier.height(AppDimensions.Space12))
        PreparationSection("相机", "确认机位与拍摄指令", card.camera,
            PreparationStep.CAMERA, mask) { toggle(PreparationStep.CAMERA) }
        Spacer(Modifier.height(AppDimensions.Space24))
        PrimaryActionButton("进入 Camera Director", onEnterCameraDirector, Modifier.fillMaxWidth())
        Spacer(Modifier.height(AppDimensions.Space32))
    }
}

@Composable
private fun PreparationSection(
    title: String, subtitle: String, detail: String,
    step: PreparationStep, mask: Int, onToggle: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.RadiusLarge),
        contentPadding = PaddingValues(AppDimensions.CardPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .testTag("director-step-${step.name}")
                    .toggleable(value = mask and step.bit != 0, role = Role.Checkbox, onValueChange = { onToggle() }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = mask and step.bit != 0, onCheckedChange = null)
                Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = AppColors.AccentBlue)
            Text(detail, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextSecondary)
        }
    }
}

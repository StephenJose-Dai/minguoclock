package club.daishenghui.minguo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 长按桌面图标 →「更换图标」/「修改应用名称」打开的页面。 */
class IconPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.refresh(this)
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_ICON
        setContent { IconPicker(mode, onDone = { finish() }) }
    }

    override fun onResume() {
        super.onResume()
        // 从长按菜单进来时也补发一次（在前台，不受系统发布次数限制）
        IconSwitcher.publishShortcuts(this)
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_ICON = "icon"
        const val MODE_NAME = "name"
    }
}

private val Gold = Color(0xFFE8C77A)
private val PickerBg = Color(0xFF0E1116)
private val PickerCard = Color(0xFF171C24)
private val Sub = Color(0xFF8E98A8)

@Composable
private fun IconPicker(mode: String, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PickerBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        if (mode == IconPickerActivity.MODE_NAME) NamePage(onDone) else IconPage(onDone)
        Spacer(Modifier.height(20.dp))
        Text(
            tr(
                "说明：图标和名称分开修改——只换图标时名称不变，只改名称时图标不变。改的是桌面上的原图标，不会多出新图标；切换后部分桌面要几秒刷新，图标位置可能会变。",
                "說明：圖示和名稱分開修改——只換圖示時名稱不變，只改名稱時圖示不變。改的是桌面上的原圖示，不會多出新圖示；切換後部分桌面要幾秒重新整理，圖示位置可能會變。",
                "Icon and name are independent: changing the icon keeps the name and vice versa. The existing home-screen icon is changed — no extra icon is added. The launcher may take a few seconds and may move the icon.",
            ),
            color = Sub, fontSize = 12.sp, lineHeight = 18.sp,
        )
    }
}

@Composable
private fun IconPage(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableIntStateOf(IconSwitcher.current(ctx)) }

    Text(tr("更换图标", "更換圖示", "Change icon"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    listOf(
        0 to tr("通用", "通用", "General"),
        1 to tr("简体", "簡體", "Simplified"),
        2 to tr("繁體（台灣）", "繁體（台灣）", "Traditional (Taiwan)"),
    ).forEach { (group, title) ->
        Spacer(Modifier.height(16.dp))
        Text(title, color = Sub, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        IconSwitcher.styles.filter { it.group == group }.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s ->
                    val on = s.index == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PickerCard)
                            .border(2.dp, if (on) Gold else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { selected = s.index }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(painterResource(s.preview), null, Modifier.size(64.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(tr(s.zh, s.hant, s.en), color = if (on) Gold else Color.White, fontSize = 13.sp)
                    }
                }
                // 一行不满 3 个时补空位，保持大小一致
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    Spacer(Modifier.height(4.dp))
    Button(
        onClick = {
            IconSwitcher.applyBuiltin(ctx, selected)
            Toast.makeText(ctx, tr("已更换，桌面可能需要几秒刷新", "已更換，桌面可能需要幾秒重新整理", "Done. The launcher may take a few seconds"), Toast.LENGTH_LONG).show()
            onDone()
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF2A1F05)),
    ) { Text(tr("使用这个图标", "使用這個圖示", "Use this icon")) }

}

@Composable
private fun NamePage(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableIntStateOf(IconSwitcher.currentName(ctx)) }
    val icon = remember { IconSwitcher.displayIcon(ctx).asImageBitmap() }

    Text(tr("修改应用名称", "修改應用程式名稱", "Rename app"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(16.dp))
    listOf(1 to tr("简体", "簡體", "Simplified"), 2 to tr("繁體（台灣）", "繁體（台灣）", "Traditional (Taiwan)")).forEach { (group, title) ->
        Text(title, color = Sub, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        IconSwitcher.names.filter { it.group == group }.forEach { n ->
            val on = n.index == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PickerCard)
                    .border(2.dp, if (on) Gold else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { selected = n.index }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)))
                Spacer(Modifier.size(14.dp))
                Text(n.text, color = if (on) Gold else Color.White, fontSize = 17.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(4.dp))
    Button(
        onClick = {
            IconSwitcher.applyName(ctx, selected)
            Toast.makeText(ctx, tr("已修改，桌面可能需要几秒刷新", "已修改，桌面可能需要幾秒重新整理", "Done. The launcher may take a few seconds"), Toast.LENGTH_LONG).show()
            onDone()
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF2A1F05)),
    ) { Text(tr("使用这个名称", "使用這個名稱", "Use this name")) }
}

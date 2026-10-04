package com.github.nrfr.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.github.nrfr.manager.AppRelease
import com.github.nrfr.manager.AppUpdateManager
import java.util.concurrent.atomic.AtomicBoolean

object HongguoStyleUpdateDialog {

    private val main = Handler(Looper.getMainLooper())

    fun show(act: Activity, current: String, release: AppRelease) {
        val night = (act.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val p = if (night) Palette.night() else Palette.day()
        val dialog = Dialog(act, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 20f), dp(act, 22f), dp(act, 20f), dp(act, 18f))
            background = round(act, p.page, 28f, p.cardBorder, if (p.cardBorder != 0) 1f else 0f)
        }

        val kicker = TextView(act).apply {
            text = "有更新"
            textSize = 11f
            letterSpacing = 0.04f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(p.accent)
            setPadding(dp(act, 9f), dp(act, 4f), dp(act, 9f), dp(act, 4f))
            background = round(act, p.accentSoft, 99f)
            includeFontPadding = false
        }
        val ver = TextView(act).apply {
            text = versionLine(current, release.version, p)
            textSize = 13f
            gravity = Gravity.END
            includeFontPadding = false
        }
        val head = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(kicker, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(ver, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(act, 10f)
            })
        }
        root.addView(head)

        val notes = noteLines(release.notes)
        val logList = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(act, 12f), 0)
        }
        notes.forEachIndexed { i, line ->
            logList.addView(noteRow(act, line, p, last = i == notes.lastIndex))
        }
        val scroller = MaxHeightScrollView(act).apply {
            maxHeightPx = dp(act, 168f)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(logList, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val thumb = View(act).apply {
            background = round(act, p.accent, 99f)
            visibility = View.GONE
        }
        val logBox = FrameLayout(act).apply {
            background = round(act, p.surface, 18f, p.divider, 1f)
            setPadding(dp(act, 14f), dp(act, 12f), dp(act, 12f), dp(act, 12f))
            addView(scroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(thumb, FrameLayout.LayoutParams(dp(act, 3f), dp(act, 24f)).apply {
                gravity = Gravity.END
                rightMargin = dp(act, 4f)
                topMargin = dp(act, 10f)
            })
        }
        var hideThumb: Runnable? = null
        scroller.setOnScrollChangeListener { v, _, y, _, oldY ->
            if (y == oldY) return@setOnScrollChangeListener
            val sv = v as ScrollView
            val child = sv.getChildAt(0) ?: return@setOnScrollChangeListener
            val range = (child.height - sv.height).coerceAtLeast(1)
            val track = (sv.height - dp(act, 20f)).coerceAtLeast(dp(act, 24f))
            val thumbH = (sv.height.toFloat() / child.height.coerceAtLeast(1) * track).toInt().coerceIn(dp(act, 24f), track)
            val top = ((sv.scrollY.toFloat() / range) * (track - thumbH)).toInt()
            thumb.translationY = top.toFloat()
            val lp = thumb.layoutParams as FrameLayout.LayoutParams
            if (lp.height != thumbH) {
                lp.height = thumbH
                thumb.layoutParams = lp
            }
            thumb.visibility = if (child.height > sv.height + 4) View.VISIBLE else View.GONE
            hideThumb?.let { main.removeCallbacks(it) }
            hideThumb = Runnable { thumb.visibility = View.GONE }
            main.postDelayed(hideThumb!!, 700)
        }
        root.addView(logBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(act, 16f)
        })

        val fill = View(act).apply { background = round(act, p.accent, 99f) }
        val track = FrameLayout(act).apply {
            background = round(act, p.btn2, 99f)
            addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val dlStatus = TextView(act).apply {
            text = "准备下载"
            textSize = 12f
            setTextColor(p.text2)
        }
        val dlRight = TextView(act).apply {
            textSize = 12f
            setTextColor(p.text2)
            gravity = Gravity.END
        }
        val dlRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(dlStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(dlRight)
        }
        val dlBox = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(dlRow)
            addView(track, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 6f)).apply {
                topMargin = dp(act, 8f)
            })
        }
        root.addView(dlBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(act, 16f)
        })

        val actions = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun pill(label: String, bg: Int, fg: Int): TextView = TextView(act).apply {
            text = label
            textSize = 15f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(fg)
            gravity = Gravity.CENTER
            background = round(act, bg, 16f)
            minHeight = dp(act, 44f)
            isClickable = true
            isFocusable = true
        }
        val later = pill("稍后", p.btn2, p.text)
        val go = pill("去更新", p.accent, p.onAccent)
        later.layoutParams = LinearLayout.LayoutParams(0, dp(act, 44f), 1f).apply { rightMargin = dp(act, 5f) }
        go.layoutParams = LinearLayout.LayoutParams(0, dp(act, 44f), 1f).apply { leftMargin = dp(act, 5f) }
        actions.addView(later)
        actions.addView(go)
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(act, 18f)
        })
        root.addView(TextView(act).apply {
            text = "只提示模块本身，不会改系统安装包"
            textSize = 11f
            setTextColor(p.text2)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, dp(act, 10f), 0, 0)
        })

        val cancelled = AtomicBoolean(false)
        var apkFile: java.io.File? = null
        var downloading = false

        fun setProgress(got: Long, total: Long) {
            val apply = {
                val t = if (total > 0) total else release.sizeBytes
                val pct = if (t > 0) ((got * 100) / t).toInt().coerceIn(0, 100) else 0
                val mb = t / (1024f * 1024f)
                val gotMb = got / (1024f * 1024f)
                val barW = track.width
                fill.layoutParams = FrameLayout.LayoutParams(
                    if (barW <= 0) 0 else (barW * pct / 100f).toInt().coerceAtLeast(if (pct > 0) dp(act, 6f) else 0),
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val size = if (t > 0) String.format("%.1f / %.1f MB", gotMb, mb) else "$pct%"
                val ss = SpannableStringBuilder()
                ss.append("$pct%", StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.setSpan(ForegroundColorSpan(p.text), 0, ss.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.append("　$size")
                dlRight.text = ss
            }
            if (track.width <= 0) track.post(apply) else apply()
        }

        fun showActions(vararg views: TextView) {
            actions.removeAllViews()
            views.forEachIndexed { i, v ->
                val lp = LinearLayout.LayoutParams(0, dp(act, 44f), 1f)
                if (views.size > 1) {
                    if (i == 0) lp.rightMargin = dp(act, 5f) else lp.leftMargin = dp(act, 5f)
                }
                actions.addView(v, lp)
            }
        }

        fun startDownload() {
            if (downloading) return
            downloading = true
            cancelled.set(false)
            kicker.text = "下载中"
            logBox.visibility = View.GONE
            dlBox.visibility = View.VISIBLE
            dlStatus.text = "正在下载安装包"
            setProgress(0, release.sizeBytes)
            val cancelBtn = pill("取消", p.btn2, p.text)
            showActions(cancelBtn)
            cancelBtn.setOnClickListener {
                cancelled.set(true)
                downloading = false
                kicker.text = "已取消"
                dlStatus.text = "下载已取消"
                val retry = pill("重新下载", p.accent, p.onAccent)
                retry.setOnClickListener { startDownload() }
                showActions(retry)
            }
            Thread({
                try {
                    val file = AppUpdateManager.downloadApk(act, release, { got, total ->
                        main.post { if (!act.isFinishing) setProgress(got, total) }
                    }, cancelled)
                    apkFile = file
                    main.post {
                        if (act.isFinishing) return@post
                        downloading = false
                        kicker.text = "下载完成"
                        dlStatus.text = "可以安装"
                        setProgress(file.length(), file.length())
                        val install = pill("安装", p.accent, p.onAccent)
                        install.setOnClickListener {
                            try {
                                AppUpdateManager.installApk(act, file)
                            } catch (e: Exception) {
                                Toast.makeText(act, e.message ?: "安装失败", Toast.LENGTH_LONG).show()
                            }
                        }
                        showActions(install)
                    }
                } catch (e: Exception) {
                    main.post {
                        downloading = false
                        if (cancelled.get()) return@post
                        kicker.text = "有更新"
                        dlStatus.text = "下载失败"
                        Toast.makeText(act, e.message ?: "下载失败", Toast.LENGTH_LONG).show()
                        val retry = pill("重新下载", p.accent, p.onAccent)
                        retry.setOnClickListener { startDownload() }
                        showActions(retry)
                    }
                }
            }, "nrfr-update-dl").start()
        }

        later.setOnClickListener { dialog.dismiss() }
        go.setOnClickListener { startDownload() }
        dialog.setOnDismissListener { cancelled.set(true) }

        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            setWindowAnimations(android.R.style.Animation_Dialog)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val dialogWidth = (act.resources.displayMetrics.widthPixels * 0.86f).toInt()
            setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            attributes = attributes.apply {
                gravity = Gravity.CENTER
                dimAmount = 0.48f
                width = dialogWidth
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                windowAnimations = android.R.style.Animation_Dialog
            }
        }
        dialog.show()
    }

    private fun noteLines(raw: String): List<String> {
        val lines = raw.replace("\r\n", "\n").lines()
            .map { it.trim().trimStart('#', '-', '*', ' ') }
            .filter { it.isNotEmpty() }
        return lines.ifEmpty { listOf("修复与体验更新", "建议更新到最新版本后再使用") }
    }

    private fun noteRow(ctx: Context, line: String, p: Palette, last: Boolean): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            if (!last) setPadding(0, 0, 0, dp(ctx, 8f))
        }
        val dot = View(ctx).apply { background = round(ctx, p.accent, 99f) }
        row.addView(dot, LinearLayout.LayoutParams(dp(ctx, 6f), dp(ctx, 6f)).apply {
            topMargin = dp(ctx, 7f)
            rightMargin = dp(ctx, 8f)
        })
        row.addView(TextView(ctx).apply {
            text = line
            textSize = 13.5f
            setTextColor(p.log)
            setLineSpacing(0f, 1.45f)
            includeFontPadding = false
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun versionLine(current: String, latest: String, p: Palette): SpannableStringBuilder {
        val s = SpannableStringBuilder()
        fun add(text: String, color: Int, bold: Boolean = false) {
            val start = s.length
            s.append(text)
            s.setSpan(ForegroundColorSpan(color), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bold) s.setSpan(StyleSpan(Typeface.BOLD), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        add("当前 ", p.text2)
        add(current, p.text, true)
        add("  →  最新 ", p.text2)
        add(latest, p.text, true)
        return s
    }

    private fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private fun round(ctx: Context, color: Int, r: Float, stroke: Int? = null, strokeDp: Float = 0f) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(ctx, r).toFloat()
            if (stroke != null && stroke != 0 && strokeDp > 0f) setStroke(dp(ctx, strokeDp), stroke)
        }

    private class MaxHeightScrollView(ctx: Context) : ScrollView(ctx) {
        var maxHeightPx = 0
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cap = if (maxHeightPx > 0) {
                MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            } else heightMeasureSpec
            super.onMeasure(widthMeasureSpec, cap)
        }
    }

    private class Palette(
        val page: Int, val surface: Int, val text: Int, val text2: Int, val log: Int,
        val accent: Int, val onAccent: Int, val accentSoft: Int, val btn2: Int,
        val divider: Int, val cardBorder: Int
    ) {
        companion object {
            // 暖灰结构色 + 雾蓝强调
            fun day() = Palette(
                page = 0xFFFAF9F7.toInt(), surface = Color.WHITE, text = 0xFF1A1917.toInt(),
                text2 = 0xFF7A7670.toInt(), log = 0xFF3D3B38.toInt(),
                accent = 0xFF3B6FD4.toInt(), onAccent = Color.WHITE,
                accentSoft = 0xFFE8EFFC.toInt(), btn2 = 0xFFF1EEE9.toInt(),
                divider = 0xFFDDD8D1.toInt(), cardBorder = 0xFFDDD8D1.toInt()
            )
            fun night() = Palette(
                page = 0xFF1E1C1A.toInt(), surface = 0xFF2C2A27.toInt(), text = 0xFFF5F2ED.toInt(),
                text2 = 0xFFB0AAA3.toInt(), log = 0xFFF5F2ED.toInt(),
                accent = 0xFF9EC0FF.toInt(), onAccent = 0xFF1A1917.toInt(),
                accentSoft = 0xFF1A2F4D.toInt(), btn2 = 0xFF2C2A27.toInt(),
                divider = 0xFF4A4642.toInt(), cardBorder = 0xFF4A4642.toInt()
            )
        }
    }
}

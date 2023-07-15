package com.arc.fast.layoutmanager.sample

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import com.arc.fast.core.screenWidth
import com.arc.fast.layoutmanager.FastRightSlideLayoutManager
import com.arc.fast.layoutmanager.sample.databinding.ActivityMainBinding
import com.arc.fast.layoutmanager.sample.extension.applyFullScreen
import com.arc.fast.layoutmanager.sample.extension.setLightSystemBar
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullScreen()
        setLightSystemBar(true)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.rv.layoutManager = FastRightSlideLayoutManager(
            // 是否启用循环模式
            enableLoop = true,
            // 是否启用ViewPager模式
            enablePagerMode = false,
            // 是否启用自动翻页
            enableAutoSlide = true,
            // 自动翻页时间
            autoSlideTime = 3000L,
            // 默认显示出来的候选item数量
            candidateCount = 2,
            // 默认显示出来的最后一个候选item的缩放比例
            candidateLastScale = 0.8f,
            // 是否启用离开时透明效果
            enableExitAlpha = true,
            // 离开达到宽度的多少比例时开始启用透明效果
            exitAlphaStartSign = 0.1f,
            // 离开时透明效果的最终值
            exitAlphaEndValue = 0.6f,
            // 是否启用离开时缩放效果
            enableExitScale = true,
            // 横向的总空间偏移量
            totalHorizontallySpaceOffset = 0,
            // 当前Position更新事件
            onCurrentPositionChangeListener = null
        )
        val itemWidth = (screenWidth * 0.8f).roundToInt()
        val data = arrayListOf(R.mipmap.s1, R.mipmap.s2, R.mipmap.s3, R.mipmap.s4)
        binding.rv.adapter =
            object : BaseQuickAdapter<Int, BaseViewHolder>(R.layout.item_layout, data) {
                override fun convert(holder: BaseViewHolder, item: Int) {
                    // 注意：item的宽度必须小于RecyclerView的宽度，否则无法计算出其他候选item的位置
                    holder.getView<ConstraintLayout>(R.id.cl_root).layoutParams.width = itemWidth
                    holder.getView<ImageView>(R.id.iv_image).setImageResource(item)
                }
            }
    }

}
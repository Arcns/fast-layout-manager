# fast-flow-layout

[![](https://jitpack.io/v/Arcns/fast-layout-manager.svg)](https://jitpack.io/#Arcns/fast-layout-manager)

> 自定义LayoutManager，实现从右向左快速层叠滑动的LayoutManager效果，提供更多灵活的配置项。

![集成效果](./image/layout_manager.gif)

#### 1.集成方式：

```
allprojects {
	repositories {
		...
		maven { url 'https://www.jitpack.io' }
	}
}
```

```
 implementation 'com.github.Arcns:fast-layout-manager:latest.release'
```

#### 2.使用方式
```
// 使用从右向左快速层叠滑动的LayoutManager
recyclerView.layoutManager = FastRightSlideLayoutManager(
    // 可选配置项：是否启用循环模式，默认false
    enableLoop = true,
    // 可选配置项：是否启用ViewPager模式，默认false
    enablePagerMode = false,
    // 可选配置项：是否启用自动翻页，默认false
    enableAutoSlide = true,
    // 可选配置项：自动翻页时间，默认3000L
    autoSlideTime = 3000L,
    // 可选配置项：默认显示出来的候选item数量，默认2
    candidateCount = 2,
    // 可选配置项：默认显示出来的最后一个候选item的缩放比例，默认0.8f
    candidateLastScale = 0.8f,
    // 可选配置项：是否启用离开时透明效果，默认false
    enableExitAlpha = true,
    // 可选配置项：离开达到宽度的多少比例时开始启用透明效果，默认0.1f
    exitAlphaStartSign = 0.1f,
    // 可选配置项：离开时透明效果的最终值，默认0.6f
    exitAlphaEndValue = 0.6f,
    // 可选配置项：是否启用离开时缩放效果，默认true
    enableExitScale = true,
    // 可选配置项：横向的总空间偏移量，默认0
    totalHorizontallySpaceOffset = 0,
    // 可选配置项：当前Position更新事件，默认null
    onCurrentPositionChangeListener = null
)
// 测试数据
val itemWidth = (screenWidth * 0.8f).roundToInt()
val data = arrayListOf(R.mipmap.s1, R.mipmap.s2, R.mipmap.s3, R.mipmap.s4)
// 
recyclerView.adapter =
    object : BaseQuickAdapter<Int, BaseViewHolder>(R.layout.item_layout, data) {
        override fun convert(holder: BaseViewHolder, item: Int) {
            // 注意：item的宽度必须小于RecyclerView的宽度，否则无法计算出其他候选item的位置
            holder.getView<ConstraintLayout>(R.id.cl_root).layoutParams.width = itemWidth
            holder.getView<ImageView>(R.id.iv_image).setImageResource(item)
        }
    }
```
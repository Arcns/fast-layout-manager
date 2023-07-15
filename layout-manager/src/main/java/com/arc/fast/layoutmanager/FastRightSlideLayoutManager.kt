package com.arc.fast.layoutmanager

import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 右滑LayoutManager
 */
class FastRightSlideLayoutManager(
    // 是否启用循环模式
    val enableLoop: Boolean = false,
    // 是否启用ViewPager模式
    val enablePagerMode: Boolean = false,
    // 是否启用自动翻页
    val enableAutoSlide: Boolean = false,
    // 自动翻页时间
    val autoSlideTime: Long = 3000L,
    // 默认显示出来的候选item数量
    val candidateCount: Int = 2,
    // 默认显示出来的最后一个候选item的缩放比例
    val candidateLastScale: Float = 0.8f,
    // 是否启用离开时透明效果
    val enableExitAlpha: Boolean = false,
    // 离开达到宽度的多少比例时开始启用透明效果
    val exitAlphaStartSign: Float = 0.1f,
    // 离开时透明效果的最终值
    val exitAlphaEndValue: Float = 0.6f,
    // 是否启用离开时缩放效果
    val enableExitScale: Boolean = true,
    // 横向的总空间偏移量
    val totalHorizontallySpaceOffset: Int = 0,
    // 当前Position更新事件
    var onCurrentPositionChangeListener: ((Int) -> Unit)? = null
) : RecyclerView.LayoutManager(), RecyclerView.SmoothScroller.ScrollVectorProvider {

    private var mRecyclerView: RecyclerView? = null

    // 是否已初始化
    private var mAttached = false
    private var mInit = false

    // 每个item的宽度
    private var mChildWidth = 0

    // 每个item的高度
    private var mChildHeight = 0

    // 横向的总空间
    private val mTotalHorizontallySpace: Int by lazy { width + totalHorizontallySpaceOffset }

    // 总偏移量
    private var mTotalOffset: Int = 0

    // 最大可偏移量
    private val mOffsetMax: Int get() = mChildWidth * (itemCount - 1)

    // 候选item的参数（第2个item开始为候选item）
    // 默认显示出来的最后一个候选item的高度
    private var mCandidateLastHeight = 0f

    // 候选item的缩放速度
    private var mCandidateScaleSpeed = 0f

    // 每个候选item显示的空间
    private var mCandidateEachDisplaySpace: Int = 0

    // 候选item的速度
    private var mCandidateSpeed: Float = 0f

    // 候选item的基础left
    private var mCandidateBaseLeft: Int = 0

    // item离开时的透明速度
    private var mExitAlphaSpeed = 1f

    // 离开达到left时开始启用透明效果
    private var mExitAlphaStartLeft = 0f

    // item离开时的缩放速度
    private var mExitScaleSpeed = 0f

    // 是否启用缩放功能
    private val mEnableScale by lazy {
        candidateLastScale < 1f
    }

    // 最后一个dx
    private var mLastDx: Int = 0

    // 当前第一个显示的item的position
    private val firstItemPosition: Int
        get() = if (mTotalOffset < 0) {
            (itemCount - ceil(abs(mTotalOffset).toDouble() / mChildWidth).toInt() % itemCount).let {
                if (it >= itemCount) 0
                else it
            }
        } else (mTotalOffset / mChildWidth) % itemCount

    // 当前第一个显示的item的left
    val firstItemLeft: Int
        get() = if (mTotalOffset % mChildWidth == 0) 0
        else if (mTotalOffset < 0) -mTotalOffset % mChildWidth - mChildWidth
        else -mTotalOffset % mChildWidth

    // 等待切换的位置
    private var mPendingPosition = RecyclerView.NO_POSITION

    // 自动翻页
    private val mAutoSlideRunnable by lazy {
        Runnable {
            if (enableAutoSlide) {
                mRecyclerView?.smoothScrollBy(
                    calculateDistanceToPosition((firstItemPosition + 1).let { if (it > itemCount - 1) 0 else it }),
                    0
                )
                startAutoSlide()
            }
        }
    }
    private var mAutoSlideInit = false
    private var mSmoothScrollToPosition = RecyclerView.NO_POSITION

    // 当前坐标
    var currentPosition: Int = RecyclerView.NO_POSITION
        private set

    // 触摸事件
    var onTouchListener: OnTouchListener? = null

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        mRecyclerView = view
        if (!mAttached) {
            mAttached = true
            // 监听触摸事件
            view?.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        stopAutoSlide()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        stopAutoSlide()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                        if (v.isPressed) {
                            v.performClick()
                        }
                        startAutoSlide()
                    }
                }
                onTouchListener?.onTouch(view, event) ?: false
            }
            // 设置惯性显示完整的item
            object : SnapHelper() {
                private var mDirection = 0
                override fun calculateDistanceToFinalSnap(
                    layoutManager: RecyclerView.LayoutManager, targetView: View
                ): IntArray =
                    intArrayOf(
                        calculateDistanceToPosition(layoutManager.getPosition(targetView)),
                        0
                    )

                override fun findTargetSnapPosition(
                    layoutManager: RecyclerView.LayoutManager, velocityX: Int,
                    velocityY: Int
                ): Int {
                    mDirection = velocityX
                    return RecyclerView.NO_POSITION
                }

                override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
                    val pos = getFixedScrollPosition(mDirection)
                    mDirection = 0
                    return if (pos != RecyclerView.NO_POSITION) layoutManager.findViewByPosition(pos) else null
                }
            }.attachToRecyclerView(view)
        }
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        if (itemCount <= 0 || mAutoSlideInit) {
            return
        }
        mAutoSlideInit = true
        startAutoSlide()
    }

    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?
    ) {
        mAutoSlideInit = false
    }


    override fun requestLayout() {
        super.requestLayout()
        mAutoSlideInit = false
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        stopAutoSlide()
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun isAutoMeasureEnabled(): Boolean = true

    override fun canScrollHorizontally(): Boolean = true

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (state.itemCount == 0) {
            removeAndRecycleAllViews(recycler)
            return
        }
        // 不支持预测动画，可以直接return
        if (state.isPreLayout) return
        // 跳转
        if (mPendingPosition != RecyclerView.NO_POSITION) {
            mTotalOffset = mPendingPosition * mChildWidth
            mPendingPosition = RecyclerView.NO_POSITION
        }
        //轻量级的将view移除屏幕
        detachAndScrapAttachedViews(recycler)
        // 填充
        if (!mInit) fill(recycler, false, 0, 0)
        else fill(recycler)
    }


    override fun scrollToPosition(position: Int) {
        if (position < 0 || position >= itemCount) return
        mPendingPosition = position
        requestLayout()
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {
        mSmoothScrollToPosition = position
        stopAutoSlide()
        recyclerView.smoothScrollBy(calculateDistanceToPosition(position), 0)
    }


    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (childCount == 0 || dx == 0) return 0

        // dx超过边界时进行重新计算
        var actualDx = dx
        mTotalOffset += dx
        if (!enableLoop) {
            if (mTotalOffset < 0) {
                actualDx = mTotalOffset
                mTotalOffset = 0
            } else if (mTotalOffset > mOffsetMax) {
                actualDx = mTotalOffset - mOffsetMax
                mTotalOffset = mOffsetMax
            }
        }
        mLastDx = actualDx

        fill(recycler)

        return if (enablePagerMode) 1 else actualDx
    }


    private fun fill(
        recycler: RecyclerView.Recycler,
        recycle: Boolean = true,
        firstPosition: Int = firstItemPosition,
        firstLeft: Int = firstItemLeft
    ) {
        var fillPosition = firstPosition
        var fillLeft = firstLeft
        // 剩余空间
        var freeSpace = 0
        // 本次填充的相对position
        var relativePosition = 0

        //根据限定条件，不停地填充View进来
        val fillChilds = ArrayList<View>()
        while (fillPosition in 0 until itemCount) {
            val itemView = recycler.getViewForPosition(fillPosition)
            fillChilds.add(itemView)
            addView(itemView, 0)
//            addView(itemView)
            measureChild(itemView, 0, 0)
            // 设置间距
            itemView.layoutParams = (itemView.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = paddingLeft
                topMargin = paddingTop
                rightMargin = paddingRight
                bottomMargin = paddingBottom
            }
            if (relativePosition == 0) {
                // 初始化公共参数
                if (!mInit) {
                    mInit = true
                    val child = getChildAt(0)!!
                    mChildWidth = getDecoratedMeasuredWidth(child) + paddingLeft + paddingRight
                    mChildHeight = getDecoratedMeasuredHeight(child) + paddingTop + paddingBottom
                    mCandidateEachDisplaySpace =
                        (mTotalHorizontallySpace - mChildWidth) / candidateCount
                    mCandidateSpeed = mCandidateEachDisplaySpace.toFloat() / mChildWidth
                    mCandidateBaseLeft = mCandidateEachDisplaySpace
                    mCandidateLastHeight = mChildHeight * candidateLastScale
                    mCandidateScaleSpeed =
                        (1 - candidateLastScale) / (mTotalHorizontallySpace - mChildWidth)
                    if (enableExitAlpha) {
                        mExitAlphaSpeed =
                            (1f - exitAlphaEndValue) / (mChildWidth * (1f - exitAlphaStartSign))
                        mExitAlphaStartLeft = -mChildWidth * exitAlphaStartSign
                    }
                    if (enableExitScale) {
                        mExitScaleSpeed = (1 - candidateLastScale) / candidateCount / mChildWidth
                    }
                }
                // 计算剩余空间
                freeSpace = mTotalHorizontallySpace - (fillLeft + mChildWidth)
            }

            //将要填充的View的左上右下
            var left: Int = 0
            var right: Int = 0
            var top = 0
            var bottom = top + mChildHeight
            var scale = 1f
            var alpha = 1f

            // 本次填充的第1个item
            if (relativePosition == 0) {
                left = fillLeft
                right = left + mChildWidth
                if (enableExitAlpha && left < mExitAlphaStartLeft) {
                    alpha =
                        exitAlphaEndValue + (mChildWidth - abs(left - mExitAlphaStartLeft)) * mExitAlphaSpeed
                }
                if (mEnableScale && enableExitScale && left < 0) {
                    scale = 1f - abs(left) * mExitScaleSpeed
                }
                if (fillPosition != currentPosition) {
                    currentPosition = fillPosition
                    onCurrentPositionChangeListener?.invoke(currentPosition)
                }
            }
            // 本次填充的第2个item（第一个候选者）
            else if (relativePosition == 1) {
                val firstRigth = fillLeft + mChildWidth
                left = (mCandidateBaseLeft + fillLeft * mCandidateSpeed).toInt()
                right = left + mChildWidth
                scale =
                    candidateLastScale + (mTotalHorizontallySpace - right) * mCandidateScaleSpeed
                fillLeft = left + mCandidateEachDisplaySpace
                freeSpace -= (right - firstRigth)
                // 根据缩放后会位移，需要重新记录left和right
                if (mEnableScale) {
                    left += (mChildWidth * (1 - scale) / 2).toInt()
                    right = left + mChildWidth
                }
            }
            // 本次填充的其他候选者
            else {
                left = fillLeft
                right = left + mChildWidth
                scale =
                    candidateLastScale + (mTotalHorizontallySpace - right) * mCandidateScaleSpeed
                fillLeft = left + mCandidateEachDisplaySpace
                freeSpace -= mCandidateEachDisplaySpace
                // 根据缩放后会位移，需要重新记录left和right
                if (mEnableScale) {
                    left += (mChildWidth * (1 - scale) / 2).toInt()
                    right = left + mChildWidth
                }
            }

            // 填充到指定位置
//            layoutDecorated(itemView, left, top, right, bottom)
            layoutDecoratedWithMargins(itemView, left, top, right, bottom)
            if (mEnableScale && itemView.scaleX != scale) {
                itemView.scaleX = scale
                itemView.scaleY = scale
//                if (fillPosition == 2) {
//                    Log.e("滑动", "$mLastDx  scale:新：$scale   旧：$lastscale ")
//                    if (firstPosition!=2 && scale != 1f && mLastDx > 0 && scale < lastscale) {
//                        Log.e("滑动抖动", "scale:新：$scale   旧：$lastscale ")
//                    }
//                    lastscale = scale
//                }
            }
            itemView.alpha = alpha

            // 判断还有没有剩余空间
            if (freeSpace <= 0 || mCandidateEachDisplaySpace <= 0) break
            fillPosition++
            // 无限循环
            if (enableLoop && fillPosition >= itemCount) {
                fillPosition = 0
            }
            relativePosition++
        }

        if (recycle) {
            val recycleChilds = ArrayList<View>()
            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: return
                if (!fillChilds.contains(child)) {
                    recycleChilds.add(child)
                }
            }
            recycleChilds.forEach {
                removeAndRecycleView(it, recycler)
            }
            recycleChilds.clear()
        }

        fillChilds.clear()
    }

    /**
     * 计算滚动到指定位置所需要的最近距离
     */
    fun calculateDistanceToPosition(targetPos: Int): Int {
        val currentPos = firstItemPosition
        val distance = if (currentPos == targetPos) {
            if (mTotalOffset % mChildWidth == 0) 0
            else if (mTotalOffset >= 0) -mTotalOffset % mChildWidth
            else abs(mTotalOffset % mChildWidth) - mChildWidth
        } else if ((targetPos - currentPos).let { it == 1 || it == itemCount - 1 }) {
            if (mTotalOffset >= 0) mChildWidth - mTotalOffset % mChildWidth
            else abs(mTotalOffset % mChildWidth)
        } else if ((targetPos - currentPos).let { it == -1 || it == -itemCount + 1 }) {
            if (mTotalOffset >= 0) mChildWidth - mTotalOffset % mChildWidth
            else abs(mTotalOffset % mChildWidth)
        }
        // 向前循环的场景
        else if (mTotalOffset > mOffsetMax) {
            // <--计算假如向左边滚动所需的距离
            val leftPosDistance =
                if (targetPos >= currentPos) targetPos - currentPos else itemCount - currentPos + targetPos
            // -->计算假如向右边滚动所需的距离
            val rightPosDistance =
                if (targetPos <= currentPos) currentPos - targetPos else currentPos + (itemCount - targetPos)
            if (leftPosDistance < rightPosDistance) {
                // <--向左边滚动 +
                mChildWidth * (mTotalOffset / mChildWidth + leftPosDistance) - mTotalOffset
            } else {
                // -->向右边滚动 -
                mChildWidth * (mTotalOffset / mChildWidth - rightPosDistance) - mTotalOffset
            }
        }
        // 向后循环的场景
        else if (mTotalOffset < 0) {
            // <--计算假如向左边滚动所需的距离
            val leftPosDistance =
                if (targetPos >= currentPos) targetPos - currentPos else itemCount - currentPos + targetPos
            // -->计算假如向右边滚动所需的距离
            val rightPosDistance =
                if (targetPos <= currentPos) currentPos - targetPos else currentPos + (itemCount - targetPos)

            if (leftPosDistance < rightPosDistance) {
                // <--向左边滚动 +
                mChildWidth * (mTotalOffset / mChildWidth + leftPosDistance - 1) - mTotalOffset
            } else {
                // -->向右边滚动 -
                mChildWidth * (mTotalOffset / mChildWidth - rightPosDistance - 1) - mTotalOffset
            }
        } else mChildWidth * targetPos - mTotalOffset
        return distance
    }

    /**
     * 计算并返回滚动结束后停留的坐标
     */
    fun getFixedScrollPosition(direction: Int): Int {
        var targetPosition = RecyclerView.NO_POSITION
        if (mInit) {
            if (mTotalOffset % mChildWidth != 0) {
                val itemPositionF =
                    abs(
                        if (mTotalOffset < 0) itemCount - abs(mTotalOffset.toFloat()) / mChildWidth % itemCount
                        else if (mTotalOffset > mOffsetMax) mTotalOffset.toFloat() / mChildWidth % itemCount
                        else mTotalOffset.toFloat() / mChildWidth
                    )
                val itemPosition = itemPositionF.toInt()
                Log.e(
                    "getFixedScrollPosition",
                    "itemPositionF:" + itemPositionF + ",itemPosition:" + itemPosition + ",direction:" + direction
                )
                targetPosition = if (enablePagerMode) {
                    if (mLastDx > 0) itemPosition + 1 else itemPosition
                } else {
                    val offset = itemPositionF - itemPosition
                    (if (direction > 0 && offset >= 0.3) itemPosition + 1
                    else if (direction < 0 && offset >= 0.7) itemPosition + 1
                    else if (direction == 0 && offset >= 0.4) itemPosition + 1
                    else itemPosition)
                }.let { if (it >= itemCount) 0 else it }
            } else {
                val currentPosition = firstItemPosition
                if (mSmoothScrollToPosition != RecyclerView.NO_POSITION && mSmoothScrollToPosition == currentPosition) {
                    startAutoSlide()
                }
                mSmoothScrollToPosition = RecyclerView.NO_POSITION
            }
        }
        Log.e("getFixedScrollPosition", "targetPosition:" + targetPosition)
        return targetPosition
    }

    /**
     * 停止定期自动滑动
     */
    private fun stopAutoSlide() {
        mRecyclerView?.removeCallbacks(mAutoSlideRunnable)
    }

    /**
     * 开始定期自动滑动
     */
    private fun startAutoSlide() {
        if (enableAutoSlide && itemCount > 1 && autoSlideTime > 0 && mRecyclerView != null) {
            stopAutoSlide()
            mRecyclerView?.postDelayed(
                mAutoSlideRunnable,
                autoSlideTime
            )
        }
    }

    // 这里必须实现ScrollVectorProvider，才能使自定义SnapHelper接收到findTargetSnapPosition回调，从而获取到velocityX的值
    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        if (childCount == 0) {
            return null
        }
        val firstChildPos = getPosition(getChildAt(0)!!)
        val direction = if (targetPosition < firstChildPos) -1 else 1
        return PointF(direction.toFloat(), 0f)
    }

}

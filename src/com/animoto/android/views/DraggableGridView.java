//TO DO:
//
// - improve timer performance (especially on Eee Pad)
// - improve child rearranging

package com.animoto.android.views;

import java.util.Collections;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.Scroller;

public class DraggableGridView extends ViewGroup implements
		View.OnTouchListener, View.OnClickListener, View.OnLongClickListener {
	// layout vars
	public static float childRatio = .9f;
	protected int colCount/* 子视图的列数 */, childSize/* 子视图的尺寸 */,
			padding/* 子视图之间的间隙 */, dpi/* 屏幕的像素密度 */, scroll = 0;
	protected float lastDelta = 0;
	protected Handler handler = new Handler();
	// dragging vars
	protected int dragged = -1, lastX = -1, lastY = -1, lastTarget = -1;
	protected boolean enabled = true, touching = false;
	// anim vars
	public static int animT = 250;
	protected ArrayList<Integer> newPositions = new ArrayList<Integer>();
	// listeners
	protected OnRearrangeListener onRearrangeListener;
	protected OnClickListener secondaryOnClickListener;
	private OnItemClickListener onItemClickListener;
	
	// CONSTRUCTOR AND HELPERS
	public DraggableGridView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setListeners();
		handler.removeCallbacks(updateTask);
		handler.postAtTime(updateTask, SystemClock.uptimeMillis() + 500);
		setChildrenDrawingOrderEnabled(true);

		DisplayMetrics metrics = new DisplayMetrics();
		((Activity) context).getWindowManager().getDefaultDisplay()
				.getMetrics(metrics);
		dpi = metrics.densityDpi;
	}
	
	protected void setListeners() {
		setOnTouchListener(this);
		super.setOnClickListener(this);
		setOnLongClickListener(this);
	}

	@Override
	public void setOnClickListener(OnClickListener l) {
		secondaryOnClickListener = l;
	}
	
	/***
	 * 滚动出界面时回弹
	 */
	protected Runnable updateTask = new Runnable() {
		public void run() {
			if (dragged != -1) {
				if (lastY < padding * 3 && scroll > 0)
					scroll -= 20;
				else if (lastY > getBottom() - getTop() - (padding * 3)
						&& scroll < getMaxScroll())
					scroll += 20;
			} else if (lastDelta != 0 && !touching) {
				scroll += lastDelta;
				lastDelta *= .9;
				if (Math.abs(lastDelta) < .25)
					lastDelta = 0;
			}
			clampScroll();
			onLayout(true, getLeft(), getTop(), getRight(), getBottom());

			handler.postDelayed(this, 25);
		}
	};

	public void onWindowFocusChanged(boolean hasWindowFocus) {
		if (!hasWindowFocus) {
			handler.removeCallbacks(updateTask);
		} else {
			handler.removeCallbacks(updateTask);
			handler.postAtTime(updateTask, SystemClock.uptimeMillis() + 500);
		}
	};
	
	@Override
	protected void onAttachedToWindow() {
		// TODO Auto-generated method stub
		super.onAttachedToWindow();
	}
	
	// OVERRIDES
	@Override
	public void addView(View child) {
		super.addView(child);
		newPositions.add(-1);
	};

	@Override
	public void removeViewAt(int index) {
		super.removeViewAt(index);
		newPositions.remove(index);
	};

	// LAYOUT
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		// compute width of view, in dp
		float w = (r - l) / (dpi / 160f);

		// determine number of columns, at least 2
		colCount = 2;
		int sub = 240;
		w -= 280;
		while (w > 0) {
			colCount++;
			w -= sub;
			sub += 40;
		}

		// determine childSize and padding, in px
		childSize = (r - l) / colCount;
		childSize = Math.round(childSize * childRatio);
		padding = ((r - l) - (childSize * colCount)) / (colCount + 1);
		for (int i = 0; i < getChildCount(); i++)
			if (i != dragged) {
				Point xy = getCoorFromIndex(i);
				getChildAt(i).layout(xy.x, xy.y, xy.x + childSize,
						xy.y + childSize);
			}
	}

	@Override
	protected int getChildDrawingOrder(int childCount, int i) {
		if (dragged == -1)
			return i;
		else if (i == childCount - 1)
			return dragged;
		else if (i >= dragged)
			return i + 1;
		return i;
	}

	/***
	 * 根据坐标获取子视图的index
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public int getIndexFromCoor(int x, int y) {
		int col = getColOrRowFromCoor(x), row = getColOrRowFromCoor(y + scroll);
		if (col == -1 || row == -1) // touch is between columns or rows
			return -1;
		int index = row * colCount + col;
		if (index >= getChildCount())
			return -1;
		return index;
	}

	/***
	 * 根据坐标位置获取当前坐标的行index或者是列index
	 * 
	 * @param coor
	 * @return
	 */
	protected int getColOrRowFromCoor(int coor) {
		coor -= padding;
		for (int i = 0; coor > 0; i++) {
			if (coor < childSize)
				return i;
			coor -= (childSize + padding);
		}
		return -1;
	}

	/***
	 * 根据坐标为位置获取新的回归index
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	protected int getTargetFromCoor(int x, int y) {
		if (getColOrRowFromCoor(y + scroll) == -1) // touch is between rows
			return -1;
		int target = getIndexFromCoor(x, y);
		return target;
	}

	/***
	 * 获取第index个子视图的左上坐标的点
	 * 
	 * @param index
	 * @return
	 */
	protected Point getCoorFromIndex(int index) {
		int col = index % colCount;
		int row = index / colCount;
		return new Point(padding + (childSize + padding) * col, padding
				+ (childSize + padding) * row - scroll);
	}

	/***
	 * 获取child视图的index
	 * 
	 * @param child
	 * @return
	 */
	public int getIndexOf(View child) {
		for (int i = 0; i < getChildCount(); i++)
			if (getChildAt(i) == child)
				return i;
		return -1;
	}

	/***
	 * 点击事件回调
	 */
	public void onClick(View view) {
		if (enabled) {
			if (secondaryOnClickListener != null)
				secondaryOnClickListener.onClick(view);
			if (onItemClickListener != null && getLastIndex() != -1)
				onItemClickListener.onItemClick(null,
						getChildAt(getLastIndex()), getLastIndex(),
						getLastIndex() / colCount);
		}
	}

	/***
	 * 长按事件回调
	 */
	public boolean onLongClick(View view) {
		if (!enabled)
			return false;
		int index = getLastIndex();
		if (index != -1) {
			dragged = index;
			animateDragged();
			return true;
		}
		return false;
	}

	/***
	 * 触摸事件回调
	 */
	public boolean onTouch(View view, MotionEvent event) {
		int action = event.getAction();
		switch (action & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			enabled = true;
			lastX = (int) event.getX();
			lastY = (int) event.getY();
			touching = true;
			break;
		case MotionEvent.ACTION_MOVE:
			int delta = lastY - (int) event.getY();
			if (dragged != -1) {
				// change draw location of dragged visual
				int x = (int) event.getX(), y = (int) event.getY();
				int l = x - (3 * childSize / 4), t = y - (3 * childSize / 4);
				getChildAt(dragged).layout(l, t, l + (childSize * 3 / 2),
						t + (childSize * 3 / 2));

				// check for new target hover
				int target = getTargetFromCoor(x, y);
				if (lastTarget != target) {
					if (target != -1) {
						animateGap(target);
						lastTarget = target;
					}
				}
			} else {
				scroll += delta;
				clampScroll();
				if (Math.abs(delta) > 2)
					enabled = false;
				onLayout(true, getLeft(), getTop(), getRight(), getBottom());
			}
			lastX = (int) event.getX();
			lastY = (int) event.getY();
			lastDelta = delta;
			break;
		case MotionEvent.ACTION_UP:
			if (dragged != -1) {
				View v = getChildAt(dragged);
				if (lastTarget != -1)
					reorderChildren();
				else {
					Point xy = getCoorFromIndex(dragged);
					v.layout(xy.x, xy.y, xy.x + childSize, xy.y + childSize);
				}
				v.clearAnimation();
				if (v instanceof ImageView)
					((ImageView) v).setAlpha(255);
				lastTarget = -1;
				dragged = -1;
			}
			touching = false;
			break;
		}
		if (dragged != -1)
			return true;
		return false;
	}

	/***
	 * 初始化拖拽视图的展示动画
	 */
	protected void animateDragged() {
		View v = getChildAt(dragged);
		int x = getCoorFromIndex(dragged).x + childSize / 2, y = getCoorFromIndex(dragged).y
				+ childSize / 2;
		int l = x - (3 * childSize / 4), t = y - (3 * childSize / 4);
		v.layout(l, t, l + (childSize * 3 / 2), t + (childSize * 3 / 2));
		AnimationSet animSet = new AnimationSet(true);
		ScaleAnimation scale = new ScaleAnimation(.667f, 1, .667f, 1,
				childSize * 3 / 4, childSize * 3 / 4);
		scale.setDuration(animT);
		AlphaAnimation alpha = new AlphaAnimation(1, .5f);
		alpha.setDuration(animT);

		animSet.addAnimation(scale);
		animSet.addAnimation(alpha);
		animSet.setFillEnabled(true);
		animSet.setFillAfter(true);

		v.clearAnimation();
		v.startAnimation(animSet);
	}

	/***
	 * 为新的回归位置腾出空间
	 * 
	 * @param target
	 */
	protected void animateGap(int target) {
		for (int i = 0; i < getChildCount(); i++) {
			View v = getChildAt(i);
			if (i == dragged)
				continue;
			int newPos = i;
			if (dragged < target && i >= dragged + 1 && i <= target)
				newPos--;
			else if (target < dragged && i >= target && i < dragged)
				newPos++;

			// animate
			int oldPos = i;
			if (newPositions.get(i) != -1)
				oldPos = newPositions.get(i);
			if (oldPos == newPos)
				continue;

			Point oldXY = getCoorFromIndex(oldPos);
			Point newXY = getCoorFromIndex(newPos);
			Point oldOffset = new Point(oldXY.x - v.getLeft(), oldXY.y
					- v.getTop());
			Point newOffset = new Point(newXY.x - v.getLeft(), newXY.y
					- v.getTop());

			TranslateAnimation translate = new TranslateAnimation(
					Animation.ABSOLUTE, oldOffset.x, Animation.ABSOLUTE,
					newOffset.x, Animation.ABSOLUTE, oldOffset.y,
					Animation.ABSOLUTE, newOffset.y);
			translate.setDuration(animT);
			translate.setFillEnabled(true);
			translate.setFillAfter(true);
			v.clearAnimation();
			v.startAnimation(translate);

			newPositions.set(i, newPos);
		}
	}

	/***
	 * 重新给子视图排序
	 */
	protected void reorderChildren() {
		// FIGURE OUT HOW TO REORDER CHILDREN WITHOUT REMOVING THEM ALL AND
		// RECONSTRUCTING THE LIST!!!
		if (onRearrangeListener != null)
			onRearrangeListener.onRearrange(dragged, lastTarget);
		ArrayList<View> children = new ArrayList<View>();
		for (int i = 0; i < getChildCount(); i++) {
			getChildAt(i).clearAnimation();
			children.add(getChildAt(i));
		}
		removeAllViews();
		while (dragged != lastTarget)
			if (lastTarget == children.size()) // dragged and dropped to the
												// right of the last element
			{
				children.add(children.remove(dragged));
				dragged = lastTarget;
			} else if (dragged < lastTarget) // shift to the right
			{
				Collections.swap(children, dragged, dragged + 1);
				dragged++;
			} else if (dragged > lastTarget) // shift to the left
			{
				Collections.swap(children, dragged, dragged - 1);
				dragged--;
			}
		for (int i = 0; i < children.size(); i++) {
			newPositions.set(i, -1);
			addView(children.get(i));
		}
		onLayout(true, getLeft(), getTop(), getRight(), getBottom());
	}

	// 滑到顶部
	public void scrollToTop() {
		scroll = 0;
	}

	// 滑到底部
	public void scrollToBottom() {
		scroll = Integer.MAX_VALUE;
		clampScroll();
	}

	/***
	 * 计算滑动距离
	 */
	protected void clampScroll() {
		int stretch = 3, overreach = getHeight() / 2;
		int max = getMaxScroll();
		max = Math.max(max, 0);

		if (scroll < -overreach) {
			scroll = -overreach;
			lastDelta = 0;
		} else if (scroll > max + overreach) {
			scroll = max + overreach;
			lastDelta = 0;
		} else if (scroll < 0) {
			if (scroll >= -stretch)
				scroll = 0;
			else if (!touching)
				scroll -= scroll / stretch;
		} else if (scroll > max) {
			if (scroll <= max + stretch)
				scroll = max;
			else if (!touching)
				scroll += (max - scroll) / stretch;
		}
	}

	/***
	 * 获取最大滑动距离
	 * 
	 * @return
	 */
	protected int getMaxScroll() {
		int rowCount = (int) Math.ceil((double) getChildCount() / colCount), max = rowCount
				* childSize + (rowCount + 1) * padding - getHeight();
		return max;
	}

	/***
	 * 获取上一次触碰的子视图index
	 * 
	 * @return
	 */
	public int getLastIndex() {
		return getIndexFromCoor(lastX, lastY);
	}

	/***
	 * 设置子视图复位回调
	 * 
	 * @param l
	 */
	public void setOnRearrangeListener(OnRearrangeListener l) {
		this.onRearrangeListener = l;
	}

	/***
	 * 设置子视图点击回调
	 * 
	 * @param l
	 */
	public void setOnItemClickListener(OnItemClickListener l) {
		this.onItemClickListener = l;
	}
}
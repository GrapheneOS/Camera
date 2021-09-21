package app.grapheneos.camera.ui

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import com.google.android.material.tabs.TabLayout

class BottomTabLayout : TabLayout {
    //    private final ArrayList<Integer> snapPoints = new ArrayList<>();
    //    private int count = 0;
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(
        context, attrs
    )

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    private var sp = 0

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val tabParent = getChildAt(0) as ViewGroup
        val firstTab = tabParent.getChildAt(0)
        val lastTab = tabParent.getChildAt(tabParent.childCount - 1)
        sp = width / 2 - firstTab.width / 2
        ViewCompat.setPaddingRelative(getChildAt(0), sp, 0, width / 2 - lastTab.width / 2, 0)
        val centerTab = tabParent.getChildAt(tabParent.childCount / 2)
        centerView(centerTab)

//        count = getTabCount();
//
//        snapPoints.clear();
//
//        int widthC = 0;
//
//        snapPoints.add(0);
//
//        View tabView = null;
//
//        for (int i = 0; i < count; ++i) {
//            tabView = tabParent.getChildAt(i);
//            widthC+=tabView.getWidth()/2 + 10;
//            snapPoints.add(widthC);
//            widthC+=tabView.getWidth()/2 + 9;
//        }
//
//        if(tabView!=null)
//            widthC+=(tabView.getWidth()/2 + 9);
//
//        snapPoints.add(widthC);
//
//        Log.i("SNAPS", Arrays.toString(snapPoints.toArray()));
    }

    // TODO: Implement snapping behavior for TabLayout's tabs
    //    @Override
    //    protected void onScrollChanged(int x, int t, int oldX, int oldT) {
    //
    //        if(Math.abs(oldX-x)<=1) {
    //            if(isSelected()){
    //                View tabView = Objects.requireNonNull(getTabAt(getSelectedTabPosition())).view;
    //                centerView(tabView);
    //            }
    //            return;
    //        }
    //
    //        int i = 0;
    //
    //        while(i<count){
    //            final int p = snapPoints.get(i);
    //            final int n = snapPoints.get(++i);
    //
    //            Log.i("i: P,L,N", i + ":"+p+","+x+","+n);
    //
    //            if(x>=p && x<=n){
    //                --i;
    //                if(getSelectedTabPosition()==i) return;
    //                View tabView = Objects.requireNonNull(getTabAt(i)).view;
    //                Log.i("Selected", String.valueOf(Objects.requireNonNull(getTabAt(i)).getText()));
    //                tabView.performClick();
    //                return;
    //            }
    //
    //        }
    //
    //        super.onScrollChanged(x, t, oldX, oldT);
    //    }
    private fun centerView(view: View) {
        scrollTo(getRelativeLeft(view) - sp - view.paddingLeft, 0)
    }

    private fun getRelativeLeft(myView: View): Int {
        return if (myView.parent === myView.rootView) myView.left else myView.left + getRelativeLeft(
            myView.parent as View
        )
    }
}
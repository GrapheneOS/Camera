package app.grapheneos.camera.ui;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;

import com.google.android.material.tabs.TabLayout;

public class BottomTabLayout extends TabLayout {

//    private final ArrayList<Integer> snapPoints = new ArrayList<>();
//    private int count = 0;

    public BottomTabLayout(Context context) {
        super(context);
    }

    public BottomTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BottomTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private int sp = 0;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        ViewGroup tabParent = (ViewGroup)getChildAt(0);

        View firstTab = tabParent.getChildAt(0);
        View lastTab = tabParent.getChildAt(tabParent.getChildCount()-1);
        sp = (getWidth()/2) - (firstTab.getWidth()/2);
        ViewCompat.setPaddingRelative(getChildAt(0), sp,0,(getWidth()/2) - (lastTab.getWidth()/2),0);

        View centerTab = tabParent.getChildAt(tabParent.getChildCount()/2);
        centerView(centerTab);

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

    public void centerView(View view){
        scrollTo(getRelativeLeft(view) - sp - view.getPaddingLeft() , 0);
    }

    private int getRelativeLeft(View myView) {
        if (myView.getParent() == myView.getRootView())
            return myView.getLeft();
        else
            return myView.getLeft() + getRelativeLeft((View) myView.getParent());
    }
}
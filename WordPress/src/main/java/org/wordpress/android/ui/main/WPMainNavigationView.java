package org.wordpress.android.ui.main;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.internal.BottomNavigationItemView;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.BottomNavigationView.OnNavigationItemReselectedListener;
import android.support.design.widget.BottomNavigationView.OnNavigationItemSelectedListener;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import org.wordpress.android.R;
import org.wordpress.android.ui.main.WPMainActivity.OnScrollToTopListener;
import org.wordpress.android.ui.notifications.NotificationsListFragment;
import org.wordpress.android.ui.prefs.AppPrefs;
import org.wordpress.android.ui.reader.ReaderPostListFragment;
import org.wordpress.android.util.AniUtils;
import org.wordpress.android.util.AniUtils.Duration;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

import java.lang.reflect.Field;

import static android.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE;

/*
 * Bottom navigation view and related adapter used by the main activity for the
 * four primary views - note that we ignore the built-in icons and labels and
 * insert our own custom views so we have more control over their appearance
 */
public class WPMainNavigationView extends BottomNavigationView
        implements OnNavigationItemSelectedListener, OnNavigationItemReselectedListener {
    private static final int NUM_PAGES = 5;

    static final int PAGE_MY_SITE = 0;
    static final int PAGE_READER = 1;
    static final int PAGE_NEW_POST = 2;
    static final int PAGE_ME = 3;
    static final int PAGE_NOTIFS = 4;

    private NavAdapter mNavAdapter;
    private FragmentManager mFragmentManager;
    private OnPageListener mListener;
    private int mPrevPosition = -1;

    interface OnPageListener {
        void onPageChanged(int position);
        void onNewPostButtonClicked();
    }

    public WPMainNavigationView(Context context) {
        super(context);
    }

    public WPMainNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WPMainNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void init(@NonNull FragmentManager fm, @NonNull OnPageListener listener) {
        mFragmentManager = fm;
        mListener = listener;

        mNavAdapter = new NavAdapter();
        assignNavigationListeners(true);
        disableShiftMode();

        // overlay each item with our custom view
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) getChildAt(0);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < getMenu().size(); i++) {
            BottomNavigationItemView itemView = (BottomNavigationItemView) menuView.getChildAt(i);
            View customView;
            // remove the background ripple and use a different layout for the post button
            if (i == PAGE_NEW_POST) {
                itemView.setBackground(null);
                customView = inflater.inflate(R.layout.navbar_post_item, menuView, false);
            } else {
                customView = inflater.inflate(R.layout.navbar_item, menuView, false);
                TextView txtLabel = customView.findViewById(R.id.nav_label);
                ImageView imgIcon = customView.findViewById(R.id.nav_icon);
                txtLabel.setText(getTitleForPosition(i));
                txtLabel.setContentDescription(getContentDescriptionForPosition(i));
                imgIcon.setImageResource(getDrawableResForPosition(i));
            }

            itemView.addView(customView);
        }

        int position = AppPrefs.getMainPageIndex();
        setCurrentPosition(position);
    }

    /*
     * uses reflection to disable "shift mode" so the item are equal width
     */
    @SuppressLint("RestrictedApi")
    private void disableShiftMode() {
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) getChildAt(0);
        try {
            Field shiftingMode = menuView.getClass().getDeclaredField("mShiftingMode");
            shiftingMode.setAccessible(true);
            shiftingMode.setBoolean(menuView, false);
            shiftingMode.setAccessible(false);
            for (int i = 0; i < menuView.getChildCount(); i++) {
                BottomNavigationItemView item = (BottomNavigationItemView) menuView.getChildAt(i);
                item.setShiftingMode(false);
                // force the view to update
                item.setChecked(item.getItemData().isChecked());
            }
        } catch (NoSuchFieldException e) {
            AppLog.e(T.MAIN, "Unable to disable shift mode", e);
        } catch (IllegalAccessException e) {
            AppLog.e(T.MAIN, "Unable to disable shift mode", e);
        }
    }

    private void assignNavigationListeners(boolean assign) {
        setOnNavigationItemSelectedListener(assign ? this : null);
        setOnNavigationItemReselectedListener(assign ? this : null);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int position = getPositionForItemId(item.getItemId());
        if (position == PAGE_NEW_POST) {
            handlePostButtonClicked();
            return false;
        } else {
            setCurrentPosition(position, false);
            mListener.onPageChanged(position);
            return true;
        }
    }

    private void handlePostButtonClicked() {
        View postView = getItemView(PAGE_NEW_POST);

        // animate the button icon before telling the listener the post button was clicked - this way
        // the user sees the animation before the editor appears
        AniUtils.startAnimation(postView, R.anim.navbar_button_scale, new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                // noop
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                mListener.onNewPostButtonClicked();
            }
            @Override
            public void onAnimationRepeat(Animation animation) {
                // noop
            }
        });
    }

    @Override
    public void onNavigationItemReselected(@NonNull MenuItem item) {
        // scroll the active fragment's contents to the top when user re-taps the current item
        int position = getPositionForItemId(item.getItemId());
        if (position != PAGE_NEW_POST) {
            Fragment fragment = mNavAdapter.getFragment(position);
            if (fragment instanceof OnScrollToTopListener) {
                ((OnScrollToTopListener) fragment).onScrollToTop();
            }
        }
    }

    Fragment getActiveFragment() {
        return mNavAdapter.getFragment(getCurrentPosition());
    }

    private int getPositionForItemId(@IdRes int itemId) {
        switch (itemId) {
            case R.id.nav_sites:
                return PAGE_MY_SITE;
            case R.id.nav_reader:
                return PAGE_READER;
            case R.id.nav_write:
                return PAGE_NEW_POST;
            case R.id.nav_me:
                return PAGE_ME;
            default:
                return PAGE_NOTIFS;
        }
    }

    private @IdRes int getItemIdForPosition(int position) {
        switch (position) {
            case PAGE_MY_SITE:
                return R.id.nav_sites;
            case PAGE_READER:
                return R.id.nav_reader;
            case PAGE_NEW_POST:
                return R.id.nav_write;
            case PAGE_ME:
                return R.id.nav_me;
            default:
                return R.id.nav_notifications;
        }
    }

    int getCurrentPosition() {
        return getPositionForItemId(getSelectedItemId());
    }

    void setCurrentPosition(int position) {
        setCurrentPosition(position, true);
    }

    private void setCurrentPosition(int position, boolean ensureSelected) {
        // new post page can't be selected, only tapped
        if (position == PAGE_NEW_POST) {
            return;
        }

        // remove the title and selected state from the previously selected item
        if (mPrevPosition > -1) {
            showTitleForPosition(mPrevPosition, false);
            setImageViewSelected(mPrevPosition, false);
        }

        // set the title and selected state from the newly selected item
        showTitleForPosition(position, true);
        setImageViewSelected(position, true);

        AppPrefs.setMainPageIndex(position);
        mPrevPosition = position;

        if (ensureSelected) {
            // temporarily disable the nav listeners so they don't fire when we change the selected page
            assignNavigationListeners(false);
            try {
                setSelectedItemId(getItemIdForPosition(position));
            } finally {
                assignNavigationListeners(true);
            }
        }

        Fragment fragment = mNavAdapter.getFragment(position);
        if (fragment != null) {
            mFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .setTransition(TRANSIT_FRAGMENT_FADE)
                    .commit();
        }
    }

    /*
     * ideally we'd use a color selector to tint the icon based on its selected state, but prior to
     * API 21 setting a color selector via XML will crash the app, and setting it programmatically
     * will have no effect
     */
    private void setImageViewSelected(int position, boolean isSelected) {
        int color = getResources().getColor(isSelected ? R.color.blue_medium : R.color.grey_lighten_10);
        getImageViewForPosition(position).setColorFilter(color, android.graphics.PorterDuff.Mode.MULTIPLY);
    }

    @DrawableRes private int getDrawableResForPosition(int position) {
        switch (position) {
            case PAGE_MY_SITE:
                return R.drawable.ic_my_sites_white_32dp;
            case PAGE_READER:
                return R.drawable.ic_reader_white_32dp;
            case PAGE_NEW_POST:
                return R.drawable.ic_create_white_24dp;
            case PAGE_ME:
                return R.drawable.ic_user_circle_white_32dp;
            default:
                return R.drawable.ic_bell_white_32dp;
        }
    }

    CharSequence getTitleForPosition(int position) {
        @StringRes int idRes;
        switch (position) {
            case PAGE_MY_SITE:
                idRes = R.string.my_site_section_screen_title;
                break;
            case PAGE_READER:
                idRes = R.string.reader_screen_title;
                break;
            case PAGE_NEW_POST:
                idRes = R.string.write_post;
                break;
            case PAGE_ME:
                idRes = R.string.me_section_screen_title;
                break;
            default:
                idRes = R.string.notifications_screen_title;
                break;
        }
        return getContext().getString(idRes);
    }

    CharSequence getContentDescriptionForPosition(int position) {
        @StringRes int idRes;
        switch (position) {
            case PAGE_MY_SITE:
                idRes = R.string.tabbar_accessibility_label_my_site;
                break;
            case PAGE_READER:
                idRes = R.string.tabbar_accessibility_label_reader;
                break;
            case PAGE_NEW_POST:
                idRes = R.string.tabbar_accessibility_label_write;
                break;
            case PAGE_ME:
                idRes = R.string.tabbar_accessibility_label_me;
                break;
            default:
                idRes = R.string.tabbar_accessibility_label_notifications;
                break;
        }
        return getContext().getString(idRes);
    }

    private TextView getTitleViewForPosition(int position) {
        if (position == PAGE_NEW_POST) {
            return null;
        }
        BottomNavigationItemView itemView = getItemView(position);
        return itemView.findViewById(R.id.nav_label);
    }

    private ImageView getImageViewForPosition(int position) {
        BottomNavigationItemView itemView = getItemView(position);
        return itemView.findViewById(R.id.nav_icon);
    }

    private void showTitleForPosition(int position, boolean show) {
        TextView txtTitle = getTitleViewForPosition(position);
        if (txtTitle != null) {
            txtTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    Fragment getFragment(int position) {
        return mNavAdapter.getFragment(position);
    }

    private BottomNavigationItemView getItemView(int position) {
        if (isValidPosition(position)) {
            BottomNavigationMenuView menuView = (BottomNavigationMenuView) getChildAt(0);
            return (BottomNavigationItemView) menuView.getChildAt(position);
        }
        return null;
    }

    /*
     * show or hide the badge on the notification page
     */
    void showNoteBadge(boolean showBadge) {
        BottomNavigationItemView notifView = getItemView(PAGE_NOTIFS);
        View badgeView = notifView.findViewById(R.id.badge);

        int currentVisibility = badgeView.getVisibility();
        int newVisibility = showBadge ? View.VISIBLE : View.GONE;
        if (currentVisibility == newVisibility) {
            return;
        }

        if (showBadge) {
            AniUtils.fadeIn(badgeView, Duration.MEDIUM);
        } else {
            AniUtils.fadeOut(badgeView, Duration.MEDIUM);
        }
    }

    private boolean isValidPosition(int position) {
        return (position >= 0 && position < NUM_PAGES);
    }

    private class NavAdapter {
        private final SparseArray<Fragment> mFragments = new SparseArray<>(NUM_PAGES);

        private Fragment createFragment(int position) {
            Fragment fragment;
            switch (position) {
                case PAGE_MY_SITE:
                    fragment = MySiteFragment.newInstance();
                    break;
                case PAGE_READER:
                    fragment = ReaderPostListFragment.newInstance();
                    break;
                case PAGE_ME:
                    fragment = MeFragment.newInstance();
                    break;
                case PAGE_NOTIFS:
                    fragment = NotificationsListFragment.newInstance();
                    break;
                default:
                    return null;
            }

            mFragments.put(position, fragment);
            return fragment;
        }

        Fragment getFragment(int position) {
            if (isValidPosition(position) && mFragments.get(position) != null) {
              return mFragments.get(position);
            } else {
                return createFragment(position);
            }
        }
    }
}
